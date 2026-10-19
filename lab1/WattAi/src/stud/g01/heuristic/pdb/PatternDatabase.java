package stud.g01.heuristic.pdb;

import core.solver.algorithm.heuristic.Predictor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.zip.CRC32;
import stud.g01.problem.npuzzle.PuzzleBoard;

/**
 * 第一阶段的3×3模式数据库原型，支持1至3个模式数字。
 * 保留空格和本组数字的位置；其他数字不可区分。
 * 移动本组数字代价1，其他数字代价0，反向0-1 BFS给出精确抽象距离。
 * 不是完整棋盘的最短距离，也不是正式15-Puzzle数据库。
 */
public final class PatternDatabase {
    private static final int CELLS = 9;
    private static final int MAGIC = 0x50444231; // PDB1
    private static final int VERSION = 1;
    private static final long MAX_FILE_BYTES = 65536;
    private static final int[][] NEIGHBORS = createNeighbors();

    private final PuzzleBoard goal;
    private final int[] pattern;
    private final int[] distances;

    private PatternDatabase(PuzzleBoard goal, int[] pattern) {
        if (goal == null || goal.size() != 3) {
            throw new IllegalArgumentException("第一阶段PDB仅支持3×3棋盘");
        }
        if (pattern == null || pattern.length < 1 || pattern.length > 3) {
            throw new IllegalArgumentException("模式组须包含1至3个数字");
        }
        boolean[] seen = new boolean[CELLS];
        for (int tile : pattern) {
            if (tile <= 0 || tile >= CELLS || seen[tile]) {
                throw new IllegalArgumentException("模式数字须为1至8且不重复");
            }
            seen[tile] = true;
        }
        this.goal = goal;
        this.pattern = pattern.clone();
        int capacity = 1;
        for (int i = 0; i <= pattern.length; i++) capacity *= CELLS;
        this.distances = new int[capacity];
        Arrays.fill(distances, -1);
    }

    /** 在抽象目标处出发，完整生成本模式的查询表。 */
    public static PatternDatabase build(PuzzleBoard goal, int... pattern) {
        PatternDatabase db = new PatternDatabase(goal, pattern);
        int root = db.project(goal);
        ArrayDeque<Pending> deque = new ArrayDeque<>();
        db.distances[root] = 0;
        deque.addFirst(new Pending(root, 0));
        while (!deque.isEmpty()) {
            Pending entry = deque.removeFirst();
            if (entry.distance != db.distances[entry.key]) continue;
            int[] positions = db.unpack(entry.key);
            int blank = positions[0];
            for (int neighbor : NEIGHBORS[blank]) {
                int trackedSlot = 0;
                for (int i = 1; i < positions.length; i++) {
                    if (positions[i] == neighbor) { trackedSlot = i; break; }
                }
                int cost = trackedSlot == 0 ? 0 : 1;
                int[] next = positions.clone();
                next[0] = neighbor;
                if (trackedSlot != 0) next[trackedSlot] = blank;
                int key = pack(next);
                int candidate = entry.distance + cost;
                if (db.distances[key] == -1 || candidate < db.distances[key]) {
                    db.distances[key] = candidate;
                    Pending improved = new Pending(key, candidate);
                    // 0代价边放队首，1代价边放队尾；不能用首次访问就固定距离的普通BFS。
                    if (cost == 0) deque.addFirst(improved);
                    else deque.addLast(improved);
                }
            }
        }
        return db;
    }

    /** 查询指定棋盘在本模式内所需移动次数的下界。 */
    public int estimate(PuzzleBoard board) {
        if (board == null || board.size() != 3) {
            throw new IllegalArgumentException("查询棋盘须为3×3");
        }
        int result = distances[project(board)];
        if (result < 0) throw new IllegalStateException("模式状态不在数据库中");
        return result;
    }

    /** 返回可交给课程搜索引擎使用的启发函数，严格检查目标匹配。 */
    public Predictor predictor() {
        return (state, requestedGoal) -> {
            if (!(state instanceof PuzzleBoard) || !goal.equals(requestedGoal)) {
                throw new IllegalArgumentException("PDB的棋盘类型或目标与当前问题不匹配");
            }
            return estimate((PuzzleBoard) state);
        };
    }

