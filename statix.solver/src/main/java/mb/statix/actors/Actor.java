package mb.statix.actors;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.metaborg.util.functions.Function1;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;

import com.google.common.collect.Queues;

class Actor<T> implements IActorRef<T>, IActor<T> {

    private static final ILogger logger = LoggerUtils.logger(Actor.class);

    private final String id;
    private final TypeTag<T> type;
    private final T impl;
    private final Set<IActorRef<? extends IActorMonitor>> monitors;

    private final T async;

    private final Object lock;
    private volatile ActorState state;
    private Future<?> task;
    private final Queue<Message> messages;

    Actor(String id, TypeTag<T> type, Function1<IActor<T>, ? extends T> supplier) {
        this.id = id;
        this.type = type;
        this.impl = supplier.apply(this);
        this.monitors = new HashSet<>();

        this.async = makeAsync();

        this.lock = new Object();
        this.state = ActorState.INIT;
        this.messages = Queues.newArrayDeque();

        validate();
    }

    private void validate() {
        if(!type.type().isInstance(impl)) {
            throw new IllegalArgumentException("Given implementation does not implement the given interface.");
        }
    }

    private void put(Message invocation) {
        // ASSERT ActorState != DONE
        synchronized(lock) {
            // It is important to change the state here and not in the message loop.
            // The reason is that doing it here ensures that the unit is activated before
            // the sending unit is suspended. If it were the other way around, there could be
            // a false deadlock reported.
            if(state.equals(ActorState.WAITING)) {
                state = ActorState.RUNNING;
                for(IActorRef<? extends IActorMonitor> monitor : monitors) {
                    monitor.async().resumed(this);
                }
                logger.info("running {}", id);
            }
            messages.add(invocation);
            lock.notify();
        }
    }

    @SuppressWarnings("unchecked") private T makeAsync() {
        return (T) Proxy.newProxyInstance(type.type().getClassLoader(), new Class[] { type.type() },
                (proxy, method, args) -> {
                    // ASSERT ActorState != DONE
                    final Class<?> returnType = method.getReturnType();
                    final Message invocation;
                    final Object returnValue;
                    if(Void.TYPE.isAssignableFrom(returnType)) {
                        invocation = new Message(method, args, null);
                        returnValue = null;
                    } else if(IFuture.class.isAssignableFrom(returnType)) {
                        final CompletableFuture<?> result = new CompletableFuture<>();
                        invocation = new Message(method, args, result);
                        returnValue = result;
                    } else {
                        throw new IllegalStateException("Unsupported method called: " + method);
                    }
                    put(invocation);
                    return returnValue;
                });
    }

    void run(ExecutorService executorService) {
        logger.info("start {}", id);
        synchronized(lock) {
            if(!state.equals(ActorState.INIT)) {
                throw new IllegalStateException("Actor already started.");
            }
            state = ActorState.RUNNING;
            task = executorService.submit(() -> this.run());
        }
    }

    /**
     * Run the actor.
     */
    private void run() {
        logger.info("starting {}", id);
        try {
            while(!state.equals(ActorState.STOPPED)) {
                final Message invocation;
                synchronized(lock) {
                    while(messages.isEmpty()) {
                        logger.info("waiting {}", id);
                        state = ActorState.WAITING;
                        for(IActorRef<? extends IActorMonitor> monitor : monitors) {
                            monitor.async().suspended(this);
                        }
                        lock.wait();
                    }
                    // Here we are always in state RUNNING:
                    // (a) invocations was not empty, and we never suspended
                    // (b) we suspended, and put() set the state to RUNNING before notify()
                    invocation = messages.remove();
                }
                logger.info("invoke {}[{}] : {}", id, state, invocation);
                invocation.deliver();
            }
        } catch(InterruptedException e) {
            logger.info("interrupted {}", id);
        } finally {
            synchronized(lock) {
                state = ActorState.STOPPED;
            }
        }
        logger.info("stopped {}", id);
    }

    @Override public String id() {
        return id;
    }

    /**
     * Get an async interface to the receiver, that can be called non-blocking.
     */
    @Override public T async() {
        return async;
    }

    @Override public void stop() {
        synchronized(lock) {
            if(state.equals(ActorState.INIT) || state.equals(ActorState.STOPPED)) {
                return;
            }
            state = ActorState.STOPPED;
            task.cancel(true);
        }
    }

    @Override public void addMonitor(IActorRef<? extends IActorMonitor> monitor) {
        synchronized(lock) {
            monitors.add(monitor);
            monitor.async().started(this);
        }
    }

    @Override public String toString() {
        return "Actor[" + id + "]";
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private class Message {

        public final Method method;
        public final Object[] args;
        public final ICompletable result;

        public Message(Method method, Object[] args, ICompletable<?> result) {
            this.method = method;
            this.args = args;
            this.result = result;
        }

        public void deliver() {
            try {
                final Object returnValue = method.invoke(impl, args);
                if(result != null) {
                    ((IFuture<?>) returnValue).whenComplete((r, ex) -> {
                        if(ex != null) {
                            result.completeExceptionally(ex);
                        } else {
                            result.complete(r);
                        }
                    });
                }
            } catch(ReflectiveOperationException | IllegalArgumentException ex) {
                if(result != null) {
                    result.completeExceptionally(ex);
                } else {
                    ex.printStackTrace();
                }
            } finally {
            }
        }

        @Override public String toString() {
            return "invoke " + method + "(" + Arrays.toString(args) + ")";
        }

    }

}