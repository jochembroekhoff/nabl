package mb.statix.modular.ndependencies.data;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Multimap;

import mb.nabl2.terms.ITerm;
import mb.statix.modular.dependencies.Dependency;
import mb.statix.modular.dependencies.details.NameDependencyDetail;
import mb.statix.modular.dependencies.details.QueryDependencyDetail;
import mb.statix.modular.util.TOverrides;
import mb.statix.scopegraph.reference.LabelWF;
import mb.statix.scopegraph.reference.ResolutionException;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.util.collection.MapMultimap;

public class RegexDataDependencyManager implements IDataDependencyManager<LabelWF<ITerm>>, Serializable {
    private static final long serialVersionUID = 1L;
    
    private MapMultimap<Scope, LabelWF<ITerm>, Dependency> dataDependencies = TOverrides.mapSetMultimap();
    private final boolean rejectNames;
    
    public RegexDataDependencyManager(boolean rejectNames) {
        this.rejectNames = rejectNames;
    }
    
    @Override
    public Collection<Dependency> getDependencies(Scope scope) {
        return dataDependencies.get2(scope).values();
    }
    
    @Override
    public Set<Dependency> getDependencies(Scope scope, ITerm label) {
        Multimap<LabelWF<ITerm>, Dependency> nmap;
        synchronized (this) {
            nmap = dataDependencies.get2(scope);
        }
        if (nmap.isEmpty()) return Collections.emptySet();
        
        Set<Dependency> tbr = new HashSet<>();
        for (LabelWF<ITerm> labelWf : nmap.keySet()) {
            try {
                if (!labelWf.canStep(label)) continue;
            } catch (ResolutionException | InterruptedException ex) {
                continue;
            }
            tbr.addAll(nmap.get(labelWf));
        }
        return tbr;
    }
    
    @Override
    public boolean addDependency(Scope scope, LabelWF<ITerm> label, Dependency dependency) {
        return dataDependencies.put(scope, label, dependency);
    }
    
    @Override
    public void removeDependencies(Collection<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            for (Entry<Scope, LabelWF<ITerm>> entry : getData(dependency)) {
                dataDependencies.remove(entry.getKey(), entry.getValue(), dependency);
            }
        }
    }
    
    @Override
    public synchronized void onDependencyAdded(Dependency dependency) {
        if (rejectNames && dependency.hasDetails(NameDependencyDetail.class)) return;
        for (Entry<Scope, LabelWF<ITerm>> entry : getData(dependency)) {
            dataDependencies.put(entry.getKey(), entry.getValue(), dependency);
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Affect
    // --------------------------------------------------------------------------------------------
    
    @Override
    public int dataNameAdditionAffectScore() {
        return 1; //O(n) lookup, exact
    }
    
    @Override
    public int dataNameRemovalOrChangeAffectScore() {
        return 3; //O(n) lookup, SOMETIMES reports affected when this is not the case
    }
    
    @Override
    public int dataAdditionAffectScore() {
        return 1; //O(n) lookup, exact
    }
    
    @Override
    public int dataRemovalAffectScore() {
        return 3; //O(n) lookup, SOMETIMES reports affected when this is not the case
    }
    
    // --------------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------------
    
    private static Collection<Entry<Scope, LabelWF<ITerm>>> getData(Dependency dependency) {
        QueryDependencyDetail detail = dependency.getDetails(QueryDependencyDetail.class);
        return detail.getRelevantData().entries();
    }
    
}