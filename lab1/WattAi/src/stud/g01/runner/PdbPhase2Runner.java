package stud.g01.runner;

import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.IdAStar;
import stud.g01.test.DisjointPdbTest;
import stud.queue.*;

/** 正式4×4表构建/重载/搜索实测。建表、加载与搜索分开计时，不修改搜索器。 */
public final class PdbPhase2Runner {
    private static final PuzzleBoard GOAL=new PuzzleBoard(4,new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0});
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        String mode=args.length>0?args[0]:"Demo";
        Path directory=Path.of(args.length>1?args[1]:".local/pdb-phase2/data");
        System.out.printf("date=%s java=%s os=%s arch=%s processors=%d max_heap_bytes=%d%n",
                java.time.ZonedDateTime.now(),System.getProperty("java.version"),System.getProperty("os.name"),
                System.getProperty("os.arch"),Runtime.getRuntime().availableProcessors(),Runtime.getRuntime().maxMemory());
        boolean existed=Files.exists(DisjointPatternDatabase.cachePath(GOAL,directory));
        long start=System.nanoTime();
        var pdb=DisjointPatternDatabase.loadOrBuild(GOAL,directory);
        System.out.printf("prepare_kind=%s prepare_wall_ms=%.3f distance_bytes=%d%n",existed?"LOAD_OR_CACHE":"BUILD_AND_SAVE",elapsed(start),pdb.distanceBytes());
        start=System.nanoTime();
        var loaded=DisjointPatternDatabase.load(GOAL,directory);
        System.out.printf("explicit_reload_wall_ms=%.3f goal_h=%d%n",elapsed(start),loaded.estimate(GOAL));
        if(mode.equalsIgnoreCase("Build"))return;
        if(mode.equalsIgnoreCase("Hard")){
            PuzzleBoard initial=new PuzzleBoard(4,new int[]{15,14,13,12,11,10,9,8,7,6,5,4,3,1,2,0});
            System.out.printf("case=specified-hard manhattan=%d pdb_h=%d time_limit_ms=60000%n",initial.manhattan(GOAL),loaded.estimate(initial));
            runIda(initial,loaded,60_000);
        }else{
            PuzzleBoard initial=GOAL.move(Direction.LEFT).move(Direction.UP).move(Direction.LEFT).move(Direction.UP);
            // 这4步每步都使曼哈顿增1，所以最优解长度恰为4，不以两算法相同替代最优性依据。
            if(initial.manhattan(GOAL)!=4)throw new AssertionError("fixture");
            long begin=System.nanoTime();
            var astar=new BestFirstSearcher(new ListFrontier(Node.evaluator(EvaluationType.FULL)),loaded.predictor());
            var path=astar.search(new NPuzzleProblem(initial,GOAL));
            double ms=elapsed(begin);
            DisjointPdbTest.validate(initial,GOAL,path,4);
            System.out.printf("algorithm=A* frontier=framework-ListFrontier moves=%d wall_ms=%.3f generated=%d expanded=%d%n",path.size(),ms,astar.nodesGenerated(),astar.nodesExpanded());
            runIda(initial,loaded,60_000);
        }
    }
    private static void runIda(PuzzleBoard initial,DisjointPatternDatabase pdb,long limit){
        var solver=new IdAStar(new StackFrontier(),pdb.predictor());
        solver.setTimeLimitMillis(limit);
        var timer=ManagementFactory.getThreadMXBean();
        boolean cpuAvailable=timer.isCurrentThreadCpuTimeSupported()&&timer.isThreadCpuTimeEnabled();
        long cpuBefore=cpuAvailable?timer.getCurrentThreadCpuTime():0;
        long begin=System.nanoTime();
        var path=solver.search(new NPuzzleProblem(initial,GOAL));
        double ms=elapsed(begin);
        String cpu=cpuAvailable?String.format(Locale.ROOT,"%.3f",(timer.getCurrentThreadCpuTime()-cpuBefore)/1e6):"N/A";
        String status=path!=null?"SOLVED":solver.aborted()?"TIMEOUT":"NO_SOLUTION";
        if(path!=null)DisjointPdbTest.validate(initial,GOAL,path,path.size());
        System.out.printf("algorithm=IDA* implementation=C-phase1 status=%s moves=%d wall_ms=%.3f cpu_time_ms=%s generated=%d expanded=%d threshold=%d target_60s=%s%n",
                status,path==null?-1:path.size(),ms,cpu,solver.nodesGenerated(),solver.nodesExpanded(),solver.lastThreshold(),path!=null&&ms<60_000?"PASS":"NOT_MET");
    }
    private static double elapsed(long before){return (System.nanoTime()-before)/1e6;}
}
