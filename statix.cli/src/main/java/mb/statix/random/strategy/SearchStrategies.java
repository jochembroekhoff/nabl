package mb.statix.random.strategy;

import static mb.nabl2.terms.build.TermBuild.B;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.metaborg.core.MetaborgRuntimeException;
import org.metaborg.util.functions.Predicate1;
import org.metaborg.util.functions.Predicate2;
import org.metaborg.util.optionals.Optionals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.oracle.truffle.api.object.dsl.Nullable;

import io.usethesource.capsule.Set;
import io.usethesource.capsule.util.stream.CapsuleCollectors;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.terms.unification.UnifierFormatter;
import mb.nabl2.util.Tuple2;
import mb.statix.constraints.CEqual;
import mb.statix.constraints.CInequal;
import mb.statix.constraints.CResolveQuery;
import mb.statix.constraints.CUser;
import mb.statix.constraints.Constraints;
import mb.statix.random.FocusedSearchState;
import mb.statix.random.SearchContext;
import mb.statix.random.SearchNode;
import mb.statix.random.SearchState;
import mb.statix.random.SearchStrategy;
import mb.statix.random.scopegraph.DataWF;
import mb.statix.random.scopegraph.Env;
import mb.statix.random.scopegraph.NameResolution;
import mb.statix.random.util.RuleUtil;
import mb.statix.random.util.WeightedDrawSet;
import mb.statix.scopegraph.reference.LabelOrder;
import mb.statix.scopegraph.reference.LabelWF;
import mb.statix.scopegraph.reference.ResolutionException;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IState;
import mb.statix.solver.completeness.ICompleteness;
import mb.statix.solver.completeness.IncrementalCompleteness;
import mb.statix.solver.completeness.IsComplete;
import mb.statix.solver.log.NullDebugContext;
import mb.statix.solver.persistent.Solver;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.solver.query.RegExpLabelWF;
import mb.statix.solver.query.RelationLabelOrder;
import mb.statix.spec.Rule;
import mb.statix.spoofax.StatixTerms;

public final class SearchStrategies {

    public final <I, O> SearchStrategy<I, O> limit(int n, SearchStrategy<I, O> s) {
        return new SearchStrategy<I, O>() {

            @Override public Stream<SearchNode<O>> doApply(SearchContext ctx, I input, SearchNode<?> parent) {
                return s.apply(ctx, input, parent).limit(n);
            }

            @Override public String toString() {
                return "limit(" + n + ", " + s.toString() + ")";
            }

        };
    }

    public final <I1, I2, O> SearchStrategy<I1, O> seq(SearchStrategy<I1, I2> s1, SearchStrategy<I2, O> s2) {
        return new SearchStrategy<I1, O>() {

            @Override public Stream<SearchNode<O>> doApply(SearchContext ctx, I1 i1, SearchNode<?> parent) {
                return s1.apply(ctx, i1, parent).flatMap(sn1 -> {
                    return s2.apply(ctx, sn1.output(), sn1).map(sn2 -> {
                        return new SearchNode<>(ctx.nextNodeId(), sn2.output(), sn2,
                                "(" + sn1.toString() + " . " + sn2.toString() + ")");
                    });
                });
            }

            @Override public String toString() {
                return "(" + s1.toString() + " . " + s2.toString() + ")";
            }

        };
    }

    public final <I, O1, O2> SearchStrategy<I, Either2<O1, O2>> alt(SearchStrategy<I, O1> s1,
            SearchStrategy<I, O2> s2) {
        return new SearchStrategy<I, Either2<O1, O2>>() {

            @Override protected Stream<SearchNode<Either2<O1, O2>>> doApply(SearchContext ctx, I input,
                    SearchNode<?> parent) {
                final Iterator<SearchNode<O1>> ns1 = s1.apply(ctx, input, parent).iterator();
                final Iterator<SearchNode<O2>> ns2 = s2.apply(ctx, input, parent).iterator();
                return Streams.stream(new Iterator<SearchNode<Either2<O1, O2>>>() {

                    @Override public boolean hasNext() {
                        return ns1.hasNext() || ns2.hasNext();
                    }

                    @Override public SearchNode<Either2<O1, O2>> next() {
                        final boolean left;
                        if(ns1.hasNext() && ns2.hasNext()) {
                            left = ctx.rnd().nextBoolean();
                        } else if(ns1.hasNext()) {
                            left = true;
                        } else if(ns2.hasNext()) {
                            left = false;
                        } else {
                            throw new NoSuchElementException();
                        }
                        if(left) {
                            final SearchNode<O1> n = ns1.next();
                            final Either2<O1, O2> output = Either2.ofLeft(n.output());
                            return new SearchNode<>(ctx.nextNodeId(), output, n.parent(), n.desc());
                        } else {
                            final SearchNode<O2> n = ns2.next();
                            final Either2<O1, O2> output = Either2.ofRight(n.output());
                            return new SearchNode<>(ctx.nextNodeId(), output, n.parent(), n.desc());
                        }
                    }

                });

            }

            @Override public String toString() {
                return "(" + s1 + " | " + s2 + ")<";

            }

        };

    }

