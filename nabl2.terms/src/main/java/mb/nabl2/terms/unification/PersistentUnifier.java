package mb.nabl2.terms.unification;

import java.io.Serializable;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.metaborg.util.Ref;
import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.usethesource.capsule.Map;
import io.usethesource.capsule.Set;
import mb.nabl2.terms.IListTerm;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.ListTerms;
import mb.nabl2.terms.Terms;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.substitution.PersistentSubstitution;
import mb.nabl2.util.CapsuleUtil;
import mb.nabl2.util.ImmutableTuple2;
import mb.nabl2.util.Tuple2;

public abstract class PersistentUnifier extends BaseUnifier implements Serializable {

    private static final long serialVersionUID = 42L;

    protected static ITermVar findRep(ITermVar var, Map.Transient<ITermVar, ITermVar> reps) {
        ITermVar rep = reps.get(var);
        if(rep == null) {
            return var;
        } else {
            rep = findRep(rep, reps);
            reps.__put(var, rep);
            return rep;
        }
    }

    ///////////////////////////////////////////
    // class Immutable
    ///////////////////////////////////////////

    public static class Immutable extends PersistentUnifier implements IUnifier.Immutable, Serializable {

        private static final long serialVersionUID = 42L;

        private final boolean finite;

        private final Ref<Map.Immutable<ITermVar, ITermVar>> reps;
        private final Map.Immutable<ITermVar, Integer> ranks;
        private final Map.Immutable<ITermVar, ITerm> terms;
        private final Set.Immutable<Diseq> disequalities;

        Immutable(final boolean finite, final Map.Immutable<ITermVar, ITermVar> reps,
                final Map.Immutable<ITermVar, Integer> ranks, final Map.Immutable<ITermVar, ITerm> terms,
                Set.Immutable<Diseq> disequalities) {
            this.finite = finite;
            this.reps = new Ref<>(reps);
            this.ranks = ranks;
            this.terms = terms;
            this.disequalities = disequalities;
        }

        @Override public boolean isFinite() {
            return finite;
        }

        @Override protected Map.Immutable<ITermVar, ITermVar> reps() {
            return reps.get();
        }

        @Override protected Map.Immutable<ITermVar, ITerm> terms() {
            return terms;
        }

        @Override public Set.Immutable<Diseq> disequalities() {
            return disequalities;
        }

        @Override public ITermVar findRep(ITermVar var) {
            final Map.Transient<ITermVar, ITermVar> reps = this.reps.get().asTransient();
            final ITermVar rep = findRep(var, reps);
            this.reps.set(reps.freeze());
            return rep;
        }

        ///////////////////////////////////////////
        // unify(ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<IUnifier.Immutable.Result<IUnifier.Immutable>> unify(ITerm left, ITerm right)
                throws OccursException {
            return new Unify(this, left, right).apply(true);
        }

        @Override public Optional<Result<mb.nabl2.terms.unification.IUnifier.Immutable>>
                unify(Iterable<? extends Entry<? extends ITerm, ? extends ITerm>> equalities) throws OccursException {
            return new Unify(this, equalities).apply(true);
        }

        @Override public Optional<IUnifier.Immutable.Result<IUnifier.Immutable>> unify(IUnifier other)
                throws OccursException {
            return new Unify(this, other).apply(true);
        }

        private static class Unify extends PersistentUnifier.Transient {

            private final Deque<Map.Entry<ITerm, ITerm>> worklist = Lists.newLinkedList();
            private final List<ITermVar> result = Lists.newArrayList();

            public Unify(PersistentUnifier.Immutable unifier, ITerm left, ITerm right) {
                super(unifier);
                worklist.push(ImmutableTuple2.of(left, right));
            }

            public Unify(PersistentUnifier.Immutable unifier,
                    Iterable<? extends Entry<? extends ITerm, ? extends ITerm>> equalities) {
                super(unifier);
                equalities.forEach(e -> {
                    worklist.push(Tuple2.of(e));
                });
            }

            public Unify(PersistentUnifier.Immutable unifier, IUnifier other) {
                super(unifier);
                other.varSet().forEach(v -> {
                    worklist.push(ImmutableTuple2.of(v, other.findTerm(v)));
                });
            }

