package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.solver.Phase2AStar;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 成员B第二阶段正式实验。
 *
 * 1. 固定 Manhattan，比较四种 Open/Closed 策略。
 * 2. 固定 FULL_OPTIMIZED，比较三个启发函数。
 * 3. 正式 PDB 的构建和加载位于搜索计时之外。
 * 4. 输出逐次原始数据和中位数汇总数据。
 */
public final class Phase2Experiment {

    private static final int WARM_UP_RUNS = 3;
    private static final int RECORDED_RUNS = 10;

    private Phase2Experiment() {
    }

    private record Configuration(
            String heuristicName,
            Predictor predictor,
            Phase2AStar.SearchMode mode) {
    }

    private record Sample(
            Phase2AStar.SearchResult result,
            boolean pathValid,
            int initialHeuristic) {
    }

    public static void main(String[] args)
            throws Exception {

        Locale.setDefault(Locale.ROOT);

        Path inputFile = Path.of(
                args.length >= 1
                        ? args[0]
                        : "WattAi/resources/"
                        + "test_8puzzle.txt");

        Path rawOutput = Path.of(
                args.length >= 2
                        ? args[1]
                        : "experiments/"
                        + "astar-phase2-raw.csv");

        Path summaryOutput = Path.of(
                args.length >= 3
                        ? args[2]
                        : "experiments/"
                        + "astar-phase2-summary.csv");

        Path pdbDirectory = Path.of(
                args.length >= 4
                        ? args[3]
                        : "lab1/.local/pdb-phase2");

        if (!Files.exists(inputFile)) {
            throw new IllegalArgumentException(
                    "Input file does not exist: "
                            + inputFile.toAbsolutePath());
        }

        createParent(rawOutput);
        createParent(summaryOutput);
        Files.createDirectories(pdbDirectory);

        ArrayList<String> lines =
                new ArrayList<>(
                        Files.readAllLines(inputFile));

        ArrayList<Problem> loaded =
                new PuzzleFeeder().getProblems(lines);

        if (loaded.isEmpty()) {
            throw new IllegalArgumentException(
                    "No puzzle cases found in "
                            + inputFile.toAbsolutePath());
        }

        Map<PuzzleBoard, DisjointPatternDatabase>
                pdbByGoal = new HashMap<>();

        Map<String, Integer> expectedLengths =
                new HashMap<>();

        try (BufferedWriter rawWriter =
                     Files.newBufferedWriter(rawOutput);
             BufferedWriter summaryWriter =
                     Files.newBufferedWriter(summaryOutput)) {

            writeRawHeader(rawWriter);
            writeSummaryHeader(summaryWriter);

            for (int index = 0;
                 index < loaded.size();
                 index++) {

                if (!(loaded.get(index)
                        instanceof NPuzzleProblem)) {

                    throw new IllegalArgumentException(
                            "Input contains a non-puzzle problem");
                }

                NPuzzleProblem problem =
                        (NPuzzleProblem) loaded.get(index);

                String caseId = "case-" + (index + 1);

                /*
                 * 成员B的1秒验收针对8-Puzzle。
                 * 正式4x4 PDB接入由组长Demo/Hard脚本单独验证。
                 */
                if (problem.initialBoard().size() != 3) {
                    throw new IllegalArgumentException(
                            caseId
                                    + " is not an 8-puzzle; "
                                    + "use the PDB phase2 runner "
                                    + "for 4x4 experiments");
                }

                long pdbStarted = System.nanoTime();

                DisjointPatternDatabase database =
                        pdbByGoal.get(problem.goalBoard());

                if (database == null) {
                    database =
                            DisjointPatternDatabase.loadOrBuild(
                                    problem.goalBoard(),
                                    pdbDirectory);

                    pdbByGoal.put(
                            problem.goalBoard(),
                            database);
                }

                double pdbPrepareMillis =
                        (System.nanoTime() - pdbStarted)
                                / 1_000_000.0;

                System.out.printf(
                        Locale.ROOT,
                        "%s PDB ready: bytes=%d "
                                + "prepare_ms=%.3f%n",
                        caseId,
                        database.distanceBytes(),
                        pdbPrepareMillis);

                Predictor misplaced =
                        PuzzleHeuristics.predictor(
                                HeuristicType.MISPLACED);

                Predictor manhattan =
                        PuzzleHeuristics.predictor(
                                HeuristicType.MANHATTAN);

                Predictor disjointPattern =
                        database.predictor();

                List<Configuration> configurations =
                        configurations(
                                misplaced,
                                manhattan,
                                disjointPattern);

                for (Configuration configuration
                        : configurations) {

                    List<Sample> samples =
                            runConfiguration(
                                    rawWriter,
                                    caseId,
                                    problem,
                                    configuration,
                                    expectedLengths);

                    writeSummary(
                            summaryWriter,
                            caseId,
                            problem,
                            configuration,
                            samples);
                }
            }
        }

        System.out.println(
                "Raw results: "
                        + rawOutput.toAbsolutePath());

        System.out.println(
                "Summary results: "
                        + summaryOutput.toAbsolutePath());

        System.out.println(
                "Open/Closed table: filter "
                        + "heuristic=MANHATTAN");

        System.out.println(
                "Heuristic table: filter "
                        + "mode=FULL_OPTIMIZED");
    }

