package stud.g01.solver;

import core.problem.Problem;
import core.problem.State;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.queue.PqFrontier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 可切换启发函数和 Open/Closed 策略的 A*。
 *
 * 实验模式：
 *
 * BASELINE：
 *     Open 不使用 best-g；
 *     Closed 只保存是否访问过。
 *
 * OPEN_BEST_G：
 *     Open 使用 best-g；
 *     Closed 只保存是否访问过。
 *
 * CLOSED_BEST_G：
 *     Open 不使用 best-g；
 *     Closed 保存最小 g，并支持 reopen。
 *
 * FULL_OPTIMIZED：
 *     Open 使用 best-g；
 *     Closed 保存最小 g，并支持 reopen。
 */
public final class Phase2AStar {

    public enum SearchMode {

        BASELINE(false, false),

        OPEN_BEST_G(true, false),

        CLOSED_BEST_G(false, true),

        FULL_OPTIMIZED(true, true);

        private final boolean openBestG;
        private final boolean closedBestG;

        SearchMode(
                boolean openBestG,
                boolean closedBestG) {

            this.openBestG = openBestG;
            this.closedBestG = closedBestG;
        }

        public boolean usesOpenBestG() {
            return openBestG;
        }

        public boolean usesClosedBestG() {
            return closedBestG;
        }
    }

    public static final class SearchResult {

        private final boolean solvable;
        private final boolean solved;
        private final Deque<Node> path;
        private final long elapsedNanos;
        private final long generated;
        private final long expanded;
        private final long duplicatePruned;
        private final long staleSkipped;
        private final long reopened;
        private final int peakOpen;
        private final int peakClosed;

        private SearchResult(
                boolean solvable,
                boolean solved,
                Deque<Node> path,
                long elapsedNanos,
                long generated,
                long expanded,
                long duplicatePruned,
                long staleSkipped,
                long reopened,
                int peakOpen,
                int peakClosed) {

            this.solvable = solvable;
            this.solved = solved;
            this.path = path == null
                    ? null
                    : new ArrayDeque<>(path);
            this.elapsedNanos = elapsedNanos;
            this.generated = generated;
            this.expanded = expanded;
            this.duplicatePruned = duplicatePruned;
            this.staleSkipped = staleSkipped;
            this.reopened = reopened;
            this.peakOpen = peakOpen;
            this.peakClosed = peakClosed;
        }

        public boolean solvable() {
            return solvable;
        }

        public boolean solved() {
            return solved;
        }

        public Deque<Node> path() {
            return path == null
                    ? null
                    : new ArrayDeque<>(path);
        }

        public int solutionLength() {
            return path == null ? -1 : path.size();
        }

        public long elapsedNanos() {
            return elapsedNanos;
        }

        public double elapsedMillis() {
            return elapsedNanos / 1_000_000.0;
        }

        public long generated() {
            return generated;
        }

        public long expanded() {
            return expanded;
        }

        public long duplicatePruned() {
            return duplicatePruned;
        }

        public long staleSkipped() {
            return staleSkipped;
        }

        public long reopened() {
            return reopened;
        }

        public int peakOpen() {
            return peakOpen;
        }

        public int peakClosed() {
            return peakClosed;
        }
    }

    private final Predictor predictor;
    private final SearchMode mode;

    public Phase2AStar(
            Predictor predictor,
            SearchMode mode) {

        this.predictor = Objects.requireNonNull(
                predictor,
                "predictor");

        this.mode = Objects.requireNonNull(
                mode,
                "mode");
    }

