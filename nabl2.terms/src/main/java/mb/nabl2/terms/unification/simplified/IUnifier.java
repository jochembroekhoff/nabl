package mb.nabl2.terms.unification.simplified;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.OccursException;

public interface IUnifier {

    Set<ITermVar> varSet();

    Set<ITermVar> freeVarSet();

    /**
     * Return all entries of this unifier.
     */
    Iterable<? extends Entry<ITermVar, ? extends ITerm>> entries();

    boolean isCyclic(ITerm term);

    boolean isGround(ITerm term);

    /**
     * Find the representative term for the given term. The representative itself is not instantiated, to prevent
     * exponential blowup in time or space. If the given term is a variable, the representative term is returned, or the
     * class variable if the variable is free in the unifier. If the given term is not a variable, it is returned
     * unchanged.
     */
    ITerm find(ITerm term);

    /**
     * Unify the two given equalities. Return a diff unifier, or throw if the terms cannot be unified.
     */
    Optional<? extends Result<? extends IUnifier>>
            unifyAll(Iterable<? extends Entry<? extends ITerm, ? extends ITerm>> equalities) throws OccursException;

    /**
     * Return a unifier with the given variables removed from the domain. Returns a substitution to eliminate the
     * variable from terms.
     */
    Result<ISubstitution.Immutable> removeAll(Iterable<ITermVar> vars);

    /**
     * Interface that gives a result and an updated immutable unifier.
     */
    interface Result<T> {

        T result();

        IUnifier unifier();

    }

    /**
     * Implementing this interface allows control over the selection of representatives, and may be used to prevent
     * unification of certain variables.
     */
    interface RepPicker<E extends Throwable> {

        /**
         * Given two variables to be unified, optionally pick the representative, or throw an exception.
         * <ul>
         * <li>Return Optional.empty to use the default rank-based mechanism to pick the representative.</li>
         * <li>Return Optional.of(true) to use the left variable as the representative.</li>
         * <li>Return Optional.of(false) to use the right variable as the representative.</li>
         * <li>Throw E to fail unification with the given exception.</li>
         * </ul>
         */
        Optional<Boolean> pick(ITermVar left, ITermVar right) throws E;

    }

}