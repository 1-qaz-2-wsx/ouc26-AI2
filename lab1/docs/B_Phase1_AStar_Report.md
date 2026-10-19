# 成员B 第一阶段 A* 运行记录

运行方式：IDEA 打开 `lab1/WattAi`，运行 `core.runner.SearchTester.main`。

运行参数：

```
resources/test_8puzzle.txt NPUZZLE 1 stud.g01.runner.PuzzleFeeder
```

环境：Oracle JDK 25（运行时版本 25+37-LTS-3491），Java HotSpot 64-Bit Server VM；操作系统为 Microsoft Windows 11 家庭中文版（64 位，系统版本 10.0.26200）；处理器为 Intel Core i7-14650HX（16 核、24 个逻辑处理器，标称频率 2.20 GHz）；物理内存为 16 GB。JVM 可用逻辑处理器数为 24，最大堆内存约为 4040 MiB。

输入文件：`resources/test_8puzzle.txt`，包含两个指定的 8-数码实例。下表使用 `0` 表示空格，棋盘按行展开。

| case_id | initial           | goal              |
| ------- | ----------------- | ----------------- |
| case-1  | 8 6 7 2 5 4 3 0 1 | 1 2 3 4 5 6 7 8 0 |
| case-2  | 6 4 7 8 5 0 3 2 1 | 1 2 3 4 5 6 7 8 0 |

| case_id | initial           | goal              | algorithm | heuristic | status | moves | wall_time_ms | cpu_time_ms | generated | expanded |
| ------- | ----------------- | ----------------- | --------- | --------- | ------ | ----: | -----------: | ----------: | --------: | -------: |
| case-1  | 8 6 7 2 5 4 3 0 1 | 1 2 3 4 5 6 7 8 0 | A*        | MISPLACED | SOLVED |    31 |     313.3200 |     296.875 |    385076 |   143848 |
| case-2  | 6 4 7 8 5 0 3 2 1 | 1 2 3 4 5 6 7 8 0 | A*        | MISPLACED | SOLVED |    31 |     228.5057 |     218.750 |    385076 |   143848 |
| case-1  | 8 6 7 2 5 4 3 0 1 | 1 2 3 4 5 6 7 8 0 | A*        | MANHATTAN | SOLVED |    31 |      42.1813 |      46.875 |     55925 |    21197 |
| case-2  | 6 4 7 8 5 0 3 2 1 | 1 2 3 4 5 6 7 8 0 | A*        | MANHATTAN | SOLVED |    31 |      37.3751 |      31.250 |     55925 |    21197 |



## PqFrontier 测试记录

运行方式：运行 `stud.g01.runner.PqFrontierTest.main`。

| 测试项      | 检查内容                                          | 检查数 | 结果 |
| ----------- | ------------------------------------------------- | -----: | ---- |
| Sorting     | 按 f 值升序出队，出队后成员索引及有效数量正确更新 |     31 | PASS |
| Replacement | 同状态更优结点替换，拒绝相同或更差代价的结点      |     19 | PASS |
| Stale-entry | 跳过堆中过期结点，返回当前有效结点                |     17 | PASS |
| Reinsertion | 已出队状态可重新插入，旧条目不影响新条目          |     19 | PASS |
| Clear       | 清空队列及成员索引，清空后可正常重新使用          |     26 | PASS |
| Empty-queue | 空队列查询、重复出队及 `contains(null)` 行为正确  |     13 | PASS |

测试结果：**6 组测试、125 项检查全部通过，进程退出代码为 0。**

