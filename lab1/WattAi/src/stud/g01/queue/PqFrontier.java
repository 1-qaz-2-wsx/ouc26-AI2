package stud.g01.queue;

import core.problem.State;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class PqFrontier implements Frontier {
    private final PriorityQueue<Node> queue;
    private final Map<State, Integer> bestG;

    public PqFrontier(Comparator<Node> comparator) {
        this.queue = new PriorityQueue<>(comparator);
        this.bestG = new HashMap<>();
    }

    public PqFrontier() {
        this(Node.evaluator(EvaluationType.FULL));
    }

    @Override
    public Node poll() {
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            State state = node.getState();
            Integer bestKnownG = bestG.get(state);
            if (bestKnownG != null && node.getPathCost() > bestKnownG) {
                continue;
            }
            return node;
        }
        return null;
    }

    @Override public void clear() { queue.clear(); bestG.clear(); }
    @Override public int size() { return queue.size(); }
    @Override public boolean isEmpty() { return queue.isEmpty(); }
    @Override public boolean contains(Node node) {
        Integer g = bestG.get(node.getState());
        return g != null && g == node.getPathCost();
    }

    @Override
    public boolean offer(Node node) {
        State state = node.getState();
        int currentG = node.getPathCost();
        Integer bestKnownG = bestG.get(state);
        if (bestKnownG == null || currentG < bestKnownG) {
            bestG.put(state, currentG);
            queue.offer(node);
            return true;
        }
        return false;
    }
}