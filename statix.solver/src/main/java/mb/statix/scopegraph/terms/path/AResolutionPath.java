package mb.statix.scopegraph.terms.path;

import javax.annotation.Nullable;

import org.immutables.serial.Serial;
import org.immutables.value.Value;

import io.usethesource.capsule.Set;
import mb.nabl2.util.collections.ConsList;
import mb.statix.scopegraph.path.IResolutionPath;
import mb.statix.scopegraph.path.IScopePath;

@Value.Immutable
@Serial.Version(value = 42L)
abstract class AResolutionPath<S, L, D> implements IResolutionPath<S, L, D> {

    @Value.Parameter @Override public abstract IScopePath<S, L> getPath();

    @Value.Parameter @Override public abstract L getLabel();

    @Value.Parameter @Override public abstract D getDatum();

    @Value.Check public @Nullable AResolutionPath<S, L, D> check() {
        return this;
    }

    @Value.Lazy @Override public ConsList<S> scopes() {
        return getPath().scopes();
    }

    @Value.Lazy @Override public Set.Immutable<S> scopeSet() {
        return getPath().scopeSet();
    }

    @Value.Lazy @Override public ConsList<L> labels() {
        return getPath().labels();
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPath());
        sb.append(Paths.PATH_SEPARATOR);
        sb.append(getLabel());
        sb.append(Paths.PATH_SEPARATOR);
        sb.append(getDatum());
        return sb.toString();
    }

}