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

package grakn.core.graql.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import grakn.core.graql.answer.ConceptMap;
import grakn.core.graql.concept.Attribute;
import grakn.core.graql.concept.Concept;
import grakn.core.graql.concept.ConceptId;
import grakn.core.graql.concept.Entity;
import grakn.core.graql.concept.EntityType;
import grakn.core.graql.concept.Label;
import grakn.core.graql.concept.Relation;
import grakn.core.graql.concept.Role;
import grakn.core.graql.concept.Thing;
import grakn.core.graql.exception.GraqlQueryException;
import grakn.core.graql.graph.MovieGraph;
import grakn.core.graql.internal.Schema;
import grakn.core.graql.query.pattern.Pattern;
import grakn.core.graql.query.pattern.property.IsaProperty;
import grakn.core.graql.query.pattern.statement.Statement;
import grakn.core.graql.query.pattern.statement.Variable;
import grakn.core.rule.GraknTestServer;
import grakn.core.server.Transaction;
import grakn.core.server.exception.InvalidKBException;
import grakn.core.server.session.SessionImpl;
import grakn.core.server.session.TransactionOLTP;
import graql.exception.GraqlException;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static grakn.core.graql.internal.Schema.MetaSchema.ENTITY;
import static grakn.core.graql.query.Graql.type;
import static grakn.core.graql.query.Graql.var;
import static grakn.core.util.GraqlTestUtil.assertExists;
import static grakn.core.util.GraqlTestUtil.assertNotExists;
import static graql.exception.ErrorMessage.NO_PATTERNS;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "Duplicates"})
public class InsertQueryIT {

    private static final Statement w = var("w");
    private static final Statement x = var("x");
    private static final Statement y = var("y");
    private static final Statement z = var("z");

    private static final String title = "title";

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @ClassRule
    public static final GraknTestServer graknServer = new GraknTestServer();

    public static SessionImpl session;

    private TransactionOLTP tx;

    @BeforeClass
    public static void newSession() {
        session = graknServer.sessionWithNewKeyspace();
        MovieGraph.load(session);
    }

    @Before
    public void newTransaction() {
        tx = session.transaction(Transaction.Type.WRITE);
    }

    @After
    public void closeTransaction() {
        tx.close();
    }

    @AfterClass
    public static void closeSession() {
        session.close();
    }

    @Test
    public void testInsertId() {
        assertInsert(var("x").has("name", "abc").isa("genre"));
    }

    @Test
    public void testInsertValue() {
        assertInsert(var("x").val(LocalDateTime.of(1992, 10, 7, 13, 14, 15)).isa("release-date"));
    }

    @Test
    public void testInsertIsa() {
        assertInsert(var("x").has("title", "Titanic").isa("movie"));
    }

    @Test
    public void testInsertMultiple() {
        assertInsert(
                var("x").has("name", "123").isa("person"),
                var("y").val(123L).isa("runtime"),
                var("z").isa("language")
        );
    }

    @Test
    public void testInsertResource() {
        assertInsert(var("x").isa("movie").has("title", "Gladiator").has("runtime", 100L));
    }

    @Test
    public void testInsertName() {
        assertInsert(var("x").isa("movie").has("title", "Hello"));
    }

    @Test
    public void testInsertRelation() {
        Statement rel = var("r").isa("has-genre").rel("genre-of-production", "x").rel("production-with-genre", "y");
        Statement x = var("x").has("title", "Godfather").isa("movie");
        Statement y = var("y").has("name", "comedy").isa("genre");
        Statement[] vars = new Statement[]{rel, x, y};
        Pattern[] patterns = new Pattern[]{rel, x, y};

        assertNotExists(tx, patterns);

        tx.execute(Graql.insert(vars));
        assertExists(tx, patterns);

        tx.execute(Graql.match(patterns).delete("r"));
        assertNotExists(tx, patterns);
    }

    @Test
    public void testInsertSameVarName() {
        tx.execute(Graql.insert(var("x").has("title", "SW"), var("x").has("title", "Star Wars").isa("movie")));

        assertExists(tx, var().isa("movie").has("title", "SW"));
        assertExists(tx, var().isa("movie").has("title", "Star Wars"));
        assertExists(tx, var().isa("movie").has("title", "SW").has("title", "Star Wars"));
    }

