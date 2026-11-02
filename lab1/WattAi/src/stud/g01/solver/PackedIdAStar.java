package stud.g01.solver;

import core.problem.Problem;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.*;
import java.util.*;
import stud.g01.heuristic.pdb.PackedPuzzleHeuristic;
import stud.g01.problem.npuzzle.*;

/** 组长新增的N-Puzzle专用IDA*快路径。每格4bit完整编码，不用哈希充当状态身份。
 * 搜索时只复用原始数组与增量PDB游标；命中目标后才创建课程框架Node。
 * 与C的通用IdAStar并存，便于消融和回归，不改core。 */
public final class PackedIdAStar extends AbstractSearcher {
    private static final int FOUND=-1,INF=Integer.MAX_VALUE,MAX_DEPTH=127;
    private final PackedPuzzleHeuristic predictor;
    private long timeLimitMillis=60000,absoluteDeadline=Long.MAX_VALUE,deadline;
    private int capacity,epoch,threshold,iterations,solutionDepth=-1;
    private boolean aborted;
    private long generated,expanded;
    private long[] tableKeys;
    private byte[] tableDepths;
    private int[] tableEpochs;
    private int[][] neighbors,orderedTo,orderedScore;
    private final int[] path=new int[MAX_DEPTH+1],solution=new int[MAX_DEPTH+1];
    private PackedPuzzleHeuristic.Cursor cursor;
    private long goalKey;

    public PackedIdAStar(Frontier frontier,PackedPuzzleHeuristic predictor){
        super(frontier);this.predictor=Objects.requireNonNull(predictor);
    }
    public void setTimeLimitMillis(long millis){if(millis<0)throw new IllegalArgumentException("Negative budget");timeLimitMillis=millis;}
    public void setDeadlineNanos(long deadline){absoluteDeadline=deadline;}
    /** 0禁用；有限直接映射表，键比较完整64bit。碰撞替换仅失去缓存命中。 */
    public void setTranspositionCapacity(int entries){
        if(entries<0||(entries!=0&&Integer.bitCount(entries)!=1))throw new IllegalArgumentException("Capacity must be zero or power of two");
        capacity=entries;
    }
    public boolean aborted(){return aborted;}
    public int lastThreshold(){return threshold;}
    public int iterations(){return iterations;}
    public long generatedCount(){return generated;}
    public long expandedCount(){return expanded;}
    public int solutionCost(){return solutionDepth;}

    @Override public Deque<Node> search(Problem generic){
        if(!(generic instanceof NPuzzleProblem))throw new IllegalArgumentException("Packed engine requires NPuzzleProblem");
        NPuzzleProblem problem=(NPuzzleProblem)generic;
        generated=expanded=0;nodesGenerated=nodesExpanded=0;iterations=threshold=0;solutionDepth=-1;aborted=false;
        if(!problem.solvable())return null;
        long now=System.nanoTime(),duration=timeLimitMillis>Long.MAX_VALUE/1_000_000?Long.MAX_VALUE:timeLimitMillis*1_000_000;
        deadline=Math.min(absoluteDeadline,duration==Long.MAX_VALUE?Long.MAX_VALUE:now+duration);
        if(now>=deadline){aborted=true;return null;}
        int size=problem.initialBoard().size();
        predictor.heuristics(problem.initialBoard(),problem.goalBoard()); // 验证绑定目标，不计入逐节点热循环。
        cursor=predictor.cursor(problem.initialBoard());
        neighbors=createNeighbors(size);orderedTo=new int[MAX_DEPTH+1][4];orderedScore=new int[MAX_DEPTH+1][4];
        goalKey=encode(problem.goalBoard());long initial=encode(problem.initialBoard());
        if(capacity>0){tableKeys=new long[capacity];tableDepths=new byte[capacity];tableEpochs=new int[capacity];}
        threshold=cursor.value();
        try{
            while(true){
                iterations++;epoch=iterations;
                int next=dfs(initial,problem.initialBoard().blankIndex(),-1,0,threshold);
                if(next==FOUND)return restore(problem);
                if(aborted||next==INF)return null;
                threshold=next;
            }
        }finally{
            tableKeys=null;tableDepths=null;tableEpochs=null;cursor=null;
            nodesGenerated=(int)Math.min(Integer.MAX_VALUE,generated);nodesExpanded=(int)Math.min(Integer.MAX_VALUE,expanded);
        }
    }

