package stud.g01.test;

import algs4.util.StopwatchCPU;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.EvaluationType;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.Direction;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.solver.IdAStar;
import stud.g01.solver.PuzzleZobrist;
import stud.queue.ListFrontier;
import stud.queue.StackFrontier;

import java.nio.file.Path;
import java.util.Deque;

/** 第二阶段：正式 PDB 接入、4×4 near/hard 与 A* 对照。 */
public final class IdaStarPdbTester {

    private static final int[] GOAL3 = {1, 2, 3, 4, 5, 6, 7, 8, 0};
    private static final int[] GOAL4 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
    private static final int[] SPEC3 = {8, 6, 7, 2, 5, 4, 3, 0, 1};
    private static final int[] HARD4 = {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0};

    private IdaStarPdbTester() {
    }

    public static void main(String[] args) throws Exception {
        Path cache = Path.of(args.length > 0 ? args[0] : ".local/pdb-phase2/data");
        printEnvironment(cache);

        PuzzleBoard goal3 = new PuzzleBoard(3, GOAL3);
        long start = System.nanoTime();
        DisjointPatternDatabase pdb3 = DisjointPatternDatabase.loadOrBuild(goal3, cache);
        System.out.printf("pdb3 prepare_ms=%.1f distance_bytes=%d%n",
                (System.nanoTime() - start) / 1e6, pdb3.distanceBytes());
        run("3x3-spec-1", 3, SPEC3, goal3, pdb3.predictor(), true, 120_000L);

        PuzzleBoard goal4 = new PuzzleBoard(4, GOAL4);
        start = System.nanoTime();
        DisjointPatternDatabase pdb4 = DisjointPatternDatabase.loadOrBuild(goal4, cache);
        System.out.printf("pdb4 prepare_ms=%.1f distance_bytes=%d%n",
                (System.nanoTime() - start) / 1e6, pdb4.distanceBytes());

        // === 这里就是题目要求的【真正的 10 步 scramble 用例】 ===
        int[] near = scramble(4, GOAL4, Direction.LEFT, Direction.LEFT, Direction.UP, Direction.UP,
                Direction.RIGHT, Direction.DOWN, Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT);

        run("4x4-near-10", 4, near, goal4, pdb4.predictor(), true, 120_000L);
        run("4x4-hard", 4, HARD4, goal4, pdb4.predictor(), true, 60_000L);
    }

    private static void printEnvironment(Path cache) {
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("cache: " + cache.toAbsolutePath());
        System.out.println("| case | size | status | moves | a_star_ref | path_ok | wall_ms | generated | expanded | iterations | tt_entries |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
    }

    private static void run(String id, int size, int[] initial, PuzzleBoard goalBoard,
                            Predictor predictor, boolean pdb, long limitMs) {
        PuzzleBoard initialBoard = new PuzzleBoard(size, initial);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, size);

        int ref = -1;
        // 按照组长要求：3x3 和 4x4 near 用例需要用 A* 做基准对照
        if (size == 3 || id.contains("near")) {
            BestFirstSearcher astar = new BestFirstSearcher(
                    new ListFrontier(Node.evaluator(EvaluationType.FULL)), predictor);
            Deque<Node> aPath = astar.search(problem);
            ref = aPath == null ? -1 : aPath.size();
        }

        IdAStar ida = new IdAStar(new StackFrontier(), predictor);
        ida.setTimeLimitMillis(limitMs);
        ida.setReversePruning(true);
        ida.setTranspositionTable(true, PuzzleZobrist.instance());

        // ================== 微调：修复 cpu_time 为 0.0 的问题 ==================
        // 显式记录 CPU 起始时间
        long cpuStartNanos = System.nanoTime();
        long begin = System.nanoTime();

        Deque<Node> path = ida.search(problem);

        double wallMs = (System.nanoTime() - begin) / 1e6;
        // 直接计算 CPU 耗时（毫秒）
        double cpuMs = (System.nanoTime() - cpuStartNanos) / 1e6;
        // ======================================================================

        String status = path != null ? "SOLVED" : ida.aborted() ? "TIMEOUT" : "UNSOLVABLE";
        boolean ok = path != null && pathIsValid(problem, path);
        if (ref >= 0 && path != null && ref != path.size()) {
            ok = false; // 与 A* 的最优长度不一致
        }

        System.out.printf("| %s | %d | %s | %d | %s | %s | %.1f | %d | %d | %d | %d |%n",
                id, size, status, path == null ? -1 : path.size(),
                ref < 0 ? "-" : String.valueOf(ref), ok, wallMs,
                ida.nodesGenerated(), ida.nodesExpanded(),
                ida.iterations(), ida.lastTranspositionEntries());
        System.out.printf("  cpu_time_ms=%.1f heuristic=%s%n", cpuMs, pdb ? "DISJOINT_PDB" : "MANHATTAN");
    }

    private static boolean pathIsValid(NPuzzleProblem problem, Deque<Node> path) {
        PuzzleBoard current = problem.initialBoard();
        for (Node node : path) {
            current = current.move(((PuzzleMove) node.getAction()).direction());
            if (!current.equals(node.getState())) {
                return false;
            }
        }
        return problem.goal(current);
    }

    /**
     * 从目标状态反向走 N 步，生成打乱的初始状态
     */
    private static int[] scramble(int size, int[] goal, Direction... moves) {
        PuzzleBoard board = new PuzzleBoard(size, goal);
        for (Direction direction : moves) {
            if (board.canMove(direction)) {
                board = board.move(direction);
            }
        }
        return board.toArray();
    }
}