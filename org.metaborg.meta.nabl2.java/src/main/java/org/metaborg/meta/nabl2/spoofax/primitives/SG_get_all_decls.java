package org.metaborg.meta.nabl2.spoofax.primitives;

import java.util.Optional;

import org.metaborg.meta.nabl2.spoofax.analysis.IScopeGraphContext;
import org.metaborg.meta.nabl2.stratego.TermIndex;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.generic.TB;
import org.spoofax.interpreter.core.InterpreterException;

public class SG_get_all_decls extends AnalysisPrimitive {

    public SG_get_all_decls() {
        super(SG_get_all_decls.class.getSimpleName(), 0, 1);
    }

    @Override public Optional<? extends ITerm> call(IScopeGraphContext<?> context, TermIndex index, ITerm term)
            throws InterpreterException {
        return context.unit(index.getResource()).solution().<ITerm>map(s -> {
            return TB.newList(s.getScopeGraph().getAllDecls());
        });
    }

}