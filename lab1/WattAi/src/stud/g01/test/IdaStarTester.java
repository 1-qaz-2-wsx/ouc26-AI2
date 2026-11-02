package stud.g01.test;

import algs4.util.StopwatchCPU;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 成员C第一阶段 IDA* 自测入口（使用成员A的棋盘实现）。
 *
 * <p>课程主入口 SearchTester 只会用 A*（见 docs/编译问题记录.md R01），IDA* 必须有自己的入口。
 * 本入口用 stud.g01.problem.npuzzle 的 PuzzleBoard/NPuzzleProblem/PuzzleHeuristics 构造用例，
 * 并用独立的 BFS 基准校验最优解长度。</p>
 */
public final class IdaStarTester {

    private static final int SIZE = 3;
    private static final int[] GOAL = {1, 2, 3, 4, 5, 6, 7, 8, 0};

    private static final long SIMPLE_TIME_LIMIT_MS = 10_000L;
    private static final long SPEC_TIME_LIMIT_MS = 120_000L;

    private IdaStarTester() {
    }

    public static void main(String[] args) {
        printEnvironment();
        System.out.println();

        System.out.println("| case_id | algorithm | heuristic | status | moves | bfs_optimal | path_ok |"
                + " wall_time_ms | cpu_time_ms | generated | expanded |");
        System.out.println("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");

        runCase("simple-1", new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8}, "MANHATTAN", SIMPLE_TIME_LIMIT_MS);
        runCase("simple-2", new int[]{1, 2, 3, 4, 5, 6, 0, 7, 8}, "MANHATTAN", SIMPLE_TIME_LIMIT_MS);
        runCase("simple-2-h0", new int[]{1, 2, 3, 4, 5, 6, 0, 7, 8}, "NONE", SIMPLE_TIME_LIMIT_MS);
        runCase("spec-1", new int[]{8, 6, 7, 2, 5, 4, 3, 0, 1}, "MANHATTAN", SPEC_TIME_LIMIT_MS);
        runCase("spec-2", new int[]{6, 4, 7, 8, 5, 0, 3, 2, 1}, "MANHATTAN", SPEC_TIME_LIMIT_MS);
        runCase("spec-1-misplaced", new int[]{8, 6, 7, 2, 5, 4, 3, 0, 1}, "MISPLACED", SPEC_TIME_LIMIT_MS);
        runCase("unsolvable", new int[]{1, 2, 3, 4, 5, 6, 8, 7, 0}, "MANHATTAN", SIMPLE_TIME_LIMIT_MS);
    }

    private static void printEnvironment() {
        System.out.println("JDK: " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch"));
        System.out.println("CPU: " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("JVM 最大堆: " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB");
    }

    private static void runCase(String caseId, int[] initial, String heuristic, long timeLimitMs) {
        PuzzleBoard initialBoard = new PuzzleBoard(SIZE, initial);
        PuzzleBoard goalBoard = new PuzzleBoard(SIZE, GOAL);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, SIZE);

        IdAStar searcher;
        if ("NONE".equals(heuristic)) {
            searcher = new IdAStar(new StackFrontier()); // 单参数构造器：h≡0
        } else {
            HeuristicType type = HeuristicType.valueOf(heuristic);
            searcher = new IdAStar(new StackFrontier(), PuzzleHeuristics.predictor(type));
        }
        searcher.setTimeLimitMillis(timeLimitMs);
        searcher.setNodeLimit(IdAStar.NO_LIMIT);

        StopwatchCPU cpu = new StopwatchCPU();
        long start = System.nanoTime();
        Deque<Node> path = searcher.search(problem);
        double wallMs = (System.nanoTime() - start) / 1e6;
        double cpuMs = cpu.elapsedTime() * 1000.0;

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
        if (moves > 0) {
            bfsOptimal = bfsOptimalLength(initial, GOAL);
            if (bfsOptimal >= 0 && bfsOptimal != moves) {
                pathOk = false; // 解比BFS基准长，说明不是最优解
            }
        }

        System.out.printf("| %s | IDA* | %s | %s | %d | %s | %s | %.1f | %.1f | %d | %d |%n",
                caseId, heuristic, status, moves,
                bfsOptimal < 0 ? "-" : String.valueOf(bfsOptimal),
                moves < 0 ? "-" : String.valueOf(pathOk),
                wallMs, cpuMs, searcher.nodesGenerated(), searcher.nodesExpanded());
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
    private static int bfsOptimalLength(int[] initial, int[] goal) {
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
            int[] tiles = decode(key);
            for (int[] next : successors(tiles)) {
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
        return -1; // 状态空间搜索完毕仍未到达目标：不可解
    }

    private static List<int[]> successors(int[] tiles) {
        int blank = 0;
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == 0) {
                blank = i;
            }
        }
        int row = blank / SIZE;
        int col = blank % SIZE;
        List<int[]> result = new ArrayList<>(4);
        if (row > 0) {
            result.add(swap(tiles, blank, blank - SIZE));
        }
        if (row < SIZE - 1) {
            result.add(swap(tiles, blank, blank + SIZE));
        }
        if (col > 0) {
            result.add(swap(tiles, blank, blank - 1));
        }
        if (col < SIZE - 1) {
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

    private static int[] decode(long key) {
        int[] tiles = new int[SIZE * SIZE];
        for (int i = tiles.length - 1; i >= 0; i--) {
            tiles[i] = (int) (key & 0xF);
            key >>= 4;
        }
        return tiles;
    }
}