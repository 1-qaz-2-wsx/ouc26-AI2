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
 * Open 表：
 * - 使用 PriorityQueue 按评价函数排序；
 * - best-g 模式使用 State -> Node 索引；
 * - 相同状态只保留路径代价 g 更小的有效结点；
 * - 被替换的旧结点暂时保留在堆中，在 poll 时跳过。
 *
 * Closed 表：
 * - 使用 State -> Integer 保存已扩展状态的最小 g；
 * - 新结点的 g 不优于 Closed 中的记录时直接拒绝；
 * - 发现更小 g 的路径时允许重新打开该状态。
 */
public final class PqFrontier implements Frontier {

    private final PriorityQueue<Node> queue;

    /**
     * Open 表索引。
     * 只记录当前仍在 Open 表中的有效结点。
     */
    private final Map<State, Node> activeNodes;

    /**
     * Closed 表。
     * 记录每个已扩展状态对应的最小路径代价 g。
     */
    private final Map<State, Integer> closedBestG;

    private final boolean bestGEnabled;
    private final boolean closedEnabled;

    private long duplicateRejected;
    private long staleSkipped;
    private long closedRejected;
    private int peakSize;

    /**
     * 同时配置 Open best-g 和 Closed 表。
     */
    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled,
            boolean closedEnabled) {

        this.queue = new PriorityQueue<>(
                Objects.requireNonNull(comparator, "comparator"));

        this.activeNodes = new HashMap<>();
        this.closedBestG = new HashMap<>();

        this.bestGEnabled = bestGEnabled;
        this.closedEnabled = closedEnabled;
    }

    /**
     * 保留原有构造器。
     *
     * bestGEnabled 为 false 时，同时关闭 Closed 表，
     * 便于作为未优化基线运行。
     */
    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled) {

        this(comparator, bestGEnabled, bestGEnabled);
    }

    public PqFrontier(Comparator<Node> comparator) {
        this(comparator, true, true);
    }

    public PqFrontier() {
        this(Node.evaluator(EvaluationType.FULL), true, true);
    }

    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node, "node");

        State state = node.getState();
        int pathCost = node.getPathCost();

        /*
         * Closed 表检查。
         *
         * 如果 Closed 中已经存在 g 更小或相同的状态，
         * 当前结点不可能带来更优路径，可以直接丢弃。
         *
         * 如果当前 g 更小，则删除旧的 Closed 记录，
         * 允许该状态重新进入 Open 表。
         */
        if (closedEnabled) {
            Integer closedG = closedBestG.get(state);

            if (closedG != null && closedG <= pathCost) {
                closedRejected++;
                return false;
            }

            if (closedG != null && pathCost < closedG) {
                closedBestG.remove(state);
            }
        }

        /*
         * Open 表 best-g 检查。
         */
        if (bestGEnabled) {
            Node current = activeNodes.get(state);

            if (current != null
                    && current.getPathCost() <= pathCost) {
                duplicateRejected++;
                return false;
            }

            queue.offer(node);
            activeNodes.put(state, node);
            updatePeakSize();
            return true;
        }

        /*
         * 基线模式：允许重复结点进入 PriorityQueue。
         */
        queue.offer(node);
        updatePeakSize();
        return true;
    }

    @Override
    public Node poll() {
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            State state = node.getState();

            /*
             * best-g 模式下，跳过已经被更优结点替换的旧条目。
             */
            if (bestGEnabled
                    && activeNodes.get(state) != node) {

                staleSkipped++;
                continue;
            }

            /*
             * 即使关闭了 best-g，只要启用了 Closed 表，
             * 也需要跳过已经有更优或相同 g 的旧结点。
             */
            if (closedEnabled) {
                Integer closedG = closedBestG.get(state);

                if (closedG != null
                        && closedG <= node.getPathCost()) {

                    if (bestGEnabled
                            && activeNodes.get(state) == node) {
                        activeNodes.remove(state);
                    }

                    staleSkipped++;
                    continue;
                }

                closedBestG.put(state, node.getPathCost());
            }

            if (bestGEnabled) {
                activeNodes.remove(state);
            }

            return node;
        }

        return null;
    }

    @Override
    public boolean contains(Node node) {
        if (node == null) {
            return false;
        }

        State state = node.getState();
        int pathCost = node.getPathCost();

        if (bestGEnabled) {
            Node current = activeNodes.get(state);

            return current != null
                    && current.getPathCost() == pathCost;
        }

        for (Node current : queue) {
            if (current.getState().equals(state)
                    && current.getPathCost() == pathCost) {
                return true;
            }
        }

        return false;
    }

    /**
     * best-g 模式下返回有效 Open 结点数量；
     * 基线模式下返回 PriorityQueue 中的结点数量。
     */
    @Override
    public int size() {
        return bestGEnabled
                ? activeNodes.size()
                : queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
        activeNodes.clear();
        closedBestG.clear();

        duplicateRejected = 0;
        staleSkipped = 0;
        closedRejected = 0;
        peakSize = 0;
    }

    public boolean bestGEnabled() {
        return bestGEnabled;
    }

    public boolean closedEnabled() {
        return closedEnabled;
    }

    public long duplicateRejected() {
        return duplicateRejected;
    }

    public long staleSkipped() {
        return staleSkipped;
    }

    public long closedRejected() {
        return closedRejected;
    }

    public int peakSize() {
        return peakSize;
    }

    public int closedSize() {
        return closedBestG.size();
    }

    private void updatePeakSize() {
        peakSize = Math.max(peakSize, size());
    }
}