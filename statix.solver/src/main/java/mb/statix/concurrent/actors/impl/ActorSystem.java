package mb.statix.concurrent.actors.impl;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import org.metaborg.util.functions.Function1;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;

import com.google.common.collect.Maps;

import mb.statix.concurrent.actors.IActor;
import mb.statix.concurrent.actors.IActorMonitor;
import mb.statix.concurrent.actors.IActorRef;
import mb.statix.concurrent.actors.IActorSystem;
import mb.statix.concurrent.actors.TypeTag;

public class ActorSystem implements IActorSystem {

    private static final ILogger logger = LoggerUtils.logger(ActorSystem.class);

    private final Object lock = new Object();
    private final Map<String, Actor<?>> actors;
    private final ExecutorService executorService;
    private volatile ActorSystemState state;
    private final IActorContext context;

    public ActorSystem() {
        this.actors = Maps.newHashMap();
        this.executorService = Executors.newCachedThreadPool();
        this.state = ActorSystemState.INIT;
        this.context = new ActorContext();
    }

    @Override public <T> IActor<T> add(String id, TypeTag<T> type, Function1<IActor<T>, T> supplier) {
        return add(null, id, type, supplier);
    }

    private <T> IActor<T> add(@Nullable IActorRef<?> parent, String id, TypeTag<T> type,
            Function1<IActor<T>, T> supplier) {
        logger.info("add actor {}", id);
        final Actor<T> actor = new Actor<>(context, id, type, supplier);
        synchronized(lock) {
            if(state.equals(ActorSystemState.STOPPED)) {
                throw new IllegalStateException("Actor system already stopped.");
            }
            if(actors.containsKey(id)) {
                throw new IllegalArgumentException("Actor with id " + id + " already exists.");
            }
            actors.put(id, actor);
            if(state.equals(ActorSystemState.RUNNING)) {
                actor.run(executorService);
            }
        }
        logger.info("added actor {}", id);
        return actor;
    }

    @Override public void addMonitor(IActorRef<?> actor, IActorRef<? extends IActorMonitor> monitor) {
        ((Actor<?>) actor).addMonitor(async(monitor));
    }

    @Override public <T> T async(IActorRef<T> receiver) {
        return ((Actor<T>) receiver).asyncSystem;
    }

    @Override public void start() {
        logger.info("start system");
        synchronized(lock) {
            if(!state.equals(ActorSystemState.INIT)) {
                throw new IllegalStateException("Actor system already started.");
            }
            state = ActorSystemState.RUNNING;
            for(Actor<?> actor : actors.values()) {
                actor.run(executorService);
            }
        }
        logger.info("started system");
    }

    @Override public void stop() {
        stop(false);
    }

    @Override public void cancel() {
        stop(true);
    }

    private void stop(boolean force) {
        synchronized(lock) {
            if(!state.equals(ActorSystemState.RUNNING)) {
                throw new IllegalStateException("Actor system not started.");
            }
            state = ActorSystemState.STOPPED;
            for(Actor<?> actor : actors.values()) {
                if(force) {
                    actor.cancel();
                } else {
                    actor.stop();
                }
            }
            executorService.shutdown();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // IActorContext
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private class ActorContext implements IActorContext {

        @Override public <U> IActor<U> add(String id, TypeTag<U> type, Function1<IActor<U>, U> supplier) {
            return ActorSystem.this.add(Actor.current.get(), id, type, supplier);
        }

        @Override public <T> T async(IActorRef<T> receiver) {
            if(!actors.containsValue(receiver)) {
                throw new IllegalArgumentException("Actor " + receiver + " not part of this system.");
            }
            return (T) ((Actor) receiver).asyncActor;
        }

        @Override public ExecutorService executor() {
            return executorService;
        }

    }

}