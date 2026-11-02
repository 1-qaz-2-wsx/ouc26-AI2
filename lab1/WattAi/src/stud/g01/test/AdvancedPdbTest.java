package stud.g01.test;

import java.nio.file.*;
import java.util.*;
import stud.g01.heuristic.pdb.*;
import stud.g01.problem.npuzzle.*;

public final class AdvancedPdbTest {
    private static int checks;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path dir=Path.of(args[0]);
        PuzzleBoard goal=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
        int[][] groups={{1,2},{3,4},{5,6},{7,8}};
        var db=DisjointPatternDatabase.loadOrBuild(goal,dir,groups);
        var reloaded=DisjointPatternDatabase.load(goal,dir,groups);
        var second=DisjointPatternDatabase.build(goal,new int[]{1,2,3},new int[]{4,5,6},new int[]{7,8});
        var predictor=DisjointPatternDatabase.combinedPredictor(true,db,second);
        PuzzleBoard board=goal;
        var cursor=predictor.cursor(board);
        check(cursor.value()==0,"goal cursor");
        Random random=new Random(20261101);
        for(int i=0;i<10000;i++){
            var options=Arrays.stream(Direction.values()).filter(board::canMove).toList();
            var d=options.get(random.nextInt(options.size()));
            var child=board.move(d);int from=board.blankIndex(),to=child.blankIndex();
            int before=cursor.value();
            cursor.move(from,to,board.tileAt(to));
            check(cursor.value()==predictor.heuristics(child,goal),"incremental versus full query");
            check(Math.abs(before-cursor.value())<=1,"consistent max heuristic");
            check(db.estimate(child)==reloaded.estimate(child),"group reload");
            cursor.undo();check(cursor.value()==before,"undo");
            // Start a new cursor periodically so test history does not grow beyond an actual solution depth.
            board=child;cursor=predictor.cursor(board);
        }
        var pathCursor=predictor.cursor(goal);var child=goal.move(Direction.LEFT);
        pathCursor.move(goal.blankIndex(),child.blankIndex(),8);
        var grandchild=child.move(Direction.UP);
        pathCursor.move(child.blankIndex(),grandchild.blankIndex(),5);
        check(pathCursor.value()==predictor.heuristics(grandchild,goal),"nested moves");
        pathCursor.undo();check(pathCursor.value()==1,"nested undo");pathCursor.undo();check(pathCursor.value()==0,"root undo");
        var deepCursor=predictor.cursor(goal);ArrayList<PuzzleBoard> history=new ArrayList<>();history.add(goal);
        PuzzleBoard current=goal;
        for(int i=0;i<40;i++){
            var options=Arrays.stream(Direction.values()).filter(current::canMove).toList();
            var next=current.move(options.get(random.nextInt(options.size())));
            deepCursor.move(current.blankIndex(),next.blankIndex(),current.tileAt(next.blankIndex()));
            check(deepCursor.value()==predictor.heuristics(next,goal),"multi-database deep projection");
            history.add(next);current=next;
        }
        for(int i=39;i>=0;i--){deepCursor.undo();check(deepCursor.value()==predictor.heuristics(history.get(i),goal),"deep backtrack");}
        // 手工检查8个位置的32bit边界，不分配518MB大表。
        Class<?> type=Class.forName("stud.g01.heuristic.pdb.DensePatternTable");
        var ctor=type.getDeclaredConstructor(int.class,int[].class,boolean.class);ctor.setAccessible(true);
        Object metadata=ctor.newInstance(16,new int[]{1,2,3,4,5,6,7},false);
        var rank=type.getDeclaredMethod("rank",long.class);rank.setAccessible(true);
        check((int)rank.invoke(metadata,0x89ABCDEFL)==518918399,"signed 32bit largest partial permutation index");
        check((int)rank.invoke(metadata,0x76543210L)==0,"smallest partial permutation index");
        Path corrupt;
        try(var files=Files.list(dir)){corrupt=files.filter(p->p.getFileName().toString().startsWith("group-v3-")).findFirst().orElseThrow();}
        byte[] bytes=Files.readAllBytes(corrupt);bytes[bytes.length/2]^=1;Files.write(corrupt,bytes);
        try{DisjointPatternDatabase.loadOrBuild(goal,dir,groups);throw new AssertionError("corrupt cache silently rebuilt");}
        catch(java.io.IOException expected){checks++;}
        PuzzleBoard otherGoal=goal.move(Direction.UP);
        var other=DisjointPatternDatabase.build(otherGoal,groups);
        var noSym=DisjointPatternDatabase.combinedPredictor(true,other);
        check(noSym.heuristics(goal,otherGoal)==other.estimate(goal),"disable target-breaking symmetry");
        System.out.println("PASS advanced PDB: "+checks+" checks");
    }
}
