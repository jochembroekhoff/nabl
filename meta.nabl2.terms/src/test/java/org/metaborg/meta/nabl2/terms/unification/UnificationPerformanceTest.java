package org.metaborg.meta.nabl2.terms.unification;

import java.util.List;

import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermVar;
import org.metaborg.meta.nabl2.terms.build.TB;
import org.metaborg.meta.nabl2.terms.unification.IUnifier;
import org.metaborg.meta.nabl2.terms.unification.PersistentUnifier;
import org.metaborg.meta.nabl2.terms.unification.UnificationException;
import org.metaborg.util.iterators.Iterables2;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class UnificationPerformanceTest {

    private static final String A = "a";
    private static final String B = "b";
    private static final String C = "c";

    public static void main(String[] args) {
        testCycle(true);
        testCycle(false);
        for(int n = 0; n <= 1000; n += 100) {
            System.out.println("Testing n = " + n);
            final long t0 = System.currentTimeMillis();
            System.out.println(testUnify(n));
            final long dt = System.currentTimeMillis() - t0;
            System.out.println("Finished in " + (dt / 1000.0) + "s");
        }
    }

    private static void testCycle(boolean allowRecursive) {
        System.out.println("Testing cycle");
        final IUnifier.Transient unifier = PersistentUnifier.Transient.of(allowRecursive);
        ITermVar varA = TB.newVar("", A);
        ITermVar varB = TB.newVar("", B);
        ITermVar varC = TB.newVar("", C);
        ITerm termB = TB.newTuple(varB, varB);
        ITerm termC = TB.newTuple(varC, varC);
        try {
            unifier.unify(varA, termB);
            unifier.unify(varB, termC);
            unifier.unify(varC, termB);
            System.out.println(unifier);
        } catch(UnificationException e) {
            System.out.println("Could not unify");
        }
        System.out.println("ground = " + unifier.isGround(termB));
        System.out.println("cyclic = " + unifier.isCyclic(termB));
        System.out.println("size = " + unifier.size(termB));
        System.out.println("vars = " + unifier.getVars(termB));
        System.out.println("equal = " + unifier.areEqual(termB, termC));
        System.out.println("unequal = " + unifier.areUnequal(termB, termC));
    }

    private static IUnifier testUnify(int n) {
        final IUnifier.Transient unifier = PersistentUnifier.Transient.of(true);
        final ITerm left = TB.newTuple(
                Iterables.concat(createVars(A, n), createTuples(B, n), Iterables2.singleton(createVar(A, n))));
        final ITerm right = TB.newTuple(
                Iterables.concat(createTuples(A, n), createVars(B, n), Iterables2.singleton(createVar(B, n))));
        try {
            unifier.unify(left, right);
        } catch(UnificationException e) {
            System.err.println("Unification failed");
            e.printStackTrace(System.err);
        }
        System.out.println("ground = " + unifier.isGround(left));
        System.out.println("cyclic = " + unifier.isCyclic(left));
        System.out.println("size = " + unifier.size(left));
        System.out.println("vars = " + unifier.getVars(left));
        System.out.println("equal = " + unifier.areEqual(left, right));
        System.out.println("unequal = " + unifier.areUnequal(left, right));
        return unifier;
    }

    private static List<ITerm> createVars(String name, int n) {
        List<ITerm> vars = Lists.newArrayListWithExpectedSize(n);
        for(int i = 1; i <= n; i++) {
            vars.add(createVar(name, i));
        }
        return vars;
    }

    private static List<ITerm> createTuples(String name, int n) {
        List<ITerm> tups = Lists.newArrayListWithExpectedSize(n);
        for(int i = 1; i <= n; i++) {
            tups.add(createTuple(name, i));
        }
        return tups;
    }

    private static ITerm createVar(String name, int i) {
        return TB.newVar("", name + "-" + i);
    }

    private static ITerm createTuple(String name, int i) {
        return TB.newTuple(createVar(name, i - 1), createVar(name, i - 1));
    }

}