package stud.g01.queue;

import core.problem.State;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A* 优先队列。
 *
 * best-g 模式使用 State -> Node 索引，为相同状态保留路径代价 g
 * 最小的有效结点。被替换的旧结点暂时保留在堆中，在 poll 时延迟删除。
 */
public final class PqFrontier implements Frontier {

    private final PriorityQueue<Node> queue;
    private final Map<State, Node> activeNodes;
    private final boolean bestGEnabled;

    private long duplicateRejected;
    private long staleSkipped;
    private int peakSize;

    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled) {

        this.queue = new PriorityQueue<>(
                Objects.requireNonNull(comparator, "comparator"));

        this.activeNodes = new HashMap<>();
        this.bestGEnabled = bestGEnabled;
    }

    public PqFrontier(Comparator<Node> comparator) {
        this(comparator, true);
    }

    public PqFrontier() {
        this(Node.evaluator(EvaluationType.FULL), true);
    }

    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node, "node");

        if (!bestGEnabled) {
            queue.offer(node);
            updatePeakSize();
            return true;
        }

        State state = node.getState();
        Node current = activeNodes.get(state);

        if (current != null
                && current.getPathCost() <= node.getPathCost()) {

            duplicateRejected++;
            return false;
        }

        queue.offer(node);
        activeNodes.put(state, node);
        updatePeakSize();
        return true;
    }

    @Override
    public Node poll() {
        if (!bestGEnabled) {
            return queue.poll();
        }

        while (!queue.isEmpty()) {
            Node node = queue.poll();

            if (activeNodes.get(node.getState()) != node) {
                staleSkipped++;
                continue;
            }

            activeNodes.remove(node.getState());
            return node;
        }

        return null;
    }

    @Override
    public boolean contains(Node node) {
        if (node == null) {
            return false;
        }

        if (bestGEnabled) {
            Node current = activeNodes.get(node.getState());

            return current != null
                    && current.getPathCost() == node.getPathCost();
        }

        for (Node current : queue) {
            if (current.getState().equals(node.getState())
                    && current.getPathCost() == node.getPathCost()) {

                return true;
            }
        }

        return false;
    }

    /**
     * best-g 模式只统计有效结点，不统计过期堆条目。
     */
    @Override
    public int size() {
        return bestGEnabled
                ? activeNodes.size()
                : queue.size();
    }

    @Override
    public boolean isEmpty() {
        return bestGEnabled
                ? activeNodes.isEmpty()
                : queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
        activeNodes.clear();
        duplicateRejected = 0;
        staleSkipped = 0;
        peakSize = 0;
    }

    public boolean bestGEnabled() {
        return bestGEnabled;
    }

    public long duplicateRejected() {
        return duplicateRejected;
    }

    public long staleSkipped() {
        return staleSkipped;
    }

    public int peakSize() {
        return peakSize;
    }

    private void updatePeakSize() {
        peakSize = Math.max(peakSize, size());
    }
}