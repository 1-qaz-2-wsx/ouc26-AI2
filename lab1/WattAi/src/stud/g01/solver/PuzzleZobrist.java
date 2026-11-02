package stud.g01.solver;
import core.problem.State;
import stud.g01.problem.npuzzle.PuzzleBoard;
import java.util.Random;
/** Member C a81d58c's reproducible 64-bit puzzle hash. Equality is checked by IDA*. */
public final class PuzzleZobrist implements StateHasher {
    private static final long[][] TABLE = new long[16][16];
    private static final PuzzleZobrist INSTANCE = new PuzzleZobrist();
    static {
        Random random = new Random(0x9E3779B97F4A7C15L);
        for (long[] row : TABLE) for (int i=0;i<row.length;i++) row[i]=random.nextLong();
    }
    private PuzzleZobrist() {}
    public static PuzzleZobrist instance() { return INSTANCE; }
    @Override public long hash(State state) { return hash(((PuzzleBoard)state).toArray()); }
    public static long hash(int[] tiles) {
        if (tiles.length != 9 && tiles.length != 16) throw new IllegalArgumentException("3x3 or 4x4 required");
        long hash=0;
        for(int i=0;i<tiles.length;i++) {
            if(tiles[i]<0 || tiles[i]>=16) throw new IllegalArgumentException("invalid tile");
            hash ^= TABLE[i][tiles[i]];
        }
        return hash;
    }
}
