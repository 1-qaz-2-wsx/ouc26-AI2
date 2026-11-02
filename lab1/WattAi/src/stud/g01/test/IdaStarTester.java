package stud.g01.test;

import algs4.util.StopwatchCPU;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.solver.IdAStar;
import stud.g01.solver.PuzzleZobrist; // 确保你有这个类
import stud.queue.StackFrontier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 根据你的实际情况，你可能需要导入 Direction 类
// import core.problem.Direction;

public final class IdaStarTester {

    // 定义两种目标状态
    private static final int[] GOAL3 = {1, 2, 3, 4, 5, 6, 7, 8, 0};
    private static final int[] GOAL4 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0};

    private static final long SIMPLE_TIME_LIMIT_MS = 10_000L;
    private static final long SPEC_TIME_LIMIT_MS = 120_000L;

    private IdaStarTester() {
    }

    public static void main(String[] args) {
        printEnvironment();
        System.out.println();

        // 表头增加了 size, mode, iterations, tt_entries, mem_delta_mb
        System.out.println("| case_id | size | mode | algorithm | heuristic | status | moves | bfs_optimal | path_ok |"
                + " wall_time_ms | cpu_time_ms | generated | expanded | iterations | tt_entries | mem_delta_mb |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");

        // ===== 3x3 测试 (保持原有状态，增加 mode 维度) =====
        int[] s1 = {1, 2, 3, 4, 5, 6, 7, 0, 8};
        int[] s2 = {1, 2, 3, 4, 5, 6, 0, 7, 8};
        int[] spec1 = {8, 6, 7, 2, 5, 4, 3, 0, 1};

        // 3x3 跑 FULL 模式
        runCase("simple-1", 3, s1, "MANHATTAN", "FULL", SIMPLE_TIME_LIMIT_MS);
        runCase("simple-2", 3, s2, "MANHATTAN", "FULL", SIMPLE_TIME_LIMIT_MS);
        runCase("spec-1", 3, spec1, "MANHATTAN", "FULL", SPEC_TIME_LIMIT_MS);

        // 3x3 对比 BASE 和 FULL 模式
        runCase("spec-1-base", 3, spec1, "MANHATTAN", "BASE", SPEC_TIME_LIMIT_MS);


        // ===== 4x4 测试 (使用 scramble 从目标反向生成) =====
        // 这里以空位(0)移动方向为例，假设 Direction 枚举有 UP, DOWN, LEFT, RIGHT
        // 从目标反向走 10 步，生成 near 用例
        // 注意：此处需要根据你项目实际的 Direction 枚举类名来修改
        // int[] near4 = scramble(4, GOAL4, Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT, Direction.UP, Direction.DOWN);

        // 如果没有 Direction，你可以暂时手写一个 4x4 接近目标的状态测一下：
        int[] near4 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 0, 15}; // 只有最后两步不同

        runCase("4x4-near", 4, near4, "MANHATTAN", "FULL", SPEC_TIME_LIMIT_MS);
        runCase("4x4-near", 4, near4, "MANHATTAN", "BASE", SPEC_TIME_LIMIT_MS);

        // ===== 不可解测试 =====
        runCase("unsolvable", 3, new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}, "MANHATTAN", "FULL", SIMPLE_TIME_LIMIT_MS);
    }

    /**
     * 【新增】反向生成打乱状态：从目标状态出发，反向走 moves 步
     */
    private static int[] scramble(int size, int[] goal, Object... moves) {
        // 这里的 Object... moves 是因为没看到你的 Direction 类，你可以替换为 Direction... moves
        PuzzleBoard board = new PuzzleBoard(size, goal);
        // for (Object d : moves) {
        //     if (board.canMove((Direction)d)) board = board.move((Direction)d);
        // }
        return board.toArray();
    }

    private static void printEnvironment() {
        System.out.println("JDK: " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        System.out.println("CPU: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("JVM 最大堆: " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB");
    }

    /**
     * 【修改】增加 int size 和 String mode 参数
     */
    private static void runCase(String caseId, int size, int[] initial, String heuristic, String mode, long timeLimitMs) {
        int[] goal = (size == 3) ? GOAL3 : GOAL4;

        PuzzleBoard initialBoard = new PuzzleBoard(size, initial);
        PuzzleBoard goalBoard = new PuzzleBoard(size, goal);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, size);

        IdAStar searcher;
        if ("NONE".equals(heuristic)) {
            searcher = new IdAStar(new StackFrontier()); // 单参数构造器：h≡0
        } else {
            HeuristicType type = HeuristicType.valueOf(heuristic);
            searcher = new IdAStar(new StackFrontier(), PuzzleHeuristics.predictor(type));
        }

        // ====== 【新增：用模式配置搜索器】 ======
        // 1. BASE 模式关闭反向剪枝，其他模式开启
        searcher.setReversePruning(!"BASE".equals(mode));

        // 2. FULL 或 PDB 模式开启置换表
        boolean useTt = "FULL".equals(mode) || "PDB".equals(mode);
        if (useTt) {
            searcher.setTranspositionTable(true, PuzzleZobrist.instance());
        } else {
            searcher.setTranspositionTable(false, null);
        }
        // =====================================

        searcher.setTimeLimitMillis(timeLimitMs);
        searcher.setNodeLimit(IdAStar.NO_LIMIT);

        // 内存占用统计
        Runtime rt = Runtime.getRuntime();
        System.gc(); // 建议GC一次再测
        long memBefore = rt.totalMemory() - rt.freeMemory();

        StopwatchCPU cpu = new StopwatchCPU();
        long start = System.nanoTime();
        Deque<Node> path = searcher.search(problem);
        double wallMs = (System.nanoTime() - start) / 1e6;
        double cpuMs = cpu.elapsedTime() * 1000.0;

        System.gc();
        long memAfter = rt.totalMemory() - rt.freeMemory();
        double memDeltaMb = (memAfter - memBefore) / (1024.0 * 1024.0);

        String status;
        int moves;
        boolean pathOk = false;
        if (path != null) {
            status = "SOLVED";
            moves = path.size();
            pathOk = pathIsValid(problem, path);
        } else if (searcher.aborted()) {
            status = "TIMEOUT";
            moves = -1;
        } else {
            status = "UNSOLVABLE";
            moves = -1;
        }

        int bfsOptimal = -1;
        // 【注意】4x4 的 BFS 极容易 OOM (内存溢出)，如果你的4x4测试崩了，请把下面这几行注释掉
        if (moves > 0 && size == 3) {
            bfsOptimal = bfsOptimalLength(initial, goal, size);
            if (bfsOptimal >= 0 && bfsOptimal != moves) {
                pathOk = false;
            }
        }

        // 【修改】输出格式增加了 size, mode, iterations, tt_entries, mem_delta_mb
        System.out.printf("| %s | %d | %s | IDA* | %s | %s | %d | %s | %s | %.1f | %.1f | %d | %d | %d | %d | %.2f |%n",
                caseId, size, mode, heuristic, status, moves,
                bfsOptimal < 0 ? "-" : String.valueOf(bfsOptimal),
                moves < 0 ? "-" : String.valueOf(pathOk),
                wallMs, cpuMs, searcher.nodesGenerated(), searcher.nodesExpanded(),
                searcher.iterations(), searcher.lastTranspositionEntries(), memDeltaMb);
    }

    /** 校验解路径：每两帧只能交换空格与相邻数字，最后一帧必须等于目标 */
    private static boolean pathIsValid(NPuzzleProblem problem, Deque<Node> path) {
        PuzzleBoard current = problem.initialBoard();
        for (Node node : path) {
            PuzzleMove move = (PuzzleMove) node.getAction();
            current = current.move(move.direction());
            if (!current.equals(node.getState())) {
                return false;
            }
        }
        return problem.goal(current);
    }

    /** 独立基准：无权图上的BFS，给出真正的最优步数，与 IDA* 不共享任何代码 */
    private static int bfsOptimalLength(int[] initial, int[] goal, int size) { // 【修改】传入 size
        long goalKey = encode(goal);
        long startKey = encode(initial);
        if (startKey == goalKey) {
            return 0;
        }
        Map<Long, Integer> distance = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        distance.put(startKey, 0);
        queue.add(startKey);
        while (!queue.isEmpty()) {
            long key = queue.poll();
            int depth = distance.get(key);
            int[] tiles = decode(key, size); // 【修改】传入 size
            for (int[] next : successors(tiles, size)) { // 【修改】传入 size
                long nextKey = encode(next);
                if (distance.containsKey(nextKey)) {
                    continue;
                }
                if (nextKey == goalKey) {
                    return depth + 1;
                }
                distance.put(nextKey, depth + 1);
                queue.add(nextKey);
            }
        }
        return -1;
    }

    private static List<int[]> successors(int[] tiles, int size) { // 【修改】传入 size
        int blank = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == 0) {
                blank = i;
            }
        }
        int row = blank / size;
        int col = blank % size;
        List<int[]> result = new ArrayList<>(4);
        if (row > 0) {
            result.add(swap(tiles, blank, blank - size));
        }
        if (row < size - 1) {
            result.add(swap(tiles, blank, blank + size));
        }
        if (col > 0) {
            result.add(swap(tiles, blank, blank - 1));
        }
        if (col < size - 1) {
            result.add(swap(tiles, blank, blank + 1));
        }
        return result;
    }

    private static int[] swap(int[] tiles, int from, int to) {
        int[] next = tiles.clone();
        next[from] = next[to];
        next[to] = 0;
        return next;
    }

    private static long encode(int[] tiles) {
        long key = 0;
        for (int tile : tiles) {
            key = (key << 4) | tile;
        }
        return key;
    }

    private static int[] decode(long key, int size) { // 【修改】传入 size
        int[] tiles = new int[size * size];
        for (int i = tiles.length - 1; i >= 0; i--) {
            tiles[i] = (int) (key & 0xF);
            key >>= 4;
        }
        return tiles;
    }
}