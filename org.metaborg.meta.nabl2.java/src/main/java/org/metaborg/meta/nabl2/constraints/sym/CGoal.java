package org.metaborg.meta.nabl2.constraints.sym;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.constraints.IConstraint;
import org.metaborg.meta.nabl2.constraints.MessageInfo;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.unification.IUnifier;

@Value.Immutable
@Serial.Version(value = 42L)
public abstract class CGoal implements ISymbolicConstraint {

    @Value.Parameter public abstract ITerm getGoal();

    @Value.Parameter @Override public abstract MessageInfo getMessageInfo();

    @Override public IConstraint find(IUnifier unifier) {
        return ImmutableCFact.of(unifier.find(getGoal()), getMessageInfo());
    }

    @Override public <T> T match(Cases<T> cases) {
        return cases.caseGoal(this);
    }

    @Override public <T> T match(IConstraint.Cases<T> cases) {
        return cases.caseSym(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(CheckedCases<T,E> cases) throws E {
        return cases.caseGoal(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(IConstraint.CheckedCases<T,E> cases) throws E {
        return cases.caseSym(this);
    }

    @Override public String toString() {
        return "?- " + getGoal();
    }

}