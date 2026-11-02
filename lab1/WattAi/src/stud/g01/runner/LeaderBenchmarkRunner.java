package stud.g01.runner;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.PackedIdAStar;
import stud.queue.StackFrontier;

/** Hard入口只加载，不建表。正式时间包含加载、恢复和独立路径验证。 */
public final class LeaderBenchmarkRunner {
    private static final PuzzleBoard GOAL=new PuzzleBoard(4,new int[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,0});
    private static final PuzzleBoard HARD=new PuzzleBoard(4,new int[]{15,14,13,12,11,10,9,8,7,6,5,4,3,1,2,0});
    public static int[][] groups(String preset){
        return switch(preset){
            case "555"->new int[][]{{1,2,3,4,5},{6,7,8,9,10},{11,12,13,14,15}};
            case "663"->new int[][]{{1,2,5,6,9,10},{3,4,7,8,11,12},{13,14,15}};
            case "762"->new int[][]{{1,2,3,5,6,7,9},{4,8,10,11,12,15},{13,14}};
            case "771"->new int[][]{{1,2,3,5,6,7,9},{4,8,10,11,12,14,15},{13}};
            default->throw new IllegalArgumentException("Unknown PDB preset: "+preset);
        };
    }
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.ROOT);
        if(Runtime.getRuntime().maxMemory()>2L*1024*1024*1024)throw new IllegalStateException("Use -Xmx2g or less");
        String mode=args.length>0?args[0]:"Hard",preset=args.length>1?args[1]:"555";
        Path directory=Path.of(args.length>2?args[2]:".local/leader-pdb");
        int capacity=args.length>3?Integer.parseInt(args[3]):0;
        long budgetMillis=args.length>4?Long.parseLong(args[4]):60000;
        var os=ManagementFactory.getOperatingSystemMXBean();
        System.out.printf("date=%s java=%s os=%s arch=%s processors=%d max_heap_bytes=%d mode=%s preset=%s tt_capacity=%d%n",
                java.time.ZonedDateTime.now(),System.getProperty("java.version"),System.getProperty("os.name"),System.getProperty("os.arch"),
                os.getAvailableProcessors(),Runtime.getRuntime().maxMemory(),mode,preset,capacity);
        System.out.printf("revision=%s dirty=%s source_sha256=%s%n",System.getProperty("experiment.revision","UNKNOWN"),System.getProperty("experiment.dirty","UNKNOWN"),System.getProperty("experiment.source_sha256","UNKNOWN"));
        if(mode.equalsIgnoreCase("Build")){
            if(!preset.equals("555")&&os instanceof com.sun.management.OperatingSystemMXBean physical){
                long available=physical.getFreeMemorySize();
                System.out.println("free_physical_bytes="+available);
                if(available<3L*1024*1024*1024)throw new IllegalStateException("Need at least 3 GiB free RAM before large PDB build; no applications were closed");
            }
            long begin=System.nanoTime();
            var baseline=DisjointPatternDatabase.loadOrBuild(GOAL,directory);
            var candidate=preset.equals("555")?baseline:DisjointPatternDatabase.loadOrBuild(GOAL,directory,groups(preset));
            System.out.printf("build_and_save_ms=%.3f candidate_distance_bytes=%d baseline_distance_bytes=%d h=%d%n",
                    elapsed(begin),candidate.distanceBytes(),baseline.distanceBytes(),DisjointPatternDatabase.combinedPredictor(true,baseline,candidate).heuristics(HARD,GOAL));
            return;
        }
        for(var pool:ManagementFactory.getMemoryPoolMXBeans())if(pool.getType()==java.lang.management.MemoryType.HEAP)pool.resetPeakUsage();
        long started=System.nanoTime(),deadline=started+budgetMillis*1_000_000L;
        var baseline=DisjointPatternDatabase.load(GOAL,directory);
        var candidate=preset.equals("555")?baseline:DisjointPatternDatabase.load(GOAL,directory,groups(preset));
        var predictor=preset.equals("555")?DisjointPatternDatabase.combinedPredictor(true,baseline):DisjointPatternDatabase.combinedPredictor(true,baseline,candidate);
        double loadMs=elapsed(started);
        long heapAfterLoad=ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        if(mode.equalsIgnoreCase("Query")){
            query(predictor);return;
        }
        if(!mode.equalsIgnoreCase("Hard"))throw new IllegalArgumentException("Expected Build, Hard or Query");
        var solver=new PackedIdAStar(new StackFrontier(),predictor);solver.setDeadlineNanos(deadline);solver.setTimeLimitMillis(budgetMillis);solver.setTranspositionCapacity(capacity);
        var cpu=ManagementFactory.getThreadMXBean();boolean cpuAvailable=cpu.isCurrentThreadCpuTimeSupported()&&cpu.isThreadCpuTimeEnabled();
        long cpuBefore=cpuAvailable?cpu.getCurrentThreadCpuTime():0,searchStarted=System.nanoTime();
        var path=solver.search(new NPuzzleProblem(HARD,GOAL));double searchMs=elapsed(searchStarted);
        long validationStarted=System.nanoTime();boolean legal=path!=null&&validPath(HARD,GOAL,path);
        double validationMs=elapsed(validationStarted),totalMs=elapsed(started);
        boolean success=legal&&totalMs<=budgetMillis;
        String status=path!=null?(legal?"SOLVED":"INVALID_PATH"):solver.aborted()?"TIMEOUT":"SEARCH_EXHAUSTED";
        String cpuMs=cpuAvailable?String.format(Locale.ROOT,"%.3f",(cpu.getCurrentThreadCpuTime()-cpuBefore)/1e6):"N/A";
        System.out.printf("preset=%s status=%s h=%d moves=%d load_ms=%.3f search_ms=%.3f validation_ms=%.3f total_ms=%.3f cpu_ms=%s generated=%d expanded=%d threshold=%d iterations=%d tt_capacity=%d legal=%s budget_ms=%d target=%s%n",
                preset,status,predictor.heuristics(HARD,GOAL),path==null?-1:path.size(),loadMs,searchMs,validationMs,totalMs,cpuMs,
                solver.generatedCount(),solver.expandedCount(),solver.lastThreshold(),solver.iterations(),capacity,legal,budgetMillis,success?"PASS":"NOT_MET");
        long sumPeaks=0;for(var pool:ManagementFactory.getMemoryPoolMXBeans())if(pool.getType()==java.lang.management.MemoryType.HEAP)sumPeaks+=pool.getPeakUsage().getUsed();
        System.out.printf("candidate_distance_bytes=%d baseline_distance_bytes=%d heap_after_load_bytes=%d sum_heap_pool_peak_bytes=%d tt_array_payload_bytes=%d memory_note=pool-peaks-are-not-process-RSS%n",candidate.distanceBytes(),baseline.distanceBytes(),heapAfterLoad,sumPeaks,capacity*13L);
        if(legal)SolutionPathWriter.write(directory.resolve("hard-"+preset+".txt"),new NPuzzleProblem(HARD,GOAL),path);
        if(!success)System.exit(2);
    }
    public static boolean validPath(PuzzleBoard initial,PuzzleBoard goal,Deque<Node> path){
        PuzzleBoard current=initial;int cost=0;
        for(Node node:path){
            if(!(node.getAction() instanceof PuzzleMove move)||!current.canMove(move.direction()))return false;
            current=current.move(move.direction());if(!current.equals(node.getState())||node.getPathCost()!=++cost)return false;
        }
        return current.equals(goal);
    }
    private static void query(PackedPuzzleHeuristic predictor){
        PuzzleBoard current=HARD;ArrayList<PuzzleBoard> corpus=new ArrayList<>();Random random=new Random(20261101);
        for(int i=0;i<4096;i++){var options=Arrays.stream(Direction.values()).filter(current::canMove).toList();current=current.move(options.get(random.nextInt(options.size())));corpus.add(current);}
        long checksum=0;for(int round=0;round<3;round++)for(var board:corpus)checksum+=predictor.heuristics(board,GOAL);
        double[] timings=new double[5];
        for(int round=0;round<timings.length;round++){long start=System.nanoTime();for(int repeat=0;repeat<20;repeat++)for(var board:corpus)checksum+=predictor.heuristics(board,GOAL);timings[round]=elapsed(start);}
        Arrays.sort(timings);System.out.printf("query_count=%d median_ms=%.3f ns_per_query=%.3f checksum=%d scope=full-Predictor-query-not-incremental%n",4096*20,timings[2],timings[2]*1e6/(4096*20),checksum);
    }
    private static double elapsed(long start){return (System.nanoTime()-start)/1e6;}
}
