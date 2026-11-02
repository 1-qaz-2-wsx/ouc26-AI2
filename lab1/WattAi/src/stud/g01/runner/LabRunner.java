package stud.g01.runner;

import core.solver.algorithm.heuristic.*;
import core.solver.queue.Node;
import stud.g01.heuristic.pdb.*;
import stud.g01.problem.npuzzle.*;
import stud.g01.solver.*;
import stud.g01.util.LabPaths;
import stud.queue.StackFrontier;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 最终联调总入口。课程core、成员搜索器和正式Hard验收算法保持独立。 */
public final class LabRunner {
    private LabRunner(){}
    private static final Set<String> ALL=Set.of("input","cache","output","algorithm","heuristic","budget-ms","tt","astar-mode");
    public static void main(String[] args){int exit=run(args);if(exit!=0)System.exit(exit);}
    public static int run(String[] args){
        try{
            String command=args.length==0?"HELP":args[0].toUpperCase(Locale.ROOT);
            Map<String,String> options=new LinkedHashMap<>();
            for(int i=1;i<args.length;i+=2){
                if(!args[i].startsWith("--")||i+1>=args.length)throw new IllegalArgumentException("参数需为 --名称 值");
                String name=args[i].substring(2);
                if(!ALL.contains(name)||options.putIfAbsent(name,args[i+1])!=null)throw new IllegalArgumentException("未知或重复参数: "+args[i]);
            }
            Set<String> allowed=switch(command){
                case "BUILD"->Set.of("input","cache");
                case "SOLVE"->ALL;
                case "HARD","QUERY"->Set.of("cache");
                case "VERIFY","COMPARE","HELP"->Set.of();
                default->throw new IllegalArgumentException("未知命令: "+command);
            };
            if(!allowed.containsAll(options.keySet()))throw new IllegalArgumentException(command+"不接受这些参数: "+options.keySet());
            if(command.equals("HELP")){
                System.out.println("LabRunner Verify | Build | Solve | Hard | Compare | Query");
                System.out.println("Solve --algorithm ASTAR|IDA|PACKED_IDA --heuristic MISPLACED|MANHATTAN|PDB --input 文件 --output 新目录");
                System.out.println("Build --input 文件 --cache 目录；正式Hard固定555+对称/完整身份TT/60秒，只接受--cache。");
                return 0;
            }
            if(Runtime.version().feature()!=25)throw new IllegalArgumentException("正式测试使用JDK25");
            if(Runtime.getRuntime().maxMemory()>2L*1024*1024*1024)throw new IllegalArgumentException("请在VM options设置 -Xmx2g");
            Path lab=LabPaths.labRoot(),cache=Path.of(options.getOrDefault("cache",LabPaths.pdbDirectory().toString())).toAbsolutePath().normalize();
            System.out.printf("command=%s lab=%s java=%s max_heap_bytes=%d revision=%s source_sha256=%s%n",
                command,lab,System.getProperty("java.version"),Runtime.getRuntime().maxMemory(),
                System.getProperty("experiment.revision","UNRECORDED"),System.getProperty("experiment.source_sha256","UNRECORDED"));
            switch(command){
                case "BUILD"->build(input(options,lab),cache);
                case "SOLVE"->{return solve(input(options,lab),cache,options,lab);}
                case "HARD"->{
                    LeaderBenchmarkRunner.main(new String[]{"Hard","555",cache.toString(),"1048576","60000"});
                    Path output=newOutput(lab,"hard-player");
                    Files.copy(cache.resolve("hard-555.txt"),output.resolve("case-1-hard.txt"));
                    System.out.println("PLAYER_INPUT="+output);
                }
                case "QUERY"->LeaderBenchmarkRunner.main(new String[]{"Query","555",cache.toString(),"0","60000"});
                case "COMPARE"->LeaderComparisonRunner.main(new String[]{newOutput(lab,"comparison").resolve("comparison.csv").toString()});
                case "VERIFY"->verify(lab);
                default->throw new AssertionError(command);
            }
            return 0;
        }catch(Exception ex){
            System.err.println("ERROR: "+ex.getClass().getSimpleName()+": "+ex.getMessage());
            return 2;
        }
    }
    private static Path input(Map<String,String> options,Path lab){
        return Path.of(options.getOrDefault("input",lab.resolve("WattAi/resources/final-smoke.txt").toString()));
    }
    private static ArrayList<core.problem.Problem> problems(Path input)throws IOException{
        var lines=new ArrayList<>(Files.readAllLines(input));
        if(!lines.isEmpty())lines.set(0,lines.get(0).replaceFirst("^\\uFEFF",""));
        var result=new PuzzleFeeder().getProblems(lines);
        if(result.isEmpty())throw new IllegalArgumentException("输入文件没有问题实例");
        return result;
    }
    private static void build(Path input,Path cache)throws IOException{
        Set<PuzzleBoard> targets=new HashSet<>();
        for(var item:problems(input)){
            var problem=(NPuzzleProblem)item;
            if(!targets.add(problem.goalBoard()))continue;
            long begin=System.nanoTime();var db=DisjointPatternDatabase.loadOrBuild(problem.goalBoard(),cache);
            System.out.printf("pdb_size=%d prepare_ms=%.3f distance_bytes=%d goal_h=%d%n",
                problem.goalBoard().size(),ms(begin),db.distanceBytes(),db.estimate(problem.goalBoard()));
        }
        System.out.println("PDB准备完成，目录="+cache);
    }
    private static Path newOutput(Path lab,String label)throws IOException{
        Path parent=lab.resolve(".local/final-tests");
        Files.createDirectories(parent);
        String time=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return Files.createTempDirectory(parent,time+"-"+label+"-");
    }
    private static final class BudgetExceeded extends RuntimeException { }
    private record Result(String status,Deque<Node> path,double prepare,double search,double validate,double total,
                          String cpu,long generated,long expanded){}
    private static int solve(Path input,Path cache,Map<String,String> options,Path lab)throws Exception{
        String algorithm=options.getOrDefault("algorithm","ASTAR").toUpperCase(Locale.ROOT);
        String heuristic=options.getOrDefault("heuristic","MANHATTAN").toUpperCase(Locale.ROOT);
        if(!Set.of("ASTAR","IDA","PACKED_IDA").contains(algorithm))throw new IllegalArgumentException("无效algorithm");
        if(!Set.of("MISPLACED","MANHATTAN","PDB").contains(heuristic))throw new IllegalArgumentException("无效heuristic");
        if(algorithm.equals("PACKED_IDA")&&!heuristic.equals("PDB"))throw new IllegalArgumentException("紧凑IDA须搭配PDB；普通启发函数用IDA");
        long budget=Long.parseLong(options.getOrDefault("budget-ms","60000"));
        if(budget<0||budget>3600000)throw new IllegalArgumentException("budget-ms范围0至3600000");
        int capacity=Integer.parseInt(options.getOrDefault("tt","1048576"));
        if(capacity<0||(capacity!=0&&Integer.bitCount(capacity)!=1)||capacity>1048576)throw new IllegalArgumentException("tt须为0或不超过1048576的2次幂");
        Phase2AStar.SearchMode astarMode=Phase2AStar.SearchMode.valueOf(options.getOrDefault("astar-mode","BASELINE").toUpperCase(Locale.ROOT));
        if(!algorithm.equals("ASTAR")&&options.containsKey("astar-mode"))throw new IllegalArgumentException("astar-mode仅适用于ASTAR");
        if(algorithm.equals("ASTAR")&&options.containsKey("tt"))throw new IllegalArgumentException("tt仅适用于IDA系列");
        var cases=problems(input);
        Path output=options.containsKey("output")?Path.of(options.get("output")).toAbsolutePath():newOutput(lab,algorithm+"-"+heuristic);
        if(Files.exists(output)){try(var files=Files.list(output)){if(files.findAny().isPresent())throw new IllegalArgumentException("输出目录非空，拒绝混入旧路径: "+output);}}
        Files.createDirectories(output);boolean all=true;
        try(var csv=Files.newBufferedWriter(output.resolve("results.csv"))){
            csv.write("case_id,size,algorithm,heuristic,astar_mode,budget_ms,tt_capacity,status,moves,prepare_ms,search_ms,validation_ms,total_ms,cpu_ms,generated,expanded,initial,goal\n");
            for(int i=0;i<cases.size();i++){
                var problem=(NPuzzleProblem)cases.get(i);
                Result r=solveOne(problem,cache,algorithm,heuristic,astarMode,budget,capacity);
                boolean export=r.status.equals("SOLVED")||r.status.equals("UNSOLVABLE");
                if(export)SolutionPathWriter.write(output.resolve("case-"+(i+1)+".txt"),problem,r.path);
                else all=false;
                csv.write(String.format(Locale.ROOT,"case-%d,%d,%s,%s,%s,%d,%d,%s,%d,%.3f,%.3f,%.3f,%.3f,%s,%d,%d,\"%s\",\"%s\"%n",
                    i+1,problem.initialBoard().size(),algorithm,heuristic,algorithm.equals("ASTAR")?astarMode:"N/A",budget,algorithm.equals("ASTAR")?0:capacity,r.status,r.path==null?-1:r.path.size(),
                    r.prepare,r.search,r.validate,r.total,r.cpu,r.generated,r.expanded,tiles(problem.initialBoard()),tiles(problem.goalBoard())));
                csv.flush();
                System.out.printf(Locale.ROOT,"case=%d algorithm=%s heuristic=%s status=%s moves=%d total_ms=%.3f generated=%d expanded=%d exported=%s%n",
                    i+1,algorithm,heuristic,r.status,r.path==null?-1:r.path.size(),r.total,r.generated,r.expanded,export);
            }
        }
        System.out.println("PLAYER_INPUT="+output);
        System.out.println("RESULTS_CSV="+output.resolve("results.csv"));
        System.out.println("计时包含本题准备/加载、搜索恢复与校验，文件导出在计时之后；Solve不代替正式Hard。");
        return all?0:2;
    }
    private static Result solveOne(NPuzzleProblem problem,Path cache,String algorithm,String heuristic,
                                   Phase2AStar.SearchMode mode,long budget,int capacity)throws Exception{
        long begin=System.nanoTime(),deadline=begin+budget*1000000L;
        var timer=ManagementFactory.getThreadMXBean();
        boolean supported=timer.isCurrentThreadCpuTimeSupported()&&timer.isThreadCpuTimeEnabled();
        long before=supported?timer.getCurrentThreadCpuTime():0;
        double prepare=0,search=0,validate=0;long generated=0,expanded=0;
        Deque<Node> path=null;String status;boolean searchAborted=false;
        if(!problem.solvable())status="UNSOLVABLE";
        else if(budget==0)status="TIMEOUT";
        else{
            Predictor h;
            if(heuristic.equals("PDB")){
                var p=new CachedPdbPredictor(cache,algorithm.equals("PACKED_IDA"));p.prepare(problem.goalBoard());h=p;
            }else h=PuzzleHeuristics.predictor(HeuristicType.valueOf(heuristic));
            prepare=ms(begin);
            if(System.nanoTime()>=deadline)status="TIMEOUT";
            else{
                long start=System.nanoTime();
                if(algorithm.equals("ASTAR")){
                    Predictor checked=(state,goal)->{if(System.nanoTime()>=deadline)throw new BudgetExceeded();return h.heuristics(state,goal);};
                    try{var r=new Phase2AStar(checked,mode).search(problem);path=r.path();generated=r.generated();expanded=r.expanded();}
                    catch(BudgetExceeded ignored){status="TIMEOUT";return finish(status,null,prepare,ms(start),0,begin,timer,supported,before,-1,-1);}
                }else if(algorithm.equals("IDA")){
                    var solver=new IdAStar(new StackFrontier(),h);
                    solver.setTimeLimitMillis(Math.max(0,(deadline-System.nanoTime())/1000000));
                    if(capacity>0){solver.setTranspositionCapacity(capacity);solver.setTranspositionTable(true,PuzzleZobrist.instance());}
                    path=solver.search(problem);generated=solver.generatedCount();expanded=solver.expandedCount();searchAborted=solver.aborted();
                }else{
                    var solver=new PackedIdAStar(new StackFrontier(),(PackedPuzzleHeuristic)h);
                    solver.setTranspositionCapacity(capacity);solver.setDeadlineNanos(deadline);solver.setTimeLimitMillis(budget);
                    path=solver.search(problem);generated=solver.generatedCount();expanded=solver.expandedCount();searchAborted=solver.aborted();
                }
                search=ms(start);start=System.nanoTime();
                boolean valid=path!=null&&LeaderBenchmarkRunner.validPath(problem.initialBoard(),problem.goalBoard(),path);
                validate=ms(start);
                if(path!=null&&!valid)throw new IllegalStateException("搜索器返回非法路径");
                status=searchAborted||System.nanoTime()>=deadline?"TIMEOUT":valid?"SOLVED":"SEARCH_FAILED";
                if(!status.equals("SOLVED"))path=null;
            }
        }
        return finish(status,path,prepare,search,validate,begin,timer,supported,before,generated,expanded);
    }
    private static Result finish(String status,Deque<Node> path,double prepare,double search,double validate,long begin,
                                 java.lang.management.ThreadMXBean timer,boolean supported,long before,long generated,long expanded){
        return new Result(status,path,prepare,search,validate,ms(begin),
            supported?String.format(Locale.ROOT,"%.3f",(timer.getCurrentThreadCpuTime()-before)/1e6):"N/A",generated,expanded);
    }
    private static String tiles(PuzzleBoard b){return Arrays.toString(b.toArray()).replaceAll("[\\[\\],]","").trim();}
    private static double ms(long start){return (System.nanoTime()-start)/1e6;}
    private static void verify(Path lab)throws Exception{
        Path output=newOutput(lab,"verify");
        stud.g01.runner.PqFrontierTest.main(new String[0]);
        stud.g01.test.Phase2AStarTest.main(new String[]{output.resolve("astar").toString()});
        stud.g01.test.SafeIdaStarTest.main(new String[0]);
        stud.g01.test.PackedIdaStarTest.main(new String[0]);
        stud.g01.test.AdvancedPdbTest.main(new String[]{output.resolve("advanced").toString()});
        stud.g01.test.PdbPrototypeTest.main(new String[]{output.resolve("prototype").toString()});
        stud.g01.test.DisjointPdbTest.main(new String[]{output.resolve("disjoint").toString()});
        stud.g01.test.BfsComparisonTest.main(new String[0]);
        stud.g01.test.UnifiedEntryTest.main(new String[0]);
        System.out.println("ALL FINAL REGRESSIONS PASS; "+output);
    }
}
