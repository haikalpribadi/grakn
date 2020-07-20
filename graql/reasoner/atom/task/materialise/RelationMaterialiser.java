/*
 * Copyright (C) 2020 Grakn Labs
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
 *
 */

package grakn.core.graql.reasoner.atom.task.materialise;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import grakn.core.concept.answer.ConceptMap;
import grakn.core.graql.reasoner.CacheCasting;
import grakn.core.graql.reasoner.ReasoningContext;
import grakn.core.graql.reasoner.atom.binary.RelationAtom;
import grakn.core.graql.reasoner.cache.MultilevelSemanticCache;
import grakn.core.graql.reasoner.query.ReasonerAtomicQuery;
import grakn.core.graql.reasoner.utils.AnswerUtil;
import grakn.core.kb.concept.api.Concept;
import grakn.core.kb.concept.api.Relation;
import grakn.core.kb.concept.api.RelationType;
import grakn.core.kb.concept.api.Role;
import grakn.core.kb.concept.api.Thing;
import grakn.core.kb.concept.manager.ConceptManager;
import graql.lang.statement.Variable;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class RelationMaterialiser implements AtomMaterialiser<RelationAtom> {

    @Override
    public Stream<ConceptMap> materialise(RelationAtom atom, ReasoningContext ctx) {
        RelationType relationType = atom.getSchemaConcept().asRelationType();
        Multimap<Role, Variable> roleVarMap = atom.getRoleVarMap();
        ConceptMap substitution = atom.getParentQuery().getSubstitution();

        //if the relation already exists, only assign roleplayers, otherwise create a new relation
        Relation relation;
        Variable varName = atom.getVarName();
        if (substitution.containsVar(varName)) {
            relation = substitution.get(varName).asRelation();
        } else {
            Relation foundRelation = findRelation(atom, substitution, ctx);
            relation = foundRelation != null ? foundRelation : relationType.addRelationInferred();
        }

        // we make the relation conform to the required number and quantity of each role player
        roleVarMap.asMap()
                .forEach((role, variables) -> {
                    Multiset<Thing> requiredPlayers = HashMultiset.create();
                    variables.forEach(var -> requiredPlayers.add(substitution.get(var).asThing()));
                    relation.rolePlayers(role)
                            .forEach(player -> {
                                // TODO if we have fast neighbor pairwise lookup, we can check if each required player is already in this relation
                                // TODO without looping over all relation players and filtering
                                if (requiredPlayers.isEmpty()) {
                                    // we can short circuit the retrieval of all role players if requirements are satisfied
                                    return;
                                }
                                requiredPlayers.remove(player, 1); // remove single occurence of this player if it exists
                            });

                    // we create each of the remaining required ones
                    requiredPlayers.forEach(newPlayer -> relation.assign(role, newPlayer));
                });

        ConceptMap relationSub = AnswerUtil.joinAnswers(
                getRoleSubstitution(atom, ctx),
                varName.isReturned() ?
                        new ConceptMap(ImmutableMap.of(varName, relation)) :
                        new ConceptMap()
        );

        ConceptMap answer = AnswerUtil.joinAnswers(substitution, relationSub);
        return Stream.of(answer);
    }

    private Relation findRelation(RelationAtom atom, ConceptMap sub, ReasoningContext ctx) {
        ReasonerAtomicQuery query = ctx.queryFactory().atomic(atom).withSubstitution(sub);
        MultilevelSemanticCache queryCache = CacheCasting.queryCacheCast(ctx.queryCache());
        ConceptMap answer = queryCache.getAnswerStream(query).findFirst().orElse(null);

        if (answer == null) queryCache.ackDBCompleteness(query);
        return answer != null ? answer.get(atom.getVarName()).asRelation() : null;
    }

    private ConceptMap getRoleSubstitution(RelationAtom atom, ReasoningContext ctx) {
        Map<Variable, Concept> roleSub = new HashMap<>();
        ConceptManager conceptManager = ctx.conceptManager();
        atom.getRolePredicates(conceptManager).forEach(p -> roleSub.put(p.getVarName(), conceptManager.getConcept(p.getPredicate())));
        return new ConceptMap(roleSub);
    }
}
