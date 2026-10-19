package stud.g01.problem.npuzzle;

/**
 * 空格移动方向枚举：UP/DOWN/LEFT/RIGHT，各带行、列偏移量。
 * 方向表示"空格"的移动；行向下增长、列向右增长。
 * 例如 DOWN(1,0) 表示空格向下移动一格（行 +1、列不变）。
 */
public enum Direction {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1);

    /** 行的偏移量 */
    private final int dr;
    /** 列的偏移量 */
    private final int dc;

    Direction(int dr, int dc) {
        this.dr = dr;
        this.dc = dc;
    }

    /** 行偏移量 */
    public int dr() {
        return dr;
    }

    /** 列偏移量 */
    public int dc() {
        return dc;
    }
}
