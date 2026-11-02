package stud.g01.test;

import algs4.util.StopwatchCPU;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.Direction;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.problem.npuzzle.PuzzleHeuristics;
import stud.g01.problem.npuzzle.PuzzleMove;
import stud.g01.problem.npuzzle.RandomPuzzleGenerator;
import stud.g01.problem.npuzzle.RandomPuzzleGenerator.PuzzleCase;
import stud.g01.solver.IdAStar;
import stud.g01.runner.SolutionPathWriter;
import stud.queue.StackFrontier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本类现在承担三件事：</p>
 * <ul>
 *   <li><b>冒烟表</b>：7 个固定用例跑 IDA*，与 BFS 最优步数对照，输出一张可贴进报告的表格；</li>
 *   <li><b>非标准目标</b>：h 值随目标变化，外加一条完整搜索链路；</li>
 *   <li><b>T1—T9 断言式验收</b>：基准值、非标准目标、3×3 全空间（三个目标）、随机可解对最优性、4×4、
 *       不可解、路径导出逐帧校验、棋盘不变量与后继数、空位距离独立启发 vs 曼哈顿的
 *       IDA* 扩展结点对比与 4×4 max 增益抽样。</li>
 * </ul>
 */
public final class IdaStarTester {

    private static final int SIZE = 3;
    private static final int[] GOAL = {1, 2, 3, 4, 5, 6, 7, 8, 0};

    private static final long SIMPLE_TIME_LIMIT_MS = 10_000L;
    private static final long SPEC_TIME_LIMIT_MS = 120_000L;

    private static final Path CSV_PATH = Paths.get("stage2_records.csv");
    private static final StringBuilder CSV = new StringBuilder();

    /** 断言失败计数：非 0 时 main 以退出码 1 结束，脚本可直接判定成败 */
    private static int failures = 0;

    /** 标准目标 3×3 的 BFS 全表（可达类 181440 态），首次使用时建立并复用 */
    private static Map<PuzzleBoard, Integer> standardTable;

    private IdaStarTester() {
    }