    public final <I1, I2, O> SearchStrategy<Either2<I1, I2>, O> match(SearchStrategy<I1, O> s1,
            SearchStrategy<I2, O> s2) {
        // this doesn't interleave!
        return new SearchStrategy<Either2<I1, I2>, O>() {

            @Override protected Stream<SearchNode<O>> doApply(SearchContext ctx, Either2<I1, I2> input,
                    SearchNode<?> parent) {
                return input.map(n1 -> {
                    return s1.apply(ctx, n1, parent);
                }, n2 -> {
                    return s2.apply(ctx, n2, parent);
                });
            }

            @Override public String toString() {
                return ">(" + s1 + " | " + s2 + ")";
            }

        };
    }

    public final SearchStrategy<SearchState, SearchState> infer() {
        return new SearchStrategy<SearchState, SearchState>() {

            @Override public Stream<SearchNode<SearchState>> doApply(SearchContext ctx, SearchState state,
                    SearchNode<?> parent) {
                final SolverResult resultConfig;
                try {
                    resultConfig = Solver.solve(state.state(), Constraints.conjoin(state.constraints()),
                            new NullDebugContext());
                } catch(InterruptedException e) {
                    throw new MetaborgRuntimeException(e);
                }
                if(resultConfig.hasErrors()) {
                    final String msg = Constraints.toString(resultConfig.errors(),
                            new UnifierFormatter(resultConfig.state().unifier(), 3));
                    ctx.addFailed(new SearchNode<>(ctx.nextNodeId(), state, parent, "infer[" + msg + "]"));
                    return Stream.empty();
                }
                final SearchState newState = state.update(resultConfig);
                return Stream.of(new SearchNode<>(ctx.nextNodeId(), newState, parent, "infer"));
            }

            @Override public String toString() {
                return "infer";
            }

        };
    }

    public final <C extends IConstraint> SearchStrategy<SearchState, FocusedSearchState<C>> select(Class<C> cls,
            Predicate1<C> include) {
        return new SearchStrategy<SearchState, FocusedSearchState<C>>() {

            @Override protected Stream<SearchNode<FocusedSearchState<C>>> doApply(SearchContext ctx, SearchState input,
                    SearchNode<?> parent) {
                @SuppressWarnings("unchecked") final Set.Immutable<C> candidates =
                        input.constraints().stream().filter(c -> cls.isInstance(c)).map(c -> (C) c)
                                .filter(include::test).collect(CapsuleCollectors.toSet());
                if(candidates.isEmpty()) {
                    //                    ctx.addFailed(new SearchNode<>(input, parent, this.toString() + "[no candidates]"));
                    return Stream.empty();
                }
                return WeightedDrawSet.of(candidates).enumerate(ctx.rnd()).map(c -> {
                    final FocusedSearchState<C> output = FocusedSearchState.of(input, c.getKey());
                    return new SearchNode<>(ctx.nextNodeId(), output, parent, "select(" + c.getKey() + ")");
                });
            }

            @Override public String toString() {
                return "select(" + cls.getSimpleName() + ", " + include.toString() + ")";
            }

        };
    }

    @SafeVarargs public final SearchStrategy<SearchState, SearchState> drop(Class<? extends IConstraint>... classes) {
        final ImmutableSet<Class<? extends IConstraint>> _classes = ImmutableSet.copyOf(classes);
        return new SearchStrategy<SearchState, SearchState>() {

            @Override protected Stream<SearchNode<SearchState>> doApply(SearchContext ctx, SearchState input,
                    SearchNode<?> parent) {
                final Set.Immutable<IConstraint> constraints = input.constraints().stream()
                        .filter(c -> !_classes.contains(c.getClass())).collect(CapsuleCollectors.toSet());
                final SearchState output = input.update(input.state(), constraints);
                final String desc = "drop"
                        + _classes.stream().map(Class::getSimpleName).collect(Collectors.joining(", ", "(", ")"));
                return Stream.of(new SearchNode<>(ctx.nextNodeId(), output, parent, desc));
            }

            @Override public String toString() {
                return "drop" + _classes.stream().map(Class::getSimpleName).collect(Collectors.joining(", ", "(", ")"));
            }

        };
    }

