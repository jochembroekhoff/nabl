package mb.statix.taico.solver.store;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Multimap;

import mb.nabl2.terms.ITermVar;
import mb.statix.scopegraph.reference.CriticalEdge;
import mb.statix.solver.Delay;
import mb.statix.solver.IConstraint;
import mb.statix.solver.IConstraintStore;
import mb.statix.solver.log.IDebugContext;
import mb.statix.taico.module.IModule;
import mb.statix.taico.module.ModuleManager;
import mb.statix.taico.scopegraph.OwnableScope;
import mb.statix.taico.util.IOwnable;

public class ModuleConstraintStore implements IConstraintStore {
    private final ModuleManager manager;
    private final Queue<IConstraint> active;
    private final Queue<IConstraint> stuckBecauseStuck;
    private final Multimap<ITermVar, IConstraint> stuckOnVar;
    private final Multimap<CriticalEdge, IConstraint> stuckOnEdge;
    
    private final Multimap<CriticalEdge, ModuleConstraintStore> edgeObservers;
    
    private volatile IObserver<ModuleConstraintStore> observer;
    private boolean progress;
    private AtomicBoolean progressCheck = new AtomicBoolean();
    
    public ModuleConstraintStore(ModuleManager manager, Iterable<? extends IConstraint> constraints, IDebugContext debug) {
        this.manager = manager;
        this.active = new LinkedBlockingQueue<>();
        this.stuckBecauseStuck = new LinkedList<>();
        this.stuckOnVar = HashMultimap.create();
        this.stuckOnEdge = HashMultimap.create();
        this.edgeObservers = HashMultimap.create();
        addAll(constraints);
    }
    
    public void setStoreObserver(IObserver<ModuleConstraintStore> observer) {
        this.observer = observer;
    }
    
    public int activeSize() {
        return active.size();
    }
    
    public int delayedSize() {
        return stuckBecauseStuck.size() + stuckOnVar.size() + stuckOnEdge.size();
    }
    
    public void addAll(Iterable<? extends IConstraint> constraints) {
        for (IConstraint constraint : constraints) {
            active.add(constraint);
        }
    }
    
    public void activateStray() {
        for (IConstraint constraint : stuckBecauseStuck) {
            active.add(constraint);
        }
        stuckBecauseStuck.clear();
        progress = false;
    } 
    
    /**
     * The solver is guaranteed to be done if it has no more constraints.
     * It should be able to be done even if there are child solvers still solving.
     * 
     * <p>NOTE: This method is not concurrency safe! There is a small window where a true result
     * does not mean that the solver is done. The result is only correct if it is requested by the
     * thread currently executing the solver, or if there is no thread currently executing the
     * solver.
     * 
     * @return
     *      true if this solver is done, false otherwise
     */
    public boolean isDone() {
        return activeSize() + delayedSize() == 0;
    }
    
    /**
     * @return
     *      true if this store can make progress
     */
    public boolean canProgress() {
        return activeSize() > 0;
    }
    
    /**
     * Check after a full cycle.
     * 
     * @return
     *      true if there are no constraints is stuck waiting, false otherwise
     */
    public boolean isStuck() {
        return !progress && activeSize() == 0 && delayedSize() != 0;
    }
    
    public void externalProgress() {
        progress = true;
    }
    
    public void activateFromVars(Iterable<? extends ITermVar> vars, IDebugContext debug) {
        for (ITermVar var : vars) {
            final Collection<IConstraint> activated;
            synchronized (stuckOnVar) {
                activated = stuckOnVar.removeAll(var);
                stuckOnVar.values().removeAll(activated);
            }
            debug.info("activating {}", activated);
            addAll(activated);
        }
    }
    
    public void activateFromEdges(Collection<? extends CriticalEdge> edges, IDebugContext debug) {
        if (edges.isEmpty()) return;
        
        for (CriticalEdge edge : edges) {
            activateFromEdge(edge, debug);
        }
        
        Set<ModuleConstraintStore> stores = new HashSet<>();
        synchronized (edgeObservers) {
            //Activate all observers
            for (CriticalEdge edge : edges) {
                for (ModuleConstraintStore store : edgeObservers.removeAll(edge)) {
                    //Only notify if it is currently not doing anything
                    if (store.activeSize() == 0) stores.add(store);
                    System.err.println("Delegating activation of edge " + edge);
                    store.activateFromEdge(edge, debug); //Activate but don't propagate
                    
                }
            }
            
            //Notify each store only once
            for (ModuleConstraintStore store : stores) {
                if (store.observer != null) store.observer.notify(this);
            }
        }
    }
    
    public void registerObserver(CriticalEdge edge, ModuleConstraintStore store, IDebugContext debug) {
        synchronized (edgeObservers) {
            edgeObservers.put(edge, store);
        }
    }
    
