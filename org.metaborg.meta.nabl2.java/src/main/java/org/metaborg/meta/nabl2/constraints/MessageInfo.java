package org.metaborg.meta.nabl2.constraints;

import java.util.Optional;

import org.immutables.value.Value;
import org.metaborg.meta.nabl2.solver.UnsatisfiableException;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.Terms.IMatcher;
import org.metaborg.meta.nabl2.terms.Terms.M;
import org.metaborg.util.iterators.Iterables2;

@Value.Immutable
public abstract class MessageInfo implements IMessageInfo {

    public enum Kind {
        ERROR,
        WARNING,
        NOTE
    }

    @Value.Parameter public abstract Kind getKind();

    @Value.Parameter public abstract Optional<ITerm> getMessage();

    @Value.Parameter public abstract Optional<ITerm> getOrigin();

    @Override public UnsatisfiableException makeException(String defaultMessage, Iterable<ITerm> contextTerms) {
        Iterable<ITerm> programPoints = getOrigin().map(t -> Iterables2.singleton(t)).orElse(contextTerms);
        String message = getMessage().map(m -> m.toString()).orElse(defaultMessage);
        return new UnsatisfiableException(getKind(), message, programPoints);
    }

    public static MessageInfo of(ITerm term) {
        return ImmutableMessageInfo.of(Kind.ERROR, Optional.empty(), Optional.of(term));
    }
    
    public static IMatcher<MessageInfo> simpleMatcher() {
        return M.term(MessageInfo::of);
    }

    public static IMatcher<MessageInfo> matcher() {
        return M.appl3("Message", kind(), M.term(), origin(), (appl, kind, message, origin) -> {
            return ImmutableMessageInfo.of(kind, Optional.of(message), origin);
        });
    }

    private static IMatcher<Optional<ITerm>> origin() {
        return M.cases(
            // @formatter:off
            M.appl0("NAME", (t) -> {
                return Optional.empty();
            }),
            M.term((t) -> {
                return Optional.of(t);
            })
            // @formatter:on
        );
    }

    private static IMatcher<Kind> kind() {
        return M.cases(
            // @formatter:off
            M.appl0("Error", e -> Kind.ERROR),
            M.appl0("Warning", e -> Kind.WARNING),
            M.appl0("Note", e -> Kind.NOTE)
            // @formatter:on
        );
    }

}