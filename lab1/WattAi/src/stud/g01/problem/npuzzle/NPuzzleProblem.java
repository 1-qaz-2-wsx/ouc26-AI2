package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * N-Puzzle 问题：由初始棋盘与目标棋盘构成，并回答搜索算法需要的问题。
 *   - 保留两个构造器（两参从棋盘取边长，三参显式给边长）；
 *   - 提供 initialBoard() / goalBoard()，返回不可变棋盘；
 *   - solvable() 比较初始与目标的可达性类别；
 *   - stepCost 恒为 1；
 *   - showSolution 打印文本（导出交给 SolutionPathWriter / 测试入口）。
 */
public class NPuzzleProblem extends Problem {

    /**
     * 由初始状态与目标状态构造问题，边长取自初始棋盘。
     */
    public NPuzzleProblem(State initialState, State goal) {
        super(initialState, goal);
        this.size = ((PuzzleBoard) initialState).size();
    }

    /**
     * 由初始状态、目标状态与边长构造问题。
     */
    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    /** 返回初始棋盘（不可变） */
    public PuzzleBoard initialBoard() {
        return (PuzzleBoard) initialState;
    }

    /** 返回目标棋盘（不可变） */
    public PuzzleBoard goalBoard() {
        return (PuzzleBoard) goal;
    }

    /**
     * 可解性判断：初始棋盘与目标棋盘是否处于同一"可达性类别"。
     * 对某棋盘计算奇偶标记 parity：
     *   - 奇数阶（3×3）：取逆序对数的奇偶；
     *   - 偶数阶（4×4）：取（逆序对数 + 空格距底行数，底行记为 1）的奇偶。
     * 两个棋盘相互可达，当且仅当 parity 相同。
     * @return true 表示可解
     */
    @Override
    public boolean solvable() {
        return parity(initialBoard()) == parity(goalBoard());
    }

    /** 计算某棋盘的可达性奇偶标记 */
    private int parity(PuzzleBoard b) {
        int[] a = b.toArray();
        // 逆序对（忽略空格 0）
        int inv = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                continue;
            }
            for (int j = i + 1; j < a.length; j++) {
                if (a[j] != 0 && a[i] > a[j]) {
                    inv++;
                }
            }
        }
        if (size % 2 == 1) {
            // 奇数阶：只看逆序对奇偶
            return inv % 2;
        }
        // 偶数阶：逆序对 + 空格距底行数（底行记为 1）
        int blankRowFromBottom = size - (b.blankIndex() / size);
        return (inv + blankRowFromBottom) % 2;
    }

    /**
     * 单步代价：华容道每移动一次空格记 1。
     * @return 恒为 1
     */
    @Override
    public int stepCost(State state, Action action) {
        return 1;
    }

    /**
     * 判断某动作在当前状态下是否合法：空格按该方向移动一格后是否仍在棋盘内。
     * @return true 表示该移动合法
     */
    @Override
    public boolean applicable(State state, Action action) {
        PuzzleBoard board = (PuzzleBoard) state;
        Direction dir = ((PuzzleMove) action).direction();
        int r = board.blankIndex() / size + dir.dr();
        int c = board.blankIndex() % size + dir.dc();
        return r >= 0 && r < size && c >= 0 && c < size;
    }

    /**
     * 在控制台打印解路径（初始棋盘、每步移动与棋盘、目标棋盘）。
     * 解路径文件的导出由 SolutionPathWriter 负责。
     * @param path 解路径（不含根节点，由框架 generatePath 得到）
     */
    @Override
    public void showSolution(Deque<Node> path) {
        System.out.println("初始棋盘：");
        System.out.println(initialBoard());
        int step = 0;
        for (Node node : path) {
            PuzzleMove move = (PuzzleMove) node.getAction();
            PuzzleBoard after = (PuzzleBoard) node.getState();
            System.out.println("第 " + (++step) + " 步：空格" + move);
            System.out.println(after);
        }
        System.out.println("目标棋盘：");
        System.out.println(goalBoard());
    }
}