    public final SearchStrategy<FocusedSearchState<CUser>, SearchState> expand() {
        return expand(ImmutableMap.of());
    }

    public final SearchStrategy<FocusedSearchState<CUser>, SearchState> expand(Map<String, Integer> weights) {
        return new SearchStrategy<FocusedSearchState<CUser>, SearchState>() {

            @Override protected Stream<SearchNode<SearchState>> doApply(SearchContext ctx,
                    FocusedSearchState<CUser> input, SearchNode<?> parent) {
                final CUser predicate = input.focus();
                final Map<Rule, Integer> rules = new HashMap<>();
                for(Rule rule : input.state().spec().rules().get(predicate.name())) {
                    rules.put(rule, weights.getOrDefault(rule.label(), 1));
                }
                return WeightedDrawSet.of(rules).enumerate(ctx.rnd()).map(Map.Entry::getKey).flatMap(rule -> {
                    return Streams.stream(RuleUtil.apply(input.state(), rule, predicate.args(), predicate))
                            .map(result -> {
                                final SearchState output =
                                        input.update(result._1(), input.constraints().__insert(result._2()));
                                final String head = rule.name() + rule.params().stream().map(Object::toString)
                                        .collect(Collectors.joining(", ", "(", ")"));
                                return new SearchNode<>(ctx.nextNodeId(), output, parent, "expand(" + head + ")");
                            });
                });
            }

            @Override public String toString() {
                return "expand" + weights.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                        .collect(Collectors.joining(", ", "(", ")"));
            }

        };
    }

