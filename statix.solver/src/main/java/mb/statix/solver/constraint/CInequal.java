package mb.statix.solver.constraint;

import java.util.Optional;

import org.metaborg.util.functions.Function1;

import com.google.common.collect.ImmutableSet;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.unification.IUnifier;
import mb.statix.solver.Completeness;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IDebugContext;
import mb.statix.solver.Result;
import mb.statix.solver.State;

public class CInequal implements IConstraint {

    private final ITerm term1;
    private final ITerm term2;

    public CInequal(ITerm term1, ITerm term2) {
        this.term1 = term1;
        this.term2 = term2;
    }

    @Override public IConstraint apply(Function1<ITerm, ITerm> map) {
        return new CInequal(map.apply(term1), map.apply(term2));
    }

    @Override public Optional<Result> solve(State state, Completeness completeness, IDebugContext debug) {
        final IUnifier.Immutable unifier = state.unifier();
        if(unifier.areUnequal(term1, term2)) {
            return Optional.of(Result.of(state, ImmutableSet.of()));
        } else {
            return Optional.empty();
        }
    }

    @Override public String toString(IUnifier unifier) {
        final StringBuilder sb = new StringBuilder();
        sb.append(unifier.toString(term1));
        sb.append(" != ");
        sb.append(unifier.toString(term2));
        return sb.toString();
    }

    @Override public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(term1);
        sb.append(" != ");
        sb.append(term2);
        return sb.toString();
    }

}