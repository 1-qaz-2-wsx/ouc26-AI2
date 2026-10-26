package stud.g01.test;

import core.solver.queue.EvaluationType;
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.Node;
import java.nio.file.*;
import java.util.*;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.heuristic.pdb.PatternDatabase;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.IdAStar;
import stud.queue.*;

/** 独立真实棋盘BFS与原型交叉检查；无需JUnit。 */
public final class DisjointPdbTest {
    private static long checks;
    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]).resolve("roundtrip-"+UUID.randomUUID());
        PuzzleBoard goal = new PuzzleBoard(3, new int[]{1,2,3,4,5,6,7,8,0});
        var db = DisjointPatternDatabase.build(goal, new int[]{1,2,3}, new int[]{4,5,6}, new int[]{7,8});
        var references = new PatternDatabase[]{PatternDatabase.build(goal,1,2,3),
                PatternDatabase.build(goal,4,5,6), PatternDatabase.build(goal,7,8)};
        check(db.estimate(goal) == 0, "goal");
        Map<PuzzleBoard,Integer> exact = new HashMap<>();
        ArrayDeque<PuzzleBoard> queue = new ArrayDeque<>();
        exact.put(goal,0); queue.add(goal);
        while (!queue.isEmpty()) {
            PuzzleBoard board = queue.remove();
            int distance = exact.get(board);
            int h = db.estimate(board);
            check(h <= distance, "admissibility");
            check(h == Arrays.stream(references).mapToInt(p -> p.estimate(board)).sum(), "independent encoding cross-check");
            for (Direction direction : Direction.values()) if (board.canMove(direction)) {
                PuzzleBoard child = board.move(direction);
                check(h <= 1 + db.estimate(child), "consistency");
                if (exact.putIfAbsent(child,distance+1) == null) queue.add(child);
            }
        }
        check(exact.size() == 181440, "full reachable 8-puzzle space");
        db.save(directory);
        var loaded = DisjointPatternDatabase.load(goal,directory);
        for (PuzzleBoard board : exact.keySet()) check(db.estimate(board)==loaded.estimate(board), "round trip");
        // A real file mutation must be rejected, not silently rebuilt.
        Path file;
        try (var files = Files.list(directory)) { file=files.filter(p -> p.toString().endsWith(".pdb")).findFirst().orElseThrow(); }
        byte[] bytes=Files.readAllBytes(file); bytes[bytes.length/2]^=1; Files.write(file,bytes);
        try { DisjointPatternDatabase.loadOrBuild(goal,directory); throw new AssertionError("corruption accepted"); }
        catch (java.io.IOException expected) { checks++; }
        try { db.predictor().heuristics(goal,goal.move(Direction.LEFT)); throw new AssertionError("wrong goal accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { DisjointPatternDatabase.build(goal,new int[]{1,2},new int[]{2,3}); throw new AssertionError("overlap accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        PuzzleBoard initial=new PuzzleBoard(3,new int[]{1,2,0,4,5,3,7,8,6});
        var problem=new NPuzzleProblem(initial,goal);
        var ida=new IdAStar(new StackFrontier(),loaded.predictor());
        validate(initial,goal,ida.search(problem),2);
        var astar=new BestFirstSearcher(new ListFrontier(Node.evaluator(EvaluationType.FULL)),loaded.predictor());
        validate(initial,goal,astar.search(problem),2);
        PuzzleBoard goal4=new PuzzleBoard(4,new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0});
        int[][] singles=new int[15][];
        for (int i=0;i<15;i++) singles[i]=new int[]{i+1};
        var small4=DisjointPatternDatabase.build(goal4,singles);
        check(small4.estimate(goal4.move(Direction.LEFT))==1,"4x4 position ranking");
        Path cache4=directory.resolve("formal4");
        small4.save(cache4);
        check(DisjointPatternDatabase.load(goal4,cache4,singles).estimate(goal4.move(Direction.LEFT))==1,"explicit custom layout reload");
        var formal4=DisjointPatternDatabase.loadOrBuild(goal4,cache4);
        check(formal4.distanceBytes()==17297280,"custom layout must not replace default5+5+5 cache");
        var reloaded4=DisjointPatternDatabase.load(goal4,cache4);
        Map<PuzzleBoard,Integer> shallow=new HashMap<>();
        ArrayDeque<PuzzleBoard> bfs4=new ArrayDeque<>();
        shallow.put(goal4,0);bfs4.add(goal4);
        while(!bfs4.isEmpty()){
            PuzzleBoard board=bfs4.remove();int distance=shallow.get(board);
            int h=formal4.estimate(board);
            check(h<=distance,"formal4 shallow admissibility");
            check(h>=board.manhattan(goal4),"formal4 dominates Manhattan");
            check(h==reloaded4.estimate(board),"formal4 reload equality");
            for(Direction d:Direction.values())if(board.canMove(d)){
                PuzzleBoard child=board.move(d);
                check(h<=1+formal4.estimate(child),"formal4 consistency");
                if(distance<9&&shallow.putIfAbsent(child,distance+1)==null)bfs4.add(child);
            }
        }
        Random random=new Random(20261026);PuzzleBoard walk=goal4;
        for(int i=0;i<10000;i++){
            var directions=Arrays.stream(Direction.values()).filter(walk::canMove).toList();
            PuzzleBoard child=walk.move(directions.get(random.nextInt(directions.size())));
            check(Math.abs(formal4.estimate(walk)-formal4.estimate(child))<=1,"formal4 random-edge consistency");
            check(formal4.estimate(child)>=child.manhattan(goal4),"formal4 random Manhattan bound");
            check(formal4.estimate(child)==reloaded4.estimate(child),"formal4 random reload");
            walk=child;
        }
        System.out.println("Formal4: "+shallow.size()+" exact shallow states plus 10000 seeded random edges");
        PuzzleBoard alternateGoal=goal.move(Direction.LEFT);
        var alternate=DisjointPatternDatabase.loadOrBuild(alternateGoal,directory.resolve("alternate"));
        check(alternate.estimate(alternateGoal)==0,"nonstandard goal zero");
        check(alternate.estimate(goal)==1,"nonstandard goal one move");
        check(DisjointPatternDatabase.load(alternateGoal,directory.resolve("alternate")).estimate(goal)==1,"nonstandard goal reload");
        System.out.println("ALL PASS: "+checks+" assertions; 181440 real states checked");
    }
    public static void validate(PuzzleBoard initial,PuzzleBoard goal,Deque<Node> path,int moves) {
        check(path!=null && path.size()==moves,"path length");
        PuzzleBoard previous=initial;
        for (Node node:path) {
            PuzzleBoard next=(PuzzleBoard)node.getState();
            boolean legal=false;
            for (Direction d:Direction.values()) if(previous.canMove(d)&&previous.move(d).equals(next)) legal=true;
            check(legal,"legal move"); previous=next;
        }
        check(previous.equals(goal),"path goal");
    }
}
