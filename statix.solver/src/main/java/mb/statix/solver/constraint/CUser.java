package mb.statix.solver.constraint;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.matching.MatchException;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.terms.unification.PersistentUnifier;
import mb.nabl2.terms.unification.UnificationException;
import mb.nabl2.util.ImmutableTuple2;
import mb.nabl2.util.Tuple2;
import mb.statix.solver.Completeness;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IGuard;
import mb.statix.solver.Result;
import mb.statix.solver.State;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.log.LazyDebugContext;
import mb.statix.solver.log.Log;
import mb.statix.spec.Rule;
import mb.statix.spec.Spec;

public class CUser implements IConstraint {

    private final String name;
    private final List<ITerm> args;

    private final @Nullable IConstraint cause;

    public CUser(String name, Iterable<? extends ITerm> args) {
        this(name, args, null);
    }

    public CUser(String name, Iterable<? extends ITerm> args, @Nullable IConstraint cause) {
        this.name = name;
        this.args = ImmutableList.copyOf(args);
        this.cause = cause;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CUser withCause(@Nullable IConstraint cause) {
        return new CUser(name, args, cause);
    }

    @Override public Iterable<Tuple2<ITerm, ITerm>> scopeExtensions(Spec spec) {
        return spec.scopeExtensions().get(name).stream().map(il -> ImmutableTuple2.of(args.get(il._1()), il._2()))
                .collect(Collectors.toList());
    }

    @Override public CUser apply(ISubstitution.Immutable subst) {
        return new CUser(name, subst.apply(args), cause);
    }

    public Optional<Result> solve(final State state, Completeness completeness, IDebugContext debug)
            throws InterruptedException, Delay {
        final Set<Rule> rules = Sets.newHashSet(state.spec().rules().get(name));
        final List<Result> results = Lists.newArrayListWithExpectedSize(1);
        final Iterator<Rule> it = rules.iterator();
        final Log unsuccessfulLog = new Log();
        outer: while(it.hasNext()) {
            final LazyDebugContext proxyDebug = new LazyDebugContext(debug);
            State result = state;
            final Rule rawRule = it.next();
            final Rule instantiatedRule;
            try {
                final Tuple2<State, Rule> appl = rawRule.apply(args, result);
                result = appl._1();
                instantiatedRule = appl._2();
            } catch(MatchException | UnificationException e) {
                proxyDebug.warn("Failed to instantiate {} for arguments {}", rawRule, args);
                continue;
            }
            proxyDebug.info("Try rule {}", instantiatedRule.toString(result.unifier()));
            for(IGuard guard : instantiatedRule.getGuard()) {
                try {
                    Optional<State> maybeResult = guard.solve(result, proxyDebug);
                    if(!maybeResult.isPresent()) {
                        proxyDebug.info("Rule rejected (unsatisfied guard constraint)");
                        it.remove();
                        continue outer;
                    } else {
                        result = maybeResult.get();
                    }
                } catch(Delay e) {
                    proxyDebug.info("Rule delayed (unsolved guard constraint)");
                    continue outer;
                }
            }
            if(state.entails(result, instantiatedRule.getGuardVars())) {
                proxyDebug.info("Rule accepted");
                proxyDebug.commit();
                results.add(Result.of(result, instantiatedRule.getBody()));
            } else {
                proxyDebug.info("Rule delayed (instantiated variables)");
                unsuccessfulLog.absorb(proxyDebug.clear());
            }
        }
        if(!results.isEmpty()) {
            if(results.size() > 1) {
                debug.error("Found overlapping rules");
                throw new IllegalArgumentException("Found overlapping rules");
            } else {
                return Optional.of(results.get(0));
            }
        } else if(rules.isEmpty()) {
            debug.info("No rule applies");
            unsuccessfulLog.flush(debug);
            return Optional.empty();
        } else {
            unsuccessfulLog.flush(debug);
            throw new Delay();
        }
    }

    @Override public String toString(IUnifier unifier) {
        final StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append("(");
        sb.append(unifier.toString(args));
        sb.append(")");
        return sb.toString();
    }

    @Override public String toString() {
        return toString(PersistentUnifier.Immutable.of());
    }

}