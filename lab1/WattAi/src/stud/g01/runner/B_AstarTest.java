package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import java.util.ArrayList;
import java.util.Deque;

public class B_AstarTest {
    public static void main(String[] args) throws Exception {
        ArrayList<String> lines = new ArrayList<>();
        java.nio.file.Files.lines(java.nio.file.Paths.get("resources/test_8puzzle.txt"))
                .forEach(lines::add);

        EngineFeeder feeder = new PuzzleFeeder();
        ArrayList<Problem> problems = feeder.getProblems(lines);

        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            System.out.println("=== Case " + (i+1) + " ===");

            AbstractSearcher searcher = feeder.getAStar(core.solver.algorithm.heuristic.HeuristicType.MANHATTAN);

            long start = System.nanoTime();
            Deque<Node> path = searcher.search(problem);
            long end = System.nanoTime();

            if (path == null) {
                System.out.println("Status: UNSOLVABLE");
            } else {
                System.out.println("Status: SOLVED");
                System.out.println("Moves: " + path.size());
                System.out.println("Wall Time (ms): " + (end - start) / 1_000_000.0);
            }
        }
    }
}