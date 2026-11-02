package stud.g01.test;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;
import stud.g01.problem.npuzzle.*;
/** Regression for C a81d58c's hash-only TT: a constant hash must still solve two moves. */
public final class SafeIdaStarTest {
    private static final int[] GOAL={1,2,3,4,5,6,7,8,0};
    private static NPuzzleProblem problem(int[] a) {
        return new NPuzzleProblem(new PuzzleBoard(3,a),new PuzzleBoard(3,GOAL),3);
    }
    private static void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        IdAStar s=new IdAStar(new StackFrontier());
        s.setTranspositionTable(true,state->0L);
        var p=problem(new int[]{1,2,3,4,5,6,0,7,8});
        var path=s.search(p);
        check(path!=null && path.size()==2,"constant hash must preserve the optimal two-step path");
        var state=p.root().getState();
        for(var node:path) {
            check(p.applicable(state,node.getAction()),"legal action");
            state=state.next(node.getAction());
            check(state.equals(node.getState()),"legal successor");
        }
        check(p.goal(state),"path reaches goal");
        var table=IdAStar.class.getDeclaredField("bestCostByState");
        table.setAccessible(true);
        check(table.get(s)==null,"TT released after return");
        check(!p.root().getState().equals(new PuzzleBoard(3,GOAL)),"unequal states sharing constant hash");
        check(s.generatedCount()>=s.expandedCount() && s.nodesGenerated()==s.generatedCount(),"long statistics");
        s.setTranspositionCapacity(1);
        for(int i=0;i<3;i++) {
            path=s.search(p);
            check(path!=null && path.size()==2,"capacity-one eviction and repeat calls");
            check(s.lastTranspositionEntries()<=1,"bounded TT");
        }
        check(s.search(problem(GOAL)).isEmpty() && s.solutionCost()==0,"zero moves");
        check(s.search(problem(new int[]{1,2,3,4,5,6,8,7,0}))==null && !s.aborted(),"unreachable");
        check(s.generatedCount()==0 && s.expandedCount()==0 && s.iterations()==0,"reset statistics");
        s.setTimeLimitMillis(0);
        check(s.search(p)==null && s.aborted(),"zero deadline");
        s.setTimeLimitMillis(IdAStar.NO_LIMIT);
        s.setNodeLimit(1);
        check(s.search(p)==null && s.aborted(),"node limit");
        s.setNodeLimit(IdAStar.NO_LIMIT);
        s.setTranspositionTable(false,null);
        check(s.search(p).size()==2 && !s.aborted() && s.lastTranspositionEntries()==0,"recovery/default path");
        var generated=IdAStar.class.getDeclaredField("generatedCount");
        var expanded=IdAStar.class.getDeclaredField("expandedCount");
        generated.setAccessible(true); expanded.setAccessible(true);
        generated.setLong(s,(long)Integer.MAX_VALUE+7);
        expanded.setLong(s,(long)Integer.MAX_VALUE+9);
        check(s.nodesGenerated()==Integer.MAX_VALUE && s.nodesExpanded()==Integer.MAX_VALUE,"saturating core counters");
        check(s.generatedCount()==(long)Integer.MAX_VALUE+7,"long counters preserve precision");
        s.search(p);
        check(s.generatedCount()<Integer.MAX_VALUE,"long counters reset");
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean.isCurrentThreadCpuTimeSupported()) {
            if(!bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
            long before=bean.getCurrentThreadCpuTime();
            for(int i=0;i<1000;i++) check(s.search(p).size()==2,"CPU sample search");
            System.out.println("actual_thread_cpu_ns="+(bean.getCurrentThreadCpuTime()-before));
        } else System.out.println("actual_thread_cpu_ns=unavailable");
        System.out.println("constant-hash regression passed");
    }
}
