package stud.g01.runner;

import core.problem.Problem;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 测试入口：读取问题文件，对每个问题求解并导出 NP_PATH_V1 解路径文件。
 *   java stud.g01.runner.NPuzzlePathExporter <问题文件> [输出目录]
 * 说明：
 *   - 问题文件每行格式与 PuzzleFeeder 一致（边长 + 初始 + 目标）；
 *   - 输出目录缺省为 examples/paths_out；
 *   - 每个问题导出为 npuzzle_N.txt（N 从 1 开始）；
 *   - 可解问题用 IDA*（曼哈顿距离）求最优解后写 SOLVED；
 *   - 求解设 60 秒 / 200 万结点上限：超时或超限记 TIMEOUT 并跳过，绝不写成 UNSOLVABLE；
 *   - 不可解问题写 UNSOLVABLE。

 */
public class NPuzzlePathExporter {

    /** 单个问题的求解时间上限（毫秒）：防止深层 4×4 让入口永久挂住 */
    private static final long TIME_LIMIT_MS = 60_000L;
    /** 单个问题的生成结点上限 */
    private static final long NODE_LIMIT = 2_000_000L;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("用法: NPuzzlePathExporter <问题文件> [输出目录]");
            return;
        }
        String problemFile = args[0];
        Path outDir = args.length >= 2 ? Paths.get(args[1]) : Paths.get("examples/paths_out");

        PuzzleFeeder feeder = new PuzzleFeeder();
        List<String> lines = Files.readAllLines(Paths.get(problemFile));
        ArrayList<Problem> problems = feeder.getProblems(new ArrayList<>(lines));

        for (int i = 0; i < problems.size(); i++) {
            NPuzzleProblem problem = (NPuzzleProblem) problems.get(i);
            Path out = outDir.resolve("npuzzle_" + (i + 1) + ".txt");

            if (!problem.solvable()) {
                SolutionPathWriter.write(out, problem, null);   // 确认不可解 → UNSOLVABLE
                System.out.println("问题 " + (i + 1) + "：不可解 → " + out);
                continue;
            }

            // 直接用本组的 IdAStar：EngineFeeder.getIdaStar 返回的是框架旧类，不能强转
            IdAStar searcher = new IdAStar(new StackFrontier(),
                    PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
            searcher.setTimeLimitMillis(TIME_LIMIT_MS);
            searcher.setNodeLimit(NODE_LIMIT);
            Deque<Node> path = searcher.search(problem);
            if (path == null) {
                // 中断记 TIMEOUT，可解却搜不到记失败；两种情况都不导出伪造路径
                String reason = searcher.aborted() ? "TIMEOUT" : "搜索失败";
                System.out.println("问题 " + (i + 1) + "：" + reason + "，不导出。"
                        + "生成结点=" + searcher.nodesGenerated() + "，扩展结点=" + searcher.nodesExpanded());
                continue;
            }
            SolutionPathWriter.write(out, problem, path);
            System.out.println("问题 " + (i + 1) + "：最优 " + path.size() + " 步 → " + out);
        }
    }
}