            public Optional<IUnifier.Immutable.Result<IUnifier.Immutable>> apply(boolean disunify)
                    throws OccursException {
                while(!worklist.isEmpty()) {
                    final Map.Entry<ITerm, ITerm> work = worklist.pop();
                    if(!unifyTerms(work.getKey(), work.getValue())) {
                        return Optional.empty();
                    }
                }

                final PersistentUnifier.Immutable unifier = freeze();
                if(finite) {
                    final ImmutableSet<ITermVar> cyclicVars =
                            result.stream().filter(v -> unifier.isCyclic(v)).collect(ImmutableSet.toImmutableSet());
                    if(!cyclicVars.isEmpty()) {
                        throw new OccursException(cyclicVars);
                    }
                }
                final IUnifier.Immutable diffUnifier = diffUnifier(result);
                return (disunify ? unifier.disunifyAll() : Optional.of(unifier)).map(u -> {
                    return new BaseUnifier.ImmutableResult<>(diffUnifier, u);
                });
            }

            private boolean unifyTerms(final ITerm left, final ITerm right) {
                // @formatter:off
                return left.match(Terms.<Boolean>cases(
                    applLeft -> right.match(Terms.<Boolean>cases()
                        .appl(applRight -> {
                            return applLeft.getArity() == applRight.getArity() &&
                                    applLeft.getOp().equals(applRight.getOp()) &&
                                    unifys(applLeft.getArgs(), applRight.getArgs());
                        })
                        .var(varRight -> {
                            return unifyTerms(varRight, applLeft)  ;
                        })
                        .otherwise(t -> {
                            return false;
                        })
                    ),
                    listLeft -> right.match(Terms.<Boolean>cases()
                        .list(listRight -> {
                            return unifyLists(listLeft, listRight);
                        })
                        .var(varRight -> {
                            return unifyTerms(varRight, listLeft);
                        })
                        .otherwise(t -> {
                            return false;
                        })
                    ),
                    stringLeft -> right.match(Terms.<Boolean>cases()
                        .string(stringRight -> {
                            return stringLeft.getValue().equals(stringRight.getValue());
                        })
                        .var(varRight -> {
                            return unifyTerms(varRight, stringLeft);
                        })
                        .otherwise(t -> {
                            return false;
                        })
                    ),
                    integerLeft -> right.match(Terms.<Boolean>cases()
                        .integer(integerRight -> {
                            return integerLeft.getValue() == integerRight.getValue();
                        })
                        .var(varRight -> {
                            return unifyTerms(varRight, integerLeft);
                        })
                        .otherwise(t -> {
                            return false;
                        })
                    ),
                    blobLeft -> right.match(Terms.<Boolean>cases()
                        .blob(blobRight -> {
                            return blobLeft.getValue().equals(blobRight.getValue());
                        })
                        .var(varRight -> {
                            return unifyTerms(varRight, blobLeft);
                        })
                        .otherwise(t -> {
                            return false;
                        })
                    ),
                    varLeft -> right.match(Terms.<Boolean>cases()
                        .var(varRight -> {
                            return unifyVars(varLeft, varRight);
                        })
                        .otherwise(termRight -> {
                            return unifyVarTerm(varLeft, termRight);
                        })
                    )
                ));
                // @formatter:on
            }

            private boolean unifyLists(final IListTerm left, final IListTerm right) {
                // @formatter:off
                return left.match(ListTerms.<Boolean>cases(
                    consLeft -> right.match(ListTerms.<Boolean>cases()
                        .cons(consRight -> {
                            worklist.push(ImmutableTuple2.of(consLeft.getHead(), consRight.getHead()));
                            worklist.push(ImmutableTuple2.of(consLeft.getTail(), consRight.getTail()));
                            return true;
                        })
                        .var(varRight -> {
                            return unifyLists(varRight, consLeft);
                        })
                        .otherwise(l -> {
                            return false;
                        })
                    ),
                    nilLeft -> right.match(ListTerms.<Boolean>cases()
                        .nil(nilRight -> {
                            return true;
                        })
                        .var(varRight -> {
                            return unifyVarTerm(varRight, nilLeft)  ;
                        })
                        .otherwise(l -> {
                            return false;
                        })
                    ),
                    varLeft -> right.match(ListTerms.<Boolean>cases()
                        .var(varRight -> {
                            return unifyVars(varLeft, varRight);
                        })
                        .otherwise(termRight -> {
                            return unifyVarTerm(varLeft, termRight);
                        })
                    )
                ));
                // @formatter:on
            }