    public void activateFromEdge(CriticalEdge edge, IDebugContext debug) {
        final Collection<IConstraint> activated;
        synchronized (stuckOnEdge) {
            activated = stuckOnEdge.removeAll(edge);
            stuckOnEdge.values().removeAll(activated);
        }
        debug.info("activating edge {}", edge);
        if (activated.isEmpty()) {
            debug.info("activating {}", activated);
        } else {
            debug.info("no constraints were activated");
        }
        addAll(activated);
    }
    
    public Iterable<IConstraintStore.Entry> active(IDebugContext debug) {
        throw new UnsupportedOperationException("Request elements one by one");
    }
    
    /**
     * Gets an element from the {@link #active} queue. If the queue is empty, this method will take
     * care of activating the stray constraints.
     * 
     * @return
     *      an active constraint, or null if there are no more active constraints
     */
    private IConstraint _getActiveConstraint() {
        IConstraint constraint = active.poll();
        if (constraint != null) return constraint;

        synchronized (this) {
            if (progress) {
                //Do the rollover
                activateStray();
                return active.poll();
            }
        }
        
        //we are stuck (potentially waiting for another solver)
        return constraint;
    }
    
    /**
     * Checks if progress has been made since the last time this method was called.
     * 
     * @return
     *      true if progress was made, false otherwise
     */
    public boolean checkProgressAndReset() {
        return progressCheck.getAndSet(false);
    }
    
    /**
     * Gets an active constraint from this store.
     * 
     * @param debug
     *      the debug context
     * @return
     *      an entry
     */
    public Entry getActiveConstraint(IDebugContext debug) {
        IConstraint constraint = _getActiveConstraint();
        if (constraint == null) return null;
        
        return new Entry() {
            @Override
            public IConstraint constraint() {
                return constraint;
            }

            @Override
            public void delay(Delay d) {
                try {
                    if (!d.vars().isEmpty()) {
                        debug.info("delayed {} on vars {}", constraint, d.vars());
                        for (ITermVar var : d.vars()) {
                            stuckOnVar.put(var, constraint);
                        }
                    } else if (!d.criticalEdges().isEmpty()) {
                        debug.info("delayed {} on critical edges {}", constraint, d.criticalEdges());
                        for (CriticalEdge edge : d.criticalEdges()) {
                            stuckOnEdge.put(edge, constraint);
                            registerAsObserver(edge, debug);
                        }
                    } else {
                        debug.warn("delayed {} for no apparent reason ", constraint);
                        stuckBecauseStuck.add(constraint);
                    }
                } finally {
                    if (d.getLockManager() != null) {
                        d.getLockManager().releaseAll();
                    }
                }
            }

            @Override
            public void remove() {
                synchronized (ModuleConstraintStore.this) {
                    progress = true;
                }
                progressCheck.set(true);
            }
        };
    }
    
    /**
     * Registers this store as an observer of the given critical edge.
     * The registration is made with the store of the solver of the owner of the scope of the given
     * critical edge.
     * 
     * @param edge
     *      the edge
     * @param debug
     *      the debug context
     */
    private void registerAsObserver(CriticalEdge edge, IDebugContext debug) {
        IModule owner;
        
        if (edge.cause() != null) {
            owner = edge.cause();
        } else {
            System.err.println("ENCOUNTERED CRITIAL EDGE WITHOUT OWNER (" + edge + ")! USING SCOPE OWNER!");
            owner = OwnableScope.ownableMatcher(manager::getModule)
                    .match(edge.scope())
                    .map(IOwnable::getOwner)
                    .orElseThrow(() -> new IllegalStateException("Scope of critical edge does not have an owning module: " + edge.scope()));
        }
        
        debug.info("Registering as observer on {}, waiting on edge {}", owner, edge);
        //TODO Static state access
        ModuleConstraintStore store = owner.getCurrentState().solver().getStore();
        store.registerObserver(edge, this, debug);
    }
    
    public Map<IConstraint, Delay> delayed() {
        Builder<IConstraint, Delay> delayed = ImmutableMap.builder();
        
        stuckBecauseStuck.stream().forEach(c -> delayed.put(c, Delay.of()));
        Multimap<IConstraint, ITermVar> stuckOnVarInverse = HashMultimap.create();
        for (Map.Entry<ITermVar, IConstraint> e : stuckOnVar.entries()) {
            stuckOnVarInverse.put(e.getValue(), e.getKey());
        }
        stuckOnVarInverse.asMap().entrySet().stream().forEach(e -> delayed.put(e.getKey(), Delay.ofVars(e.getValue())));
        stuckOnEdge.entries().stream().forEach(e -> delayed.put(e.getValue(), Delay.ofCriticalEdge(e.getKey())));
        return delayed.build();
    }
}

