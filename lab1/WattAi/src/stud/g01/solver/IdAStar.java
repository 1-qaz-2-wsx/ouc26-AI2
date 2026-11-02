package stud.g01.solver;

import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * IDA*（Iterative Deepening A*，迭代加深的A*）原型。
 * 保留成员C a81d58c 的可选反向剪枝/置换表API；组长接手修复碰撞、容量、时钟与统计。
 *
 * <p>算法流程：每轮只在 f(n)=g(n)+h(n) &lt;= limit 的范围内做深度优先搜索；
 * 如果这一轮越过了目标，就说明当前 limit 下的解已经找到（h 可采纳时该解最优）；
 * 如果没找到，就把本轮遇到的所有被剪掉的结点中最小的 f 作为下一轮的 limit。
 * 当 limit 已经增长到整棵搜索树都没有更大的 f 时，说明问题无解。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>返回值与框架其它搜索算法一致：{@code null} 表示无解或被中断，非空队列为解路径，
 *       队列中不含根结点，导出路径文件时要另行补上初始状态（见 docs/棋盘与路径接口.md）。</li>
 *   <li>{@link Frontier} 在 IDA* 中并不使用，构造器保留该参数只是为了满足框架约定
 *       （{@code EngineFeeder.getIdaStar} 用 {@code new StackFrontier()} 构造本类）。</li>
 *   <li>被中断（超时或超过结点上限）时返回 {@code null} 且 {@link #aborted()} 为 true，
 *       调用方必须记为 TIMEOUT，不能当作 UNSOLVABLE。</li>
 * </ul>
 */
public class IdAStar extends AbstractSearcher {

    /** 不限制结点数或时间 */
    public static final long NO_LIMIT = -1L;

    private final Predictor predictor;

    private long nodeLimit = NO_LIMIT;
    private long timeLimitMillis = NO_LIMIT;

    private long deadline = Long.MAX_VALUE;
    private boolean aborted;
    private int threshold;
    private int solutionCost = -1;

    // 递归过程中共享的搜索上下文
    private Problem currentProblem;
    private Node currentGoal;
    private boolean reversePruning = true;
    private boolean transpositionEnabled;
    private StateHasher stateHasher;
    private int transpositionCapacity = 65536;
    private Map<StateKey, Integer> bestCostByState;
    private int lastTranspositionEntries;
    private int iterations;
    private long generatedCount, expandedCount;

    /** States must remain immutable during a search. Hash is only a bucket selector. */
    private record StateKey(long hash, State state) {
        @Override public int hashCode() { return Long.hashCode(hash); }
        @Override public boolean equals(Object other) {
            return other instanceof StateKey k && hash == k.hash && state.equals(k.state);
        }
    }
    public void setReversePruning(boolean enabled) { reversePruning = enabled; }
    public void setTranspositionTable(boolean enabled, StateHasher hasher) {
        if (enabled && hasher == null) throw new IllegalArgumentException("StateHasher required");
        transpositionEnabled = enabled;
        stateHasher = hasher;
    }
    /** FIFO eviction only removes pruning information; it cannot introduce false matches. */
    public void setTranspositionCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        transpositionCapacity = capacity;
    }
    public int iterations() { return iterations; }
    public int lastTranspositionEntries() { return lastTranspositionEntries; }
    public long generatedCount() { return generatedCount; }
    public long expandedCount() { return expandedCount; }
    @Override public int nodesGenerated() { return (int)Math.min(Integer.MAX_VALUE, generatedCount); }
    @Override public int nodesExpanded() { return (int)Math.min(Integer.MAX_VALUE, expandedCount); }

    // ================== 【新增的字段】 ==================
    private boolean reversePruning = true;                  // 反向移动剪枝，默认开
    private boolean transpositionEnabled;                   // 置换表，默认关（便于做对照）
    private StateHasher stateHasher;
    private Map<Long, Integer> bestCostByState;             // 每轮阈值重建：哈希 -> 已访问最小 g
    private int lastTranspositionEntries;
    private int iterations;
    // ====================================================

    /**
     * @param frontier  仅用于满足框架约定，IDA* 不使用
     * @param predictor 启发函数，必须可采纳（例如曼哈顿距离），否则解的最优性没有保证
     */
    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        if (predictor == null) {
            throw new IllegalArgumentException("predictor 不能为 null");
        }
        this.predictor = predictor;
    }

    /**
     * 保留骨架中的单参数构造器：默认 h≡0，此时退化为迭代加深的Dijkstra，
     * 只能用于状态空间很小的例子。正式实验请使用双参数构造器。
     */
    public IdAStar(Frontier frontier) {
        this(frontier, (state, goal) -> 0);
    }

    /** 结点数上限（按已生成结点计），达到后中断搜索；{@link #NO_LIMIT} 表示不限制 */
    public void setNodeLimit(long limit) {
        this.nodeLimit = limit;
    }

    /** 时间上限（毫秒），超过后中断搜索；{@link #NO_LIMIT} 表示不限制 */
    public void setTimeLimitMillis(long millis) {
        this.timeLimitMillis = millis;
    }

    /** 上一次搜索是否因超时或超过结点上限被中断 */
    public boolean aborted() {
        return aborted;
    }

    /** 上一次搜索最终使用的 f 阈值 */
    public int lastThreshold() {
        return threshold;
    }

    /** 解路径的代价（N-Puzzle 中即步数），未找到解为 -1 */
    public int solutionCost() {
        return solutionCost;
    }

    // ================== 【新增的 public 方法】 ==================
    public void setReversePruning(boolean enabled) {
        this.reversePruning = enabled;
    }

    public void setTranspositionTable(boolean enabled, StateHasher hasher) {
        if (enabled && hasher == null) {
            // 补全了图片截断的异常信息
            throw new IllegalArgumentException("启用置换表时必须提供 StateHasher");
        }
        this.transpositionEnabled = enabled;
        this.stateHasher = hasher;
    }

    public int iterations() {
        return iterations;
    }

    public int lastTranspositionEntries() {
        return lastTranspositionEntries;
    }
    // ===========================================================

    @Override
    public Deque<Node> search(Problem problem) {
        // 允许同一个搜索对象反复使用：每次搜索前清理统计与辅助结构
        frontier.clear();
        explored.clear();
        nodesGenerated = 0;
        nodesExpanded = 0;
        solutionCost = -1;
        aborted = false;
        threshold = 0;
        generatedCount = expandedCount = 0;
        iterations = lastTranspositionEntries = 0;
        bestCostByState = null;

        // One monotonic deadline covers solvability, root construction and every iteration.
        long budget = timeLimitMillis < 0 ? Long.MAX_VALUE
                : (timeLimitMillis > Long.MAX_VALUE / 1_000_000L ? Long.MAX_VALUE : timeLimitMillis * 1_000_000L);
        deadline = System.nanoTime() + budget;

        // [新增] 每次搜索重置迭代次数与置换表统计
        iterations = 0;
        lastTranspositionEntries = 0;
        bestCostByState = null;

        if (!problem.solvable()) {
            isAborted();
            return null;
        }

        currentProblem = problem;
        try {
            Node root = problem.root(predictor);
            int limit = root.evaluation();
            threshold = limit;
            while (true) {
                currentGoal = null;

                int next = searchWithinThreshold(root, limit);
                if (bestCostByState != null) lastTranspositionEntries = bestCostByState.size();

                // [新增] 本轮结束后记录置换表的大小
                if (transpositionEnabled && bestCostByState != null) {
                    lastTranspositionEntries = bestCostByState.size();
                }

                if (currentGoal != null) {
                    solutionCost = currentGoal.getPathCost();
                    return generatePath(currentGoal); // 队列不含根结点
                }
                if (aborted) {
                    return null;
                }
                if (next == Integer.MAX_VALUE) {
                    return null; // 搜索树已遍历完毕仍没有目标：无解
                }
                limit = next;
                threshold = next;
            }
        } finally {
            currentProblem = null;
            currentGoal = null;
            bestCostByState = null;
        }
    }

    /**
     * 在 f &lt;= limit 的范围内深度优先搜索。
     *
     * @return 本轮所有被剪掉的结点中最小的 f；不存在更大的 f 时返回 {@link Integer#MAX_VALUE}
     */
    private int searchWithinThreshold(Node node, int limit) {
        if (isAborted()) return Integer.MAX_VALUE;
        int f = node.evaluation();
        if (f > limit) {
            return f; // 剪枝，同时把 f 作为下一轮阈值的候选
        }

        if (currentProblem.goal(node.getState())) {
            currentGoal = node;
            return Integer.MAX_VALUE;
        }

        if (isAborted()) {
            return Integer.MAX_VALUE;
        }


        int minExceeded = Integer.MAX_VALUE;
        Node parent = node.getParent();
        for (Node child : currentProblem.childNodes(node, predictor)) {

            // 反向移动剪枝：子状态与祖父状态相同，说明这一步只是把上一步撤销回去
            if (reversePruning && parent != null && child.getState().equals(parent.getState())) {
                continue;
            }
            // =================================================================

            int exceeded = searchWithinThreshold(child, limit);
            if (currentGoal != null || aborted) {
                return exceeded;
            }
            if (exceeded < minExceeded) {
                minExceeded = exceeded;
            }
        }
        return minExceeded;
    }

    private boolean isAborted() {
        if (aborted) {
            return true;
        }
        if (nodeLimit != NO_LIMIT && generatedCount >= nodeLimit) {
            aborted = true;
            return true;
        }
        if (timeLimitMillis != NO_LIMIT
                && System.nanoTime() - deadline >= 0) {
            aborted = true;
            return true;
        }
        return false;
    }
}
