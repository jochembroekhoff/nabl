package mb.nabl2.spoofax.primitives;

import org.metaborg.util.collection.IRelation3;

import mb.nabl2.scopegraph.IScopeGraph;
import mb.nabl2.scopegraph.terms.Label;
import mb.nabl2.scopegraph.terms.Occurrence;
import mb.nabl2.scopegraph.terms.Scope;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.matching.TermMatch.IMatcher;

public class SG_get_export_edges extends ScopeGraphEdgePrimitive<Occurrence> {

    public SG_get_export_edges() {
        super(SG_get_export_edges.class.getSimpleName());
    }

    @Override protected IMatcher<Occurrence> getSourceMatcher() {
        return Occurrence.matcher();
    }

    @Override protected IRelation3<Occurrence, Label, ? extends ITerm>
            getEdges(IScopeGraph<Scope, Label, Occurrence> scopeGraph) {
        return scopeGraph.getExportEdges();
    }

}