    public static void main(String[] args) throws Exception {
        printEnvironment();
        CSV.append("case_id,size,seed,walk_steps,initial,goal,solvable,algorithm,heuristic,")
           .append("status,moves,wall_time_ms,cpu_time_ms,generated,expanded\n");

        // 一、第一阶段冒烟：固定用例 + BFS 最优步数对照
        smokeTable();
        checkNonStandardGoal();
        checkNonStandardGoalSearch();

        // 二、第二阶段验收
        t1HeuristicBasics();
        t2NonStandardGoal();
        t3FullSpaceCheck();
        t4RandomSolvable3x3();
        t5FourByFour();
        t6Unsolvable();
        t7PathExport();
        t8BoardInvariants();
        t9BlankDistanceStudy();

        Files.write(CSV_PATH, CSV.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println();
        System.out.println("记录已写入 " + CSV_PATH.toAbsolutePath());
        if (failures == 0) {
            System.out.println(">>> 全部检查通过");
        } else {
            System.out.println(">>> 共 " + failures + " 项检查失败");
            System.exit(1);
        }
    }

    // ---------------- 一、冒烟表 ----------------

    private static void smokeTable() {
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

    private static void runCase(String caseId, int[] initial, String heuristic, long timeLimitMs) {
        PuzzleBoard initialBoard = new PuzzleBoard(SIZE, initial);
        PuzzleBoard goalBoard = new PuzzleBoard(SIZE, GOAL);
        NPuzzleProblem problem = new NPuzzleProblem(initialBoard, goalBoard, SIZE);

        // 每条用例顺带记录三个 h 值；能断言的部分变成检查项，不再只靠人眼看
        System.out.println("  · " + caseId + "  h(错位/曼哈顿/空位)="
                + initialBoard.misplaced(goalBoard) + "/"
                + initialBoard.manhattan(goalBoard) + "/"
                + initialBoard.blankDistance(goalBoard));
        checkValue(goalBoard.misplaced(goalBoard), 0, caseId + " 目标态错位");
        checkValue(goalBoard.manhattan(goalBoard), 0, caseId + " 目标态曼哈顿");
        checkValue(goalBoard.blankDistance(goalBoard), 0, caseId + " 目标态空位距离");

        PuzzleBoard sameGoal = new PuzzleBoard(SIZE, GOAL);
        check(goalBoard.equals(sameGoal), caseId + " equals 一致性");
        check(goalBoard.hashCode() == sameGoal.hashCode(), caseId + " hashCode 一致性");

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
            bfsOptimal = bfsOptimalLength(initial);
            if (bfsOptimal >= 0 && bfsOptimal != moves) {
                pathOk = false; // 解比 BFS 基准长，说明不是最优解
            }
        }

        System.out.printf("| %s | IDA* | %s | %s | %d | %s | %s | %.1f | %.1f | %d | %d |%n",
                caseId, heuristic, status, moves,
                bfsOptimal < 0 ? "-" : String.valueOf(bfsOptimal),
                moves < 0 ? "-" : String.valueOf(pathOk),
                wallMs, cpuMs, searcher.nodesGenerated(), searcher.nodesExpanded());
    }

    /**
     * 非标准目标下的 h 值：同一个盘面换一个目标，三个值必须都跟着变。
     * 这一组是「曼哈顿查的是目标映射表、不是 tile-1」的直接证据。
     */
    private static void checkNonStandardGoal() {
        System.out.println("===== 非标准目标：h 值随目标变化 =====");
        PuzzleBoard stdGoal = new PuzzleBoard(SIZE, GOAL);
        PuzzleBoard altGoal = new PuzzleBoard(SIZE, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        PuzzleBoard nearAlt = new PuzzleBoard(SIZE, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        PuzzleBoard nearStd = new PuzzleBoard(SIZE, new int[]{1, 2, 3, 4, 5, 6, 7, 8, 0});

        // 目标态自身三个值都是 0：两种目标都必须成立
        checkValue(nearAlt.misplaced(altGoal), 0, "非标准目标态 错位");
        checkValue(nearAlt.manhattan(altGoal), 0, "非标准目标态 曼哈顿");
        checkValue(nearAlt.blankDistance(altGoal), 0, "非标准目标态 空位距离");
        checkValue(nearStd.misplaced(stdGoal), 0, "标准目标态 错位");
        checkValue(nearStd.manhattan(stdGoal), 0, "标准目标态 曼哈顿");
        checkValue(nearStd.blankDistance(stdGoal), 0, "标准目标态 空位距离");

        // 同一个盘面、换目标：8 与空格互相错位一格，三个值都应为 1
        checkValue(nearAlt.misplaced(stdGoal), 1, "盘面(空位@7) 对标准目标 错位");
        checkValue(nearAlt.manhattan(stdGoal), 1, "盘面(空位@7) 对标准目标 曼哈顿");
        checkValue(nearAlt.blankDistance(stdGoal), 1, "盘面(空位@7) 对标准目标 空位距离");
        checkValue(nearStd.misplaced(altGoal), 1, "标准目标盘面 对非标准目标 错位");
        checkValue(nearStd.manhattan(altGoal), 1, "标准目标盘面 对非标准目标 曼哈顿");
        checkValue(nearStd.blankDistance(altGoal), 1, "标准目标盘面 对非标准目标 空位距离");

        // 深层盘面：不写死数值，只要求换目标后估值必须变化
        PuzzleBoard deep = new PuzzleBoard(SIZE, new int[]{8, 1, 5, 4, 3, 2, 0, 7, 6});
        int hStd = deep.manhattan(stdGoal);
        int hAlt = deep.manhattan(altGoal);
        check(hStd != hAlt, "深层盘面在两个目标下的曼哈顿应不同（相同则说明用了 tile-1 而非目标映射）："
                + hStd + " vs " + hAlt);

        // 报告 2.4 直接引用的对照表：同一个盘面，换目标后三个 h 值全变
        System.out.println();
        System.out.println("  对照表（错位/曼哈顿/空位距离）");
        System.out.println("    盘面 A（空位@7）　　对标准目标 " + triple(nearAlt, stdGoal)
                + "　对非标准目标 " + triple(nearAlt, altGoal));
        System.out.println("    盘面 B（标准目标）　对标准目标 " + triple(nearStd, stdGoal)
                + "　对非标准目标 " + triple(nearStd, altGoal));
        System.out.println("    深层盘面　　　　　　对标准目标 " + triple(deep, stdGoal)
                + "　对非标准目标 " + triple(deep, altGoal));
    }

    /** 非标准目标下的完整搜索链路：能解、步数对、逐帧有效 */
    private static void checkNonStandardGoalSearch() {
        System.out.println("===== 非标准目标搜索 =====");
        PuzzleBoard searchInitial = new PuzzleBoard(SIZE, GOAL);
        PuzzleBoard searchGoal = new PuzzleBoard(SIZE, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        NPuzzleProblem problem = new NPuzzleProblem(searchInitial, searchGoal, SIZE);

        IdAStar searcher = new IdAStar(new StackFrontier(),
                PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
        searcher.setTimeLimitMillis(SIMPLE_TIME_LIMIT_MS);
        Deque<Node> path = searcher.search(problem);

        check(path != null, "非标准目标应当能搜到解");
        checkValue(path == null ? -1 : path.size(), 1, "非标准目标 解步数");
        check(path != null && pathIsValid(problem, path), "非标准目标 路径逐帧校验");
    }

    // ---------------- 二、T1 基准值 ----------------

    private static void t1HeuristicBasics() {
        System.out.println("===== T1 目标态与一步态 =====");
        PuzzleBoard goal = RandomPuzzleGenerator.standardGoal(3);
        check(goal.misplaced(goal) == 0, "T1 目标态 misplaced 应为 0");
        check(goal.manhattan(goal) == 0, "T1 目标态 manhattan 应为 0");
        check(goal.blankDistance(goal) == 0, "T1 目标态 blankDistance 应为 0");

        PuzzleBoard one = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        check(one.manhattan(goal) == 1, "T1 一步态 manhattan 应为 1");
        check(one.misplaced(goal) == 1, "T1 一步态 misplaced 应为 1");
        check(one.blankDistance(goal) == 1, "T1 一步态 blankDistance 应为 1");
        record("T1-basic", 3, -1L, -1, one, goal, true, "NONE", "MISPLACED/MANHATTAN",
                "CHECK", -1, 0, 0, 0, 0);
    }

    // ---------------- T2 非标准目标 ----------------

    private static void t2NonStandardGoal() {
        System.out.println("===== T2 非标准目标 =====");
        PuzzleBoard std = RandomPuzzleGenerator.standardGoal(3);
        PuzzleBoard center = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8});
        PuzzleBoard corner = new PuzzleBoard(3, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8});
        for (PuzzleBoard goal : new PuzzleBoard[]{center, corner}) {
            PuzzleBoard same = new PuzzleBoard(3, goal.toArray());
            check(same.equals(goal), "T2 重建目标应相等");
            check(goal.misplaced(goal) == 0 && goal.manhattan(goal) == 0
                    && goal.blankDistance(goal) == 0, "T2 非标准目标处三值应为 0");

            PuzzleCase pc = RandomPuzzleGenerator.solvablePairWithGoal(goal, 20261026L, 6);
            NPuzzleProblem p = new NPuzzleProblem(pc.initial, pc.goal, 3);
            check(p.solvable(), "T2 游走生成的对必须可解");
            Map<PuzzleBoard, Integer> table = bfsDistances(goal);
            Integer d = table.get(pc.initial);
            check(d != null, "T2 可解对必须出现在 BFS 表中");
            if (d != null) {
                check(pc.initial.manhattan(goal) <= d, "T2 曼哈顿不得超过最优步数");
                check(pc.initial.misplaced(goal) <= d, "T2 错位不得超过最优步数");
                check(pc.initial.blankDistance(goal) <= d, "T2 空位距离不得超过最优步数");
            }
            record("T2-nonstd", 3, 20261026L, 6, pc.initial, goal, p.solvable(),
                    "NONE", "NONE", "CHECK", -1, 0, 0, 0, 0);
        }
        check(!std.equals(center), "T2 标准目标与中心空位目标不应相等");
    }

    // ---------------- T3 3×3 全空间 ----------------

    private static void t3FullSpaceCheck() {
        System.out.println("===== T3 3×3 全空间校验（三个目标） =====");
        PuzzleBoard[] goals = {
                RandomPuzzleGenerator.standardGoal(3),                      // 空格@8（角）
                new PuzzleBoard(3, new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8}),   // 空格@4（中心）
                new PuzzleBoard(3, new int[]{1, 0, 2, 3, 4, 5, 6, 7, 8})    // 空格@1（边）
        };
        for (PuzzleBoard goal : goals) {
            long t0 = System.currentTimeMillis();
            StopwatchCPU cpu = new StopwatchCPU();
            Map<PuzzleBoard, Integer> table = bfsDistances(goal);
            double cpuMs = cpu.elapsedTime() * 1000.0;

            int states = table.size();
            int admViolation = 0;
            int consViolation = 0;
            int maxGainOverManhattan = 0;
            int blankOverManhattan = 0;
            double sumManRatio = 0;
            double sumMisRatio = 0;
            double sumBlankRatio = 0;
            int positive = 0;

            // 目标位置表只算一次，循环内复用（避免每态重建 O(N²) 表）
            int[] goalPos = goal.goalPositions();
            for (Map.Entry<PuzzleBoard, Integer> e : table.entrySet()) {
                PuzzleBoard s = e.getKey();
                int d = e.getValue();
                int hm = s.manhattan(goalPos);
                int hb = s.blankDistance(goal);
                int hmi = s.misplaced(goal);
                if (hm > d || hmi > d || hb > d) {
                    admViolation++;
                }
                if (Math.max(hm, hb) > hm) {
                    maxGainOverManhattan++;
                }
                if (hb > hm) {
                    blankOverManhattan++;
                }
                if (d > 0) {
                    positive++;
                    sumManRatio += (double) hm / d;
                    sumMisRatio += (double) hmi / d;
                    sumBlankRatio += (double) hb / d;
                }
                for (Direction dir : Direction.values()) {
                    if (!s.canMove(dir)) {
                        continue;
                    }
                    PuzzleBoard next = s.move(dir);
                    // 三个启发函数都要满足单调性：h(s) ≤ c(s,s') + h(s')，c≡1
                    if (hm > 1 + next.manhattan(goalPos)
                            || hmi > 1 + next.misplaced(goal)
                            || hb > 1 + next.blankDistance(goal)) {
                        consViolation++;
                    }
                }
            }

            System.out.printf("目标空位@%d：状态数=%d  建表 %.0f ms / %d ms%n",
                    goal.blankIndex(), states, cpuMs, System.currentTimeMillis() - t0);
            System.out.printf("  可采纳性违反=%d，一致性违反=%d%n", admViolation, consViolation);
            System.out.printf("  max(h_man,d_blank) > h_man 的状态数=%d；d_blank > h_man 的状态数=%d%n",
                    maxGainOverManhattan, blankOverManhattan);
            System.out.printf("  平均 h/d*：曼哈顿=%.3f，错位=%.3f，空位距离=%.3f（%d 个非目标态）%n",
                    sumManRatio / positive, sumMisRatio / positive, sumBlankRatio / positive, positive);

            check(admViolation == 0, "T3 全空间可采纳性必须无违反");
            check(consViolation == 0, "T3 全空间一致性必须无违反");
            record("T3-fullspace", 3, -1L, -1, goal, goal, true, "BFS", "NONE",
                    "ADM=" + admViolation + ";CONS=" + consViolation
                            + ";MAXGAIN=" + maxGainOverManhattan,
                    -1, 0, Math.round(cpuMs), 0, states);
        }
    }

    // ---------------- T4 随机可解对：最短性 ----------------

    private static void t4RandomSolvable3x3() {
        System.out.println("===== T4 3×3 随机可解对（最短性） =====");
        PuzzleBoard goal = RandomPuzzleGenerator.standardGoal(3);
        int[] steps = {5, 10, 15, 20, 25, 30};
        for (int i = 0; i < steps.length; i++) {
            long seed = 1000L + i;
            PuzzleCase pc = RandomPuzzleGenerator.solvablePairWithGoal(goal, seed, steps[i]);
            NPuzzleProblem problem = new NPuzzleProblem(pc.initial, pc.goal, 3);
            Integer d = standardTable().get(pc.initial);
            if (d == null) {
                check(false, "T4 游走结果必须可解");
                continue;
            }
            IdAStar searcher = new IdAStar(new StackFrontier(),
                    PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
            searcher.setTimeLimitMillis(30_000L);
            StopwatchCPU cpu = new StopwatchCPU();
            long t0 = System.currentTimeMillis();
            Deque<Node> path = searcher.search(problem);
            long wall = System.currentTimeMillis() - t0;
            int moves = path == null ? -1 : path.size();
            String status = path == null ? (searcher.aborted() ? "TIMEOUT" : "UNSOLVABLE") : "SOLVED";

            System.out.printf("seed=%d walk_steps=%d d*=%d moves=%d %s 扩展=%d%n",
                    seed, steps[i], d, moves, status, searcher.nodesExpanded());
            check(path != null, "T4 可解问题必须找到解");
            check(moves == d, "T4 IDA* 步数必须等于 BFS 最优步数");
            check(pathIsValid(problem, path), "T4 路径必须合法");
            record("T4-random", 3, seed, steps[i], pc.initial, goal, true, "IDA*", "MANHATTAN",
                    status, moves, wall, Math.round(cpu.elapsedTime() * 1000.0),
                    searcher.nodesGenerated(), searcher.nodesExpanded());
        }
    }

    // ---------------- T5 4×4 ----------------

    private static void t5FourByFour() {
        System.out.println("===== T5 4×4 =====");
        for (int walk = 1; walk <= 3; walk++) {
            long seed = 4000L + walk;
            PuzzleCase pc = RandomPuzzleGenerator.randomSolvablePair(4, seed, walk);
            NPuzzleProblem problem = new NPuzzleProblem(pc.initial, pc.goal, 4);
            check(problem.solvable(), "T5 浅层 4×4 对必须可解");

            IdAStar searcher = new IdAStar(new StackFrontier(),
                    PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
            searcher.setTimeLimitMillis(20_000L);
            StopwatchCPU cpu = new StopwatchCPU();
            long t0 = System.currentTimeMillis();
            Deque<Node> path = searcher.search(problem);
            long wall = System.currentTimeMillis() - t0;
            int moves = path == null ? -1 : path.size();
            String status = path == null ? (searcher.aborted() ? "TIMEOUT" : "UNSOLVABLE") : "SOLVED";
            System.out.printf("4×4 seed=%d walk_steps=%d moves=%d %s%n", seed, walk, moves, status);
            check(path != null, "T5 浅层 4×4 必须求解成功");
            check(moves <= walk, "T5 解长度不得超过游走步数");
            check(pathIsValid(problem, path), "T5 4×4 路径必须合法");
            record("T5-4x4", 4, seed, walk, pc.initial, pc.goal, true, "IDA*", "MANHATTAN",
                    status, moves, wall, Math.round(cpu.elapsedTime() * 1000.0),
                    searcher.nodesGenerated(), searcher.nodesExpanded());
        }

        PuzzleCase deep = RandomPuzzleGenerator.randomSolvablePair(4, 4444L, 40);
        NPuzzleProblem dp = new NPuzzleProblem(deep.initial, deep.goal, 4);
        int hm = deep.initial.manhattan(deep.goal);
        System.out.printf("4×4 深对 walk_steps=40  manhattan=%d  solvable=%s%n", hm, dp.solvable());
        check(dp.solvable(), "T5 深对必须可解");
        check(hm <= 40, "T5 深对启发值不得超过游走上界");
        record("T5-4x4-deep", 4, 4444L, 40, deep.initial, deep.goal, dp.solvable(), "NONE", "MANHATTAN",
                "CHECK", -1, 0, 0, 0, 0);
    }

    // ---------------- T6 不可解 ----------------

    private static void t6Unsolvable() {
        System.out.println("===== T6 不可解 =====");
        int[] sizes = {3, 4};
        for (int size : sizes) {
            PuzzleCase bad = RandomPuzzleGenerator.unsolvablePair(size, 6000L + size, 8);
            NPuzzleProblem problem = new NPuzzleProblem(bad.initial, bad.goal, size);
            IdAStar searcher = new IdAStar(new StackFrontier(),
                    PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
            Deque<Node> path = searcher.search(problem);
            System.out.printf("%d×%d 交换两块后 solvable=%s，搜索返回 %s%n",
                    size, size, problem.solvable(), path == null ? "null" : "非 null");
            check(!problem.solvable(), "T6 交换两个非空将牌后必须不可解");
            check(path == null && !searcher.aborted(), "T6 不可解必须立即返回 null 且不算 TIMEOUT");
            record("T6-unsolvable", size, 6000L + size, 8, bad.initial, bad.goal, false, "IDA*",
                    "MANHATTAN", "UNSOLVABLE", -1, 0, 0,
                    searcher.nodesGenerated(), searcher.nodesExpanded());
        }
    }

    // ---------------- T7 路径导出 ----------------

    private static void t7PathExport() throws IOException {
        System.out.println("===== T7 路径导出与逐帧校验 =====");
        Path outDir = Paths.get("stage2_output");
        Files.createDirectories(outDir);

        PuzzleBoard goal = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 0, 5, 6, 7, 8});
        PuzzleCase pc = RandomPuzzleGenerator.solvablePairWithGoal(goal, 7001L, 8);
        NPuzzleProblem problem = new NPuzzleProblem(pc.initial, pc.goal, 3);
        IdAStar searcher = new IdAStar(new StackFrontier(),
                PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
        Deque<Node> path = searcher.search(problem);
        check(path != null, "T7 非标准目标必须求解成功");

        Path solvedFile = outDir.resolve("stage2_nonstd_goal.txt");
        SolutionPathWriter.write(solvedFile, problem, path);
        int v = verifyPathFile(solvedFile, problem);
        check(v == 0, "T7 SOLVED 文件逐帧校验必须无违反，实际违反 " + v);

        PuzzleCase bad = RandomPuzzleGenerator.unsolvablePair(3, 7002L, 8);
        NPuzzleProblem badProblem = new NPuzzleProblem(bad.initial, bad.goal, 3);
        Path badFile = outDir.resolve("stage2_unsolvable.txt");
        SolutionPathWriter.write(badFile, badProblem, null);
        int v2 = verifyPathFile(badFile, badProblem);
        check(v2 == 0, "T7 UNSOLVABLE 文件必须符合 moves=-1/states=1，实际违反 " + v2);

        // 边界：初始即目标（接口文档约定 moves 0、states 1，不应当作无解）
        PuzzleBoard same = new PuzzleBoard(3, goal.toArray());
        NPuzzleProblem trivial = new NPuzzleProblem(same, goal, 3);
        check(trivial.solvable(), "T7 初始即目标必须可解");
        IdAStar trivialSearcher = new IdAStar(new StackFrontier(),
                PuzzleHeuristics.predictor(HeuristicType.MANHATTAN));
        Deque<Node> trivialPath = trivialSearcher.search(trivial);
        check(trivialPath != null && trivialPath.isEmpty(),
                "T7 初始即目标应返回空路径而不是 null");
        Path trivialFile = outDir.resolve("stage2_trivial.txt");
        SolutionPathWriter.write(trivialFile, trivial, trivialPath);
        int v3 = verifyPathFile(trivialFile, trivial);
        check(v3 == 0, "T7 初始即目标须为 moves 0 / states 1，实际违反 " + v3);

        System.out.println("导出：" + solvedFile.toAbsolutePath() + " / " + badFile.toAbsolutePath()
                + " / " + trivialFile.toAbsolutePath());
        record("T7-export", 3, 7001L, 8, pc.initial, pc.goal, true, "IDA*", "MANHATTAN",
                "SOLVED", path == null ? -1 : path.size(), 0, 0,
                searcher.nodesGenerated(), searcher.nodesExpanded());
        record("T7-trivial", 3, -1L, 0, same, goal, true, "IDA*", "MANHATTAN",
                "SOLVED", 0, 0, 0,
                trivialSearcher.nodesGenerated(), trivialSearcher.nodesExpanded());
    }

    /**
     * 逐帧校验 NP_PATH_V1 文件。
     * @return 违反条数；0 表示完全符合 docs/棋盘与路径接口.md 的格式与合法性约定
     */
    private static int verifyPathFile(Path file, NPuzzleProblem problem) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int v = 0;
        if (lines.isEmpty() || !"NP_PATH_V1".equals(lines.get(0).trim())) {
            System.out.println("  违反：缺少协议头");
            return 1;
        }
        int size = -1;
        int moves = Integer.MIN_VALUE;
        int states = -1;
        String status = null;
        int[] goal = null;
        List<int[]> frames = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("\\s+");
            try {
                switch (t[0]) {
                    case "size":
                        size = Integer.parseInt(t[1]);
                        break;
                    case "status":
                        status = t[1];
                        break;
                    case "moves":
                        moves = Integer.parseInt(t[1]);
                        break;
                    case "states":
                        states = Integer.parseInt(t[1]);
                        break;
                    case "goal":
                        goal = parseInts(t, 1);
                        break;
                    case "state": {
                        int step = Integer.parseInt(t[1]);
                        if (step != frames.size()) {
                            System.out.println("  违反：帧序号不连续，期望 " + frames.size() + "，实际 " + step);
                            v++;
                        }
                        frames.add(parseInts(t, 2));
                        break;
                    }
                    default:
                        System.out.println("  违反：未知字段 " + t[0]);
                        v++;
                }
            } catch (RuntimeException ex) {
                // 字段残缺或数字非法：记一条违反即可，不能让校验器自己抛异常中断
                System.out.println("  违反：第 " + (i + 1) + " 行字段残缺或数字非法（" + line + "）");
                v++;
            }
        }

        if (size != problem.initialBoard().size()) {
            System.out.println("  违反：size 与棋盘不符");
            v++;
        }
        if (states != frames.size()) {
            System.out.println("  违反：states=" + states + " 与实际帧数 " + frames.size() + " 不符");
            v++;
        }
        if (goal == null || !java.util.Arrays.equals(goal, problem.goalBoard().toArray())) {
            System.out.println("  违反：goal 行与问题目标不一致");
            v++;
        }
        if ("SOLVED".equals(status)) {
            if (moves != frames.size() - 1) {
                System.out.println("  违反：SOLVED 时 states 应等于 moves+1");
                v++;
            }
            if (frames.isEmpty()) {
                // 没有帧就无法比对首末帧；先记一条违反，避免后面 get(0) 直接抛异常
                System.out.println("  违反：SOLVED 文件没有任何 state 帧");
                v++;
            } else {
                if (!java.util.Arrays.equals(frames.get(0), problem.initialBoard().toArray())) {
                    System.out.println("  违反：第 0 帧不是初始状态");
                    v++;
                }
                if (!java.util.Arrays.equals(frames.get(frames.size() - 1), problem.goalBoard().toArray())) {
                    System.out.println("  违反：最后一帧不是目标状态");
                    v++;
                }
                // 相邻帧校验用问题自身的边长，不依赖文件里的 size 字段是否可信
                int boardSize = problem.initialBoard().size();
                for (int i = 1; i < frames.size(); i++) {
                    v += frameTransitionViolation(frames.get(i - 1), frames.get(i), boardSize);
                }
            }
        } else if ("UNSOLVABLE".equals(status)) {
            if (moves != -1 || frames.size() != 1) {
                System.out.println("  违反：UNSOLVABLE 应为 moves=-1、states=1");
                v++;
            }
        } else {
            System.out.println("  违反：status 非法 " + status);
            v++;
        }
        return v;
    }

    // ---------------- T8 棋盘不变量与后继生成 ----------------

    /**
     * 覆盖《棋盘与路径接口》"最低交叉检查"中此前缺失的两条：
     *   ① 修改构造时传入的数组、或修改 toArray() 返回值，都不能改变棋盘；
     *   ② 空格在角、边、中心分别产生 2、3、4 个合法后继（3×3 与 4×4）。
     * 附带验证：actions() 恒给出 4 个候选、后继互不重复、生成后继不改变父棋盘、
     * 每个后继恰为一次"空格与相邻将牌交换"。
     */
    private static void t8BoardInvariants() {
        System.out.println("===== T8 棋盘不变量与后继生成 =====");

        // ① 构造器深拷贝 + toArray 返回副本
        int[] src = {1, 2, 3, 4, 5, 6, 7, 0, 8};
        PuzzleBoard board = new PuzzleBoard(3, src);
        src[0] = 99;
        check(board.tileAt(0) == 1,
                "T8 修改构造入参数组后棋盘不应改变（tileAt(0)=" + board.tileAt(0) + "）");
        int[] out = board.toArray();
        out[0] = 99;
        check(board.tileAt(0) == 1,
                "T8 修改 toArray() 返回值后棋盘不应改变（tileAt(0)=" + board.tileAt(0) + "）");
        check(board.equals(new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8})),
                "T8 外部改动不得影响棋盘的相等性");
        check(board.hashCode() == new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8}).hashCode(),
                "T8 相等棋盘的 hashCode 必须相同");

        // ② 后继数：{边长, 空格下标, 期望后继数}
        int[][] cases = {
                {3, 0, 2}, {3, 1, 3}, {3, 4, 4},
                {4, 0, 2}, {4, 1, 3}, {4, 4, 3}, {4, 5, 4},
        };
        String[] where = {"角", "边", "中心", "角", "边", "边", "中心"};
        System.out.println("  边长  空格@  位置  候选  合法后继  期望");
        for (int i = 0; i < cases.length; i++) {
            int size = cases[i][0];
            int blank = cases[i][1];
            int expected = cases[i][2];
            PuzzleBoard start = boardWithBlankAt(size, blank);
            int[] before = start.toArray();
            NPuzzleProblem problem = new NPuzzleProblem(start, start, size);

            int candidates = 0;
            for (var ignored : start.actions()) {
                candidates++;
            }
            check(candidates == 4, "T8 " + size + "×" + size + " actions() 应给 4 个候选，实际 " + candidates);

            List<Node> children = problem.childNodes(problem.root());
            System.out.printf("  %d×%d  %2d    %s    %d     %d       %d%n",
                    size, size, blank, where[i], candidates, children.size(), expected);
            check(children.size() == expected,
                    "T8 " + size + "×" + size + " 空格@" + blank + "（" + where[i] + "）后继数应为 "
                            + expected + "，实际 " + children.size());

            // 生成后继不得改变父棋盘（不可变性，搜索树依赖这一点）
            check(java.util.Arrays.equals(before, start.toArray()),
                    "T8 " + size + "×" + size + " 空格@" + blank + " 生成后继后父棋盘被改动");

            // 每个后继恰为一次合法交换，且互不重复
            List<int[]> seen = new ArrayList<>();
            for (Node child : children) {
                int[] arr = ((PuzzleBoard) child.getState()).toArray();
                for (int[] prev : seen) {
                    check(!java.util.Arrays.equals(prev, arr),
                            "T8 " + size + "×" + size + " 空格@" + blank + " 出现重复后继");
                }
                seen.add(arr);
                check(frameTransitionViolation(before, arr, size) == 0,
                        "T8 " + size + "×" + size + " 空格@" + blank + " 的后继不是一次合法交换");
            }
        }

        // ③ 构造器校验：NPuzzleProblem 对边长不一致、size 参数不符必须当场拒收
        PuzzleBoard b3 = new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8});
        PuzzleBoard b4 = new PuzzleBoard(4, new int[]{1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 0});
        check(expectIllegalArgument(() -> new NPuzzleProblem(b3, b4, 3)),
                "T8 三参构造器对边长不一致的棋盘必须抛 IllegalArgumentException");
        check(expectIllegalArgument(() -> new NPuzzleProblem(b3, b3, 4)),
                "T8 三参构造器对 size 与棋盘边长不符必须抛 IllegalArgumentException");
        check(expectIllegalArgument(() -> new NPuzzleProblem(b3, null, 3)),
                "T8 构造器对非 PuzzleBoard 的目标必须抛 IllegalArgumentException");

        record("T8-invariants", 3, -1L, -1, new PuzzleBoard(3, new int[]{1, 2, 3, 4, 5, 6, 7, 0, 8}),
                RandomPuzzleGenerator.standardGoal(3), true, "NONE", "NONE", "CHECK", -1, 0, 0, 0, 0);
    }

    /** 断言动作抛出 IllegalArgumentException：抛出返回 true，未抛出返回 false */
    private static boolean expectIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    // ---------------- T9 空位距离研究 ----------------

    /**
     * 第二阶段"空位距离研究"的两项定量实验（评审缺项的补齐）：
     * <ul>
     *   <li>① 与 T4 同种子同游走步数的六个可解对，分别用 空位距离 / 曼哈顿 /
     *       max(曼哈顿,空位) 作为 IDA* 启发函数，对比扩展结点数——
     *       "空位距离单独用弱到什么程度"用数字说话；</li>
     *   <li>② 4×4 固定种子随机抽样 50 万盘面（只保留与标准目标同可达类的可解盘面），
     *       统计 d_blank &gt; h_manhattan 的状态数，即 max(曼哈顿,空位) 相对纯曼哈顿的
     *       潜在增益——把此前临时运行的结论固化为可复现实验。</li>
     * </ul>
     */
    private static void t9BlankDistanceStudy() {
        System.out.println("===== T9 空位距离研究 =====");

        // ① IDA* 扩展结点对比
        PuzzleBoard goal = RandomPuzzleGenerator.standardGoal(3);
        int[] steps = {5, 10, 15, 20, 25, 30};
        long nodeCap = 2_000_000L;
        System.out.println("  walk  d*  |  曼哈顿扩展   空位扩展   max扩展  空位状态");
        for (int i = 0; i < steps.length; i++) {
            long seed = 1000L + i;
            PuzzleCase pc = RandomPuzzleGenerator.solvablePairWithGoal(goal, seed, steps[i]);
            NPuzzleProblem problem = new NPuzzleProblem(pc.initial, pc.goal, 3);
            Integer d = standardTable().get(pc.initial);
            check(d != null, "T9 游走结果必须可解");
            if (d == null) {
                continue;
            }

            long expMan = -1;
            long expBlank = -1;
            long expMax = -1;
            String blankStatus = "SOLVED";
            for (String h : new String[]{"MANHATTAN", "BLANK", "MAX"}) {
                IdAStar searcher = new IdAStar(new StackFrontier(), t9Predictor(h));
                searcher.setTimeLimitMillis(60_000L);
                searcher.setNodeLimit(nodeCap);
                Deque<Node> path = searcher.search(problem);
                long expanded = searcher.nodesExpanded();
                if ("MANHATTAN".equals(h)) {
                    expMan = expanded;
                    check(path != null, "T9 曼哈顿必须解出 d*=" + d + " 的用例");
                } else if ("BLANK".equals(h)) {
                    expBlank = expanded;
                    blankStatus = searcher.aborted() ? "CAPPED" : (path == null ? "UNSOLVABLE" : "SOLVED");
                } else {
                    expMax = expanded;
                    check(path != null, "T9 max(曼哈顿,空位) 必须解出该用例");
                }
            }
            System.out.printf("  %4d  %2d  |  %10d  %8d  %7d  %s%n",
                    steps[i], d, expMan, expBlank, expMax, blankStatus);
            check(expMax == expMan, "T9 max 与纯曼哈顿的扩展数必须完全一致（实测等效）");
            record("T9-blank-compare", 3, seed, steps[i], pc.initial, goal, true,
                    "IDA*", "MANHATTAN/BLANK/MAX",
                    "MAN=" + expMan + ";BLANK=" + expBlank + ";MAX=" + expMax
                            + ";BLANK_" + blankStatus,
                    -1, 0, 0, -1, (int) expBlank);
        }

        // ② 4×4 抽样：max(曼哈顿, 空位) 的增益统计（固定种子，可复现）
        int samples = 500_000;
        long samplingSeed = 20261102L;
        java.util.Random rnd = new java.util.Random(samplingSeed);
        int[] tiles = new int[16];
        for (int i = 0; i < 16; i++) {
            tiles[i] = i;
        }
        PuzzleBoard goal4 = RandomPuzzleGenerator.standardGoal(4);
        int goalParity = parityOf(tiles);
        long gainStates = 0;
        long accepted = 0;
        for (int s = 0; s < samples; s++) {
            shuffle(tiles, rnd);
            if (parityOf(tiles) != goalParity) {
                continue; // 只统计与标准目标同可达类的可解盘面
            }
            accepted++;
            PuzzleBoard board = new PuzzleBoard(4, tiles);
            if (board.blankDistance(goal4) > board.manhattan(goal4)) {
                gainStates++;
            }
        }
        System.out.printf("  4×4 抽样 %d 次得 %d 个可解盘面：d_blank > h_man 的状态数=%d（增益比例 %.6f%%）%n",
                samples, accepted, gainStates, 100.0 * gainStates / accepted);
        check(gainStates == 0, "T9 4×4 抽样中 max(曼哈顿,空位) 相对纯曼哈顿不应有增益");
        record("T9-4x4-sampling", 4, samplingSeed, -1, goal4, goal4, true, "NONE",
                "MANHATTAN/BLANK", "SAMPLES=" + accepted + ";GAIN=" + gainStates,
                -1, 0, 0, 0, (int) accepted);
    }

    /** T9 用的三个估价器：MANHATTAN / BLANK / MAX（MAX = max(曼哈顿, 空位距离)） */
    private static Predictor t9Predictor(String h) {
        switch (h) {
            case "MANHATTAN":
                return PuzzleHeuristics.manhattan();
            case "BLANK":
                return PuzzleHeuristics.blankDistance();
            case "MAX":
                return PuzzleHeuristics.manhattanMaxBlank();
            default:
                throw new IllegalArgumentException("未知启发类型: " + h);
        }
    }

    /** 偶数阶盘面的可达性奇偶标记：逆序对数 + 空格距底行数（底行记 1），模 2；边长由数组长度推出 */
    private static int parityOf(int[] a) {
        int size = (int) Math.round(Math.sqrt(a.length));
        int inv = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                continue;
            }
            for (int j = i + 1; j < a.length; j++) {
                if (a[j] != 0 && a[i] > a[j]) {
                    inv++;
                }
            }
        }
        int blankRowFromBottom = size - (indexOfZero(a) / size);
        return (inv + blankRowFromBottom) % 2;
    }

    private static int indexOfZero(int[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Fisher-Yates 原地洗牌（Random 来自固定种子，保证抽样可复现） */
    private static void shuffle(int[] a, java.util.Random rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    /** 构造边长 size、空格位于 blankIdx、其余位置按 1..N²-1 顺序填充的棋盘 */
    private static PuzzleBoard boardWithBlankAt(int size, int blankIdx) {
        int total = size * size;
        int[] a = new int[total];
        int v = 1;
        for (int i = 0; i < total; i++) {
            a[i] = (i == blankIdx) ? 0 : v++;
        }
        return new PuzzleBoard(size, a);
    }

    // ---------------- 三、公共工具 ----------------

    /** 两帧之间必须恰好是一次"空格与相邻将牌交换" */
    private static int frameTransitionViolation(int[] a, int[] b, int size) {
        int pa = -1;
        int pb = -1;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                pa = i;
            }
            if (b[i] == 0) {
                pb = i;
            }
        }
        if (pa < 0 || pb < 0) {
            System.out.println("  违反：帧中缺少空格");
            return 1;
        }
        int dist = Math.abs(pa / size - pb / size) + Math.abs(pa % size - pb % size);
        if (dist != 1) {
            System.out.println("  违反：空格位移不是 1 格（" + pa + "→" + pb + "）");
            return 1;
        }
        for (int i = 0; i < a.length; i++) {
            if (i == pa || i == pb) {
                continue;
            }
            if (a[i] != b[i]) {
                System.out.println("  违反：非空格位置发生非交换改动，下标 " + i);
                return 1;
            }
        }
        if (b[pa] != a[pb] || b[pb] != 0) {
            System.out.println("  违反：空格与相邻将牌未正确交换");
            return 1;
        }
        return 0;
    }

    /** 校验解路径：每两帧只能交换空格与相邻数字，最后一帧必须等于目标 */
    private static boolean pathIsValid(NPuzzleProblem problem, Deque<Node> path) {
        if (path == null) {
            return false;
        }
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

    /** 标准目标 3×3 的 BFS 全表，懒加载一次，供冒烟表与 T4 共用 */
    private static Map<PuzzleBoard, Integer> standardTable() {
        if (standardTable == null) {
            standardTable = bfsDistances(RandomPuzzleGenerator.standardGoal(3));
        }
        return standardTable;
    }

    /** 从目标反向 BFS，返回该可达类内全部状态的精确最短距离（BFS 与 IDA* 不共享搜索代码） */
    private static Map<PuzzleBoard, Integer> bfsDistances(PuzzleBoard goal) {
        Map<PuzzleBoard, Integer> dist = new HashMap<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        dist.put(goal, 0);
        queue.add(goal);
        while (!queue.isEmpty()) {
            PuzzleBoard cur = queue.poll();
            int d = dist.get(cur);
            for (Direction dir : Direction.values()) {
                if (!cur.canMove(dir)) {
                    continue;
                }
                PuzzleBoard next = cur.move(dir);
                if (!dist.containsKey(next)) {
                    dist.put(next, d + 1);
                    queue.add(next);
                }
            }
        }
        return dist;
    }

    /** 标准目标下某初始盘面的 BFS 最优步数；不可达返回 -1 */
    private static int bfsOptimalLength(int[] initial) {
        Integer d = standardTable().get(new PuzzleBoard(SIZE, initial));
        return d == null ? -1 : d;
    }

    private static int[] parseInts(String[] tokens, int from) {
        int[] a = new int[tokens.length - from];
        for (int i = from; i < tokens.length; i++) {
            a[i - from] = Integer.parseInt(tokens[i]);
        }
        return a;
    }

    private static String boardText(PuzzleBoard board) {
        StringBuilder sb = new StringBuilder();
        int[] a = board.toArray();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(a[i]);
        }
        return sb.toString();
    }

    private static void record(String caseId, int size, long seed, int walkSteps,
                               PuzzleBoard initial, PuzzleBoard goal, boolean solvable,
                               String algorithm, String heuristic, String status, int moves,
                               long wallMs, long cpuMs, int generated, int expanded) {
        CSV.append(caseId).append(',').append(size).append(',').append(seed).append(',')
           .append(walkSteps).append(",\"").append(boardText(initial)).append("\",\"")
           .append(boardText(goal)).append("\",").append(solvable).append(',')
           .append(algorithm).append(',').append(heuristic).append(',').append(status).append(',')
           .append(moves).append(',').append(wallMs).append(',').append(cpuMs).append(',')
           .append(generated).append(',').append(expanded).append('\n');
    }

    /** 断言：失败时计数并打印，不中断后续检查 */
    private static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.out.println("  [失败] " + message);
        }
    }

    /**
     * 断言数值相等。通过时静默，避免刷乱表格；失败时打印实际值与期望值。
     * 需要在报告里引用的数值，用 triple() 之类的显式打印展示。
     */
    private static void checkValue(long actual, long expected, String label) {
        if (actual != expected) {
            failures++;
            System.out.println("  [失败] " + label + "：实际 " + actual + "，期望 " + expected);
        }
    }

    /** 把某盘面对某目标的三个 h 值打成 "错位/曼哈顿/空位" 形式 */
    private static String triple(PuzzleBoard board, PuzzleBoard goal) {
        return board.misplaced(goal) + "/" + board.manhattan(goal) + "/" + board.blankDistance(goal);
    }

    private static void printEnvironment() {
        System.out.println("===== 环境 =====");
        System.out.println("JDK      : " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("OS       : " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
        System.out.println("CPU      : " + System.getenv("PROCESSOR_IDENTIFIER"));
        System.out.println("JVM 最大堆: " + (Runtime.getRuntime().maxMemory() >> 20) + " MiB");
        System.out.println("file.enc : " + System.getProperty("file.encoding"));
        System.out.println("cwd      : " + Paths.get("").toAbsolutePath());
    }
}