            private boolean unifyVarTerm(final ITermVar var, final ITerm term) {
                final ITermVar rep = findRep(var);
                if(term instanceof ITermVar) {
                    throw new IllegalStateException();
                }
                final ITerm repTerm = getTerm(rep); // term for the representative
                if(repTerm != null) {
                    worklist.push(ImmutableTuple2.of(repTerm, term));
                } else {
                    putTerm(rep, term);
                    result.add(rep);
                }
                return true;
            }

            private boolean unifyVars(final ITermVar left, final ITermVar right) {
                final ITermVar leftRep = findRep(left);
                final ITermVar rightRep = findRep(right);
                if(leftRep.equals(rightRep)) {
                    return true;
                }
                final int leftRank = Optional.ofNullable(ranks.__remove(leftRep)).orElse(1);
                final int rightRank = Optional.ofNullable(ranks.__remove(rightRep)).orElse(1);
                final boolean swap = leftRank > rightRank;
                final ITermVar var = swap ? rightRep : leftRep; // the eliminated variable
                final ITermVar rep = swap ? leftRep : rightRep; // the new representative
                ranks.__put(rep, leftRank + rightRank);
                putRep(var, rep);
                final ITerm varTerm = removeTerm(var); // term for the eliminated var
                if(varTerm != null) {
                    final ITerm repTerm = getTerm(rep); // term for the representative
                    if(repTerm != null) {
                        worklist.push(ImmutableTuple2.of(varTerm, repTerm));
                        // don't add to result
                    } else {
                        putTerm(rep, varTerm);
                        result.add(rep);
                    }
                } else {
                    result.add(var);
                }
                return true;
            }

            private boolean unifys(final Iterable<ITerm> lefts, final Iterable<ITerm> rights) {
                Iterator<ITerm> itLeft = lefts.iterator();
                Iterator<ITerm> itRight = rights.iterator();
                while(itLeft.hasNext()) {
                    if(!itRight.hasNext()) {
                        return false;
                    }
                    worklist.push(ImmutableTuple2.of(itLeft.next(), itRight.next()));
                }
                if(itRight.hasNext()) {
                    return false;
                }
                return true;
            }

            ///////////////////////////////////////////
            // diffUnifier(Set<ITermVar>)
            ///////////////////////////////////////////

            private IUnifier.Immutable diffUnifier(Collection<ITermVar> vars) {
                final PersistentUnifier.Transient diff = new PersistentUnifier.Transient(finite);
                for(ITermVar var : vars) {
                    final ITermVar rep;
                    final ITerm term;
                    if((rep = getRep(var)) != null) {
                        diff.putRep(var, rep);
                    } else if((term = getTerm(var)) != null) {
                        diff.putTerm(var, term);
                    }
                }
                return diff.freeze();
            }

        }

        ///////////////////////////////////////////
        // diff(ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<IUnifier.Immutable> diff(ITerm term1, ITerm term2) {
            try {
                return unify(term1, term2).map(Result::result);
            } catch(OccursException e) {
                return Optional.empty();
            }
        }

        ///////////////////////////////////////////
        // disunify(Set<ITermVar>, ITerm, ITerm)
        ///////////////////////////////////////////

        @Override public Optional<Result<IUnifier.Immutable>> disunify(Iterable<ITermVar> universals, ITerm left,
                ITerm right) {
            final Optional<IUnifier.Immutable> result = disunify(new Unify(this, left, right));
            if(!result.isPresent()) {
                // disequality discharged, terms are unequal
                return Optional.of(new BaseUnifier.ImmutableResult<>(PersistentUnifier.Immutable.of(finite), this));
            }

            final IUnifier.Immutable disequality = result.get().removeAll(universals).unifier();
            if(disequality.isEmpty()) {
                // no disequalities left, terms are equal
                return Optional.empty();
            }

            final java.util.Set<ITermVar> universalVars =
                    Sets.intersection(ImmutableSet.copyOf(universals), disequality.freeVarSet());

            final IUnifier.Immutable newUnifier = new PersistentUnifier.Immutable(finite, reps.get(), ranks, terms,
                    disequalities.__insert(new Diseq(universalVars, disequality.equalityMap())));
            return Optional.of(new BaseUnifier.ImmutableResult<>(disequality, newUnifier));
        }

