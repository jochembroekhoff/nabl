package org.metaborg.meta.nabl2.terms.generic;

import java.util.Iterator;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.terms.IAnnotation;
import org.metaborg.meta.nabl2.terms.INilTerm;
import org.metaborg.meta.nabl2.terms.ITerm;

import com.google.common.collect.ImmutableClassToInstanceMap;

@Value.Immutable
@Serial.Structural
abstract class NilTerm implements INilTerm {

    @Override public boolean isGround() {
        return true;
    }

    @Override public int getLength() {
        return 0;
    }

    @Value.Default @Value.Auxiliary @Override public ImmutableClassToInstanceMap<IAnnotation> getAnnotations() {
        return ImmutableClassToInstanceMap.<IAnnotation> builder().build();
    }

    @Override public <T> T match(ITerm.Cases<T> cases) {
        return cases.caseList(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(ITerm.CheckedCases<T,E> cases) throws E {
        return cases.caseList(this);
    }

    @Override public <T> T match(Cases<T> cases) {
        return cases.caseNil(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(CheckedCases<T,E> cases) throws E {
        return cases.caseNil(this);
    }

    @Override public Iterator<ITerm> iterator() {
        return new ListTermIterator(this);
    }

    @Override public String toString() {
        return "[]";
    }

}