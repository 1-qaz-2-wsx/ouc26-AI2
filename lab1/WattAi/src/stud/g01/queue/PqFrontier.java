package stud.g01.queue;

import core.problem.State;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* 的 Open 表和可选 Closed 表。
 *
 * Open 使用 PriorityQueue。
 *
 * 当 bestGEnabled=true 时：
 * 同一个状态在 Open 中只保留 g 值最小的有效节点。
 * 被替换的旧节点会被标记为无效，并延迟到堆顶时物理删除。
 *
 * 当 bestGEnabled=false 时：
 * 允许同一个状态存在多个 Open 节点。
 *
 * Closed 由 closedPolicy 控制：
 *
 * NONE：
 *     不维护 Closed。
 *
 * BEST_G：
 *     维护 State -> 最小 g。
 *     新节点的 g 不小于 Closed 中的 g 时被拒绝；
 *     新节点的 g 更小时允许重新打开状态。
 *
 * poll() 不自动把节点加入 Closed。
 * 搜索器在确认节点不是目标并准备扩展时，
 * 应调用 markExpanded(node)。
 */
public final class PqFrontier implements Frontier {

    public enum ClosedPolicy {
        NONE,
        BEST_G
    }

    /**
     * PriorityQueue 中的物理条目。
     *
     * valid=false 表示该条目已经不属于逻辑 Open，
     * 但仍可能暂时残留在 PriorityQueue 中。
     */
    private static final class Entry {

        private final Node node;
        private boolean valid;

        private Entry(Node node) {
            this.node = node;
            this.valid = true;
        }
    }

    /**
     * 物理优先队列。
     */
    private final PriorityQueue<Entry> queue;

    /**
     * 逻辑 Open 索引。
     *
     * 只有仍然有效的 Entry 会放在 pending 中。
     */
    private final Map<State, Set<Entry>> pending;

    /**
     * Closed：State -> 已扩展状态的最小 g。
     */
    private final Map<State, Integer> closedBestG;

    private final boolean bestGEnabled;
    private final ClosedPolicy closedPolicy;

    /**
     * 有效 Open 条目数量。
     *
     * 该值不包含已经被替换或被 Closed 淘汰的物理条目。
     */
    private int validSize;

    private int peakSize;
    private int peakClosed;

    private long duplicateRejected;
    private long closedRejected;
    private long staleSkipped;
    private long reopened;

