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

package grakn.core.graql.reasoner.atom.task.relate;

import com.google.common.collect.ImmutableMap;
import grakn.core.graql.reasoner.ReasoningContext;
import grakn.core.graql.reasoner.atom.Atom;
import grakn.core.graql.reasoner.atom.AtomicUtil;
import grakn.core.graql.reasoner.atom.binary.AttributeAtom;
import grakn.core.graql.reasoner.atom.binary.IsaAtom;
import grakn.core.graql.reasoner.atom.predicate.ValuePredicate;
import grakn.core.graql.reasoner.cache.SemanticDifference;
import grakn.core.graql.reasoner.cache.VariableDefinition;
import grakn.core.graql.reasoner.unifier.MultiUnifierImpl;
import grakn.core.graql.reasoner.unifier.UnifierImpl;
import grakn.core.graql.reasoner.unifier.UnifierType;
import grakn.core.kb.graql.reasoner.unifier.MultiUnifier;
import grakn.core.kb.graql.reasoner.unifier.Unifier;
import graql.lang.statement.Variable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

public class AttributeSemanticProcessor implements SemanticProcessor<AttributeAtom> {

    private final TypeAtomSemanticProcessor binarySemanticProcessor = new TypeAtomSemanticProcessor();

    @Override
    public Unifier getUnifier(AttributeAtom childAtom, Atom parentAtom, UnifierType unifierType, ReasoningContext ctx) {
        if (!(parentAtom instanceof AttributeAtom)) {
            // in general this >= parent, hence for rule unifiers we can potentially specialise child to match parent
            if (unifierType.equals(UnifierType.RULE)) {
                if (parentAtom instanceof IsaAtom) return childAtom.toIsaAtom().getUnifier(parentAtom, unifierType);
            }
            return UnifierImpl.nonExistent();
        }

        AttributeAtom parent = (AttributeAtom) parentAtom;
        Unifier unifier = new UnifierImpl(ImmutableMap.of(childAtom.getAttributeVariable(), parent.getAttributeVariable()));

        //unify owner isa
        Unifier ownerUnifier = binarySemanticProcessor.getUnifier(childAtom.ownerIsa(), parent.ownerIsa(), unifierType, ctx);
        if (ownerUnifier == null) return UnifierImpl.nonExistent();
        unifier = unifier.merge(ownerUnifier);

        return AtomicUtil.isPredicateCompatible(childAtom, parentAtom, unifier, unifierType, ctx.conceptManager()) ?
                unifier : UnifierImpl.nonExistent();
    }

    @Override
    public MultiUnifier getMultiUnifier(AttributeAtom childAtom, Atom parentAtom, UnifierType unifierType, ReasoningContext ctx) {
        if (!(parentAtom instanceof AttributeAtom)) {
            // in general this >= parent, hence for rule unifiers we can potentially specialise child to match parent
            if (unifierType.equals(UnifierType.RULE)) {
                if (parentAtom instanceof IsaAtom)
                    return childAtom.toIsaAtom().getMultiUnifier(parentAtom, unifierType);
            }
            return MultiUnifierImpl.nonExistent();
        }
        Unifier unifier = getUnifier(childAtom, parentAtom, unifierType, ctx);
        return unifier != null ? new MultiUnifierImpl(unifier) : MultiUnifierImpl.nonExistent();
    }

    @Override
    public SemanticDifference computeSemanticDifference(AttributeAtom parent, Atom child, Unifier unifier, ReasoningContext ctx) {
        SemanticDifference baseDiff = binarySemanticProcessor.computeSemanticDifference(parent.toIsaAtom(), child, unifier, ctx);
        if (!child.isAttributeAtom()) return baseDiff;
        AttributeAtom childAtom = (AttributeAtom) child;
        Set<VariableDefinition> diff = new HashSet<>();

        Variable parentVar = parent.getAttributeVariable();
        Unifier unifierInverse = unifier.inverse();
        Set<ValuePredicate> predicatesToSatisfy = childAtom.getMultiPredicate().stream()
                .flatMap(vp -> vp.unify(unifierInverse).stream()).collect(toSet());
        parent.getMultiPredicate().forEach(predicatesToSatisfy::remove);

        diff.add(new VariableDefinition(parentVar, null, null, new ArrayList<>(), predicatesToSatisfy));
        return baseDiff.merge(new SemanticDifference(diff));
    }
}
