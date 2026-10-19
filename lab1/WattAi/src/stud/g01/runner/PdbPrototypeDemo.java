package stud.g01.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Deque;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.PatternDatabase;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

/** 第一阶段演示：生成 -> 保存 -> 加载 -> 查询 -> 接入一次简单搜索。 */
public final class PdbPrototypeDemo {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? ".local/pdb/group-123.pdb" : args[0]);
        PuzzleBoard goal = new PuzzleBoard(3, new int[]{1,2,3,4,5,6,7,8,0});
        long before = System.nanoTime();
        PatternDatabase generated = PatternDatabase.build(goal, 1,2,3);
        double buildMillis = (System.nanoTime() - before) / 1e6;
        before = System.nanoTime();
        generated.save(output);
        double saveMillis = (System.nanoTime() - before) / 1e6;
        before = System.nanoTime();
        PatternDatabase loaded = PatternDatabase.load(output);
        double loadMillis = (System.nanoTime() - before) / 1e6;

        System.out.println("Stage 1 PDB prototype: 3x3 only, not a 15-puzzle solver");
        System.out.println("JDK: " + System.getProperty("java.version"));
        System.out.println("Pattern: " + Arrays.toString(loaded.patternTiles()));
        System.out.println("Reachable abstract states: " + loaded.abstractStateCount());
        System.out.println("Database bytes: " + Files.size(output));
        System.out.printf("Build_ms=%.3f Save_ms=%.3f Load_ms=%.3f%n", buildMillis, saveMillis, loadMillis);
        printEstimate("goal", loaded, goal);
        printEstimate("untracked tile 8 moved", loaded,
                new PuzzleBoard(3, new int[]{1,2,3,4,5,6,7,0,8}));
        PuzzleBoard initial = new PuzzleBoard(3, new int[]{1,2,0,4,5,3,7,8,6});
        printEstimate("tile 3 moved, plus untracked tile 6", loaded, initial);

        IdAStar searcher = new IdAStar(new StackFrontier(), loaded.predictor());
        Deque<Node> path = searcher.search(new NPuzzleProblem(initial, goal));
        if (path == null || path.size() != 2 || !goal.equals(path.getLast().getState())) {
            throw new IllegalStateException("Known two-step integration case failed");
        }
        System.out.printf("IDA* with loaded PDB: moves=%d generated=%d expanded=%d%n",
                path.size(), searcher.nodesGenerated(), searcher.nodesExpanded());
        System.out.println("Database saved locally: " + output.toAbsolutePath());
        System.out.println("These single-run timings are a demo, not a final performance comparison.");
    }

    private static void printEstimate(String name, PatternDatabase pdb, PuzzleBoard board) {
        System.out.println(name + ": h=" + pdb.estimate(board));
    }
}
