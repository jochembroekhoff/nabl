package mb.statix.generator.scopegraph;

import java.util.Optional;

import mb.statix.scopegraph.reference.ResolutionException;

public interface DataWF<D, X> {

    Optional<Optional<X>> wf(D d) throws ResolutionException, InterruptedException;

}