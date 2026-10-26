package stud.g01.solver;

import core.problem.State;

/** 状态哈希接口，供 IDA* 置换表使用；equals 相同的状态必须得到相同哈希。 */
public interface StateHasher {
    long hash(State state);
}