    private int dfs(long board,int blank,int previousBlank,int depth,int limit){
        int f=depth+cursor.value();if(f>limit)return f;
        if((expanded&2047)==0&&System.nanoTime()>=deadline){aborted=true;return INF;}
        if(board==goalKey){
            if(System.nanoTime()>=deadline){aborted=true;return INF;}
            solutionDepth=depth;System.arraycopy(path,0,solution,0,depth);return FOUND;
        }
        if(depth>=MAX_DEPTH)throw new IllegalStateException("Puzzle search depth exceeds workspace");
        if(capacity>0){
            long hash=board^(board>>>33);hash*=0xff51afd7ed558ccdL;hash^=hash>>>33;
            int index=(int)hash&(capacity-1);
            if(tableEpochs[index]==epoch&&tableKeys[index]==board&&Byte.toUnsignedInt(tableDepths[index])<=depth)return INF;
            tableEpochs[index]=epoch;tableKeys[index]=board;tableDepths[index]=(byte)depth;
        }
        expanded++;
        int count=0,minExceeded=INF;
        int[] targets=orderedTo[depth],scores=orderedScore[depth];
        for(int to:neighbors[blank]){
            if(to==previousBlank)continue; // 在创建状态和PDB查询之前去掉立即撤销。
            int tile=(int)(board >>>(to*4))&15;generated++;
            cursor.move(blank,to,tile);int score=depth+1+cursor.value();cursor.undo();
            if(score>limit){minExceeded=Math.min(minExceeded,score);continue;}
            int position=count++;
            while(position>0&&scores[position-1]>score){scores[position]=scores[position-1];targets[position]=targets[position-1];position--;}
            scores[position]=score;targets[position]=to;
        }
        for(int i=0;i<count;i++){
            int to=targets[i],tile=(int)(board >>>(to*4))&15;
            long child=board^((long)tile<<(blank*4))^((long)tile<<(to*4));
            path[depth]=to;cursor.move(blank,to,tile);
            int next=dfs(child,to,blank,depth+1,limit);cursor.undo();
            if(next==FOUND||aborted)return next;
            minExceeded=Math.min(minExceeded,next);
        }
        return minExceeded;
    }
    private Deque<Node> restore(NPuzzleProblem problem){
        ArrayDeque<Node> result=new ArrayDeque<>();
        PuzzleBoard board=problem.initialBoard();Node parent=problem.root(predictor);
        for(int i=0;i<solutionDepth;i++){
            int delta=solution[i]-board.blankIndex();
            Direction direction=delta==-board.size()?Direction.UP:delta==board.size()?Direction.DOWN:delta==-1?Direction.LEFT:Direction.RIGHT;
            PuzzleMove action=new PuzzleMove(direction);board=board.move(direction);
            Node node=new Node(board,parent,action,i+1,predictor.heuristics(board,problem.goalBoard()));result.add(node);parent=node;
        }
        return result;
    }
    private static long encode(PuzzleBoard board){long key=0;for(int i=0;i<board.size()*board.size();i++)key|=(long)board.tileAt(i)<<(4*i);return key;}
    private static int[][] createNeighbors(int size){
        int[][] result=new int[size*size][];
        for(int p=0;p<result.length;p++){
            int[] buffer=new int[4];int n=0;
            if(p/size>0)buffer[n++]=p-size;if(p/size<size-1)buffer[n++]=p+size;
            if(p%size>0)buffer[n++]=p-1;if(p%size<size-1)buffer[n++]=p+1;
            result[p]=Arrays.copyOf(buffer,n);
        }
        return result;
    }
}
