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
 * 可切换 Predictor 和 Open/Closed 策略的 A*。
 *
 * 不修改课程框架中的 BestFirstSearcher，便于进行重复状态处理
 * 的消融实验，并支持 Closed best-g 和 reopen。
 */
public final class Phase2AStar {

    public enum SearchMode {

        /**
         * Open 允许重复；Closed 只保存状态是否扩展过。
         */
        BASELINE(false, false),

        /**
         * Open 为相同状态保留最小 g；Closed 使用集合。
         */
        OPEN_BEST_G(true, false),

        /**
         * Open 允许重复；Closed 保存已扩展状态的最小 g。
         */
        CLOSED_BEST_G(false, true),

        /**
         * Open、Closed 都使用 best-g，并支持 reopen。
         */
        FULL_OPTIMIZED(true, true);

        private final boolean openBestG;
        private final boolean closedBestG;

        SearchMode(boolean openBestG, boolean closedBestG) {
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
        boolean solvable = problem.solvable();

        if (!solvable) {
            return result(
                    false, false, null, started,
                    0, 0, 0, 0, 0, 0, 0);
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

        open.offer(problem.root(predictor));

        while (!open.isEmpty()) {
            Node node = open.poll();

            if (node == null) {
                break;
            }

            State state = node.getState();
            int nodeG = node.getPathCost();

            /*
             * Open 不使用 best-g 时，队列中可能保留已经由更优路径
             * 扩展过的重复结点，因此取出后还要检查 Closed。
             */
            if (mode.usesClosedBestG()) {
                Integer previousG = closedBestG.get(state);

                if (previousG != null && previousG <= nodeG) {
                    duplicatePruned++;
                    continue;
                }
            } else if (closedSet.contains(state)) {
                duplicatePruned++;
                continue;
            }

            /*
             * 目标从 Open 中取出时结束。返回路径不包含根结点，
             * 因而 path.size() 就是移动步数。
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

            for (Node child
                    : problem.childNodes(node, predictor)) {

                generated++;

                State childState = child.getState();
                int childG = child.getPathCost();
                boolean reopen = false;

                if (mode.usesClosedBestG()) {
                    Integer closedG =
                            closedBestG.get(childState);

                    if (closedG != null) {
                        if (closedG <= childG) {
                            duplicatePruned++;
                            continue;
                        }

                        /*
                         * 找到通往已扩展状态的更短路径。
                         * 将其移出 Closed，允许重新进入 Open。
                         */
                        closedBestG.remove(childState);
                        reopen = true;
                    }
                } else if (closedSet.contains(childState)) {
                    duplicatePruned++;
                    continue;
                }

                if (open.offer(child)) {
                    if (reopen) {
                        reopened++;
                    }
                } else {
                    duplicatePruned++;
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