    public int abstractStateCount() {
        int count = 0;
        for (int distance : distances) if (distance >= 0) count++;
        return count;
    }

    public int[] patternTiles() { return pattern.clone(); }
    public PuzzleBoard goalBoard() { return goal; }

    /** 保存版本、目标、模式、距离表及CRC32，避免误读或静默使用损坏数据。 */
    public void save(Path path) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(3);
            out.writeInt(pattern.length);
            for (int tile : pattern) out.writeInt(tile);
            for (int tile : goal.toArray()) out.writeInt(tile);
            out.writeInt(distances.length);
            for (int distance : distances) out.writeInt(distance);
        }
        byte[] body = buffer.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(body);
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(absolute))) {
            out.write(body);
            out.writeLong(crc.getValue());
        }
    }

    /** 只加载已经生成的表；不会在查询时重新搜索。 */
    public static PatternDatabase load(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 64 || size > MAX_FILE_BYTES) throw new IOException("PDB文件长度非法");
        byte[] file = Files.readAllBytes(path);
        if (file.length < 64 || file.length > MAX_FILE_BYTES) throw new IOException("PDB文件长度非法");
        int bodyLength = file.length - Long.BYTES;
        CRC32 crc = new CRC32();
        crc.update(file, 0, bodyLength);
        if (crc.getValue() != ByteBuffer.wrap(file, bodyLength, Long.BYTES).getLong()) {
            throw new IOException("PDB校验失败，文件可能损坏");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(file, 0, bodyLength))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION || in.readInt() != 3) {
                throw new IOException("不支持的PDB格式、版本或规模");
            }
            int count = in.readInt();
            if (count < 1 || count > 3) throw new IOException("PDB模式大小非法");
            int[] pattern = new int[count];
            for (int i = 0; i < count; i++) pattern[i] = in.readInt();
            int[] goal = new int[CELLS];
            for (int i = 0; i < CELLS; i++) goal[i] = in.readInt();
            PatternDatabase db = new PatternDatabase(new PuzzleBoard(3, goal), pattern);
            if (in.readInt() != db.distances.length) throw new IOException("PDB表大小不匹配");
            for (int i = 0; i < db.distances.length; i++) {
                int value = in.readInt();
                boolean valid = distinct(db.unpack(i));
                if ((valid && (value < 0 || value > 31)) || (!valid && value != -1)) {
                    throw new IOException("PDB表含非法状态或距离");
                }
                db.distances[i] = value;
            }
            if (in.available() != 0 || db.estimate(db.goal) != 0) {
                throw new IOException("PDB含多余数据或目标距离错误");
            }
            return db;
        } catch (IllegalArgumentException ex) {
            throw new IOException("PDB元数据非法", ex);
        }
    }

    // 编码元组为[空格位置, 本组第1个数字位置, ...]，以9为基数。
    // 重复位置的槽位不使用；3个模式数字只需要9^4=6561个int槽位。
    private int project(PuzzleBoard board) {
        int[] location = new int[CELLS];
        for (int i = 0; i < CELLS; i++) location[board.tileAt(i)] = i;
        int key = location[0];
        for (int tile : pattern) key = key * CELLS + location[tile];
        return key;
    }

    private static int pack(int[] positions) {
        int key = 0;
        for (int position : positions) key = key * CELLS + position;
        return key;
    }

    private int[] unpack(int key) {
        int[] positions = new int[pattern.length + 1];
        for (int i = positions.length - 1; i >= 0; i--) {
            positions[i] = key % CELLS;
            key /= CELLS;
        }
        return positions;
    }

    private static boolean distinct(int[] positions) {
        int used = 0;
        for (int p : positions) {
            if ((used & (1 << p)) != 0) return false;
            used |= 1 << p;
        }
        return true;
    }

    private static int[][] createNeighbors() {
        int[][] result = new int[CELLS][];
        for (int p = 0; p < CELLS; p++) {
            int[] buffer = new int[4];
            int count = 0;
            if (p / 3 > 0) buffer[count++] = p - 3;
            if (p / 3 < 2) buffer[count++] = p + 3;
            if (p % 3 > 0) buffer[count++] = p - 1;
            if (p % 3 < 2) buffer[count++] = p + 1;
            result[p] = Arrays.copyOf(buffer, count);
        }
        return result;
    }

    private record Pending(int key, int distance) { }
}
