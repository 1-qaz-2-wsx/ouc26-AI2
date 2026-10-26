package stud.g01.solver;

import core.problem.State;
import stud.g01.problem.npuzzle.PuzzleBoard;

import java.util.Random;

/** N-Puzzle 的 64 位 Zobrist 哈希：哈希 = 所有 (位置,数字) 随机值异或。 */
public final class PuzzleZobrist implements StateHasher {

    private static final int MAX_CELLS = 16;
    private static final int MAX_TILES = 16;
    private static final long[][] TABLE = new long[MAX_CELLS][MAX_TILES];

    static {
        Random random = new Random(0x9E3779B97F4A7C15L); // 固定种子，结果可复现
        for (int cell = 0; cell < MAX_CELLS; cell++) {
            for (int tile = 0; tile < MAX_TILES; tile++) {
                TABLE[cell][tile] = random.nextLong();
            }
        }
    }

    private PuzzleZobrist() {
    }

    public static PuzzleZobrist instance() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final PuzzleZobrist INSTANCE = new PuzzleZobrist();
    }

    @Override
    public long hash(State state) {
        return hash(((PuzzleBoard) state).toArray());
    }

    public static long hash(int[] tiles) {
        if (tiles.length > MAX_CELLS) {
            throw new IllegalArgumentException("仅支持 3×3 或 4×4 棋盘");
        }
        long h = 0L;
        for (int cell = 0; cell < tiles.length; cell++) {
            int tile = tiles[cell];
            if (tile < 0 || tile >= MAX_TILES) {
                throw new IllegalArgumentException("非法数字: " + tile);
            }
            h ^= TABLE[cell][tile];
        }
        return h;
    }
}