    @Test
    public void testInsertRepeat() {
        Statement language = var("x").has("name", "123").isa("language");
        InsertQuery query = Graql.insert(language);

        assertEquals(0, tx.stream(Graql.match(language)).count());
        tx.execute(query);
        assertEquals(1, tx.stream(Graql.match(language)).count());
        tx.execute(query);
        assertEquals(2, tx.stream(Graql.match(language)).count());
        tx.execute(query);
        assertEquals(3, tx.stream(Graql.match(language)).count());

        tx.execute(Graql.match(language).delete("x"));
        assertEquals(0, tx.stream(Graql.match(language)).count());
    }

    @Test
    public void testMatchInsertQuery() {
        Statement language1 = var().isa("language").has("name", "123");
        Statement language2 = var().isa("language").has("name", "456");

        tx.execute(Graql.insert(language1, language2));
        assertExists(tx, language1);
        assertExists(tx, language2);

        tx.execute(Graql.match(var("x").isa("language")).insert(var("x").has("name", "HELLO")));
        assertExists(tx, var().isa("language").has("name", "123").has("name", "HELLO"));
        assertExists(tx, var().isa("language").has("name", "456").has("name", "HELLO"));

        tx.execute(Graql.match(var("x").isa("language")).delete("x"));
        assertNotExists(tx, language1);
        assertNotExists(tx, language2);
    }

    @Test
    public void testIterateInsertResults() {
        InsertQuery insert = Graql.insert(
                var("x").has("name", "123").isa("person"),
                var("z").has("name", "xyz").isa("language")
        );

        Set<ConceptMap> results = tx.stream(insert).collect(toSet());
        assertEquals(1, results.size());
        ConceptMap result = results.iterator().next();
        assertEquals(ImmutableSet.of(new Variable("x"), new Variable("z")), result.vars());
        assertThat(result.concepts(), Matchers.everyItem(notNullValue(Concept.class)));
    }

    @Test
    public void testMatchInsertShouldInsertDataEvenWhenResultsAreNotCollected() {
        Statement language1 = var().isa("language").has("name", "123");
        Statement language2 = var().isa("language").has("name", "456");

        tx.execute(Graql.insert(language1, language2));
        assertExists(tx, language1);
        assertExists(tx, language2);

        InsertQuery query = Graql.match(var("x").isa("language")).insert(var("x").has("name", "HELLO"));
        tx.stream(query);

        assertExists(tx, var().isa("language").has("name", "123").has("name", "HELLO"));
        assertExists(tx, var().isa("language").has("name", "456").has("name", "HELLO"));
    }

