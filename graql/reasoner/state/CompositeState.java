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

package grakn.core.graql.reasoner.state;

import grakn.core.concept.answer.ConceptMap;
import grakn.core.graql.reasoner.explanation.CompositeExplanation;
import grakn.core.graql.reasoner.query.CompositeQuery;
import grakn.core.graql.reasoner.query.ReasonerAtomicQuery;
import grakn.core.graql.reasoner.query.ResolvableQuery;
import grakn.core.kb.graql.reasoner.unifier.Unifier;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * <p>
 * Query state corresponding to a conjunctive query with negated patterns present(CompositeQuery).
 *
 * Q = A ∧ {...} ∧ ¬B ∧ ¬C ∧ {...}
 *
 * Now each answer x to query Q has to belong to the set:
 *
 * {x : x ∈ A ∧ x !∈ B ∧ x !∈ C ∧ {...}}
 *
 * or equivalently:
 *
 * {x : x ∈ A x ∈ B^C ∧ x ∈ C^C ∧ {...}}
 *
 * where upper C letter marks set complement.
 *
 * As a result the answer set ans(Q) is equal to:
 *
 * ans(Q) = ans(A) \ [ ( ans(A) ∩ ans(B) ) ∪ ( ans(A) ∩ ans(C) ) ]
 *
 * or equivalently
 *
 * ans(Q) = ans(A) ∩ ans(B^C) ∩ ans(C^C)
 *
 * </p>
 *
 * @author Kasper Piskorski
 */
public class CompositeState extends AnswerPropagatorState<CompositeQuery> {

    private final Set<ResolvableQuery> complements;

    public CompositeState(CompositeQuery query, ConceptMap sub, Unifier u, AnswerPropagatorState parent, Set<ReasonerAtomicQuery> subGoals) {
        super(query, sub, u, parent, subGoals);
        this.complements = getQuery().getComplementQueries();
    }

    @Override
    Iterator<ResolutionState> generateChildStateIterator() {
        return getQuery().innerStateIterator(this, getVisitedSubGoals());
    }

    @Override
    ConceptMap consumeAnswer(AnswerState state) {
        ConceptMap sub = state.getSubstitution();
        return new ConceptMap(sub.map(), new CompositeExplanation(sub), getQuery().withSubstitution(sub).getPattern());
    }

    @Override
    public ResolutionState propagateAnswer(AnswerState state) {
        ConceptMap answer = consumeAnswer(state);

        /*
        NB:negation queries are not executed till completion so we want to leave the global subGoals intact - make a copy.
        If  we used the same subgoals, we could end up with a query which answers weren't fully consumed but that was marked as visited.
        As a result, if it happens that a negated query has multiple answers and is visited more than a single time - because of the admissibility check, answers might be missed.
        */
        Set<ReasonerAtomicQuery> subGoals = new HashSet<>(getVisitedSubGoals());
        boolean isNegationSatisfied = complements.stream()
                .map(q -> q.withSubstitution(answer))
                .noneMatch(q -> q.resolve(subGoals, true).findFirst().isPresent());

        return isNegationSatisfied ?
                new AnswerState(
                        new ConceptMap(answer.map(), answer.explanation(), getQuery().getPattern(answer.map())),
                        getUnifier(),
                        getParentState())
                : null;
    }
}
