# 成员C 第一阶段 IDA* 运行记录

运行方式：IDEA 打开 lab1/WattAi，运行 stud.g01.test.IdaStarTester.main
环境：JDK 25.0.4.1 (Oracle)，Windows 11 10.0 amd64，AMD64 Family 25 Model 97 Stepping 2，JVM 最大堆 3896 MiB

| case_id          | algorithm | heuristic | status     | moves | bfs_optimal | path_ok | wall_time_ms | cpu_time_ms | generated | expanded |
| ---------------- | --------- | --------- | ---------- | ----- | ----------- | ------- | ------------ | ----------- | --------- | -------- |
| simple-1         | IDA*      | MANHATTAN | SOLVED     | 1     | 1           | true    | 8.2          | 0.0         | 3         | 1        |
| simple-2         | IDA*      | MANHATTAN | SOLVED     | 2     | 2           | true    | 0.1          | 0.0         | 5         | 2        |
| simple-2-h0      | IDA*      | NONE      | SOLVED     | 2     | 2           | true    | 0.3          | 0.0         | 28        | 10       |
| spec-1           | IDA*      | MANHATTAN | SOLVED     | 31    | 31          | true    | 31.4         | 31.3        | 36978     | 14195    |
| spec-2           | IDA*      | MANHATTAN | SOLVED     | 31    | 31          | true    | 18.3         | 15.6        | 46527     | 17818    |
| spec-1-misplaced | IDA*      | MISPLACED | SOLVED     | 31    | 31          | true    | 1472.5       | 1421.9      | 7352243   | 2702739  |
| unsolvable       | IDA*      | MANHATTAN | UNSOLVABLE | -1    | -           | -       | 0.0          | 0.0         | 0         | 0        |

说明：
- bfs_optimal 由测试入口内独立的 BFS 基准给出（3x3 状态空间 181440 可穷举），不是与另一种算法互相印证。
- path_ok 逐帧校验每一步只交换空格与相邻数字，末帧等于目标。
- 两个指定实例最优解均为 31 步，即 8-数码最长最优解；曼哈顿距离下约 3.7~4.7 万生成结点。
- 同一实例换错位将牌数，生成结点约 200 倍、时间从 31ms 涨到 1473ms，可作报告 4.2 对比材料。
- 本阶段未实现路径判重与 Zobrist 哈希，结点数偏大属预期。
- 正式实验数据待成员A的棋盘合入 main 后，用其 PuzzleBoard/NPuzzleProblem 复测替换。