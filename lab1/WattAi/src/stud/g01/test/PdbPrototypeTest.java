package stud.g01.test;

import core.solver.algorithm.heuristic.Predictor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import stud.g01.heuristic.pdb.PatternDatabase;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

/** Standalone assertion-based tests. Failures terminate with a nonzero exit code. */
public final class PdbPrototypeTest {
    private static final int[] GOAL = {1,2,3,4,5,6,7,8,0};
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? ".local/pdb-test" : args[0]);
        Files.createDirectories(output);
        PuzzleBoard goal = board(GOAL);
        int[] pattern = {1,2,3};
        PatternDatabase pdb = PatternDatabase.build(goal, pattern);
        pattern[0] = 8;
        check(pdb.estimate(goal) == 0, "goal estimate is zero; pattern input is copied");
        check(pdb.abstractStateCount() == 3024, "all 9*8*7*6 abstract states are reached");
        check(pdb.estimate(board(1,2,3,4,5,6,7,0,8)) == 0,
                "moving an untracked tile has zero pattern cost");
        check(pdb.estimate(board(1,2,0,4,5,3,7,8,6)) == 1,
                "one tracked tile move costs one, despite two real moves");
        check(pdb.estimate(board(1,2,3,4,5,6,8,7,0)) == 0,
                "permuting untracked tiles leaves abstraction unchanged");
        int[] copy = pdb.patternTiles();
        copy[0] = 8;
        check(Arrays.equals(pdb.patternTiles(), new int[]{1,2,3}), "pattern metadata is immutable");
        check(PatternDatabase.build(goal, 3).abstractStateCount() == 72, "one-tile pattern covers 9*8 states");
        check(PatternDatabase.build(goal, 2,1).abstractStateCount() == 504, "two-tile pattern covers 9*8*7 states");
        check(PatternDatabase.build(goal, 3,1,2).estimate(board(1,2,0,4,5,3,7,8,6)) == 1,
                "pattern tile order does not change the cost definition");

        Map<String, OracleEntry> oracle = dijkstraOracle();
        check(oracle.size() == 3024, "independent Dijkstra covers complete abstract graph");
        Path file = output.resolve("nested/group-123.pdb");
        pdb.save(file);
        PatternDatabase loaded = PatternDatabase.load(file);
        for (OracleEntry entry : oracle.values()) {
            PuzzleBoard sample = representative(entry.cells);
            check(pdb.estimate(sample) == entry.distance, "0-1 BFS agrees with independent Dijkstra");
            check(loaded.estimate(sample) == entry.distance, "save/load preserves every abstract distance");
        }
        System.out.println("PASS: 3024 abstract distances match independent Dijkstra and disk reload");

        int reachable = checkFullPuzzleLowerBounds(pdb);
        check(reachable == 181440, "independent BFS covers the complete reachable 8-puzzle space");
        System.out.println("PASS: lower bound and cost-partition consistency on 181440 full states");

        Predictor predictor = loaded.predictor();
        check(predictor.heuristics(board(1,2,0,4,5,3,7,8,6), goal) == 1,
                "course Predictor interface can query the PDB");
        IdAStar searcher = new IdAStar(new StackFrontier(), predictor);
        NPuzzleProblem simple = new NPuzzleProblem(board(1,2,0,4,5,3,7,8,6), goal);
        java.util.Deque<core.solver.queue.Node> solution = searcher.search(simple);
        check(solution != null && solution.size() == 2, "loaded PDB integrates with existing IDA* on known two-step case");
        check(goal.equals(solution.getLast().getState()), "integrated search ends at requested goal");
        PuzzleBoard otherGoal = board(1,2,3,4,5,6,7,0,8);
        expectIllegal(() -> predictor.heuristics(goal, otherGoal), "reject mismatched database goal");
        PatternDatabase other = PatternDatabase.build(otherGoal, 1,2,3);
        check(other.estimate(otherGoal) == 0, "nonstandard goal builds correctly");
        other.save(output.resolve("other-goal.pdb"));
        check(PatternDatabase.load(output.resolve("other-goal.pdb")).goalBoard().equals(otherGoal),
                "nonstandard goal is saved and loaded");
        expectIllegal(() -> PatternDatabase.build(goal, 1,1,2), "reject duplicate pattern tiles");
        expectIllegal(() -> PatternDatabase.build(goal, 0,1), "blank is not a pattern tile");
        expectIllegal(() -> PatternDatabase.build(goal), "reject empty pattern");
        expectIllegal(() -> PatternDatabase.build(goal, 1,2,3,4), "prototype explicitly limits pattern size");
        expectIllegal(() -> PatternDatabase.build(goal, 9), "reject invalid tile");
        PuzzleBoard goal4 = new PuzzleBoard(4, new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0});
        expectIllegal(() -> PatternDatabase.build(goal4, 1,2,3), "prototype explicitly rejects 15-puzzle");
        expectIllegal(() -> pdb.estimate(goal4), "reject incompatible query size");

        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 2] ^= 1;
        Path damaged = output.resolve("damaged.pdb");
        Files.write(damaged, bytes);
        expectIo(() -> PatternDatabase.load(damaged), "reject corrupted database");
        Files.write(damaged, new byte[]{1,2,3});
        expectIo(() -> PatternDatabase.load(damaged), "reject truncated database");
        System.out.println("ALL PASS: " + checks + " assertions; output=" + output.toAbsolutePath());
    }

    // Independent oracle: explicit abstract board strings + PriorityQueue Dijkstra.
    // Production uses packed positions + deque, so neither codec nor search is reused.
    private record OracleEntry(int[] cells, int distance) { }

    private static Map<String, OracleEntry> dijkstraOracle() {
        int[] abstractGoal = {1,2,3,-1,-1,-1,-1,-1,0};
        PriorityQueue<OracleEntry> queue = new PriorityQueue<>(
                java.util.Comparator.comparingInt(OracleEntry::distance));
        Map<String, OracleEntry> best = new HashMap<>();
        OracleEntry root = new OracleEntry(abstractGoal, 0);
        best.put(Arrays.toString(abstractGoal), root);
        queue.add(root);
        while (!queue.isEmpty()) {
            OracleEntry entry = queue.remove();
            if (best.get(Arrays.toString(entry.cells)) != entry) continue;
            int blank = indexOf(entry.cells, 0);
            for (int neighbor : neighbors(blank)) {
                int cost = entry.cells[neighbor] == -1 ? 0 : 1;
                int[] next = swap(entry.cells, blank, neighbor);
                String key = Arrays.toString(next);
                int distance = entry.distance + cost;
                OracleEntry previous = best.get(key);
                if (previous == null || distance < previous.distance) {
                    OracleEntry improved = new OracleEntry(next, distance);
                    best.put(key, improved);
                    queue.add(improved);
                }
            }
        }
        return best;
    }

    private static PuzzleBoard representative(int[] cells) {
        int[] full = cells.clone();
        int tile = 4;
        for (int i = 0; i < full.length; i++) if (full[i] == -1) full[i] = tile++;
        return board(full);
    }

    // Independent full-board BFS computes true shortest solution lengths.
    private static int checkFullPuzzleLowerBounds(PatternDatabase pdb) {
        Map<Long, Integer> distances = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long root = pack(GOAL);
        distances.put(root, 0);
        queue.add(root);
        while (!queue.isEmpty()) {
            long key = queue.remove();
            int depth = distances.get(key);
            int[] cells = unpack(key);
            int h = pdb.estimate(board(cells));
            check(h >= 0 && h <= depth, "PDB never exceeds true shortest distance");
            int blank = indexOf(cells, 0);
            for (int neighbor : neighbors(blank)) {
                int moved = cells[neighbor];
                int[] next = swap(cells, blank, neighbor);
                int cost = moved >= 1 && moved <= 3 ? 1 : 0;
                check(h <= cost + pdb.estimate(board(next)), "PDB is consistent with partitioned move costs");
                long nextKey = pack(next);
                if (!distances.containsKey(nextKey)) {
                    distances.put(nextKey, depth + 1);
                    queue.add(nextKey);
                }
            }
        }
        return distances.size();
    }

    private static int[] neighbors(int position) {
        int[] buffer = new int[4];
        int count = 0;
        if (position / 3 > 0) buffer[count++] = position - 3;
        if (position / 3 < 2) buffer[count++] = position + 3;
        if (position % 3 > 0) buffer[count++] = position - 1;
        if (position % 3 < 2) buffer[count++] = position + 1;
        return Arrays.copyOf(buffer, count);
    }

    private static int[] swap(int[] cells, int blank, int neighbor) {
        int[] next = cells.clone();
        next[blank] = next[neighbor];
        next[neighbor] = 0;
        return next;
    }

    private static int indexOf(int[] cells, int value) {
        for (int i = 0; i < cells.length; i++) if (cells[i] == value) return i;
        throw new AssertionError("value missing in test fixture");
    }

    private static long pack(int[] cells) {
        long result = 0;
        for (int cell : cells) result = (result << 4) | cell;
        return result;
    }

    private static int[] unpack(long packed) {
        int[] cells = new int[9];
        for (int i = 8; i >= 0; i--) { cells[i] = (int)(packed & 15); packed >>>= 4; }
        return cells;
    }

    private static PuzzleBoard board(int... tiles) { return new PuzzleBoard(3, tiles); }
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static void expectIllegal(Runnable action, String description) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(description);
    }
    private interface IoAction { void run() throws IOException; }
    private static void expectIo(IoAction action, String description) throws IOException {
        try { action.run(); } catch (IOException expected) { checks++; return; }
        throw new AssertionError(description);
    }
}
