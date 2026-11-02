package stud.g01.test;

import java.util.*;
import stud.g01.heuristic.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.PackedIdAStar;
import stud.queue.StackFrontier;

public final class PackedIdaStarTest {
    private static int checks;
    private static void check(boolean b,String text){checks++;if(!b)throw new AssertionError(text);}
    public static void main(String[] args){
        PuzzleBoard goal=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
        var db=DisjointPatternDatabase.build(goal,new int[]{1,2,3},new int[]{4,5,6},new int[]{7,8});
        var h=DisjointPatternDatabase.combinedPredictor(true,db);
        var solver=new PackedIdAStar(new StackFrontier(),h);
        int[][] cases={{1,2,3,4,5,6,7,8,0},{1,2,3,4,5,6,7,0,8},{1,2,0,4,5,3,7,8,6},{8,6,7,2,5,4,3,0,1}};
        int[] lengths={0,1,2,31};
        for(int capacity:new int[]{0,1,1024}){
            solver.setTranspositionCapacity(capacity);
            for(int i=0;i<cases.length;i++){
                var initial=new PuzzleBoard(3,cases[i]);
                var path=solver.search(new NPuzzleProblem(initial,goal));
                check(path!=null&&!solver.aborted(),"solved");
                DisjointPdbTest.validate(initial,goal,path,lengths[i]);
                check(path.size()==lengths[i],"independent known optimal length");
            }
        }
        solver.setTimeLimitMillis(0);
        check(solver.search(new NPuzzleProblem(new PuzzleBoard(3,cases[3]),goal))==null&&solver.aborted(),"zero budget");
        check(solver.lastThreshold()==0,"new aborted search resets old threshold");
        solver.setTimeLimitMillis(60000);
        check(solver.search(new NPuzzleProblem(new PuzzleBoard(3,new int[]{1,2,3,4,5,6,8,7,0}),goal))==null&&!solver.aborted(),"unsolvable versus timeout");
        Map<PuzzleBoard,Integer> exact=new HashMap<>();ArrayDeque<PuzzleBoard> queue=new ArrayDeque<>();
        exact.put(goal,0);queue.add(goal);
        while(!queue.isEmpty()){
            var board=queue.remove();int distance=exact.get(board);
            var path=solver.search(new NPuzzleProblem(board,goal));
            check(path!=null&&path.size()==distance,"BFS shallow independent oracle");
            if(distance<8)for(var direction:Direction.values())if(board.canMove(direction)){
                var child=board.move(direction);if(exact.putIfAbsent(child,distance+1)==null)queue.add(child);
            }
        }
        System.out.println("PASS packed IDA: "+checks+" checks including "+exact.size()+" exact BFS states");
    }
}