    /**
     * 创建 Open/Closed 管理器。
     *
     * @param comparator 节点优先级比较器
     * @param bestGEnabled 是否启用 Open best-g
     * @param closedPolicy Closed 策略
     */
    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled,
            ClosedPolicy closedPolicy) {

        Comparator<Node> checkedComparator =
                Objects.requireNonNull(
                        comparator,
                        "comparator");

        this.queue = new PriorityQueue<>(
                (left, right) ->
                        checkedComparator.compare(
                                left.node,
                                right.node));

        this.pending = new HashMap<>();
        this.closedBestG = new HashMap<>();

        this.bestGEnabled = bestGEnabled;
        this.closedPolicy = Objects.requireNonNull(
                closedPolicy,
                "closedPolicy");
    }

    /**
     * 兼容原有三参数 boolean 构造器。
     *
     * closedEnabled=false：
     *     不维护 Closed。
     *
     * closedEnabled=true：
     *     使用 Closed best-g。
     */
    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled,
            boolean closedEnabled) {

        this(
                comparator,
                bestGEnabled,
                closedEnabled
                        ? ClosedPolicy.BEST_G
                        : ClosedPolicy.NONE);
    }

    /**
     * 只配置 Open best-g，不维护 Closed。
     */
    public PqFrontier(
            Comparator<Node> comparator,
            boolean bestGEnabled) {

        this(
                comparator,
                bestGEnabled,
                ClosedPolicy.NONE);
    }

    /**
     * 默认启用 Open best-g，不维护 Closed。
     *
     * 这样不会与课程原有搜索器自己的 Closed 重叠。
     */
    public PqFrontier(Comparator<Node> comparator) {
        this(
                comparator,
                true,
                ClosedPolicy.NONE);
    }

    public PqFrontier() {
        this(Node.evaluator(EvaluationType.FULL));
    }

    /**
     * 将节点加入 Open。
     *
     * @return true 表示节点被接受；
     *         false 表示节点因 Open 或 Closed 规则被拒绝。
     */
    @Override
    public boolean offer(Node node) {
        Objects.requireNonNull(node, "node");

        State state = Objects.requireNonNull(
                node.getState(),
                "node.state");

        int pathCost = node.getPathCost();

        /*
         * Closed best-g 检查。
         *
         * g 不优于 Closed 中的记录时直接拒绝。
         */
        if (closedPolicy == ClosedPolicy.BEST_G) {
            Integer closedG = closedBestG.get(state);

            if (closedG != null && closedG <= pathCost) {
                closedRejected++;
                return false;
            }
        }

        Set<Entry> entries = pending.get(state);

        /*
         * Open best-g：
         * 当前状态已经有更优或相同 g 时拒绝；
         * 新节点更优时，让旧 Entry 永久失效。
         */
        if (bestGEnabled && entries != null) {
            Entry current = entries.iterator().next();

            if (current.node.getPathCost() <= pathCost) {
                duplicateRejected++;
                return false;
            }

            current.valid = false;
            entries.clear();
            validSize--;
        }

        if (entries == null) {
            entries = new HashSet<>();
            pending.put(state, entries);
        }

        Entry entry = new Entry(node);

        queue.offer(entry);
        entries.add(entry);
        validSize++;

        /*
         * 只有节点确实进入 Open 后，
         * 才把它计为一次 reopen。
         */
        if (closedPolicy == ClosedPolicy.BEST_G) {
            Integer previousClosedG = closedBestG.get(state);

            if (previousClosedG != null
                    && pathCost < previousClosedG) {
                closedBestG.remove(state);
                reopened++;
            }
        }

        peakSize = Math.max(
                peakSize,
                validSize);

        return true;
    }

    /**
     * 取出 Open 中优先级最高的有效节点。
     *
     * 过期条目会在堆顶被物理删除。
     *
     * poll() 不自动登记 Closed。
     */
    @Override
    public Node poll() {
        discardInvalidHead();

        if (queue.isEmpty()) {
            return null;
        }

        Entry entry = queue.poll();

        /*
         * discardInvalidHead() 已经保证堆顶有效。
         */
        entry.valid = false;
        validSize--;

        State state = entry.node.getState();
        Set<Entry> entries = pending.get(state);

        if (entries != null) {
            entries.remove(entry);

            if (entries.isEmpty()) {
                pending.remove(state);
            }
        }

        return entry.node;
    }

    /**
     * 登记一个已经确认需要扩展的节点。
     *
     * Phase2AStar 应在：
     *
     * 1. poll() 取出节点；
     * 2. 判断节点不是目标；
     * 3. 准备生成后继；
     *
     * 之后调用此方法。
     */
    public void markExpanded(Node node) {
        Objects.requireNonNull(node, "node");

        if (closedPolicy == ClosedPolicy.NONE) {
            return;
        }

        State state = node.getState();
        int pathCost = node.getPathCost();

        Integer previousG = closedBestG.get(state);

        if (previousG == null
                || pathCost < previousG) {
            closedBestG.put(state, pathCost);
        }

        /*
         * 当前状态已经进入 Closed。
         * Open 中 g 不优于 Closed 记录的条目全部失效。
         */
        invalidateClosedEntries(state);

        peakClosed = Math.max(
                peakClosed,
                closedBestG.size());
    }

    @Override
    public boolean contains(Node node) {
        if (node == null) {
            return false;
        }

        Set<Entry> entries = pending.get(node.getState());

        if (entries == null) {
            return false;
        }

        for (Entry entry : entries) {
            if (entry.valid
                    && entry.node.getPathCost()
                    == node.getPathCost()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 返回有效 Open 节点数量。
     *
     * 这里返回的是逻辑数量，不是 PriorityQueue 的物理大小。
     */
    @Override
    public int size() {
        return validSize;
    }

    /**
     * 判断逻辑 Open 是否为空。
     *
     * 与 size() 使用同一个 validSize，
     * 因此不会因为过期物理节点残留而错误返回 false。
     */
    @Override
    public boolean isEmpty() {
        return validSize == 0;
    }

    @Override
    public void clear() {
        queue.clear();
        pending.clear();
        closedBestG.clear();

        validSize = 0;
        peakSize = 0;
        peakClosed = 0;

        duplicateRejected = 0;
        closedRejected = 0;
        staleSkipped = 0;
        reopened = 0;
    }

    /**
     * 删除某个状态在 Open 中已经被 Closed 支配的条目。
     *
     * 这些条目会立即从逻辑 Open 中移除，
     * 但可能仍暂时残留在 PriorityQueue 中。
     */
    private void invalidateClosedEntries(State state) {
        Set<Entry> entries = pending.get(state);

        if (entries == null) {
            return;
        }

        Iterator<Entry> iterator = entries.iterator();

        while (iterator.hasNext()) {
            Entry entry = iterator.next();

            if (isRejectedByClosed(
                    state,
                    entry.node.getPathCost())) {

                entry.valid = false;
                iterator.remove();
                validSize--;
            }
        }

        if (entries.isEmpty()) {
            pending.remove(state);
        }
    }

    private boolean isRejectedByClosed(
            State state,
            int pathCost) {

        if (closedPolicy != ClosedPolicy.BEST_G) {
            return false;
        }

        Integer closedG = closedBestG.get(state);

        return closedG != null
                && closedG <= pathCost;
    }

    /**
     * 清理 PriorityQueue 堆顶的过期条目。
     *
     * validSize 在条目标记失效时已经更新，
     * 所以这里不能再次减少 validSize。
     */
    private void discardInvalidHead() {
        while (!queue.isEmpty()
                && !queue.peek().valid) {

            queue.poll();
            staleSkipped++;
        }
    }

    public boolean bestGEnabled() {
        return bestGEnabled;
    }

    public boolean closedEnabled() {
        return closedPolicy != ClosedPolicy.NONE;
    }

    public ClosedPolicy closedPolicy() {
        return closedPolicy;
    }

    public long duplicateRejected() {
        return duplicateRejected;
    }

    public long closedRejected() {
        return closedRejected;
    }

    public long duplicatePruned() {
        return duplicateRejected + closedRejected;
    }

    public long staleSkipped() {
        return staleSkipped;
    }

    public long reopened() {
        return reopened;
    }

    public int peakSize() {
        return peakSize;
    }

    public int closedSize() {
        return closedBestG.size();
    }

    public int peakClosed() {
        return peakClosed;
    }
}