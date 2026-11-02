package stud.g01.heuristic.pdb;

import core.solver.algorithm.heuristic.Predictor;
import stud.g01.problem.npuzzle.PuzzleBoard;

/** 可选快路径；普通Predictor与既有搜索构造器不受影响。 */
public interface PackedPuzzleHeuristic extends Predictor {
    Cursor cursor(PuzzleBoard initial);

    interface Cursor {
        int value();
        /** 空格从from移到to，数字tile从to移到from；调用者保证移动合法。 */
        void move(int from, int to, int tile);
        void undo();
    }
}
