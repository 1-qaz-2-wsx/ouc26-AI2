package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * IDA*（Iterative Deepening A*，迭代加深的A*）原型。
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

    /** 每展开多少个结点检查一次系统时钟，避免在热循环里反复取时间 */
    private static final int TIME_CHECK_INTERVAL = 2048;

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

    @Override
    public Deque<Node> search(Problem problem) {
        // 允许同一个搜索对象反复使用：每次搜索前清理统计与辅助结构
        frontier.clear();
        explored.clear();
        nodesGenerated = 0;
        nodesExpanded = 0;
        solutionCost = -1;
        aborted = false;

        if (!problem.solvable()) {
            return null;
        }

        currentProblem = problem;
        deadline = timeLimitMillis == NO_LIMIT
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + timeLimitMillis;

        Node root = problem.root(predictor);
        int limit = root.evaluation(); // 初始阈值取 h(初始状态)
        threshold = limit;

        try {
            while (true) {
                currentGoal = null;
                int next = searchWithinThreshold(root, limit);

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
        }
    }

    /**
     * 在 f &lt;= limit 的范围内深度优先搜索。
     *
     * @return 本轮所有被剪掉的结点中最小的 f；不存在更大的 f 时返回 {@link Integer#MAX_VALUE}
     */
    private int searchWithinThreshold(Node node, int limit) {
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
        nodesExpanded++;

        int minExceeded = Integer.MAX_VALUE;
        Node parent = node.getParent();
        for (Node child : currentProblem.childNodes(node, predictor)) {
            nodesGenerated++;
            // 反向移动剪枝：子状态与祖父状态相同，说明这一步只是把上一步撤销回去
            if (parent != null && child.getState().equals(parent.getState())) {
                continue;
            }
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
        if (nodeLimit != NO_LIMIT && nodesGenerated >= nodeLimit) {
            aborted = true;
            return true;
        }
        if (timeLimitMillis != NO_LIMIT
                && nodesExpanded % TIME_CHECK_INTERVAL == 0
                && System.currentTimeMillis() > deadline) {
            aborted = true;
            return true;
        }
        return false;
    }
}