package stud.g01.problem.npuzzle;

import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;

/**
 * N-Puzzle 启发函数统一入口。
 * predictor(type) 提供 MISPLACED（错位将牌数）与 MANHATTAN（曼哈顿距离）；
 * 尚未实现的类型明确抛 UnsupportedOperationException，不能返回 null（避免后续 NPE）。
 * 另有两个直接返回的估价器供第二阶段实验使用：blankDistance()（空位距离单独使用）
 * 与 manhattanMaxBlank()（max(曼哈顿, 空位距离)）。
 */
public final class PuzzleHeuristics {

    /** 工具类，禁止实例化 */
    private PuzzleHeuristics() {
    }

    /**
     * 返回指定类型的估价器。
     * @param type 启发函数类型
     * @return 对应的 Predictor
     * @throws UnsupportedOperationException 该类型尚未实现
     */
    public static Predictor predictor(HeuristicType type) {
        switch (type) {
            case MISPLACED:
                return (state, goal) -> ((PuzzleBoard) state).misplaced((PuzzleBoard) goal);
            case MANHATTAN:
                return manhattan();
            default:
                // DISJOINT_PATTERN 等由组长提供 PDB 后接入；未实现前明确报错，避免 NPE
                throw new UnsupportedOperationException("尚未实现该启发类型: " + type);
        }
    }

    /**
     * 曼哈顿距离预测器：缓存目标棋盘的"将牌 → 位置"映射。
     * 一次搜索中 goal 是同一对象，用引用相等判断复用即可（O(1)），
     * 避免每次估值都重建 O(n^2) 的表。数值与 PuzzleBoard.manhattan(goal) 完全一致。
     */
    public static Predictor manhattan() {
        return new Predictor() {
            private PuzzleBoard cachedGoal;
            private int[] goalPos;

            @Override
            public int heuristics(core.problem.State state, core.problem.State goal) {
                PuzzleBoard g = (PuzzleBoard) goal;
                if (g != cachedGoal) {
                    goalPos = g.goalPositions();
                    cachedGoal = g;
                }
                return ((PuzzleBoard) state).manhattan(goalPos);
            }
        };
    }

    /**
     * 空位距离预测器：只估空格到目标空位的行列距离。
     * 可采纳但很弱（3×3 全空间 avg h/d* 仅 0.094），主要供第二阶段
     * "空位距离作为独立启发函数"的对照实验使用，不建议实际启用。
     */
    public static Predictor blankDistance() {
        return (state, goal) -> ((PuzzleBoard) state).blankDistance((PuzzleBoard) goal);
    }

    /**
     * max(曼哈顿, 空位距离) 预测器：两种可采纳下界取较大者，仍可采纳。
     * 与 MANHATTAN 的区别仅在目标空格不在角落/边缘的特殊目标上，
     * 实测（3×3 全空间 + 4×4 抽样 50 万）增益为 0，但作为更稳的下界保留。
     * 数值上恒等于 manhattan 与 blankDistance 的较大者。
     */
    public static Predictor manhattanMaxBlank() {
        return new Predictor() {
            private PuzzleBoard cachedGoal;
            private int[] goalPos;

            @Override
            public int heuristics(core.problem.State state, core.problem.State goal) {
                PuzzleBoard g = (PuzzleBoard) goal;
                if (g != cachedGoal) {
                    goalPos = g.goalPositions();
                    cachedGoal = g;
                }
                PuzzleBoard b = (PuzzleBoard) state;
                return Math.max(b.manhattan(goalPos), b.blankDistance(g));
            }
        };
    }
}
