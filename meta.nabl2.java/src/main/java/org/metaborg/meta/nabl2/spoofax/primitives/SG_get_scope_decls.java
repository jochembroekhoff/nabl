package org.metaborg.meta.nabl2.spoofax.primitives;

import java.util.List;
import java.util.Optional;

import org.metaborg.meta.nabl2.scopegraph.terms.Scope;
import org.metaborg.meta.nabl2.spoofax.analysis.IScopeGraphUnit;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.generic.TB;
import org.spoofax.interpreter.core.InterpreterException;

public class SG_get_scope_decls extends AnalysisPrimitive {

    public SG_get_scope_decls() {
        super(SG_get_scope_decls.class.getSimpleName());
    }

    @Override public Optional<? extends ITerm> call(IScopeGraphUnit unit, ITerm term, List<ITerm> terms) throws InterpreterException {
        return Scope.matcher().match(term).<ITerm>flatMap(scope -> {
            return unit.solution().<ITerm>map(s -> {
                return TB.newList(s.scopeGraph().getDecls().inverse().get(scope));
            });
        });
    }

}