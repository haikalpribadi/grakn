/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.graql.reasoner.query;

import grakn.core.graql.concept.Concept;
import grakn.core.graql.concept.EntityType;
import grakn.core.graql.concept.RelationType;
import grakn.core.graql.concept.Role;
import grakn.core.graql.internal.reasoner.atom.binary.RelationshipAtom;
import grakn.core.graql.internal.reasoner.query.ReasonerAtomicQuery;
import grakn.core.graql.internal.reasoner.query.ReasonerQueries;
import grakn.core.graql.internal.reasoner.query.ReasonerQueryImpl;
import grakn.core.graql.internal.reasoner.rule.InferenceRule;
import grakn.core.graql.internal.reasoner.rule.RuleUtils;
import grakn.core.graql.query.Graql;
import grakn.core.graql.query.pattern.Conjunction;
import grakn.core.graql.query.pattern.Pattern;
import grakn.core.graql.query.pattern.statement.Statement;
import grakn.core.graql.reasoner.graph.GeoGraph;
import grakn.core.rule.GraknTestServer;
import grakn.core.server.Transaction;
import grakn.core.server.session.SessionImpl;
import grakn.core.server.session.TransactionOLTP;
import java.util.Set;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.stream.Collectors.toSet;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@SuppressWarnings("CheckReturnValue")
public class QueryIT {
    @ClassRule
    public static final GraknTestServer server = new GraknTestServer();

    private static SessionImpl geoSession;

    @BeforeClass
    public static void loadContext(){
        geoSession = server.sessionWithNewKeyspace();
        GeoGraph geoGraph = new GeoGraph(geoSession);
        geoGraph.load();
    }

    @AfterClass
    public static void closeSession(){
        geoSession.close();
    }

