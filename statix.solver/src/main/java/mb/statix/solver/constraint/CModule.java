package mb.statix.solver.constraint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.metaborg.util.log.Level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.ITermVar;
import mb.nabl2.terms.substitution.ISubstitution;
import mb.nabl2.terms.unification.IUnifier;
import mb.nabl2.util.TermFormatter;
import mb.nabl2.util.Tuple3;
import mb.statix.scopegraph.reference.CriticalEdge;
import mb.statix.solver.ConstraintContext;
import mb.statix.solver.ConstraintResult;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.State;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.log.LazyDebugContext;
import mb.statix.solver.log.Log;
import mb.statix.spec.Rule;
import mb.statix.spec.Spec;
import mb.statix.taico.solver.MConstraintContext;
import mb.statix.taico.solver.MConstraintResult;
import mb.statix.taico.solver.MState;

/**
 * Implementation for a user constraint (rule application).
 * 
 * <pre>ruleName(arguments)</pre>
 */
public class CModule implements IConstraint {

    private final String name;
    private final List<ITerm> args;

    private final @Nullable IConstraint cause;

    /**
     * Creates a new user constraint without a cause.
     * 
     * @param name
     *      the name of the rule to invoke
     * @param args
     *      the arguments
     */
    public CModule(String name, Iterable<? extends ITerm> args) {
        this(name, args, null);
    }

    /**
     * Creates a new user constraint with a cause.
     * 
     * @param name
     *      the name of the rule to invoke
     * @param args
     *      the arguments
     * @param cause
     *      the constraint that caused this constraint to be added
     */
    public CModule(String name, Iterable<? extends ITerm> args, @Nullable IConstraint cause) {
        this.name = name;
        this.args = ImmutableList.copyOf(args);
        this.cause = cause;
    }

    @Override public Iterable<ITerm> terms() {
        return args;
    }

    @Override public Optional<IConstraint> cause() {
        return Optional.ofNullable(cause);
    }

    @Override public CModule withCause(@Nullable IConstraint cause) {
        return new CModule(name, args, cause);
    }

    @Override public Collection<CriticalEdge> criticalEdges(Spec spec) {
        return spec.scopeExtensions().get(name).stream()
                .map(il -> CriticalEdge.of(args.get(il._1()), il._2()))
                .collect(Collectors.toList());
    }

    @Override public CModule apply(ISubstitution.Immutable subst) {
        return new CModule(name, subst.apply(args), cause);
    }
    
    @Override
    public boolean canModifyState() {
        return true;
    }

    /**
     * Not supported.
     * 
     * @throws UnsupportedOperationException
     *      Always.
     */
    @Override public Optional<ConstraintResult> solve(State state, ConstraintContext params)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot solve module constraints without mutable state.");
    }
    
    @Override public Optional<MConstraintResult> solveMutable(MState state, MConstraintContext params)
            throws InterruptedException, Delay {
        if (state.solver().isSeparateSolver()) {
            System.err.println("Separated solver reaching module boundary!!!!");
        }
        
        final IDebugContext debug = params.debug();
        
        final List<ITerm> args = groundArguments(state.unifier());
        final List<Rule> rules = Lists.newLinkedList(state.spec().rules().get(name));
        final Log unsuccessfulLog = new Log();
        final Iterator<Rule> it = rules.iterator();
        while(it.hasNext()) {
            if(Thread.interrupted()) {
                throw new InterruptedException();
            }
            final LazyDebugContext proxyDebug = new LazyDebugContext(debug);
            final Rule rawRule = it.next();
            if(proxyDebug.isEnabled(Level.Info)) {
                proxyDebug.info("Try module boundary {}", rawRule.toString());
            }
            
            final Set<IConstraint> instantiatedBody;
            final Tuple3<MState, Set<ITermVar>, Set<IConstraint>> appl;
            
            final MState childState;
            try {
                MState copyState = state.copy();
                if((appl = rawRule.applyModuleBoundary(args, copyState).orElse(null)) != null) {
                    childState = appl._1();
                    instantiatedBody = appl._3();
                    state = copyState;
                } else {
                    proxyDebug.info("Module boundary rejected (mismatching arguments)");
                    unsuccessfulLog.absorb(proxyDebug.clear());
                    continue;
                }
            } catch(Delay d) {
                proxyDebug.info("Module boundary delayed (unsolved guard constraint)");
                unsuccessfulLog.absorb(proxyDebug.clear());
                unsuccessfulLog.flush(debug);
                throw d;
            }
            
            //TODO IMPORTANT Fix the isRigid and isClosed to their correct forms (check ownership and delegate)
            proxyDebug.info("Creating new solver for module boundary in {}", this.name);
            state.solver().childSolver(childState, instantiatedBody, state.solver().isRigid(), state.solver().isClosed());
            proxyDebug.info("Module boundary accepted");
            proxyDebug.commit();
            
            return Optional.of(new MConstraintResult(state));
        }
        debug.info("No rule applies");
        unsuccessfulLog.flush(debug);
        return Optional.empty();
    }

    /**
     * If any of the arguments are not ground, this method throws a delay exception.
     * Otherwise, it recursively and eagerly evaluates each argument so it can be passed to the
     * new module.
     * 
     * @param unifier
     *      the unifier to use
     * 
     * @return
     *      the list of arguments, recursively evaluated
     * 
     * @throws Delay
     *      If one of the arguments is not ground.
     */
    private List<ITerm> groundArguments(final IUnifier.Immutable unifier) throws Delay {
        for (ITerm term : this.args) {
            if (!unifier.isGround(term)) {
                //TODO IMPORTANT Is this correct? How about a term where some of it's innards are unknown, but not all of them? (The delay waits on all vars, but some might be known)
                throw Delay.ofVars(unifier.getVars(term));
            }
        }
        
        final List<ITerm> args = new ArrayList<>();
        for (ITerm term : this.args) {
            if (term instanceof ITermVar) {
                //TODO IMPOTANT try catch?
                ITerm actual = unifier.findRecursive(term);
                args.add(actual);
            } else {
                args.add(term);
            }
        }
        
        return args;
    }

    @Override public String toString(TermFormatter termToString) {
        final StringBuilder sb = new StringBuilder();
        sb.append("module{");
        sb.append(name);
        sb.append('(');
        sb.append(termToString.format(args));
        sb.append(')');
        sb.append('}');
        return sb.toString();
    }

    @Override public String toString() {
        return toString(ITerm::toString);
    }

}
