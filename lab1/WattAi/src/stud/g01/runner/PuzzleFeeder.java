package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.queue.ListFrontier;

import java.util.ArrayList;

/**
 * N-Puzzle 的 EngineFeeder：把输入文件里的数字串解析成问题实例，并向搜索算法提供
 * 估价器（启发函数）与搜索队列。模式参考框架自带的寻路示例 WalkerFeeder。
 * 保留无参构造器供反射创建；getProblems 按课程每行格式
 * 解析（任意空白分隔、忽略空行、非法输入指出行号）。
 */
public class PuzzleFeeder extends EngineFeeder {

    /**
     * 把输入文本中的每一行解析成一个 NPuzzleProblem 实例。
     * 每行格式（任意空白分隔）：
     *   第 1 个数：棋盘边长 size（3 表示 3×3，4 表示 4×4）
     *   接下来 size*size 个数：初始棋盘（按行展开，0 表示空格）
     *   最后 size*size 个数：目标棋盘（按行展开，0 表示空格）
     * 空行忽略；非法输入抛 IllegalArgumentException 并指出行号。
     * @param problemLines 读进来的所有文本行，每行一个问题实例
     * @return 问题实例列表
     */
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        ArrayList<Problem> problems = new ArrayList<>();
        for (int lineNo = 0; lineNo < problemLines.size(); lineNo++) {
            String line = problemLines.get(lineNo);
            if (line == null || line.trim().isEmpty()) {
                continue;   // 跳过空行
            }
            String[] cells = line.trim().split("\\s+");
            try {
                int size = Integer.parseInt(cells[0]);      // 棋盘边长
                if (size != 3 && size != 4) {
                    throw new IllegalArgumentException("棋盘边长必须为 3 或 4");
                }
                int total = size * size;
                if (cells.length != 1 + 2 * total) {
                    throw new IllegalArgumentException("数字个数不符：需要 1+2*size*size 个");
                }
                // 初始棋盘：cells[1 .. total]
                int[] initial = new int[total];
                for (int i = 0; i < total; i++) {
                    initial[i] = Integer.parseInt(cells[1 + i]);
                }
                // 目标棋盘：cells[1+total .. 2*total]
                int[] goal = new int[total];
                for (int i = 0; i < total; i++) {
                    goal[i] = Integer.parseInt(cells[1 + total + i]);
                }

                PuzzleBoard initialState = new PuzzleBoard(size, initial);
                PuzzleBoard goalState = new PuzzleBoard(size, goal);
                problems.add(new NPuzzleProblem(initialState, goalState, size));
            } catch (Exception e) {
                throw new IllegalArgumentException("第 " + (lineNo + 1) + " 行解析失败: " + e.getMessage(), e);
            }
        }
        return problems;
    }

    /**
     * 生成按指定规则排序的搜索队列（Frontier）。
     * 第一阶段先用框架自带的 ListFrontier（按 f=g+h 排序即可用 A*）。
     * @param type 结点估值规则（FULL 表示 f = g + h）
     * @return 一个 Frontier 实例
     */
    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new ListFrontier(Node.evaluator(type));
    }

    /**
     * 返回对状态进行估值的估价器（启发函数）。
     * @param type 启发函数类型
     * @return 对应估价器
     */
    @Override
    public Predictor getPredictor(HeuristicType type) {
        return PuzzleHeuristics.predictor(type);
    }
}