    @Test
    public void testQueryReiterationCondition_CyclicalRuleGraph(){
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ ($x, $y) isa is-located-in; };";
            ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
            assertTrue(query.requiresReiteration());
        }
    }

    //TODO go over stratification with type hierarchies
    @Ignore
    @Test
    public void testQueryReiterationCondition_CyclicalRuleGraphWithTypeHierarchiesInBodies(){
        try (SessionImpl session = server.sessionWithNewKeyspace()) {
            try (TransactionOLTP tx = session.transaction(Transaction.Type.WRITE)) {

                Role someRole = tx.putRole("someRole");
                tx.putRelationshipType("relation")
                        .relates(someRole);
                RelationType inferredBase = tx.putRelationshipType("inferredBase")
                        .relates(someRole);
                tx.putRelationshipType("inferred")
                        .relates(someRole).sup(inferredBase);
                tx.putEntityType("genericEntity")
                        .plays(someRole);

                tx.putRule("rule1",
                        Graql.parsePattern(
                                "{" +
                                        "($x, $y) isa relation; " +
                                        "($y, $z) isa inferredBase;" +
                                        "};"
                        ),
                        Graql.parsePattern("{ (someRole: $x, someRole: $z) isa inferred; };"));

                tx.commit();
            }
            try (TransactionOLTP tx = session.transaction(Transaction.Type.WRITE)) {
                String patternString = "{ ($x, $y) isa inferred; };";
                ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
                assertTrue(RuleUtils.subGraphIsCyclical(
                        RuleUtils.getRules(tx).map(r -> new InferenceRule(r, tx)).collect(toSet()))
                );
                assertTrue(query.requiresReiteration());
            }
        }
    }

    //TODO go over stratification with type hierarchies
    @Ignore
    @Test
    public void testQueryReiterationCondition_CyclicalRuleGraphWithTypeHierarchiesInHead(){
        try (SessionImpl session = server.sessionWithNewKeyspace()) {
            try (TransactionOLTP tx = session.transaction(Transaction.Type.WRITE)) {

                tx.putEntityType("genericEntity");
                EntityType baseEntity = tx.putEntityType("baseEntity");
                tx.putEntityType("subEntity").sup(baseEntity);

                tx.putRule("rule1",
                        Graql.parsePattern("{$x isa genericEntity;};"),
                        Graql.parsePattern("{$x isa subEntity;};"));
                tx.putRule("rule2",
                        Graql.parsePattern("{$x isa baseEntity;};"),
                        Graql.parsePattern("{$x isa genericEntity;};"));

                tx.commit();
            }
            try (TransactionOLTP tx = session.transaction(Transaction.Type.WRITE)) {
                String patternString = "{ $x isa baseEntity;};";
                ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
                assertTrue(RuleUtils.subGraphIsCyclical(
                        RuleUtils.getRules(tx).map(r -> new InferenceRule(r, tx)).collect(toSet()))
                );
                assertTrue(query.requiresReiteration());
            }
        }
    }

    @Test //simple equality tests between original and a copy of a query
    public void testAlphaEquivalence_QueryCopyIsAlphaEquivalent(){
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ $x isa city;$y isa country;($x, $y) isa is-located-in; };";
            ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
            queryEquivalence(query, query.copy(), true);
        }
    }

    @Test //check two queries are alpha-equivalent - equal up to the choice of free variables
    public void testAlphaEquivalence() {
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ " +
                    "$x isa city, has name 'Warsaw';" +
                    "$y isa region;" +
                    "($x, $y) isa is-located-in;" +
                    "($y, $z) isa is-located-in;" +
                    "$z isa country, has name 'Poland'; };";

            String patternString2 = "{ " +
                    "($r, $ctr) isa is-located-in;" +
                    "($c, $r) isa is-located-in;" +
                    "$c isa city, has name 'Warsaw';" +
                    "$r isa region;" +
                    "$ctr isa country, has name 'Poland'; };";

            ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
            ReasonerQueryImpl query2 = ReasonerQueries.create(conjunction(patternString2, tx), tx);
            queryEquivalence(query, query2, true);
        }
    }

    //TODO
    @Ignore
    @Test
    public void testAlphaEquivalence_chainTreeAndLoopStructure() {
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String chainString = "{" +
                    "($x, $y) isa is-located-in;" +
                    "($y, $z) isa is-located-in;" +
                    "($z, $u) isa is-located-in;" +
                    "}";

            String treeString = "{" +
                    "($x, $y) isa is-located-in;" +
                    "($y, $z) isa is-located-in;" +
                    "($y, $u) isa is-located-in;" +
                    "}";

            String loopString = "{" +
                    "($x, $y) isa is-located-in;" +
                    "($y, $z) isa is-located-in;" +
                    "($z, $x) isa is-located-in;" +
                    "}";

            ReasonerQueryImpl chainQuery = ReasonerQueries.create(conjunction(chainString, tx), tx);
            ReasonerQueryImpl treeQuery = ReasonerQueries.create(conjunction(treeString, tx), tx);
            ReasonerQueryImpl loopQuery = ReasonerQueries.create(conjunction(loopString, tx), tx);
            queryEquivalence(chainQuery, treeQuery, false);
            queryEquivalence(chainQuery, loopQuery, false);
            queryEquivalence(treeQuery, loopQuery, false);
        }
    }

    @Test //tests various configurations of alpha-equivalence with extra type atoms present
    public void testAlphaEquivalence_nonMatchingTypes() {
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String polandId = getConcept(tx, "name", "Poland").id().getValue();
            String patternString = "{ $y id '" + polandId + "'; $y isa country; (geo-entity: $y1, entity-location: $y) isa is-located-in; };";
            String patternString2 = "{ $x1 id '" + polandId + "'; $y isa country; (geo-entity: $x1, entity-location: $x2) isa is-located-in; };";
            String patternString3 = "{ $y id '" + polandId + "'; $x isa city; (geo-entity: $x, entity-location: $y) isa is-located-in; $y isa country; };";
            String patternString4 = "{ $x isa city; (entity-location: $y1, geo-entity: $x) isa is-located-in; };";
            String patternString5 = "{ (geo-entity: $y1, entity-location: $y2) isa is-located-in; };";

            ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
            ReasonerQueryImpl query2 = ReasonerQueries.create(conjunction(patternString2, tx), tx);
            ReasonerQueryImpl query3 = ReasonerQueries.create(conjunction(patternString3, tx), tx);
            ReasonerQueryImpl query4 = ReasonerQueries.create(conjunction(patternString4, tx), tx);
            ReasonerQueryImpl query5 = ReasonerQueries.create(conjunction(patternString5, tx), tx);

            queryEquivalence(query, query2, false);
            queryEquivalence(query, query3, false);
            queryEquivalence(query2, query3, false);
            queryEquivalence(query, query4, false);
            queryEquivalence(query, query5, false);
            queryEquivalence(query3, query4, false);
            queryEquivalence(query3, query5, false);
            queryEquivalence(query4, query5, false);
        }
    }

    @Test //tests alpha-equivalence of queries with indirect types
    public void testAlphaEquivalence_indirectTypes(){
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ (entity-location: $x2, geo-entity: $x1) isa is-located-in;" +
                    "$x1 isa $t1; $t1 sub geoObject; };";
            String patternString2 = "{ (geo-entity: $y1, entity-location: $y2) isa is-located-in;" +
                    "$y1 isa $t2; $t2 sub geoObject; };";

            ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
            ReasonerQueryImpl query2 = ReasonerQueries.create(conjunction(patternString2, tx), tx);
            queryEquivalence(query, query2, true);
        }
    }

    @Test
    public void testAlphaEquivalence_RelationsWithSubstitution(){
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ (role: $x, role: $y);$x id 'V666'; };";
            String patternString2 = "{ (role: $x, role: $y);$y id 'V666'; };";
            String patternString3 = "{ (role: $x, role: $y);$x id 'V666';$y id 'V667'; };";
            String patternString4 = "{ (role: $x, role: $y);$y id 'V666';$x id 'V667'; };";
            String patternString5 = "{ (entity-location: $x, geo-entity: $y);$x id 'V666';$y id 'V667'; };";
            String patternString6 = "{ (entity-location: $x, geo-entity: $y);$y id 'V666';$x id 'V667'; };";
            String patternString7 = "{ (role: $x, role: $y);$x id 'V666';$y id 'V666'; };";
            Conjunction<Statement> pattern = conjunction(patternString, tx);
            Conjunction<Statement> pattern2 = conjunction(patternString2, tx);
            Conjunction<Statement> pattern3 = conjunction(patternString3, tx);
            Conjunction<Statement> pattern4 = conjunction(patternString4, tx);
            Conjunction<Statement> pattern5 = conjunction(patternString5, tx);
            Conjunction<Statement> pattern6 = conjunction(patternString6, tx);
            Conjunction<Statement> pattern7 = conjunction(patternString7, tx);

            ReasonerAtomicQuery query = ReasonerQueries.atomic(pattern, tx);
            ReasonerAtomicQuery query2 = ReasonerQueries.atomic(pattern2, tx);
            ReasonerAtomicQuery query3 = ReasonerQueries.atomic(pattern3, tx);
            ReasonerAtomicQuery query4 = ReasonerQueries.atomic(pattern4, tx);
            ReasonerAtomicQuery query5 = ReasonerQueries.atomic(pattern5, tx);
            ReasonerAtomicQuery query6 = ReasonerQueries.atomic(pattern6, tx);
            ReasonerAtomicQuery query7 = ReasonerQueries.atomic(pattern7, tx);

            queryEquivalence(query, query2, true);
            queryEquivalence(query, query3, false);
            queryEquivalence(query, query4, false);
            queryEquivalence(query, query5, false);
            queryEquivalence(query, query6, false);
            queryEquivalence(query, query7, false);

            queryEquivalence(query2, query3, false);
            queryEquivalence(query2, query4, false);
            queryEquivalence(query2, query5, false);
            queryEquivalence(query2, query6, false);
            queryEquivalence(query2, query7, false);

            queryEquivalence(query3, query4, true);
            queryEquivalence(query3, query5, false);
            queryEquivalence(query3, query6, false);
            queryEquivalence(query3, query7, false);

            queryEquivalence(query4, query5, false);
            queryEquivalence(query4, query6, false);
            queryEquivalence(query4, query7, false);

            queryEquivalence(query5, query6, false);
            queryEquivalence(query5, query7, false);

            queryEquivalence(query6, query7, false);
        }
    }

