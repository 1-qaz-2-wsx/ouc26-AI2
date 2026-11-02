package stud.g01.runner;

import algs4.util.StopwatchCPU;
import core.problem.Problem;
import core.problem.ProblemType;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;
import java.util.Scanner;

import static core.solver.algorithm.heuristic.HeuristicType.*;

/**
 * 学生目录中的课程搜索检测入口副本。
 *
 * 参数：
 * args[0]：问题输入文件
 * args[1]：问题类型
 * args[2]：项目阶段
 * args[3]：学生 Feeder 类名
 */
public final class B_Phase3ExperimentRunner {

    private B_Phase3ExperimentRunner() {
    }

    public static void main(String[] args)
            throws ClassNotFoundException,
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException,
            InstantiationException,
            IOException {

        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "需要4个参数：<输入文件> <问题类型> <阶段> <Feeder类名>\n"
                            + "例如：resources/test_8puzzle.txt NPUZZLE 1 "
                            + "stud.g01.runner.PuzzleFeeder");
        }

        printEnvironment();

        EngineFeeder feeder = (EngineFeeder)
                Class.forName(args[3])
                        .getDeclaredConstructor()
                        .newInstance();

        ArrayList<String> problemLines;
        try (Scanner scanner = new Scanner(
                new File(args[0]),
                StandardCharsets.UTF_8)) {
            problemLines = getProblemLines(scanner);
        }

        ArrayList<Problem> problems =
                feeder.getProblems(problemLines);

        ProblemType type = ProblemType.valueOf(args[1]);
        int step = Integer.parseInt(args[2]);

        ArrayList<HeuristicType> heuristics =
                getHeuristicTypes(type, step);

        if (heuristics.isEmpty()) {
            throw new IllegalArgumentException(
                    "当前主入口未配置该问题类型和阶段的启发函数："
                            + type + "，阶段=" + step);
        }

        System.out.println(
                "输入文件：" + new File(args[0]).getAbsolutePath());
        System.out.println("问题类型：" + type);
        System.out.println("项目阶段：" + step);
        System.out.println("Feeder：" + args[3]);
        System.out.println("问题数量：" + problems.size());
        System.out.println();

        for (HeuristicType heuristicType : heuristics) {
            solveProblems(
                    problems,
                    feeder.getAStar(heuristicType),
                    heuristicType);
            System.out.println();
        }
    }

    private static void printEnvironment() {
        System.out.println("========== 运行环境 ==========");
        System.out.println("java.version="
                + System.getProperty("java.version"));
        System.out.println("java.runtime.version="
                + System.getProperty("java.runtime.version"));
        System.out.println("java.vendor="
                + System.getProperty("java.vendor"));
        System.out.println("java.vm.name="
                + System.getProperty("java.vm.name"));
        System.out.println("os.name="
                + System.getProperty("os.name"));
        System.out.println("os.version="
                + System.getProperty("os.version"));
        System.out.println("os.arch="
                + System.getProperty("os.arch"));
        System.out.println("available_processors="
                + Runtime.getRuntime().availableProcessors());
        System.out.println("max_heap_bytes="
                + Runtime.getRuntime().maxMemory());
        System.out.println("==============================");
        System.out.println();
    }

    private static ArrayList<HeuristicType> getHeuristicTypes(
            ProblemType type,
            int step) {

        ArrayList<HeuristicType> heuristics = new ArrayList<>();

        if (type == ProblemType.PATHFINDING) {
            heuristics.add(PF_GRID);
            heuristics.add(PF_EUCLID);
        } else if (step == 1) {
            heuristics.add(MISPLACED);
            heuristics.add(MANHATTAN);
        } else if (step == 3) {
            heuristics.add(DISJOINT_PATTERN);
        }

        return heuristics;
    }

    private static void solveProblems(
            ArrayList<Problem> problems,
            AbstractSearcher searcher,
            HeuristicType heuristicType) {

        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            String caseId = "case-" + (i + 1);

            System.out.println(
                    "========== " + caseId + " ==========");
            System.out.println("algorithm=ASTAR");
            System.out.println("heuristic=" + heuristicType);

            StopwatchCPU cpuTimer = new StopwatchCPU();
            long wallStart = System.nanoTime();

            Deque<Node> path = searcher.search(problem);

            double wallTimeMs =
                    (System.nanoTime() - wallStart) / 1_000_000.0;
            double cpuTimeMs =
                    cpuTimer.elapsedTime() * 1_000.0;

            int moves = path == null ? -1 : path.size();
            var generated = searcher.nodesGenerated();
            var expanded = searcher.nodesExpanded();

            if (path == null) {
                System.out.println("No Solution");
                System.out.printf(
                        Locale.ROOT,
                        "case_id=%s,algorithm=ASTAR,heuristic=%s,"
                                + "status=NO_SOLUTION,moves=NA,"
                                + "wall_time_ms=%.6f,cpu_time_ms=%.6f,"
                                + "generated=%d,expanded=%d%n",
                        caseId,
                        heuristicType,
                        wallTimeMs,
                        cpuTimeMs,
                        generated,
                        expanded);
                System.out.println();
                continue;
            }

            problem.showSolution(path);

            System.out.printf(
                    Locale.ROOT,
                    "case_id=%s,algorithm=ASTAR,heuristic=%s,"
                            + "status=SOLVED,moves=%d,"
                            + "wall_time_ms=%.6f,cpu_time_ms=%.6f,"
                            + "generated=%d,expanded=%d%n",
                    caseId,
                    heuristicType,
                    moves,
                    wallTimeMs,
                    cpuTimeMs,
                    generated,
                    expanded);

            System.out.println();
        }
    }

    public static ArrayList<String> getProblemLines(Scanner scanner) {
        ArrayList<String> lines = new ArrayList<>();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        return lines;
    }
}