package stud.g01.test;

import algs4.util.StopwatchCPU;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.Direction;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.runner.SolutionPathWriter;
import stud.g01.solver.IdAStar;
import stud.g01.solver.Phase2AStar;
import stud.g01.solver.PuzzleZobrist;
import stud.queue.StackFrontier;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * 第二阶段：同一台机器、同一启发函数下的 A* �? IDA* 对比�?
 *
 * <p>流程：每个算法先预热 1 次，再正式跑 3 次，按墙钟时间取中位数�?
 * 内存用后台线程采�? JVM 堆峰值，报告的是“峰�? - 基线”的近似值�?
 * A* 使用成员B�? stud.g01.queue.PqFrontier（注意不是课程里那个空的
 * stud.queue.PqFrontier）�?4×4 指定困难实例只跑 IDA*，因�? A* �?
 * Open/Closed 表在该规模下不可行�?</p>
 */
public final class SolverComparisonTester {

    private static final int[] GOAL3 = {1, 2, 3, 4, 5, 6, 7, 8, 0};
    private static final int[] GOAL4 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};
    private static final int[] SPEC3_1 = {8, 6, 7, 2, 5, 4, 3, 0, 1};
    private static final int[] SPEC3_2 = {6, 4, 7, 8, 5, 0, 3, 2, 1};
    private static final int[] HARD4 = {15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 1, 2, 0};

    private static final int REPEATS = 3;
    private static final long HARD_LIMIT_MS = 60_000L;

    private SolverComparisonTester() {
    }

    public static void main(String[] args) throws Exception {
        Path cache = Path.of(args.length > 0 ? args[0] : ".local/pdb-phase2/data");
        printEnvironment(cache);

        PuzzleBoard goal3 = new PuzzleBoard(3, GOAL3);
        long start = System.nanoTime();
        DisjointPatternDatabase pdb3 = DisjointPatternDatabase.loadOrBuild(goal3, cache);
        System.out.printf("pdb3 prepare_wall_ms=%.1f distance_bytes=%d%n",
                (System.nanoTime() - start) / 1e6, pdb3.distanceBytes());

        PuzzleBoard goal4 = new PuzzleBoard(4, GOAL4);
        start = System.nanoTime();
        DisjointPatternDatabase pdb4 = DisjointPatternDatabase.loadOrBuild(goal4, cache);
        System.out.printf("pdb4 prepare_wall_ms=%.1f distance_bytes=%d%n",
                (System.nanoTime() - start) / 1e6, pdb4.distanceBytes());
        System.out.println();

        System.out.println("| case | algorithm | heuristic | status | moves | matches_astar | path_ok |"
                + " wall_ms_med | cpu_ms_med | generated | expanded | peak_open | peak_closed"
                + " | tt_entries | peak_mem_mb |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");

        Predictor manhattan = PuzzleHeuristics.predictor(HeuristicType.MANHATTAN);
        runComparison("3x3-spec-1", 3, SPEC3_1, goal3, manhattan, "MANHATTAN", false);
        runComparison("3x3-spec-1", 3, SPEC3_1, goal3, pdb3.predictor(), "PDB", false);
        runComparison("3x3-spec-2", 3, SPEC3_2, goal3, pdb3.predictor(), "PDB", false);

        int[] near10 = scramble(4, GOAL4, Direction.LEFT, Direction.LEFT, Direction.UP, Direction.UP,
                Direction.RIGHT, Direction.DOWN, Direction.DOWN, Direction.LEFT, Direction.UP, Direction.RIGHT);
        runComparison("4x4-near-10", 4, near10, goal4, pdb4.predictor(), "PDB", true);

        if (args.length < 2 || !"skip-hard".equals(args[1])) {
            runIdaOnly("4x4-hard", 4, HARD4, goal4, pdb4.predictor(), "PDB", HARD_LIMIT_MS);
        }
    }

    private static void printEnvironment(Path cache) {
        System.out.println("JDK: " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        System.out.println("CPU: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("JVM max heap: " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB");
        System.out.println("PDB cache: " + cache.toAbsolutePath());
    }

    private static void runComparison(String caseId, int size, int[] initial, PuzzleBoard goalBoard,
                                      Predictor predictor, String heuristic, boolean exportPath)
            throws Exception {
        PuzzleBoard initialBoard = new PuzzleBoard(size, initial);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, size);

        // 预热：避免把类加载和 JIT 计入正式样本
        runAStar(problem, predictor, false);
        runIda(problem, predictor, Long.MAX_VALUE, false);

        List<Result> aResults = new ArrayList<>();
        List<Result> iResults = new ArrayList<>();
        Result lastIda = null;
        for (int i = 0; i < REPEATS; i++) {
            aResults.add(runAStar(problem, predictor, false));
            Result ida = runIda(problem, predictor, Long.MAX_VALUE, false);
            iResults.add(ida);
            lastIda = ida;
        }

        // Memory is sampled in a separate run so GC does not pollute timing.
        double aMemory = runAStar(problem, predictor, true).memMb;
        double iMemory = runIda(problem, predictor, Long.MAX_VALUE, true).memMb;

        Result aMedian = median(aResults).withMemory(aMemory);
        Result iMedian = median(iResults).withMemory(iMemory);
        boolean matches = aMedian.status.equals("SOLVED") && iMedian.status.equals("SOLVED")
                && aMedian.moves == iMedian.moves;

        printRow(caseId, "A*", heuristic, aMedian, "-");
        printRow(caseId, "IDA*", heuristic, iMedian, String.valueOf(matches));

        if (exportPath) {
            if (lastIda != null && lastIda.path != null) {
                Path out = Path.of("examples/paths_out/ida-near10.txt");
                SolutionPathWriter.write(out, problem, lastIda.path);
                System.out.println("  exported_path=" + out.toAbsolutePath()
                        + " moves=" + lastIda.moves + " path_ok=" + lastIda.pathOk);
            } else {
                System.out.println("  export_skipped: IDA* 未得到解");
            }
        }
    }

    private static void runIdaOnly(String caseId, int size, int[] initial, PuzzleBoard goalBoard,
                                   Predictor predictor, String heuristic, long limitMs) {
        PuzzleBoard initialBoard = new PuzzleBoard(size, initial);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, size);
        Result result = runIda(problem, predictor, limitMs, true);
        printRow(caseId, "IDA*", heuristic, result, "-");
        System.out.println("  note: 4x4 困难实例不运行 A*，Open/Closed 表在该规模下不可行");
    }

    private static Result runAStar(NPuzzleProblem problem, Predictor predictor, boolean sampleMemory) {
        Phase2AStar searcher = new Phase2AStar(predictor, Phase2AStar.SearchMode.FULL_OPTIMIZED);
        return measureAStar(searcher, problem, sampleMemory);
    }

    private static Result runIda(NPuzzleProblem problem, Predictor predictor, long limitMs,
                                 boolean sampleMemory) {
        IdAStar searcher = new IdAStar(new StackFrontier(), predictor);
        searcher.setReversePruning(true);
        searcher.setTranspositionTable(true, PuzzleZobrist.instance());
        searcher.setTranspositionCapacity(1_048_576);   // 加这一行
        if (limitMs != Long.MAX_VALUE) {
            searcher.setTimeLimitMillis(limitMs);
        }
        return measure(() -> searcher.search(problem), searcher::nodesGenerated,
                searcher::nodesExpanded, searcher::lastTranspositionEntries, 0, 0,
                problem, sampleMemory);
    }

    private static Result measureAStar(Phase2AStar searcher, NPuzzleProblem problem,
                                       boolean sampleMemory) {
        HeapSampler sampler = sampleMemory ? new HeapSampler() : null;
        long base = sampleMemory ? usedHeapAfterGc() : 0;
        StopwatchCPU cpu = new StopwatchCPU();
        long start = System.nanoTime();
        if (sampler != null) {
            sampler.start();
        }
        Phase2AStar.SearchResult result = searcher.search(problem);
        long peak = sampler == null ? 0 : sampler.stop();
        double wallMs = (System.nanoTime() - start) / 1e6;
        double cpuMs = cpu.elapsedTime() * 1000.0;
        double memMb = sampleMemory ? Math.max(0, peak - base) / (1024.0 * 1024.0) : 0;

        Deque<Node> path = result.path();
        String status = result.solved() ? "SOLVED" : result.solvable() ? "NO_SOLUTION" : "UNSOLVABLE";
        int moves = path == null ? -1 : path.size();
        boolean ok = path != null && pathIsValid(problem, path);
        return new Result(status, moves, ok, wallMs, cpuMs,
                (int) result.generated(), (int) result.expanded(),
                result.peakOpen(), result.peakClosed(), 0, memMb, path);
    }

    private static Result measure(SearchCall call, IntSupplier generated, IntSupplier expanded,
                                  IntSupplier ttEntries, int peakOpen, int peakClosed,
                                  NPuzzleProblem problem, boolean sampleMemory) {
        HeapSampler sampler = sampleMemory ? new HeapSampler() : null;
        long base = sampleMemory ? usedHeapAfterGc() : 0;
        StopwatchCPU cpu = new StopwatchCPU();
        long start = System.nanoTime();
        if (sampler != null) {
            sampler.start();
        }
        Deque<Node> path = call.run();
        long peak = sampler == null ? 0 : sampler.stop();
        double wallMs = (System.nanoTime() - start) / 1e6;
        double cpuMs = cpu.elapsedTime() * 1000.0;
        double memMb = sampleMemory ? Math.max(0, peak - base) / (1024.0 * 1024.0) : 0;

        String status = path != null ? "SOLVED" : "TIMEOUT";
        int moves = path == null ? -1 : path.size();
        boolean ok = path != null && pathIsValid(problem, path);
        return new Result(status, moves, ok, wallMs, cpuMs, generated.getAsInt(),
                expanded.getAsInt(), peakOpen, peakClosed, ttEntries.getAsInt(), memMb, path);
    }

    private static void printRow(String caseId, String algorithm, String heuristic,
                                 Result result, String matches) {
        System.out.printf("| %s | %s | %s | %s | %d | %s | %s | %.1f | %.1f | %d | %d | %d | %d | %d | %.1f |%n",
                caseId, algorithm, heuristic, result.status, result.moves, matches, result.pathOk,
                result.wallMs, result.cpuMs, result.generated, result.expanded,
                result.peakOpen, result.peakClosed, result.ttEntries, result.memMb);
    }

    private static Result median(List<Result> results) {
        results.sort(Comparator.comparingDouble(r -> r.wallMs));
        return results.get(results.size() / 2);
    }

    private static long usedHeapAfterGc() {
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
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

    private static int[] scramble(int size, int[] goal, Direction... moves) {
        PuzzleBoard board = new PuzzleBoard(size, goal);
        for (Direction direction : moves) {
            if (board.canMove(direction)) {
                board = board.move(direction);
            }
        }
        return board.toArray();
    }

    @FunctionalInterface
    private interface SearchCall {
        Deque<Node> run();
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt();
    }

    private static final class Result {
        final String status;
        final int moves;
        final boolean pathOk;
        final double wallMs;
        final double cpuMs;
        final int generated;
        final int expanded;
        final int peakOpen;
        final int peakClosed;
        final int ttEntries;
        final double memMb;
        final Deque<Node> path;

        Result(String status, int moves, boolean pathOk, double wallMs, double cpuMs,
               int generated, int expanded, int peakOpen, int peakClosed,
               int ttEntries, double memMb, Deque<Node> path) {
            this.status = status;
            this.moves = moves;
            this.pathOk = pathOk;
            this.wallMs = wallMs;
            this.cpuMs = cpuMs;
            this.generated = generated;
            this.expanded = expanded;
            this.peakOpen = peakOpen;
            this.peakClosed = peakClosed;
            this.ttEntries = ttEntries;
            this.memMb = memMb;
            this.path = path;
        }

        Result withMemory(double memoryMb) {
            return new Result(status, moves, pathOk, wallMs, cpuMs, generated, expanded,
                    peakOpen, peakClosed, ttEntries, memoryMb, path);
        }
    }

    private static final class HeapSampler {
        private final Thread thread;
        private volatile boolean running = true;
        private long peak;

        HeapSampler() {
            thread = new Thread(this::sample, "heap-sampler");
            thread.setDaemon(true);
        }

        void start() {
            thread.start();
        }

        long stop() {
            running = false;
            try {
                thread.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return peak;
        }

        private void sample() {
            while (running) {
                long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                if (used > peak) {
                    peak = used;
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }
    }
}
