package mb.statix.solver;

import org.metaborg.util.functions.Predicate2;
import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.ImmutableSet;

import io.usethesource.capsule.Set;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.unification.IUnifier;

public class Completeness {

    private final Set.Immutable<IConstraint> incomplete;

    public Completeness() {
        this(Set.Immutable.of());
    }

    public Completeness(Set.Immutable<IConstraint> incomplete) {
        this.incomplete = incomplete;
    }

    public boolean isComplete(ITerm scope, ITerm label, State state) {
        final IUnifier unifier = state.unifier();
        final Predicate2<ITerm, ITerm> equal = (t1, t2) -> {
            return t2.equals(label) && unifier.areEqual(t1, scope).orElse(true);
        };
        return incomplete.stream().flatMap(c -> Iterables2.stream(c.scopeExtensions(state.spec())))
                .noneMatch(sl -> equal.test(sl._1(), sl._2()));
    }

    public Completeness add(IConstraint constraint) {
        return new Completeness(incomplete.__insert(constraint));
    }

    public Completeness addAll(Iterable<IConstraint> constraints) {
        return new Completeness(incomplete.__insertAll(ImmutableSet.copyOf(constraints)));
    }

    public Completeness remove(IConstraint constraint) {
        return new Completeness(incomplete.__remove(constraint));
    }

    public Completeness removeAll(Iterable<IConstraint> constraints) {
        return new Completeness(incomplete.__removeAll(ImmutableSet.copyOf(constraints)));
    }

}