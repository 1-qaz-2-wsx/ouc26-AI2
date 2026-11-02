package stud.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * 兼容类，只为让课程框架编译通过：
 * {@code core.runner.EngineFeeder} 第11行导入 {@code stud.solver.IdAStar}，
 * 第54行按 {@code new IdAStar(frontier, predictor)} 构造，而学生实现放在
 * {@code stud.g01.solver.IdAStar}（见 docs/编译问题记录.md B02）。
 *
 * <p>本类不做任何算法工作，只把调用转发给成员C的实现。
 * 是否保留本类、放在哪个包，由组长按编译记录的方案统一决定。</p>
 */
public class IdAStar extends AbstractSearcher {

    private final stud.g01.solver.IdAStar delegate;

    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.delegate = new stud.g01.solver.IdAStar(frontier, predictor);
    }

    @Override
    public Deque<Node> search(Problem problem) {
        return delegate.search(problem);
    }

    @Override
    public int nodesGenerated() {
        return delegate.nodesGenerated();
    }

    @Override
    public int nodesExpanded() {
        return delegate.nodesExpanded();
    }

    /** 上一次搜索是否被中断（超时或超过结点上限） */
    public boolean aborted() {
        return delegate.aborted();
    }
}