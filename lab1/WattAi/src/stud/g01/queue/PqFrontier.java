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
 * A* 使用的优先队列。
 *
 * PriorityQueue 中允许暂时保留被替换的旧条目，
 * activeNodes 只记录当前仍在 Frontier 中的有效结点。
 */
public class PqFrontier implements Frontier {

    private final PriorityQueue<Node> queue;

    // 以 State 为键，避免依赖 Node 的 hashCode。
    private final Map<State, Node> activeNodes;

    public PqFrontier(Comparator<Node> comparator) {
        queue = new PriorityQueue<>(
                Objects.requireNonNull(comparator, "comparator")
        );
        activeNodes = new HashMap<>();
    }

    public PqFrontier() {
        this(Node.evaluator(EvaluationType.FULL));
    }

    /**
     * 相同状态只接受路径代价 g 更小的结点。
     */
    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node, "node");

        State state = node.getState();
        Node current = activeNodes.get(state);

        if (current != null
                && node.getPathCost() >= current.getPathCost()) {
            return false;
        }

        queue.offer(node);
        activeNodes.put(state, node);
        return true;
    }

    /**
     * 跳过已被替换的旧条目，只返回当前有效结点。
     */
    @Override
    public Node poll() {
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            State state = node.getState();

            // 使用对象身份判断此条目是否为索引中的当前有效结点。
            if (activeNodes.get(state) != node) {
                continue;
            }

            // 返回前同步清理成员索引。
            activeNodes.remove(state);
            return node;
        }

        return null;
    }

    /**
     * 保留原测试约定：
     * 状态相同且 g 相同，才认为是当前包含的结点。
     */
    @Override
    public boolean contains(Node node) {
        if (node == null) {
            return false;
        }

        Node current = activeNodes.get(node.getState());

        return current != null
                && current.getPathCost() == node.getPathCost();
    }

    /**
     * 只统计有效结点，不统计堆中的过期条目。
     */
    @Override
    public int size() {
        return activeNodes.size();
    }

    @Override
    public boolean isEmpty() {
        return activeNodes.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
        activeNodes.clear();
    }
}