        private Optional<IUnifier.Immutable> disunifyAll() {
            final Set.Transient<Diseq> disequalities = Set.Transient.of();
            for(Diseq diseq : this.disequalities) {
                final Optional<IUnifier.Immutable> result = disunify(new Unify(this, diseq.disequalities().entrySet()));
                if(!result.isPresent()) {
                    // disequality discharged, terms are unequal
                    disequalities.__remove(diseq);
                    continue;
                }

                final IUnifier.Immutable newDiseq = result.get().removeAll(diseq.universals()).unifier();
                if(newDiseq.isEmpty()) {
                    // no disequalities left, terms are equal
                    return Optional.empty();
                }

                final java.util.Set<ITermVar> universalVars =
                        Sets.intersection(diseq.universals(), newDiseq.freeVarSet());

                // not unified yet, keep
                disequalities.__insert(new Diseq(universalVars, newDiseq.equalityMap()));
            }
            final IUnifier.Immutable result =
                    new PersistentUnifier.Immutable(finite, reps.get(), ranks, terms, disequalities.freeze());
            return Optional.of(result);
        }

        /**
         * Disunify the given disequality.
         * 
         * Reduces the disequality to canonical form for the current unifier. Returns a reduced map of disequalities, or
         * none if the disequality is satisfied.
         */
        private Optional<IUnifier.Immutable> disunify(Unify unify) {
            final Optional<Result<IUnifier.Immutable>> unifyResult;
            try {
                // NOTE We prevent Unify from doing disunification, as this
                //      results in infinite recursion
                unifyResult = unify.apply(false);
            } catch(OccursException e) {
                // unify failed, terms are unequal
                return Optional.empty();
            }
            if(!unifyResult.isPresent()) {
                // unify failed, terms are unequal
                return Optional.empty();
            }
            // unify succeeded, terms are not unequal
            final IUnifier.Immutable diff = unifyResult.get().result();
            return Optional.of(diff);
        }

        ///////////////////////////////////////////
        // retain(ITermVar)
        ///////////////////////////////////////////

        @Override public IUnifier.Immutable.Result<ISubstitution.Immutable> retain(ITermVar var) {
            return retainAll(Set.Immutable.of(var));
        }

        @Override public IUnifier.Immutable.Result<ISubstitution.Immutable> retainAll(Iterable<ITermVar> vars) {
            return removeAll(Sets.difference(varSet(), ImmutableSet.copyOf(vars)));
        }

        ///////////////////////////////////////////
        // remove(ITermVar)
        ///////////////////////////////////////////

        @Override public IUnifier.Immutable.Result<ISubstitution.Immutable> remove(ITermVar var) {
            return removeAll(Iterables2.singleton(var));
        }

        @Override public IUnifier.Immutable.Result<ISubstitution.Immutable> removeAll(Iterable<ITermVar> vars) {
            return new RemoveAll(this, vars).apply();
        }

        private static class RemoveAll extends PersistentUnifier.Transient {

            private final Set.Immutable<ITermVar> vars;

            public RemoveAll(PersistentUnifier.Immutable unifier, Iterable<ITermVar> vars) {
                super(unifier);
                this.vars = CapsuleUtil.toSet(vars);
            }

            public IUnifier.Immutable.Result<ISubstitution.Immutable> apply() {
                // remove vars from unifier
                final ISubstitution.Immutable subst = removeAll();
                // remove disequalities
                CapsuleUtil.updateOrRemove(disequalities, diseq -> {
                    final Map.Transient<ITermVar, ITerm> newDiseq = Map.Transient.of();
                    for(Map.Entry<ITermVar, ITerm> entry : diseq.disequalities().entrySet()) {
                        ITermVar var = (ITermVar) subst.apply(entry.getKey());
                        ITerm term = subst.apply(entry.getValue());
                        if(!(vars.contains(var) || vars.contains(term))) {
                            newDiseq.__put(var, term);
                        }
                    }
                    final Set.Immutable<ITermVar> universalVars = diseq.universals().subtract(vars);
                    return newDiseq.isEmpty() ? null : new Diseq(universalVars, newDiseq.freeze());
                });
                // TODO Check if variables escaped?
                final IUnifier.Immutable newUnifier = freeze();
                return new BaseUnifier.ImmutableResult<>(subst, newUnifier);
            }