//    Bug #11150 Relations with resources as single VarPatternAdmin
//    TODO: replace this with different schema than genealogySchema
//    @Test //tests whether directly and indirectly reified relations are equivalent
//    public void testAlphaEquivalence_reifiedRelation(){
//        TransactionImpl<?> tx = genealogySchema.tx();
//        String patternString = "{ $rel (happening: $b, protagonist: $p) isa event-protagonist has event-role 'parent'; };";
//        String patternString2 = "{ $rel (happening: $c, protagonist: $r) isa event-protagonist; $rel has event-role 'parent'; };";
//
//        ReasonerQueryImpl query = ReasonerQueries.create(conjunction(patternString, tx), tx);
//        ReasonerQueryImpl query2 = ReasonerQueries.create(conjunction(patternString2, tx), tx);
//        queryEquivalence(query, query2, true);
//    }

    @Test
    public void testWhenReifyingRelation_ExtraAtomIsCreatedWithUserDefinedName(){
        try(TransactionOLTP tx = geoSession.transaction(Transaction.Type.WRITE)) {
            String patternString = "{ (geo-entity: $x, entity-location: $y) isa is-located-in; };";
            String patternString2 = "{ ($x, $y) has name 'Poland'; };";

            Conjunction<Statement> pattern = conjunction(patternString, tx);
            Conjunction<Statement> pattern2 = conjunction(patternString2, tx);
            ReasonerQueryImpl query = ReasonerQueries.create(pattern, tx);
            ReasonerQueryImpl query2 = ReasonerQueries.create(pattern2, tx);
            assertFalse(query.getAtoms(RelationshipAtom.class).findFirst().orElse(null).isUserDefined());
            assertTrue(query2.getAtoms(RelationshipAtom.class).findFirst().orElse(null).isUserDefined());
            assertEquals(query.getAtoms().size(), 1);
            assertEquals(query2.getAtoms().size(), 2);
        }
    }

    private void queryEquivalence(ReasonerQueryImpl a, ReasonerQueryImpl b, boolean expectation){
        assertEquals(a.toString() + " =? " + b.toString(), a.equals(b), expectation);
        assertEquals(b.toString() + " =? " + a.toString(), b.equals(a), expectation);
        //check hash additionally if need to be equal
        if (expectation) {
            assertTrue(a.toString() + " hash=? " + b.toString(), a.hashCode() == b.hashCode());
        }
    }

    private Conjunction<Statement> conjunction(String patternString, TransactionOLTP tx){
        Set<Statement> vars = Graql.parsePattern(patternString)
                .getDisjunctiveNormalForm().getPatterns()
                .stream().flatMap(p -> p.getPatterns().stream()).collect(toSet());
        return Graql.and(vars);
    }

    private static Concept getConcept(TransactionOLTP tx, String typeLabel, String val){
        return tx.stream(Graql.match((Pattern) Graql.var("x").has(typeLabel, val)).get("x"))
                .map(ans -> ans.get("x")).findAny().get();
    }
}
