package mb.statix.solver.constraint;

import java.util.Optional;

import org.metaborg.util.functions.Function1;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.unification.IUnifier;
import mb.statix.solver.Config;
import mb.statix.solver.IConstraint;
import mb.statix.solver.State;

public class CTrue implements IConstraint {
    @SuppressWarnings("unused") private static final ILogger logger = LoggerUtils.logger(CTrue.class);

    @Override public IConstraint apply(Function1<ITerm, ITerm> map) {
        return this;
    }

    @Override public Optional<Config> solve(State state) {
        return Optional.of(Config.builder().state(state).build());
    }

    @Override public String toString(IUnifier unifier) {
        return toString();
    }

    @Override public String toString() {
        return "true";
    }

}