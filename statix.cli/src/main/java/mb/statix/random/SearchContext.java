package mb.statix.random;

import java.util.Random;

public interface SearchContext {

    public Random rnd();

    public int nextNodeId();

    public void progress(char c);

}