    public SearchResult search(Problem problem) {
        Objects.requireNonNull(problem, "problem");

        long started = System.nanoTime();

        if (!problem.solvable()) {
            return result(
                    false,
                    false,
                    null,
                    started,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        }

        PqFrontier open = new PqFrontier(
                Node.evaluator(EvaluationType.FULL),
                mode.usesOpenBestG());

        Set<State> closedSet =
                mode.usesClosedBestG()
                        ? null
                        : new HashSet<>();

        Map<State, Integer> closedBestG =
                mode.usesClosedBestG()
                        ? new HashMap<>()
                        : null;

        long generated = 0;
        long expanded = 0;
        long duplicatePruned = 0;
        long reopened = 0;

        int peakClosed = 0;

        Node root = problem.root(predictor);
        open.offer(root);

        while (!open.isEmpty()) {
            Node node = open.poll();

            if (node == null) {
                break;
            }

            State state = node.getState();
            int nodeG = node.getPathCost();

            /*
             * 对于 Closed best-g：
             *
             * 如果当前状态已经以更小或相同的 g 被扩展，
             * 当前结点就是重复结点，应当跳过。
             */
            if (mode.usesClosedBestG()) {
                Integer closedG = closedBestG.get(state);

                if (closedG != null && closedG <= nodeG) {
                    duplicatePruned++;
                    continue;
                }
            } else {
                /*
                 * 对于普通 Closed Set：
                 * 状态只要扩展过一次，后续重复结点都跳过。
                 */
                if (closedSet.contains(state)) {
                    duplicatePruned++;
                    continue;
                }
            }

            /*
             * 目标状态必须在从 Open 取出后判断。
             *
             * 对于使用可采纳启发函数的 A*，
             * 第一个从 Open 取出的目标结点是最优解。
             */
            if (problem.goal(state)) {
                return result(
                        true,
                        true,
                        generatePath(node),
                        started,
                        generated,
                        expanded,
                        duplicatePruned,
                        open.staleSkipped(),
                        reopened,
                        open.peakSize(),
                        peakClosed);
            }

            /*
             * 将当前状态加入 Closed。
             */
            if (mode.usesClosedBestG()) {
                closedBestG.put(state, nodeG);

                peakClosed = Math.max(
                        peakClosed,
                        closedBestG.size());
            } else {
                closedSet.add(state);

                peakClosed = Math.max(
                        peakClosed,
                        closedSet.size());
            }

            /*
             * 扩展当前结点。
             */
            for (Node child
                    : problem.childNodes(node, predictor)) {

                generated++;

                State childState = child.getState();
                int childG = child.getPathCost();

                boolean reopenedChild = false;

                /*
                 * 先检查 Closed。
                 */
                if (mode.usesClosedBestG()) {
                    Integer closedG =
                            closedBestG.get(childState);

                    if (closedG != null) {
                        if (closedG <= childG) {
                            /*
                             * 已经以更小或相同代价扩展过。
                             */
                            duplicatePruned++;
                            continue;
                        }

                        /*
                         * 找到更短路径。
                         * 移除旧 Closed 记录，允许重新进入 Open。
                         */
                        closedBestG.remove(childState);
                        reopenedChild = true;
                    }
                } else {
                    if (closedSet.contains(childState)) {
                        duplicatePruned++;
                        continue;
                    }
                }

                /*
                 * PqFrontier 根据 Open best-g 配置决定：
                 *
                 * - 是否接受相同状态的更小 g；
                 * - 是否拒绝相同或更大的 g；
                 * - 是否保留旧结点并延迟删除。
                 */
                boolean accepted = open.offer(child);

                if (accepted) {
                    if (reopenedChild) {
                        reopened++;
                    }
                } else {
                    duplicatePruned++;
                }
            }

            expanded++;
        }

        /*
         * 对于可解问题，如果 Open 为空仍未找到目标，
         * 说明搜索没有得到解。
         */
        return result(
                true,
                false,
                null,
                started,
                generated,
                expanded,
                duplicatePruned,
                open.staleSkipped(),
                reopened,
                open.peakSize(),
                peakClosed);
    }

    public Predictor predictor() {
        return predictor;
    }

    public SearchMode mode() {
        return mode;
    }

    private static Deque<Node> generatePath(Node goal) {
        Deque<Node> path = new ArrayDeque<>();

        Node current = goal;

        while (current.getParent() != null) {
            path.push(current);
            current = current.getParent();
        }

        return path;
    }

    private static SearchResult result(
            boolean solvable,
            boolean solved,
            Deque<Node> path,
            long started,
            long generated,
            long expanded,
            long duplicatePruned,
            long staleSkipped,
            long reopened,
            int peakOpen,
            int peakClosed) {

        return new SearchResult(
                solvable,
                solved,
                path,
                System.nanoTime() - started,
                generated,
                expanded,
                duplicatePruned,
                staleSkipped,
                reopened,
                peakOpen,
                peakClosed);
    }
}