    private static List<Configuration> configurations(
            Predictor misplaced,
            Predictor manhattan,
            Predictor disjointPattern) {

        List<Configuration> result =
                new ArrayList<>();

        for (Phase2AStar.SearchMode mode
                : Phase2AStar.SearchMode.values()) {

            result.add(new Configuration(
                    "MANHATTAN",
                    manhattan,
                    mode));
        }

        result.add(new Configuration(
                "MISPLACED",
                misplaced,
                Phase2AStar.SearchMode
                        .FULL_OPTIMIZED));

        result.add(new Configuration(
                "DISJOINT_PATTERN",
                disjointPattern,
                Phase2AStar.SearchMode
                        .FULL_OPTIMIZED));

        return result;
    }

    private static List<Sample> runConfiguration(
            BufferedWriter rawWriter,
            String caseId,
            NPuzzleProblem problem,
            Configuration configuration,
            Map<String, Integer> expectedLengths)
            throws Exception {

        List<Sample> samples = new ArrayList<>();
        int totalRuns = WARM_UP_RUNS + RECORDED_RUNS;

        int initialHeuristic =
                configuration.predictor().heuristics(
                        problem.initialBoard(),
                        problem.goalBoard());

        for (int run = 0; run < totalRuns; run++) {
            Phase2AStar.SearchResult result =
                    new Phase2AStar(
                            configuration.predictor(),
                            configuration.mode())
                            .search(problem);

            boolean pathValid =
                    result.solved()
                            ? validPath(problem, result.path())
                            : result.path() == null;

            validateResult(
                    caseId,
                    problem,
                    result,
                    pathValid,
                    expectedLengths);

            if (run < WARM_UP_RUNS) {
                continue;
            }

            int recordedRun =
                    run - WARM_UP_RUNS + 1;

            Sample sample = new Sample(
                    result,
                    pathValid,
                    initialHeuristic);

            samples.add(sample);

            writeRawRow(
                    rawWriter,
                    caseId,
                    problem,
                    configuration,
                    recordedRun,
                    sample);

            System.out.printf(
                    Locale.ROOT,
                    "%s %-17s %-17s run=%d "
                            + "moves=%d time_ms=%.3f "
                            + "generated=%d expanded=%d%n",
                    caseId,
                    configuration.heuristicName(),
                    configuration.mode(),
                    recordedRun,
                    result.solutionLength(),
                    result.elapsedMillis(),
                    result.generated(),
                    result.expanded());
        }

        rawWriter.flush();
        return samples;
    }

    private static void validateResult(
            String caseId,
            NPuzzleProblem problem,
            Phase2AStar.SearchResult result,
            boolean pathValid,
            Map<String, Integer> expectedLengths) {

        if (!result.solvable()) {
            if (problem.solvable()) {
                throw new IllegalStateException(
                        caseId
                                + " solvability result mismatch");
            }
            return;
        }

        if (!result.solved()) {
            throw new IllegalStateException(
                    caseId
                            + " is solvable but was not solved");
        }

        if (!pathValid) {
            throw new IllegalStateException(
                    caseId + " produced an invalid path");
        }

        Integer expected =
                expectedLengths.putIfAbsent(
                        caseId,
                        result.solutionLength());

        if (expected != null
                && expected != result.solutionLength()) {

            throw new IllegalStateException(
                    caseId
                            + " produced inconsistent "
                            + "solution lengths: "
                            + expected
                            + " and "
                            + result.solutionLength());
        }
    }

    private static boolean validPath(
            NPuzzleProblem problem,
            Deque<Node> path) {

        if (path == null) {
            return false;
        }

        Predictor zero = (state, goal) -> 0;
        Node current = problem.root(zero);
        int expectedCost = 1;

        for (Node expected : path) {
            Node matching = null;

            for (Node child
                    : problem.childNodes(current, zero)) {

                if (child.getState().equals(
                        expected.getState())) {

                    matching = child;
                    break;
                }
            }

            if (matching == null
                    || expected.getPathCost()
                    != expectedCost) {

                return false;
            }

            current = matching;
            expectedCost++;
        }

        return problem.goal(current.getState());
    }

