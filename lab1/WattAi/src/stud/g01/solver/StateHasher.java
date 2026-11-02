package stud.g01.solver;
import core.problem.State;
/** C's state hash API. Equal states must hash equally; collisions are allowed. */
@FunctionalInterface
public interface StateHasher { long hash(State state); }
