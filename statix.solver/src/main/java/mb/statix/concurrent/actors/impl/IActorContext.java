package mb.statix.concurrent.actors.impl;

import org.metaborg.util.functions.Function1;

import mb.statix.concurrent.actors.IActor;
import mb.statix.concurrent.actors.IActorRef;
import mb.statix.concurrent.actors.TypeTag;
import mb.statix.concurrent.actors.impl.ActorSystem.ActorTask;

interface IActorContext {

    <U> IActor<U> add(String id, TypeTag<U> type, Function1<IActor<U>, U> supplier);

    <T> T async(IActorRef<T> receiver);

    ActorTask schedule(Actor<?> actor, int priority);

    ActorTask reschedule(ActorTask oldTask, int newPriority);

    boolean preempt(int priority);

}