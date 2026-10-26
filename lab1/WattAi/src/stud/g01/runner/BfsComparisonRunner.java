package stud.g01.runner;

import java.util.*;
import stud.g01.problem.npuzzle.*;

/** 在单位代价真实8数码图比较BFS/BiBFS；不是用BiBFS生成加性PDB。 */
public final class BfsComparisonRunner {
    public record Result(int distance,long generated,long expanded,int peakFrontier,int visited,double wallMs){}
    public static Result solve(PuzzleBoard initial,PuzzleBoard goal,boolean bidirectional){
        if(initial.size()!=goal.size())throw new IllegalArgumentException("Board size mismatch");
        long start=System.nanoTime(),generated=0,expanded=0;
        Map<PuzzleBoard,Integer> left=new HashMap<>(),right=new HashMap<>();
        Set<PuzzleBoard> front=new HashSet<>(),back=new HashSet<>();
        left.put(initial,0);front.add(initial);
        if(bidirectional){right.put(goal,0);back.add(goal);}
        int peak=front.size()+back.size();
        if(initial.equals(goal))return new Result(0,0,0,peak,left.size()+right.size(),(System.nanoTime()-start)/1e6);
        while(!front.isEmpty()&&(!bidirectional||!back.isEmpty())){
            // 每次完整展开一层；双向时选较小的一侧，不能在层中随意切换。
            if(bidirectional&&front.size()>back.size()){
                var f=front;front=back;back=f;
                var m=left;left=right;right=m;
            }
            Set<PuzzleBoard> next=new HashSet<>();
            int best=Integer.MAX_VALUE;
            for(PuzzleBoard board:front){
                expanded++;
                for(Direction d:Direction.values())if(board.canMove(d)){
                    generated++;
                    PuzzleBoard child=board.move(d);
                    int depth=left.get(board)+1;
                    if(left.putIfAbsent(child,depth)!=null)continue;
                    next.add(child);
                    Integer opposite=right.get(child);
                    if(bidirectional&&opposite!=null)best=Math.min(best,depth+opposite);
                    if(!bidirectional&&child.equals(goal))best=depth;
                }
            }
            front=next;
            peak=Math.max(peak,front.size()+back.size());
            if(best!=Integer.MAX_VALUE)return new Result(best,generated,expanded,peak,left.size()+right.size(),(System.nanoTime()-start)/1e6);
        }
        return new Result(-1,generated,expanded,peak,left.size()+right.size(),(System.nanoTime()-start)/1e6);
    }
    public static void main(String[] args){
        Locale.setDefault(Locale.ROOT);
        PuzzleBoard goal=new PuzzleBoard(3,new int[]{1,2,3,4,5,6,7,8,0});
        PuzzleBoard[] cases={new PuzzleBoard(3,new int[]{1,2,0,4,5,3,7,8,6}),new PuzzleBoard(3,new int[]{8,6,7,2,5,4,3,0,1})};
        System.out.println("case,algorithm,moves,generated,expanded,peak_frontier,visited_entries,wall_ms");
        for(int i=0;i<cases.length;i++)for(boolean bi:new boolean[]{false,true}){
            // 同一种实现先热身，再重复3次取wall时间中位数；计数为确定性的完整层口径。
            solve(cases[i],goal,bi);
            Result[] runs=new Result[3];for(int n=0;n<3;n++)runs[n]=solve(cases[i],goal,bi);
            Arrays.sort(runs,Comparator.comparingDouble(Result::wallMs));Result r=runs[1];
            System.out.printf("%s,%s,%d,%d,%d,%d,%d,%.3f%n",i==0?"two-step":"known-31-step",bi?"BiBFS":"BFS",r.distance,r.generated,r.expanded,r.peakFrontier,r.visited,r.wallMs);
        }
        System.out.println("Full layers; generated includes duplicate edges; visited_entries counts both maps for BiBFS, not measured RAM.");
    }
}
