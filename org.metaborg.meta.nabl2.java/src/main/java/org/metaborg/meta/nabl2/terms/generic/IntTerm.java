package org.metaborg.meta.nabl2.terms.generic;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.terms.IAnnotation;
import org.metaborg.meta.nabl2.terms.IIntTerm;

import com.google.common.collect.ImmutableClassToInstanceMap;

@Value.Immutable
@Serial.Structural
abstract class IntTerm implements IIntTerm {

    @Value.Parameter @Override public abstract int getValue();

    @Override public boolean isGround() {
        return true;
    }

    @Value.Default @Value.Auxiliary @Override public ImmutableClassToInstanceMap<IAnnotation> getAnnotations() {
        return ImmutableClassToInstanceMap.<IAnnotation> builder().build();
    }

    @Override public <T> T match(Cases<T> cases) {
        return cases.caseInt(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(CheckedCases<T,E> cases) throws E {
        return cases.caseInt(this);
    }

    @Override public String toString() {
        return Integer.toString(getValue());
    }

}