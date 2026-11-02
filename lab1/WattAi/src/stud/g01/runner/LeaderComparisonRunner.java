package stud.g01.runner;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import core.solver.algorithm.heuristic.*;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.*;
import stud.queue.StackFrontier;

/** 同机同题对照，预热2次、记录5次；PDB初始化在搜索计时之外。 */
public final class LeaderComparisonRunner {
    private record Sample(int moves,double ms,long generated,long expanded){}
    private static final PuzzleBoard GOAL=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
    private static final int[][] CASES={{8,6,7,2,5,4,3,0,1},{6,4,7,8,5,0,3,2,1}};
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.ROOT);
        Path output=Path.of(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());
        System.out.printf("java=%s revision=%s dirty=%s source_sha256=%s max_heap_bytes=%d%n",System.getProperty("java.version"),System.getProperty("experiment.revision","UNKNOWN"),System.getProperty("experiment.dirty","UNKNOWN"),System.getProperty("experiment.source_sha256","UNKNOWN"),Runtime.getRuntime().maxMemory());
        var db=DisjointPatternDatabase.build(GOAL,new int[]{1,2,3},new int[]{4,5,6},new int[]{7,8});
        Predictor manhattan=PuzzleHeuristics.predictor(HeuristicType.MANHATTAN);
        var packed=DisjointPatternDatabase.combinedPredictor(true,db);
        try(var out=Files.newBufferedWriter(output)){
            out.write("category,case,engine,mode,heuristic,repeat,moves,wall_ms,generated,expanded,path_valid\n");
            double best=Double.POSITIVE_INFINITY;Phase2AStar.SearchMode bestMode=null;
            for(var mode:Phase2AStar.SearchMode.values()){
                double sum=0;
                for(int c=0;c<CASES.length;c++)sum+=runs(out,"OPEN_CLOSED",c,"ASTAR",mode,"MANHATTAN",manhattan,packed);
                if(sum<best){best=sum;bestMode=mode;}
            }
            System.out.printf("selected_astar_mode=%s selection=sum-of-two-Manhattan-medians score_ms=%.3f%n",bestMode,best);
            for(int c=0;c<CASES.length;c++){
                runs(out,"HEURISTIC",c,"ASTAR",bestMode,"MISPLACED",PuzzleHeuristics.predictor(HeuristicType.MISPLACED),packed);
                runs(out,"HEURISTIC",c,"ASTAR",bestMode,"MANHATTAN",manhattan,packed);
                runs(out,"HEURISTIC",c,"ASTAR",bestMode,"PDB",db.predictor(),packed);
                runs(out,"ENGINE",c,"IDA_GENERIC",bestMode,"MANHATTAN",manhattan,packed);
                runs(out,"ENGINE",c,"IDA_GENERIC",bestMode,"PDB",db.predictor(),packed);
                runs(out,"ENGINE",c,"IDA_PACKED",bestMode,"PDB_SYMMETRY",packed,packed);
            }
        }
        System.out.println("CSV: "+output.toAbsolutePath());
        System.out.println("Generated convention: ASTAR/IDA_GENERIC includes reverse successors; IDA_PACKED excludes immediate reverse before generation.");
    }
    private static double runs(BufferedWriter out,String category,int caseIndex,String engine,Phase2AStar.SearchMode mode,String hName,Predictor h,PackedPuzzleHeuristic packed)throws Exception{
        var initial=new PuzzleBoard(3,CASES[caseIndex]);var problem=new NPuzzleProblem(initial,GOAL);
        ArrayList<Sample> recorded=new ArrayList<>();
        for(int run=-2;run<5;run++){
            Sample s;
            if(engine.equals("ASTAR")){
                var result=new Phase2AStar(h,mode).search(problem);
                validate(initial,result.path(),result.solutionLength());
                s=new Sample(result.solutionLength(),result.elapsedMillis(),result.generated(),result.expanded());
            }else if(engine.equals("IDA_GENERIC")){
                var solver=new IdAStar(new StackFrontier(),h);solver.setTimeLimitMillis(60000);
                long start=System.nanoTime();var path=solver.search(problem);double ms=(System.nanoTime()-start)/1e6;
                validate(initial,path,path==null?-1:path.size());s=new Sample(path.size(),ms,solver.generatedCount(),solver.expandedCount());
            }else{
                var solver=new PackedIdAStar(new StackFrontier(),packed);solver.setTranspositionCapacity(1048576);
                long start=System.nanoTime();var path=solver.search(problem);double ms=(System.nanoTime()-start)/1e6;
                validate(initial,path,path==null?-1:path.size());s=new Sample(path.size(),ms,solver.generatedCount(),solver.expandedCount());
            }
            if(run<0)continue;
            recorded.add(s);out.write(String.format(Locale.ROOT,"%s,case-%d,%s,%s,%s,%d,%d,%.6f,%d,%d,true%n",category,caseIndex+1,engine,mode,hName,run+1,s.moves,s.ms,s.generated,s.expanded));
        }
        recorded.sort(Comparator.comparingDouble(Sample::ms));Sample median=recorded.get(2);
        System.out.printf("category=%s case=%d engine=%s mode=%s h=%s median_ms=%.3f moves=%d generated=%d expanded=%d%n",category,caseIndex+1,engine,mode,hName,median.ms,median.moves,median.generated,median.expanded);
        out.flush();return median.ms;
    }
    private static void validate(PuzzleBoard initial,Deque<Node> path,int moves){
        if(path==null||moves!=31||!LeaderBenchmarkRunner.validPath(initial,GOAL,path))throw new AssertionError("Expected legal optimal 31-step reference case");
    }
}