    public final SearchStrategy<FocusedSearchState<CResolveQuery>, SearchState> resolve() {
        return new SearchStrategy<FocusedSearchState<CResolveQuery>, SearchState>() {

            @Override protected Stream<SearchNode<SearchState>> doApply(SearchContext ctx,
                    FocusedSearchState<CResolveQuery> input, SearchNode<?> parent) {
                final IState.Immutable state = input.state();
                final IUnifier unifier = state.unifier();
                final CResolveQuery query = input.focus();

                final Scope scope = Scope.matcher().match(query.scopeTerm(), unifier).orElse(null);
                if(scope == null) {
                    ctx.addFailed(new SearchNode<>(ctx.nextNodeId(), input, parent, "resolve[no scope]"));
                    return Stream.empty();
                }

                final Boolean isAlways;
                try {
                    isAlways = query.min().getDataEquiv().isAlways(state.spec()).orElse(null);
                } catch(InterruptedException e) {
                    throw new MetaborgRuntimeException(e);
                }
                if(isAlways == null) {
                    ctx.addFailed(
                            new SearchNode<>(ctx.nextNodeId(), input, parent, "resolve[cannot decide data equiv]"));
                    return Stream.empty();
                }

                final ICompleteness completeness = new IncrementalCompleteness(state.spec());
                completeness.addAll(input.constraints(), unifier);
                final IsComplete isComplete3 = (s, l, st) -> completeness.isComplete(s, l, st.unifier());
                final Predicate2<Scope, ITerm> isComplete2 = (s, l) -> completeness.isComplete(s, l, state.unifier());
                final LabelWF<ITerm> labelWF = RegExpLabelWF.of(query.filter().getLabelWF());
                final LabelOrder<ITerm> labelOrd = new RelationLabelOrder(query.min().getLabelOrder());
                final DataWF<ITerm, CEqual> dataWF =
                        new ConstraintDataWF(isComplete3, state, query.filter().getDataWF(), query);
                //                lengths = length((IListTerm) query.resultTerm(), state.unifier()).map(ImmutableList::of)
                //                        .orElse(ImmutableList.of(0, 1, 2, -1));

                // @formatter:off
                final NameResolution<Scope, ITerm, ITerm, CEqual> nameResolution = new NameResolution<>(
                        state.scopeGraph(), query.relation(),
                        labelWF, labelOrd, isComplete2,
                        dataWF, isAlways, isComplete2);
                // @formatter:on

                final AtomicInteger count = new AtomicInteger(1);
                try {
                    nameResolution.resolve(scope, () -> {
                        count.incrementAndGet();
                        return false;
                    });
                } catch(ResolutionException e) {
                    ctx.addFailed(new SearchNode<>(ctx.nextNodeId(), input, parent,
                            "resolve[counting error:" + e.getMessage() + "]"));
                    return Stream.empty();
                } catch(InterruptedException e) {
                    throw new MetaborgRuntimeException(e);
                }

                final List<Integer> indices =
                        IntStream.range(0, count.get()).boxed().collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(indices, ctx.rnd());

                return indices.stream().flatMap(idx -> {
                    final AtomicInteger select = new AtomicInteger(idx);
                    final Env<Scope, ITerm, ITerm, CEqual> env;
                    try {
                        env = nameResolution.resolve(scope, () -> select.getAndDecrement() == 0);
                    } catch(ResolutionException e) {
                        ctx.addFailed(new SearchNode<>(ctx.nextNodeId(), input, parent,
                                "resolve[resolution error:" + e.getMessage() + "]"));
                        return Stream.empty();
                    } catch(InterruptedException e) {
                        throw new MetaborgRuntimeException(e);
                    }

                    return WeightedDrawSet.of(env.matches).enumerate(ctx.rnd()).map(entry -> {
                        final Env.Builder<Scope, ITerm, ITerm, CEqual> subEnv = Env.builder();
                        subEnv.match(entry.getKey());
                        entry.getValue().forEach(subEnv::reject);
                        env.rejects.forEach(subEnv::reject);
                        return subEnv.build();
                    }).map(subEnv -> {
                        final List<ITerm> pathTerms = subEnv.matches.stream().map(m -> StatixTerms.explicate(m.path))
                                .collect(ImmutableList.toImmutableList());
                        final ImmutableList.Builder<IConstraint> constraints = ImmutableList.builder();
                        constraints.add(new CEqual(B.newList(pathTerms), query.resultTerm(), query));
                        subEnv.matches.stream().flatMap(m -> Optionals.stream(m.condition))
                                .forEach(condition -> constraints.add(condition));
                        subEnv.rejects.stream().flatMap(m -> Optionals.stream(m.condition))
                                .forEach(condition -> constraints.add(new CInequal(condition.term1(), condition.term2(),
                                        condition.cause().orElse(null))));
                        constraints.addAll(input.constraints());
                        final SearchState newState = input.update(input.state(), constraints.build());
                        return new SearchNode<>(ctx.nextNodeId(), newState, parent,
                                "resolve[" + idx + "/" + count.get() + "]");
                    });
                });
            }

            @Override public String toString() {
                return "resolve";
            }

            class ConstraintDataWF implements DataWF<ITerm, CEqual> {
                private final IsComplete isComplete3;
                private final IState.Immutable state;
                private final Rule dataWf;
                private final IConstraint cause;

                private ConstraintDataWF(IsComplete isComplete3, IState.Immutable state, Rule dataWf,
                        @Nullable IConstraint cause) {
                    this.isComplete3 = isComplete3;
                    this.state = state;
                    this.dataWf = dataWf;
                    this.cause = cause;
                }

                @Override public Optional<Optional<CEqual>> wf(ITerm datum)
                        throws ResolutionException, InterruptedException {

                    // apply rule
                    final Optional<Tuple2<IState.Immutable, IConstraint>> stateAndConstraint =
                            RuleUtil.apply(state, dataWf, ImmutableList.of(datum), null);
                    if(!stateAndConstraint.isPresent()) {
                        return Optional.empty();
                    }
                    final IState.Immutable newState = stateAndConstraint.get()._1();
                    final IConstraint constraint = stateAndConstraint.get()._2();

                    // solve rule constraint
                    final SolverResult result = Solver.solve(newState, constraint, isComplete3, new NullDebugContext());
                    if(result.hasErrors()) {
                        return Optional.empty();
                    }
                    if(!result.delays().isEmpty()) {
                        return Optional.empty();
                    }

                    final List<ITerm> leftTerms = Lists.newArrayList();
                    final List<ITerm> rightTerms = Lists.newArrayList();
                    // NOTE The retain operation is important because it may change
                    //      representatives, which can be local to newUnifier.
                    final IUnifier.Immutable newUnifier = result.state().unifier().retainAll(state.vars()).unifier();
                    for(ITermVar var : state.vars()) {
                        final ITerm term = newUnifier.findTerm(var);
                        if(!state.unifier().areEqual(var, term).orElse(false)) {
                            leftTerms.add(var);
                            rightTerms.add(term);
                        }
                    }
                    if(!leftTerms.isEmpty()) {
                        final CEqual eq = new CEqual(B.newTuple(leftTerms), B.newTuple(rightTerms), cause);
                        return Optional.of(Optional.of(eq));
                    }

                    return Optional.of(Optional.empty());
                }

            }

        };
    }

}