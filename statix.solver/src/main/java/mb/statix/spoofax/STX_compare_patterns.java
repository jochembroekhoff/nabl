package mb.statix.spoofax;

import static mb.nabl2.terms.build.TermBuild.B;
import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.List;
import java.util.Optional;

import org.spoofax.interpreter.core.IContext;
import org.spoofax.interpreter.core.InterpreterException;

import com.google.inject.Inject;

import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.matching.Pattern;

public class STX_compare_patterns extends StatixPrimitive {

    @Inject public STX_compare_patterns() {
        super(STX_compare_patterns.class.getSimpleName(), 0);
    }

    @Override protected Optional<? extends ITerm> call(IContext env, ITerm term, List<ITerm> terms)
            throws InterpreterException {
        return M.tuple2(StatixTerms.pattern(), StatixTerms.pattern(), (t, p1, p2) -> {
            return B.newInt(Pattern.leftRightOrdering.compare(p1, p2));
        }).match(term).map(Optional::of)
                .orElseThrow(() -> new InterpreterException("Expected tuple of patterns, got " + term + "."));
    }

}