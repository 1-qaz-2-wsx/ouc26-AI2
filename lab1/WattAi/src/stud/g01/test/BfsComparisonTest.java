package stud.g01.test;

import stud.g01.runner.BfsComparisonRunner;
import stud.g01.problem.npuzzle.PuzzleBoard;

public final class BfsComparisonTest {
    public static void main(String[] args){
        PuzzleBoard goal=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
        int[][] fixtures={{1,2,3,4,5,6,7,8,0},{1,2,3,4,5,6,7,0,8},{1,2,0,4,5,3,7,8,6},{8,6,7,2,5,4,3,0,1}};
        int[] wanted={0,1,2,31};
        for(int i=0;i<fixtures.length;i++)for(boolean bidirectional:new boolean[]{false,true}){
            var result=BfsComparisonRunner.solve(new PuzzleBoard(3,fixtures[i]),goal,bidirectional);
            if(result.distance()!=wanted[i])throw new AssertionError("Wrong BFS distance: "+result);
        }
        System.out.println("PASS BFS/BiBFS: distances 0, 1, 2, 31");
    }
}