            private ISubstitution.Immutable removeAll() {
                final ISubstitution.Transient subst = PersistentSubstitution.Transient.of();
                for(ITermVar var : vars) {
                    ITermVar rep;
                    if((rep = removeRep(var)) != null) { // var |-> rep
                        subst.compose(var, rep);
                        for(ITermVar notRep : getInvReps(var)) {
                            putRep(notRep, rep);
                        }
                    } else {
                        final Collection<ITermVar> newReps = getInvReps(var);
                        if(!newReps.isEmpty()) { // rep |-> var
                            rep = newReps.stream().max((r1, r2) -> Integer.compare(getRank(r1), getRank(r2))).get();
                            removeRep(rep);
                            subst.compose(var, rep);
                            for(ITermVar notRep : newReps) {
                                if(!notRep.equals(rep)) {
                                    putRep(notRep, rep);
                                }
                            }
                            final ITerm term;
                            if((term = removeTerm(var)) != null) { // var |-> term
                                putTerm(rep, term);
                            }
                        } else {
                            final ITerm term;
                            if((term = removeTerm(var)) != null) { // var |-> term
                                subst.compose(var, term);
                            }
                        }
                    }
                }
                for(Entry<ITermVar, ITerm> entry : termEntries()) {
                    final ITermVar rep = entry.getKey();
                    final ITerm term = entry.getValue();
                    putTerm(rep, subst.apply(term));
                }
                return subst.freeze();
            }

        }

        ///////////////////////////////////////////
        // construction
        ///////////////////////////////////////////

        @Override public IUnifier.Transient melt() {
            return new BaseUnifier.Transient(this);
        }

        public static IUnifier.Immutable of() {
            return of(true);
        }

        public static IUnifier.Immutable of(boolean finite) {
            return new PersistentUnifier.Immutable(finite, Map.Immutable.of(), Map.Immutable.of(), Map.Immutable.of(),
                    Set.Immutable.of());
        }

    }

    ///////////////////////////////////////////
    // class Transient
    ///////////////////////////////////////////

    static class Transient {

        protected final boolean finite;

        private final Map.Transient<ITermVar, ITermVar> reps;
        protected final Map.Transient<ITermVar, Integer> ranks;
        private final Map.Transient<ITermVar, ITerm> terms;
        protected final Set.Transient<Diseq> disequalities;

        Transient(boolean finite) {
            this(finite, Map.Transient.of(), Map.Transient.of(), Map.Transient.of(), Set.Transient.of());
        }

        Transient(PersistentUnifier.Immutable unifier) {
            this(unifier.finite, unifier.reps.get().asTransient(), unifier.ranks.asTransient(),
                    unifier.terms.asTransient(), unifier.disequalities.asTransient());
        }

        Transient(boolean finite, Map.Transient<ITermVar, ITermVar> reps, Map.Transient<ITermVar, Integer> ranks,
                Map.Transient<ITermVar, ITerm> terms, Set.Transient<Diseq> disequalities) {
            this.finite = finite;
            this.reps = reps;
            this.ranks = ranks;
            this.terms = terms;
            this.disequalities = disequalities;
        }

        protected Iterable<Entry<ITermVar, ITermVar>> repEntries() {
            return reps.entrySet();
        }

        protected Iterable<Entry<ITermVar, ITerm>> termEntries() {
            return terms.entrySet();
        }

        protected ITermVar findRep(ITermVar var) {
            return PersistentUnifier.findRep(var, reps);
        }

        protected ITermVar getRep(ITermVar var) {
            return reps.get(var);
        }

        protected java.util.Set<ITermVar> getInvReps(ITermVar rep) {
            return reps.entrySet().stream().filter(e -> e.getValue().equals(rep)).map(Entry::getKey)
                    .collect(Collectors.toSet());
        }

        protected void putRep(ITermVar var, ITermVar rep) {
            reps.__put(var, rep);
        }

        protected ITermVar removeRep(ITermVar var) {
            return reps.__remove(var);
        }

        protected ITerm getTerm(ITermVar rep) {
            return terms.get(rep);
        }

        protected void putTerm(ITermVar rep, ITerm term) {
            terms.__put(rep, term);
        }

        protected ITerm removeTerm(ITermVar rep) {
            return terms.__remove(rep);
        }

        protected int getRank(ITermVar var) {
            return ranks.getOrDefault(var, 1);
        }

        protected PersistentUnifier.Immutable freeze() {
            final PersistentUnifier.Immutable unifier = new PersistentUnifier.Immutable(finite, reps.freeze(),
                    ranks.freeze(), terms.freeze(), disequalities.freeze());
            return unifier;
        }

    }

}