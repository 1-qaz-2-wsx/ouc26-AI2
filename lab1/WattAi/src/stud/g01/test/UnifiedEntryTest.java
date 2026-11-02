package stud.g01.test;

import java.nio.file.*;
import java.util.*;
import core.solver.algorithm.heuristic.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.heuristic.pdb.CachedPdbPredictor;
import stud.g01.runner.LabRunner;
import stud.g01.util.LabPaths;

/** 集成契约：公共PDB、三种搜索、真实导出和失败状态。 */
public final class UnifiedEntryTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path root=LabPaths.labRoot().resolve(".local");
        Files.createDirectories(root);
        Path run=Files.createTempDirectory(root,"unified-test-");
        Path input=run.resolve("input.txt"),cache=run.resolve("cache");
        Files.write(input,List.of(
            "3 1 2 3 4 0 6 7 5 8 1 2 3 4 5 6 7 8 0",
            "4 1 2 3 4 5 6 7 8 9 10 0 12 13 14 11 15 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 0",
            "3 1 2 3 4 5 6 7 8 0 1 2 3 4 5 6 7 8 0",
            "3 2 1 3 4 5 6 7 8 0 1 2 3 4 5 6 7 8 0"));
        check(LabRunner.run(new String[]{"Solve","--input",input.toString(),"--algorithm","PACKED_IDA","--heuristic","PDB","--cache",cache.toString(),"--output",run.resolve("missing").toString()})!=0,"missing cache must fail");
        check(!Files.exists(cache),"solve must not build cache");
        check(LabRunner.run(new String[]{"Build","--input",input.toString(),"--cache",cache.toString()})==0,"offline build");
        for(String algorithm:List.of("ASTAR","IDA","PACKED_IDA")){
            Path output=run.resolve(algorithm);
            check(LabRunner.run(new String[]{"Solve","--input",input.toString(),"--algorithm",algorithm,"--heuristic","PDB","--cache",cache.toString(),"--output",output.toString()})==0,"solve "+algorithm);
            for(int i=1;i<=4;i++){
                String text=Files.readString(output.resolve("case-"+i+".txt"));
                check(text.startsWith("NP_PATH_V1\n"),"header");
                check(text.contains(i==4?"status UNSOLVABLE":"status SOLVED"),"status");
                check(text.contains(i<=2?"moves 2":i==3?"moves 0":"moves -1"),"length");
            }
            check(LabRunner.run(new String[]{"Solve","--input",input.toString(),"--algorithm",algorithm,"--heuristic","PDB","--cache",cache.toString(),"--output",output.toString()})!=0,"refuse stale mixed output");
        }
        String old=System.getProperty("npuzzle.pdb.dir");
        try{
            System.setProperty("npuzzle.pdb.dir",cache.toString());
            Predictor h=PuzzleHeuristics.predictor(HeuristicType.DISJOINT_PATTERN);
            PuzzleBoard goal=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
            check(h instanceof CachedPdbPredictor,"public factory supports PDB");
            check(h.heuristics(goal,goal)==0,"goal h");
            check(h.heuristics(goal.move(Direction.LEFT),goal)==1,"one step h");
            PuzzleBoard custom=goal.move(Direction.UP);
            check(LabRunner.run(new String[]{"Build","--input",writeCustom(run,custom).toString(),"--cache",cache.toString()})==0,"custom goal build");
            check(h.heuristics(custom,custom)==0,"same predictor switches custom goal");
            check(h.heuristics(goal,goal)==0,"switch back to previous target");
        }finally{if(old==null)System.clearProperty("npuzzle.pdb.dir");else System.setProperty("npuzzle.pdb.dir",old);}
        Path timeout=run.resolve("timeout");
        check(LabRunner.run(new String[]{"Solve","--input",input.toString(),"--algorithm","PACKED_IDA","--heuristic","PDB","--cache",cache.toString(),"--output",timeout.toString(),"--budget-ms","0"})!=0,"zero budget must fail");
        check(!Files.exists(timeout.resolve("case-1.txt")),"timeout must not export solved/unsolvable");
        check(Files.readString(timeout.resolve("results.csv")).contains("TIMEOUT"),"timeout in results");
        check(LabRunner.run(new String[]{"Solve","--algorithm","PACKED_IDA","--heuristic","MANHATTAN"})!=0,"reject unsupported fast path");
        check(LabRunner.run(new String[]{"Solve","--unknown","x"})!=0,"reject unknown option");
        check(LabRunner.run(new String[]{"Hard","--heuristic","MANHATTAN"})!=0,"hard rejects misleading options");
        System.out.println("PASS unified entry: "+checks+" checks");
    }
    private static Path writeCustom(Path run,PuzzleBoard goal)throws Exception{
        Path file=run.resolve("custom.txt");
        String line="3 "+Arrays.toString(goal.toArray()).replaceAll("[\\[\\],]","").trim();
        Files.writeString(file,line+" "+line.substring(2)+"\n");return file;
    }
}
