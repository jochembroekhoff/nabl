package mb.nabl2.sets;

import static mb.nabl2.terms.matching.TermMatch.M;

import java.util.Optional;
import java.util.Set;

import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import mb.nabl2.terms.matching.TermMatch.IMatcher;

public class SetEvaluator {

    public static <T> IMatcher<ISetProducer<T>> matcher(IMatcher<ISetProducer<T>> elemMatcher) {
        // @formatter:off
        return M.<ISetProducer<T>>casesFix(m -> Iterables2.from(
            elemMatcher,
            M.appl0("EmptySet", (t) -> () -> Sets.newHashSet()),
            M.appl2("Union", m, m, (t, leftSet, rightSet) -> () -> {
                Set<IElement<T>> result = Sets.newHashSet();
                result.addAll(leftSet.apply());
                result.addAll(rightSet.apply());
                return (Set<IElement<T>>)result;
            }),
            M.appl3("Isect", m, SetTerms.projectionMatcher(), m, (t, leftSet, proj, rightSet) -> () -> {
                Multimap<Object,IElement<T>> leftProj = project(leftSet.apply(), proj);
                Multimap<Object,IElement<T>> rightProj = project(rightSet.apply(), proj);
                Multimap<Object,IElement<T>> result = HashMultimap.create();
                result.putAll(leftProj);
                result.putAll(rightProj);
                result.keySet().retainAll(rightProj.keySet());
                result.keySet().retainAll(leftProj.keySet());
                return (Set<IElement<T>>)Sets.newHashSet(result.values());
            }),
            M.appl3("Lsect", m, SetTerms.projectionMatcher(), m, (t, leftSet, proj, rightSet) -> () -> {
                Multimap<Object,IElement<T>> leftProj = project(leftSet.apply(), proj);
                Multimap<Object,IElement<T>> rightProj = project(rightSet.apply(), proj);
                Multimap<Object,IElement<T>> result = HashMultimap.create();
                result.putAll(leftProj);
                result.keySet().retainAll(rightProj.keySet());
                return (Set<IElement<T>>)Sets.newHashSet(result.values());
            }),
            M.appl3("Diff", m, SetTerms.projectionMatcher(), m, (t, leftSet, proj, rightSet) -> () -> {
                Multimap<Object,IElement<T>> leftProj = project(leftSet.apply(), proj);
                Multimap<Object,IElement<T>> rightProj = project(rightSet.apply(), proj);
                Multimap<Object,IElement<T>> result = HashMultimap.create();
                result.putAll(leftProj);
                result.keySet().removeAll(rightProj.keySet());
                return (Set<IElement<T>>)Sets.newHashSet(result.values());
            })
        ));
        // @formatter:on
    }

    public static <T> Multimap<Object, IElement<T>> project(Set<IElement<T>> elems, Optional<String> proj) {
        Multimap<Object, IElement<T>> result = HashMultimap.create();
        for(IElement<T> elem : elems) {
            result.put(proj.map(p -> elem.project(p)).orElseGet(() -> elem.getValue()), elem);
        }
        return result;
    }

}