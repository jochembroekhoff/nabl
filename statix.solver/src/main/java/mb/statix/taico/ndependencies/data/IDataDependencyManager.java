package mb.statix.taico.ndependencies.data;

import java.io.Serializable;
import java.util.Collection;

import mb.nabl2.terms.ITerm;
import mb.statix.scopegraph.terms.Scope;
import mb.statix.taico.dependencies.Dependency;
import mb.statix.taico.dependencies.affect.IDataAdditionAffect;
import mb.statix.taico.dependencies.affect.IDataRemovalOrChangeAffect;
import mb.statix.taico.name.NameAndRelation;
import mb.statix.taico.ndependencies.observer.IDependencyObserver;

public interface IDataDependencyManager<T> extends IDependencyObserver, IDataAdditionAffect, IDataRemovalOrChangeAffect, Serializable {
    /**
     * The dependencies of the given scope.
     * 
     * @param scope
     *      the scope
     * 
     * @return
     *      the dependencies
     */
    public Iterable<Dependency> getDependencies(Scope scope);
    
    /**
     * The dependencies on the given edge (scope and label).
     * 
     * @param scope
     *      the scope
     * @param label
     *      the label
     * 
     * @return
     *      the dependencies
     */
    public Collection<Dependency> getDependencies(Scope scope, ITerm label);
    
    /**
     * Adds the given dependency.
     * 
     * @param scope
     *      the scope
     * @param label
     *      the label / label matcher
     * @param dependency
     *      the dependency
     * 
     * @return
     *      true if the dependency was added, false if it was already present
     */
    public boolean addDependency(Scope scope, T label, Dependency dependency);
    
    // --------------------------------------------------------------------------------------------
    // Affect
    // --------------------------------------------------------------------------------------------
    
    @Override
    default Iterable<Dependency> affectedByDataAddition(NameAndRelation nameAndRelation, Scope scope) {
        return getDependencies(scope, nameAndRelation.getRelation());
    }
    
    @Override
    default Iterable<Dependency> affectedByDataRemovalOrChange(NameAndRelation nameAndRelation, Scope scope) {
        return getDependencies(scope, nameAndRelation.getRelation());
    }
}