    private static void writeRawHeader(
            BufferedWriter writer)
            throws Exception {

        writer.write(
                "case_id,board_size,heuristic,mode,run,"
                        + "solvable,solved,path_valid,"
                        + "initial_h,solution_length,"
                        + "elapsed_ms,generated,expanded,"
                        + "duplicate_pruned,stale_skipped,"
                        + "reopened,peak_open,peak_closed");

        writer.newLine();
    }

    private static void writeRawRow(
            BufferedWriter writer,
            String caseId,
            NPuzzleProblem problem,
            Configuration configuration,
            int run,
            Sample sample)
            throws Exception {

        Phase2AStar.SearchResult result =
                sample.result();

        writer.write(String.format(
                Locale.ROOT,
                "%s,%d,%s,%s,%d,%s,%s,%s,%d,%d,"
                        + "%.6f,%d,%d,%d,%d,%d,%d,%d",
                caseId,
                problem.initialBoard().size(),
                configuration.heuristicName(),
                configuration.mode(),
                run,
                result.solvable(),
                result.solved(),
                sample.pathValid(),
                sample.initialHeuristic(),
                result.solutionLength(),
                result.elapsedMillis(),
                result.generated(),
                result.expanded(),
                result.duplicatePruned(),
                result.staleSkipped(),
                result.reopened(),
                result.peakOpen(),
                result.peakClosed()));

        writer.newLine();
    }

    private static void writeSummaryHeader(
            BufferedWriter writer)
            throws Exception {

        writer.write(
                "case_id,board_size,heuristic,mode,runs,"
                        + "solvable,solved,path_valid,"
                        + "initial_h,solution_length,"
                        + "median_ms,generated,expanded,"
                        + "duplicate_pruned,stale_skipped,"
                        + "reopened,peak_open,peak_closed");

        writer.newLine();
    }

    private static void writeSummary(
            BufferedWriter writer,
            String caseId,
            NPuzzleProblem problem,
            Configuration configuration,
            List<Sample> samples)
            throws Exception {

        if (samples.isEmpty()) {
            throw new IllegalStateException(
                    "No recorded samples");
        }

        Sample representative = samples.get(0);

        for (Sample sample : samples) {
            ensureStableCounters(
                    representative.result(),
                    sample.result());
        }

        List<Double> times = samples.stream()
                .map(sample ->
                        sample.result().elapsedMillis())
                .sorted(Comparator.naturalOrder())
                .toList();

        double median;

        if (times.size() % 2 == 1) {
            median = times.get(times.size() / 2);
        } else {
            int upper = times.size() / 2;
            median = (
                    times.get(upper - 1)
                            + times.get(upper)) / 2.0;
        }

        Phase2AStar.SearchResult result =
                representative.result();

        writer.write(String.format(
                Locale.ROOT,
                "%s,%d,%s,%s,%d,%s,%s,%s,%d,%d,"
                        + "%.6f,%d,%d,%d,%d,%d,%d,%d",
                caseId,
                problem.initialBoard().size(),
                configuration.heuristicName(),
                configuration.mode(),
                samples.size(),
                result.solvable(),
                result.solved(),
                representative.pathValid(),
                representative.initialHeuristic(),
                result.solutionLength(),
                median,
                result.generated(),
                result.expanded(),
                result.duplicatePruned(),
                result.staleSkipped(),
                result.reopened(),
                result.peakOpen(),
                result.peakClosed()));

        writer.newLine();
        writer.flush();
    }

    private static void ensureStableCounters(
            Phase2AStar.SearchResult first,
            Phase2AStar.SearchResult another) {

        boolean stable =
                first.solvable() == another.solvable()
                        && first.solved()
                        == another.solved()
                        && first.solutionLength()
                        == another.solutionLength()
                        && first.generated()
                        == another.generated()
                        && first.expanded()
                        == another.expanded()
                        && first.duplicatePruned()
                        == another.duplicatePruned()
                        && first.staleSkipped()
                        == another.staleSkipped()
                        && first.reopened()
                        == another.reopened()
                        && first.peakOpen()
                        == another.peakOpen()
                        && first.peakClosed()
                        == another.peakClosed();

        if (!stable) {
            throw new IllegalStateException(
                    "Search counters changed between "
                            + "identical recorded runs");
        }
    }

    private static void createParent(Path path)
            throws Exception {

        Path parent =
                path.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}