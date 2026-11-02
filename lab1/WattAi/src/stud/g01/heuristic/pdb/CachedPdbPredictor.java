package stud.g01.heuristic.pdb;
import core.problem.State;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.util.LabPaths;
import java.io.*;
import java.nio.file.Path;

/**
 * 公共Predictor的PDB接入。只加载预建表，绝不在求解时隐式建表。
 * 按完整目标绑定，目标变化时重新加载；每个实例只保留当前目标的数据库。
 * 实例供单个搜索线程使用，不能把不同目标的表混用。
 */
public final class CachedPdbPredictor implements PackedPuzzleHeuristic {
    private final Path directory;
    private final boolean symmetry;
    private PuzzleBoard goal;
    private PackedPuzzleHeuristic delegate;
    public CachedPdbPredictor(){this(LabPaths.pdbDirectory(),false);}
    public CachedPdbPredictor(Path directory,boolean symmetry){
        this.directory=directory.toAbsolutePath().normalize();this.symmetry=symmetry;
    }
    public void prepare(PuzzleBoard target)throws IOException{
        if(delegate!=null&&target.equals(goal))return;
        // 先成功加载新目标，再替换旧绑定；失败时不会把旧表误用于新目标。
        var db=DisjointPatternDatabase.load(target,directory);
        var next=DisjointPatternDatabase.combinedPredictor(symmetry,db);
        goal=target;delegate=next;
    }
    private void ensure(PuzzleBoard target){
        try{prepare(target);}
        catch(IOException ex){throw new UncheckedIOException("PDB缺失或损坏，先运行LabRunner Build；缓存目录="+directory,ex);}
    }
    @Override public int heuristics(State state,State requestedGoal){
        if(!(state instanceof PuzzleBoard)||!(requestedGoal instanceof PuzzleBoard))
            throw new IllegalArgumentException("PDB仅支持PuzzleBoard");
        ensure((PuzzleBoard)requestedGoal);return delegate.heuristics(state,requestedGoal);
    }
    @Override public Cursor cursor(PuzzleBoard initial){
        if(delegate==null)throw new IllegalStateException("先prepare目标，再创建PDB游标");
        return delegate.cursor(initial);
    }
}
