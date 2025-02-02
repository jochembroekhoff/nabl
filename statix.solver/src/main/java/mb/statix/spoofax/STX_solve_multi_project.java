package mb.statix.spoofax;

import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;
import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;
import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;

import com.google.common.collect.Maps;
import com.google.inject.Inject;

import mb.nabl2.terms.ITerm;
import mb.statix.constraints.Constraints;
import mb.statix.constraints.messages.IMessage;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IState;
import mb.statix.solver.log.IDebugContext;
import mb.statix.solver.persistent.Solver;
import mb.statix.solver.persistent.SolverResult;
import mb.statix.spec.Spec;

public class STX_solve_multi_project extends StatixPrimitive {
    private static final ILogger logger = LoggerUtils.logger(STX_solve_multi_project.class);

    @Inject public STX_solve_multi_project() {
        super(STX_solve_multi_project.class.getSimpleName(), 5);
    }

    @Override protected Optional<? extends ITerm> call(IContext env, ITerm term, List<ITerm> terms)
            throws InterpreterException {

        final Spec spec =
                StatixTerms.spec().match(terms.get(0)).orElseThrow(() -> new InterpreterException("Expected spec."));
        reportOverlappingRules(spec);

        final SolverResult initial = M.blobValue(SolverResult.class).match(terms.get(1))
                .orElseThrow(() -> new InterpreterException("Expected solver result."));

        final IDebugContext debug = getDebugContext(terms.get(2));
        final IProgress progress = getProgress(terms.get(3));
        final ICancel cancel = getCancel(terms.get(4));

        final List<SolverResult> results = M.listElems(M.blobValue(SolverResult.class)).match(term)
                .orElseThrow(() -> new InterpreterException("Expected list of solver results."));

        final List<IConstraint> constraints = new ArrayList<>(initial.delays().keySet());
        final Map<IConstraint, IMessage> messages = Maps.newHashMap(initial.messages());
        IState.Immutable state = initial.state();
        for(SolverResult result : results) {
            try {
                state = state.add(result.state());
            } catch(IllegalArgumentException e) {
                // can this ever occur?
                logger.error("Unexpectedely failed to merge file results.", e);
                return Optional.empty();
            }
            constraints.add(result.delayed());
            messages.putAll(result.messages());
        }

        final SolverResult resultConfig;
        try {
            final double t0 = System.currentTimeMillis();
            resultConfig = Solver.solve(spec, state, Constraints.conjoin(constraints), (s, l, st) -> true, debug,
                    cancel, progress, 0);
            final double dt = System.currentTimeMillis() - t0;
            logger.info("Project analyzed in {} s", (dt / 1_000d));
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        messages.putAll(resultConfig.messages());
        final ITerm resultTerm = B.newBlob(resultConfig.withMessages(messages));
        return Optional.of(resultTerm);
    }

}