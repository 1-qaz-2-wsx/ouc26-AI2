package core.runner;

import algs4.util.StopwatchCPU;
import core.problem.Problem;
import core.problem.ProblemType;
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
 * 对学生的搜索算法进行检测的主程序。
 *
 * 参数：
 * args[0]：问题输入文件，例如 resources/test_8puzzle.txt
 * args[1]：问题类型，例如 NPUZZLE
 * args[2]：项目阶段，例如 1
 * args[3]：学生的 Feeder，例如 stud.g01.runner.PuzzleFeeder
 *
 * 计时范围仅包含 searcher.search(problem)，
 * 不包含输入读取、求解器创建和解路径打印。
 */
public final class SearchTester {

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
                            + "stud.g01.runner.PuzzleFeeder"
            );
        }

        printEnvironment();

        // 根据 args[3] 提供的类名创建学生的 EngineFeeder。
        EngineFeeder feeder = (EngineFeeder)
                Class.forName(args[3])
                        .getDeclaredConstructor()
                        .newInstance();

        // 使用 UTF-8 读取输入文件，读取完成后自动关闭。
        ArrayList<String> problemLines;
        try (Scanner scanner = new Scanner(
                new File(args[0]), StandardCharsets.UTF_8)) {
            problemLines = getProblemLines(scanner);
        }

        // 将输入文本转换为问题实例。
        ArrayList<Problem> problems = feeder.getProblems(problemLines);

        ProblemType type = ProblemType.valueOf(args[1]);
        int step = Integer.parseInt(args[2]);

        ArrayList<HeuristicType> heuristics =
                getHeuristicTypes(type, step);

        if (heuristics.isEmpty()) {
            throw new IllegalArgumentException(
                    "当前主入口未配置该问题类型和阶段的启发函数："
                            + type + "，阶段=" + step
            );
        }

        System.out.println(
                "输入文件：" + new File(args[0]).getAbsolutePath());
        System.out.println("问题类型：" + type);
        System.out.println("项目阶段：" + step);
        System.out.println("Feeder：" + args[3]);
        System.out.println("问题数量：" + problems.size());
        System.out.println();

        for (HeuristicType heuristicType : heuristics) {
            // 保留课程入口的调用方式，使用 A* 求解。
            solveProblems(
                    problems,
                    feeder.getAStar(heuristicType),
                    heuristicType
            );
            System.out.println();
        }
    }

    /**
     * 打印实际 Java 运行环境。
     *
     * available_processors 为 JVM 可用逻辑处理器数；
     * max_heap_bytes 为 JVM 最大堆内存，不是机器物理内存。
     */
    private static void printEnvironment() {
        System.out.println("========== 运行环境 ==========");

        System.out.println(
                "java.version="
                        + System.getProperty("java.version"));

        System.out.println(
                "java.runtime.version="
                        + System.getProperty("java.runtime.version"));

        System.out.println(
                "java.vendor="
                        + System.getProperty("java.vendor"));

        System.out.println(
                "java.vm.name="
                        + System.getProperty("java.vm.name"));

        System.out.println(
                "os.name="
                        + System.getProperty("os.name"));

        System.out.println(
                "os.version="
                        + System.getProperty("os.version"));

        System.out.println(
                "os.arch="
                        + System.getProperty("os.arch"));

        System.out.println(
                "available_processors="
                        + Runtime.getRuntime().availableProcessors());

        System.out.println(
                "max_heap_bytes="
                        + Runtime.getRuntime().maxMemory());

        System.out.println("==============================");
        System.out.println();
    }

    /**
     * 根据问题类型和当前阶段获取启发函数列表。
     */
    private static ArrayList<HeuristicType> getHeuristicTypes(
            ProblemType type, int step) {

        ArrayList<HeuristicType> heuristics = new ArrayList<>();

        if (type == ProblemType.PATHFINDING) {
            heuristics.add(PF_GRID);
            heuristics.add(PF_EUCLID);
        } else {
            if (step == 1) {
                heuristics.add(MISPLACED);
                heuristics.add(MANHATTAN);
            } else if (step == 3) {
                heuristics.add(DISJOINT_PATTERN);
            }
        }

        return heuristics;
    }

    /**
     * 使用给定搜索器依次求解问题，展示路径并输出统计数据。
     */
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

            // CPU 计时沿用课程提供的 StopwatchCPU。
            StopwatchCPU cpuTimer = new StopwatchCPU();
            long wallStart = System.nanoTime();

            Deque<Node> path = searcher.search(problem);

            // 在打印路径之前结束计时。
            double wallTimeMs =
                    (System.nanoTime() - wallStart) / 1_000_000.0;
            double cpuTimeMs =
                    cpuTimer.elapsedTime() * 1_000.0;

            // 在展示路径前保存结果，避免展示过程影响路径长度。
            int moves = path == null ? -1 : path.size();
            var generated = searcher.nodesGenerated();
            var expanded = searcher.nodesExpanded();

            if (path == null) {
                // null 本身不足以确定不可解的原因，
                // 因此使用 NO_SOLUTION，避免误记为 UNSOLVABLE。
                System.out.println("No Solution");

                System.out.printf(
                        Locale.ROOT,
                        "case_id=%s，algorithm=ASTAR，heuristic=%s，"
                                + "status=NO_SOLUTION，moves=NA，"
                                + "wall_time_ms=%.6f，cpu_time_ms=%.6f，"
                                + "generated=%d，expanded=%d%n",
                        caseId,
                        heuristicType,
                        wallTimeMs,
                        cpuTimeMs,
                        generated,
                        expanded
                );

                System.out.println();
                continue;
            }

            // 展示初始状态、各步移动及目标状态。
            problem.showSolution(path);

            System.out.printf(
                    Locale.ROOT,
                    "case_id=%s，algorithm=ASTAR，heuristic=%s，"
                            + "status=SOLVED，moves=%d，"
                            + "wall_time_ms=%.6f，cpu_time_ms=%.6f，"
                            + "generated=%d，expanded=%d%n",
                    caseId,
                    heuristicType,
                    moves,
                    wallTimeMs,
                    cpuTimeMs,
                    generated,
                    expanded
            );

            System.out.println();
        }
    }

    /**
     * 从输入文件读取非空行。
     */
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