package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;

import java.util.ArrayList;
import java.util.List;

/**
 * N-Puzzle 棋盘状态。
 * 一维行优先存储，位置从 0 开始，空格数字为 0。棋盘不可变：
 * 构造时复制数组，返回数组也复制；一次移动产生新棋盘，不改父状态。
 *   PuzzleBoard(int size, int[] tiles)、size()、tileAt(int index)、
 *   blankIndex()、toArray()、canMove(Direction)、move(Direction)、
 *   misplaced(PuzzleBoard goal)、manhattan(PuzzleBoard goal)。
 * 空格在文本显示中为 '#'。
 */
public class PuzzleBoard extends State {

    /** 棋盘边长：3 或 4 */
    private final int size;
    /** 一维行优先的棋子数组，tiles[blankIndex] == 0 */
    private final int[] tiles;
    /** 空格下标 */
    private final int blankIndex;

    /**
     * 由边长和一维棋子数组构造棋盘。
     * @param size  棋盘边长（3 或 4）
     * @param tiles 长度为 size*size 的一维数组，按行优先，0 表示空格；
     *              必须恰好包含 0..size*size-1 且无重复
     * @throws IllegalArgumentException 边长或棋子数字非法
     */
    public PuzzleBoard(int size, int[] tiles) {
        if (size != 3 && size != 4) {
            throw new IllegalArgumentException("棋盘边长必须为 3 或 4: " + size);
        }
        if (tiles == null || tiles.length != size * size) {
            throw new IllegalArgumentException("棋子数量必须为 size*size，实际: " + (tiles == null ? "null" : tiles.length));
        }
        // 校验数字恰好包含 0..size*size-1 且无重复
        boolean[] seen = new boolean[size * size];
        int blank = -1;
        for (int i = 0; i < tiles.length; i++) {
            int v = tiles[i];
            if (v < 0 || v >= size * size || seen[v]) {
                throw new IllegalArgumentException("非法棋子数字（须恰含 0.." + (size * size - 1) + " 且无重复）: " + v);
            }
            seen[v] = true;
            if (v == 0) {
                blank = i;
            }
        }
        this.size = size;
        this.tiles = tiles.clone();   // 深拷贝，防止外部修改
        this.blankIndex = blank;
    }

    /** 棋盘边长 */
    public int size() {
        return size;
    }

    /**
     * 返回第 index 个位置的棋子数字（按行优先）。
     * @throws IndexOutOfBoundsException 下标越界
     */
    public int tileAt(int index) {
        if (index < 0 || index >= tiles.length) {
            throw new IndexOutOfBoundsException("下标越界: " + index);
        }
        return tiles[index];
    }

    /** 空格下标（行优先位置） */
    public int blankIndex() {
        return blankIndex;
    }

    /** 返回一维数组的副本 */
    public int[] toArray() {
        return tiles.clone();
    }

    /** index 所在行（0 基） */
    private int rowOf(int index) {
        return index / size;
    }

    /** index 所在列（0 基） */
    private int colOf(int index) {
        return index % size;
    }

    /**
     * 空格能否朝 direction 方向移动一格。
     * @param direction 移动方向
     * @return true 表示移动后仍在棋盘内
     */
    public boolean canMove(Direction direction) {
        int r = rowOf(blankIndex) + direction.dr();
        int c = colOf(blankIndex) + direction.dc();
        return r >= 0 && r < size && c >= 0 && c < size;
    }

    /**
     * 返回空格朝 direction 移动一格后的新棋盘（不改当前棋盘）。
     * @param direction 移动方向
     * @return 移动后的新棋盘
     * @throws IllegalArgumentException 该方向越界，不能移动
     */
    public PuzzleBoard move(Direction direction) {
        if (!canMove(direction)) {
            throw new IllegalArgumentException("该方向越界，无法移动: " + direction);
        }
        int target = (rowOf(blankIndex) + direction.dr()) * size + (colOf(blankIndex) + direction.dc());
        int[] newTiles = tiles.clone();
        newTiles[blankIndex] = newTiles[target];
        newTiles[target] = 0;
        return new PuzzleBoard(size, newTiles);
    }

    /**
     * 错位将牌数：不在目标位置上的方块个数（空格不计）。
     * @param goal 目标棋盘
     * @return 错位将牌数
     * @throws IllegalArgumentException 目标棋盘边长不同
     */
    public int misplaced(PuzzleBoard goal) {
        if (goal.size != this.size) {
            throw new IllegalArgumentException("目标棋盘边长不同");
        }
        int count = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] != 0 && tiles[i] != goal.tiles[i]) {
                count++;
            }
        }
        return count;
    }

    /**
     * 曼哈顿距离：每块到其目标位置的行列距离之和（空格不计）。
     * @param goal 目标棋盘
     * @return 曼哈顿距离之和
     * @throws IllegalArgumentException 目标棋盘边长不同
     */
    public int manhattan(PuzzleBoard goal) {
        if (goal.size != this.size) {
            throw new IllegalArgumentException("目标棋盘边长不同");
        }
        // 先记录每个数字在目标棋盘中的位置 goalPos[tile] = index
        int[] goalPos = new int[size * size];
        for (int i = 0; i < goal.tiles.length; i++) {
            if (goal.tiles[i] != 0) {
                goalPos[goal.tiles[i]] = i;
            }
        }
        int sum = 0;
        for (int i = 0; i < tiles.length; i++) {
            int t = tiles[i];
            if (t != 0) {
                sum += Math.abs(rowOf(i) - rowOf(goalPos[t]))
                     + Math.abs(colOf(i) - colOf(goalPos[t]));
            }
        }
        return sum;
    }

    /**
     * 在控制台按方阵显示棋盘；空格显示为 '#'。
     */
    @Override
    public void draw() {
        System.out.println(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == 0) { sb.append('#'); } else { sb.append(tiles[i]); }
            if (colOf(i) == size - 1) {
                if (i < tiles.length - 1) {
                    sb.append('\n');
                }
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * 执行动作（空格朝对应方向移动一格），返回新棋盘。
     */
    @Override
    public State next(Action action) {
        Direction dir = ((PuzzleMove) action).direction();
        return move(dir);
    }

    /**
     * 返回四个候选动作（UP/DOWN/LEFT/RIGHT）。
     * 具体能否移动由 NPuzzleProblem.applicable 过滤。
     */
    @Override
    public Iterable<? extends Action> actions() {
        List<Action> list = new ArrayList<>(4);
        for (Direction d : Direction.values()) {
            list.add(new PuzzleMove(d));
        }
        return list;
    }

    /**
     * 两个棋盘相等，当且仅当边长与所有位置的数字都相同。
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PuzzleBoard)) {
            return false;
        }
        PuzzleBoard other = (PuzzleBoard) obj;
        if (this.size != other.size) {
            return false;
        }
        for (int i = 0; i < tiles.length; i++) {
            if (this.tiles[i] != other.tiles[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 与 equals 一致的哈希值：对每个位置数字加权累加。
     */
    @Override
    public int hashCode() {
        int h = 1;
        for (int v : tiles) {
            h = 31 * h + v;
        }
        return h;
    }
}
