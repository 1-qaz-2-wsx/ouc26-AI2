package stud.g01.problem.npuzzle;

import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;

/**
 * N-Puzzle 启发函数统一入口。
 * 第一阶段支持 MISPLACED（错位将牌数）和
 * MANHATTAN（曼哈顿距离）；尚未实现的类型明确抛 UnsupportedOperationException，
 * 不能返回 null（避免后续 NPE）。
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
                return (state, goal) -> ((PuzzleBoard) state).manhattan((PuzzleBoard) goal);
            default:
                // DISJOINT_PATTERN 等由组长提供 PDB 后接入；未实现前明确报错，避免 NPE
                throw new UnsupportedOperationException("尚未实现该启发类型: " + type);
        }
    }
}
