package stud.g01.problem.npuzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 随机用例生成器：只负责"造出合法输入"，不负责判定可解性。
 * 一切随机都来自显式种子，同一 (size, seed, walkSteps) 必得同一组棋盘。
 */
public final class RandomPuzzleGenerator {

    private RandomPuzzleGenerator() {
    }

    /** 一次随机实验的完整输入，便于原样复现 */
    public static final class PuzzleCase {
        public final int size;
        public final long seed;
        public final int walkSteps;
        public final PuzzleBoard initial;
        public final PuzzleBoard goal;

        PuzzleCase(int size, long seed, int walkSteps, PuzzleBoard initial, PuzzleBoard goal) {
            this.size = size;
            this.seed = seed;
            this.walkSteps = walkSteps;
            this.initial = initial;
            this.goal = goal;
        }
    }

    /** 标准目标：[1,2,...,n^2-1,0] */
    public static PuzzleBoard standardGoal(int size) {
        int n = size * size;
        int[] a = new int[n];
        for (int i = 0; i < n - 1; i++) {
            a[i] = i + 1;
        }
        a[n - 1] = 0;
        return new PuzzleBoard(size, a);
    }

    /**
     * 可复现的随机合法目标：任意排列，含空格。
     * 不修正奇偶性——奇偶由 solvable() 判定，生成器不越界。
     */
    public static PuzzleBoard randomGoal(int size, long seed) {
        int n = size * size;
        int[] a = new int[n];
        for (int i = 0; i < n; i++) {
            a[i] = i;
        }
        Random rnd = new Random(mix(seed, 0x5DEECE66DL));
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
        return new PuzzleBoard(size, a);
    }

    /** 目标随机、初始由游走生成：二者必然可解 */
    public static PuzzleCase randomSolvablePair(int size, long seed, int walkSteps) {
        return solvablePairWithGoal(randomGoal(size, seed), seed, walkSteps);
    }

    /** 目标由调用方指定（例如标准目标），初始由游走生成：必然可解 */
    public static PuzzleCase solvablePairWithGoal(PuzzleBoard goal, long seed, int walkSteps) {
        PuzzleBoard initial = randomWalk(goal, walkSteps, new Random(mix(seed, walkSteps)));
        return new PuzzleCase(goal.size(), seed, walkSteps, initial, goal);
    }

    /**
     * 从 from 出发做 steps 步随机合法移动，禁止立即回退。
     * 注意：返回状态与 from 的最短距离 ≤ steps，不保证等于 steps（可能绕路或重访）。
     */
    public static PuzzleBoard randomWalk(PuzzleBoard from, int steps, Random rnd) {
        PuzzleBoard cur = from;
        Direction last = null;
        int moved = 0;
        while (moved < steps) {
            List<Direction> legal = new ArrayList<>(4);
            Direction back = opposite(last);
            for (Direction d : Direction.values()) {
                if (d != back && cur.canMove(d)) {
                    legal.add(d);
                }
            }
            if (legal.isEmpty()) {
                break;
            }
            Direction pick = legal.get(rnd.nextInt(legal.size()));
            cur = cur.move(pick);
            last = pick;
            moved++;
        }
        return cur;
    }

    /**
     * 由可解对构造不可解对：交换两个非空将牌，逆序数奇偶翻转、空位位置不变，
     * 因此与目标不在同一可达类。
     */
    public static PuzzleCase unsolvablePair(int size, long seed, int walkSteps) {
        PuzzleCase ok = randomSolvablePair(size, seed, walkSteps);
        int[] a = ok.initial.toArray();
        int i = -1;
        int j = -1;
        for (int k = 0; k < a.length; k++) {
            if (a[k] == 0) {
                continue;
            }
            if (i < 0) {
                i = k;
            } else {
                j = k;
                break;
            }
        }
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
        return new PuzzleCase(size, seed, walkSteps, new PuzzleBoard(size, a), ok.goal);
    }

    private static Direction opposite(Direction d) {
        if (d == null) {
            return null;
        }
        switch (d) {
            case UP:
                return Direction.DOWN;
            case DOWN:
                return Direction.UP;
            case LEFT:
                return Direction.RIGHT;
            case RIGHT:
                return Direction.LEFT;
            default:
                return null;
        }
    }

    /** 由 (seed, salt) 派生互不干扰的随机流，保证改 walkSteps 不改目标 */
    private static long mix(long seed, long salt) {
        long h = seed * 0x9E3779B97F4A7C15L + salt;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return h;
    }
}