    @Test
    public void testErrorWhenInsertWithPredicate() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage("predicate");
        tx.execute(Graql.insert(var().id("123").gt(3)));
    }

    @Test
    public void testErrorWhenInsertWithMultipleIds() {
        exception.expect(GraqlException.class);
        exception.expectMessage(allOf(containsString("id"), containsString("123"), containsString("456")));
        tx.execute(Graql.insert(var().id("123").id("456").isa("movie")));
    }

    @Test
    public void whenInsertingAResourceWithMultipleValues_Throw() {
        Statement varPattern = var().val("123").val("456").isa("title");

        exception.expect(GraqlQueryException.class);
        exception.expectMessage(isOneOf(
                GraqlQueryException.insertMultipleProperties(varPattern, "", "123", "456").getMessage(),
                GraqlQueryException.insertMultipleProperties(varPattern, "", "456", "123").getMessage()
        ));

        tx.execute(Graql.insert(varPattern));
    }

    @Test
    public void testErrorWhenSubRelation() {
        exception.expect(IllegalArgumentException.class);
        tx.execute(Graql.insert(
                var().sub("has-genre").rel("genre-of-production", "x").rel("production-with-genre", "y"),
                var("x").id("Godfather").isa("movie"),
                var("y").id("comedy").isa("genre")
        ));
    }

    @Test
    public void testInsertRepeatType() {
        assertInsert(var("x").has("title", "WOW A TITLE").isa("movie").isa("movie"));
    }

    @Test
    public void testKeyCorrectUsage() throws InvalidKBException {
        tx.execute(Graql.define(
                type("a-new-type").sub("entity").key("a-new-resource-type"),
                type("a-new-resource-type").sub(Schema.MetaSchema.ATTRIBUTE.getLabel().getValue()).datatype(Query.DataType.STRING)
        ));

        tx.execute(Graql.insert(var().isa("a-new-type").has("a-new-resource-type", "hello")));
    }

    @Test
    public void whenInsertingAThingWithTwoKeyResources_Throw() throws InvalidKBException {
        tx.execute(Graql.define(
                type("a-new-type").sub("entity").key("a-new-attribute-type"),
                type("a-new-attribute-type").sub(Schema.MetaSchema.ATTRIBUTE.getLabel().getValue()).datatype(Query.DataType.STRING)
        ));

        tx.execute(Graql.insert(
                var().isa("a-new-type").has("a-new-attribute-type", "hello").has("a-new-attribute-type", "goodbye")
        ));

        exception.expect(InvalidKBException.class);
        tx.commit();
    }

    @Ignore // TODO: Un-ignore this when constraints are designed and implemented
    @Test
    public void testKeyUniqueValue() throws InvalidKBException {
        tx.execute(Graql.define(
                type("a-new-type").sub("entity").key("a-new-resource-type"),
                type("a-new-resource-type")
                        .sub(type(Schema.MetaSchema.ATTRIBUTE.getLabel().getValue()))
                        .datatype(Query.DataType.STRING)
        ));

        tx.execute(Graql.insert(
                var("x").isa("a-new-type").has("a-new-resource-type", "hello"),
                var("y").isa("a-new-type").has("a-new-resource-type", "hello")
        ));

        exception.expect(InvalidKBException.class);
        tx.commit();
    }

    @Test
    public void testKeyRequiredOwner() throws InvalidKBException {
        tx.execute(Graql.define(
                type("a-new-type").sub("entity").key("a-new-resource-type"),
                type("a-new-resource-type").sub(Schema.MetaSchema.ATTRIBUTE.getLabel().getValue()).datatype(Query.DataType.STRING)
        ));

        tx.execute(Graql.insert(var().isa("a-new-type")));

        exception.expect(InvalidKBException.class);
        tx.commit();
    }

    @Test
    public void whenExecutingAnInsertQuery_ResultContainsAllInsertedVars() {
        Statement x = var("x");
        Statement type = var("type");

        // Note that two variables refer to the same type. They should both be in the result
        InsertQuery query = Graql.insert(x.isa(type), type.type("movie"));

        ConceptMap result = tx.stream(query).iterator().next();
        assertThat(result.vars(), containsInAnyOrder(x.var(), type.var()));
        assertEquals(result.get(type.var()), result.get(x.var()).asEntity().type());
        assertEquals(result.get(type.var()).asType().label(), Label.of("movie"));
    }

    @Test
    public void whenAddingAnAttributeRelationshipWithProvenance_TheAttributeAndProvenanceAreAdded() {
        InsertQuery query = Graql.insert(
                y.has("provenance", z.val("Someone told me")),
                w.isa("movie").has(title, x.val("My Movie"), y)
        );

        ConceptMap answer = Iterables.getOnlyElement(tx.execute(query));

        Entity movie = answer.get(w.var()).asEntity();
        Attribute<String> theTitle = answer.get(x.var()).asAttribute();
        Relation hasTitle = answer.get(y.var()).asRelation();
        Attribute<String> provenance = answer.get(z.var()).asAttribute();

        assertThat(hasTitle.rolePlayers().toArray(), arrayContainingInAnyOrder(movie, theTitle));
        assertThat(hasTitle.attributes().toArray(), arrayContaining(provenance));
    }

    @Test
    public void whenAddingProvenanceToAnExistingRelationship_TheProvenanceIsAdded() {
        InsertQuery query = Graql.match(w.isa("movie").has(title, x.val("The Muppets"), y))
                .insert(x, w, y.has("provenance", z.val("Someone told me")));

        ConceptMap answer = Iterables.getOnlyElement(tx.execute(query));

        Entity movie = answer.get(w.var()).asEntity();
        Attribute<String> theTitle = answer.get(x.var()).asAttribute();
        Relation hasTitle = answer.get(y.var()).asRelation();
        Attribute<String> provenance = answer.get(z.var()).asAttribute();

        assertThat(hasTitle.rolePlayers().toArray(), arrayContainingInAnyOrder(movie, theTitle));
        assertThat(hasTitle.attributes().toArray(), arrayContaining(provenance));
    }

    @Test
    public void testErrorWhenInsertRelationWithEmptyRolePlayer() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(
                allOf(containsString("$y"), containsString("id"), containsString("isa"), containsString("sub"))
        );
        tx.execute(Graql.insert(
                var().rel("genre-of-production", "x").rel("production-with-genre", "y").isa("has-genre"),
                var("x").isa("genre").has("name", "drama")
        ));
    }

    @Test
    public void testErrorWhenAddingInstanceOfConcept() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(
                allOf(containsString("meta-type"), containsString("my-thing"), containsString(Schema.MetaSchema.THING.getLabel().getValue()))
        );
        tx.execute(Graql.insert(var("my-thing").isa(Schema.MetaSchema.THING.getLabel().getValue())));
    }

    @Test
    public void whenInsertingAResourceWithoutAValue_Throw() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(allOf(containsString("name")));
        tx.execute(Graql.insert(var("x").isa("name")));
    }

    @Test
    public void whenInsertingAnInstanceWithALabel_Throw() {
        exception.expect(IllegalArgumentException.class);
        tx.execute(Graql.insert(type("abc").isa("movie")));
    }

    @Test
    public void whenInsertingAResourceWithALabel_Throw() {
        exception.expect(IllegalArgumentException.class);
        tx.execute(Graql.insert(type("bobby").val("bob").isa("name")));
    }

    @Test
    public void testInsertDuplicatePattern() {
        tx.execute(Graql.insert(var().isa("person").has("name", "a name"), var().isa("person").has("name", "a name")));
        assertEquals(2, tx.stream(Graql.match(x.has("name", "a name"))).count());
    }

    @Test
    public void testInsertResourceOnExistingId() {
        ConceptId apocalypseNow = tx.stream(Graql.match(var("x").has("title", "Apocalypse Now")).get("x"))
                .map(ans -> ans.get("x")).findAny().get().id();

        assertNotExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
        tx.execute(Graql.insert(var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow")));
        assertExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
    }

    @Test
    public void testInsertResourceOnExistingIdWithType() {
        ConceptId apocalypseNow = tx.stream(Graql.match(var("x").has("title", "Apocalypse Now")).get("x"))
                .map(ans -> ans.get("x")).findAny().get().id();

        assertNotExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
        tx.execute(Graql.insert(var().id(apocalypseNow.getValue()).isa("movie").has("title", "Apocalypse Maybe Tomorrow")));
        assertExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
    }

    @Test
    public void testInsertResourceOnExistingResourceId() {
        ConceptId apocalypseNow = tx.stream(Graql.match(var("x").val("Apocalypse Now")).get("x"))
                .map(ans -> ans.get("x")).findAny().get().id();

        assertNotExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Not Right Now"));
        tx.execute(Graql.insert(var().id(apocalypseNow.getValue()).has("title", "Apocalypse Not Right Now")));
        assertExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Not Right Now"));
    }

    @Test
    public void testInsertResourceOnExistingResourceIdWithType() {
        ConceptId apocalypseNow = tx.stream(Graql.match(var("x").val("Apocalypse Now")).get("x"))
                .map(ans -> ans.get("x")).findAny().get().id();

        assertNotExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
        tx.execute(Graql.insert(var().id(apocalypseNow.getValue()).isa("title").has("title", "Apocalypse Maybe Tomorrow")));
        assertExists(tx, var().id(apocalypseNow.getValue()).has("title", "Apocalypse Maybe Tomorrow"));
    }

    @Test
    public void testInsertInstanceWithoutType() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(allOf(containsString("isa")));
        tx.execute(Graql.insert(var().has("name", "Bob")));
    }

    @Test
    public void whenInsertingMultipleRolePlayers_BothRolePlayersAreAdded() {
        List<ConceptMap> results = tx.execute(Graql.match(
                var("g").has("title", "Godfather"),
                var("m").has("title", "The Muppets")
        ).insert(
                var("c").isa("cluster").has("name", "2"),
                var("r").rel("cluster-of-production", "c").rel("production-with-cluster", "g").rel("production-with-cluster", "m").isa("has-cluster")
        ));

        Thing cluster = results.get(0).get("c").asThing();
        Thing godfather = results.get(0).get("g").asThing();
        Thing muppets = results.get(0).get("m").asThing();
        Relation relationship = results.get(0).get("r").asRelation();

        Role clusterOfProduction = tx.getRole("cluster-of-production");
        Role productionWithCluster = tx.getRole("production-with-cluster");

        assertEquals(relationship.rolePlayers().collect(toSet()), ImmutableSet.of(cluster, godfather, muppets));
        assertEquals(relationship.rolePlayers(clusterOfProduction).collect(toSet()), ImmutableSet.of(cluster));
        assertEquals(relationship.rolePlayers(productionWithCluster).collect(toSet()), ImmutableSet.of(godfather, muppets));
    }

    @Test
    public void whenInsertingWithAMatch_ProjectMatchResultsOnVariablesInTheInsert() {
        tx.execute(Graql.define(
                type("maybe-friends").relates("friend").sub("relationship"),
                type("person").plays("friend")
        ));

        InsertQuery query = Graql.match(
                var().rel("actor", x).rel("production-with-cast", z),
                var().rel("actor", y).rel("production-with-cast", z)
        ).insert(
                w.rel("friend", x).rel("friend", y).isa("maybe-friends")
        );

        List<ConceptMap> answers = tx.execute(query);

        for (ConceptMap answer : answers) {
            assertThat(
                    "Should contain only variables mentioned in the insert (so excludes `$z`)",
                    answer.vars(),
                    containsInAnyOrder(x.var(), y.var(), w.var())
            );
        }

        assertEquals("Should contain only distinct results", answers.size(), Sets.newHashSet(answers).size());
    }

    @Test(expected = Exception.class)
    public void matchInsertNullVar() {
        tx.execute(Graql.match(var("x").isa("movie")).insert((Statement) null));
    }

    @Test(expected = Exception.class)
    public void matchInsertNullCollection() {
        tx.execute(Graql.match(var("x").isa("movie")).insert((Collection<? extends Statement>) null));
    }

    @Test
    public void whenMatchInsertingAnEmptyPattern_Throw() {
        exception.expect(GraqlException.class);
        exception.expectMessage(NO_PATTERNS.getMessage());
        tx.execute(Graql.match(var()).insert(Collections.EMPTY_SET));
    }

    @Test(expected = Exception.class)
    public void insertNullVar() {
        tx.execute(Graql.insert((Statement) null));
    }

    @Test(expected = Exception.class)
    public void insertNullCollection() {
        tx.execute(Graql.insert((Collection<? extends Statement>) null));
    }

    @Test
    public void whenInsertingAnEmptyPattern_Throw() {
        exception.expect(GraqlException.class);
        exception.expectMessage(NO_PATTERNS.getMessage());
        tx.execute(Graql.insert(Collections.EMPTY_SET));
    }

    @Test
    public void whenSettingTwoTypes_Throw() {
        EntityType movie = tx.getEntityType("movie");
        EntityType person = tx.getEntityType("person");

        // We have to construct it this way because you can't have two `isa`s normally
        // TODO: less bad way?
        Statement varPattern = Statement.create(
                new Variable("x"),
                new LinkedHashSet<>(ImmutableList.of(new IsaProperty(type("movie")), new IsaProperty(type("person"))))
        );

        // We don't know in what order the message will be
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(isOneOf(
                GraqlQueryException.insertMultipleProperties(varPattern, "isa", movie, person).getMessage(),
                GraqlQueryException.insertMultipleProperties(varPattern, "isa", person, movie).getMessage()
        ));

        tx.execute(Graql.insert(var("x").isa("movie"), var("x").isa("person")));
    }

    @Test
    public void whenSpecifyingExistingConceptIdWithIncorrectType_Throw() {
        EntityType movie = tx.getEntityType("movie");
        EntityType person = tx.getEntityType("person");

        Concept aMovie = movie.instances().iterator().next();

        exception.expect(GraqlQueryException.class);
        exception.expectMessage(GraqlQueryException.insertPropertyOnExistingConcept("isa", person, aMovie).getMessage());

        tx.execute(Graql.insert(var("x").id(aMovie.id().getValue()).isa("person")));
    }

    @Test
    public void whenInsertingASchemaConcept_Throw() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(GraqlQueryException.insertUnsupportedProperty(Query.Property.SUB.toString()).getMessage());

        tx.execute(Graql.insert(type("new-type").sub(type(ENTITY.getLabel().getValue()))));
    }

    @Test
    public void whenModifyingASchemaConceptInAnInsertQuery_Throw() {
        exception.expect(GraqlQueryException.class);
        exception.expectMessage(GraqlQueryException.insertUnsupportedProperty(Query.Property.PLAYS.toString()).getMessage());

        tx.execute(Graql.insert(type("movie").plays("actor")));
    }

    private void assertInsert(Statement... vars) {
        // Make sure vars don't exist
        for (Statement var : vars) {
            assertNotExists(tx, var);
        }

        // Insert all vars
        tx.execute(Graql.insert(vars));

        // Make sure all vars exist
        for (Statement var : vars) {
            assertExists(tx, var);
        }

        // Delete all vars
        for (Statement var : vars) {
            tx.execute(Graql.match(var).delete(var.var()));
        }

        // Make sure vars don't exist
        for (Statement var : vars) {
            assertNotExists(tx, var);
        }
    }
}
