package mb.statix.concurrent.p_raffrayi.impl;

import mb.nabl2.relations.IRelation;
import mb.scopegraph.oopsla20.reference.EdgeOrData;
import mb.statix.concurrent.p_raffrayi.nameresolution.LabelOrder;

public class RelationLabelOrder<L> implements LabelOrder<L> {

    private final IRelation<EdgeOrData<L>> labelOrd;

    public RelationLabelOrder(IRelation<EdgeOrData<L>> labelOrd) {
        this.labelOrd = labelOrd;
    }

    @Override public boolean lt(EdgeOrData<L> l1, EdgeOrData<L> l2) {
        return labelOrd.contains(l1, l2);
    }

    @Override public String toString() {
        return labelOrd.toString();
    }

}