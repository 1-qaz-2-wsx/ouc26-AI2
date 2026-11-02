package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.queue.PqFrontier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 可切换启发函数和 Open/Closed 策略的 A*。
 *
 * Open 和 Closed 均由 PqFrontier 统一管理。
 * Phase2AStar 不再维护第二份 Closed。
 *
 * 四种实验模式：
 *
 * BASELINE：
 *     Open best-g=false，Closed=false。
 *
 * OPEN_BEST_G：
 *     Open best-g=true，Closed=false。
 *
 * CLOSED_BEST_G：
 *     Open best-g=false，Closed best-g=true。
 *
 * FULL_OPTIMIZED：
 *     Open best-g=true，Closed best-g=true。
 */
public final class Phase2AStar {

    public enum SearchMode {

        /**
         * 不启用 Open best-g，不启用 Closed。
         */
        BASELINE(false, false),

        /**
         * 启用 Open best-g，不启用 Closed。
         */
        OPEN_BEST_G(true, false),

        /**
         * 不启用 Open best-g，启用 Closed best-g。
         */
        CLOSED_BEST_G(false, true),

        /**
         * 同时启用 Open best-g 和 Closed best-g。
         */
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

        public PqFrontier.ClosedPolicy closedPolicy() {
            return closedBestG
                    ? PqFrontier.ClosedPolicy.BEST_G
                    : PqFrontier.ClosedPolicy.NONE;
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
            return path == null
                    ? -1
                    : path.size();
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

        /*
         * 所有 Open/Closed 逻辑均由 PqFrontier 管理。
         *
         * BASELINE：
         *     best-g=false, ClosedPolicy.NONE
         *
         * OPEN_BEST_G：
         *     best-g=true, ClosedPolicy.NONE
         *
         * CLOSED_BEST_G：
         *     best-g=false, ClosedPolicy.BEST_G
         *
         * FULL_OPTIMIZED：
         *     best-g=true, ClosedPolicy.BEST_G
         */
        PqFrontier open = new PqFrontier(
                Node.evaluator(EvaluationType.FULL),
                mode.usesOpenBestG(),
                mode.closedPolicy());

        long generated = 0;
        long expanded = 0;

        open.offer(problem.root(predictor));

        Node node;

        /*
         * poll() 会跳过已经失效的物理条目。
         * 当没有有效 Open 节点时返回 null。
         */
        while ((node = open.poll()) != null) {

            /*
             * 目标节点不登记 Closed，
             * 因为它不会再被扩展。
             */
            if (problem.goal(node.getState())) {
                return result(
                        true,
                        true,
                        generatePath(node),
                        started,
                        generated,
                        expanded,
                        open.duplicatePruned(),
                        open.staleSkipped(),
                        open.reopened(),
                        open.peakSize(),
                        open.peakClosed());
            }

            /*
             * 只有非目标节点准备扩展时，
             * 才登记 Closed。
             *
             * PqFrontier 在这里统一维护 Closed，
             * Phase2AStar 没有第二份 Closed。
             */
            open.markExpanded(node);

            for (Node child
                    : problem.childNodes(node, predictor)) {

                generated++;

                if (!open.offer(child)) {
                    /*
                     * offer() 被拒绝的节点已经由 Frontier
                     * 计入 duplicatePruned。
                     */
                }
            }

            expanded++;
        }

        return result(
                true,
                false,
                null,
                started,
                generated,
                expanded,
                open.duplicatePruned(),
                open.staleSkipped(),
                open.reopened(),
                open.peakSize(),
                open.peakClosed());
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