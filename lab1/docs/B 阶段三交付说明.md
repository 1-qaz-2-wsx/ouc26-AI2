# B 阶段三交付说明：A* 优先队列、Open/Closed 优化与三种启发函数比较

## 一、阶段目标

本阶段完成最终优先队列接入，并在统一的 A* 框架下完成以下工作：

1. 使用 `PqFrontier` 替换第一阶段的 `ListFrontier`；
2. 接入基于 `PriorityQueue` 的 A* 优先队列；
3. 使用 `activeNodes` 实现 best-g 重复状态处理；
4. 在 `poll` 时跳过已经过期的结点；
5. 完成第一阶段两个指定 8-Puzzle 实例的验收；
6. 比较 Open/Closed 表优化策略；
7. 比较错位将牌数、曼哈顿距离和 Disjoint Pattern 三种启发函数；
8. 输出阶段二和阶段三的实验原始数据及汇总结果。

---

## 二、运行环境和测试数据

### 2.1 运行环境

本次实验使用以下环境：

- 操作系统：Windows 11；
- Java：Java 25；
- Java 可执行文件：

```text
E:\dev\java25\bin\java.exe
```

- CPU 架构：amd64；
- 可用处理器：24；
- 输入文件：

```text
WattAi/resources/test_8puzzle.txt
```

- PDB 文件大小：

```text
6552 bytes
```

- PDB 目录：

```text
lab1/.local/pdb-phase3
```

所有正式实验均使用两个指定的 8-Puzzle 实例：

```text
case-1
case-2
```

两个实例均可解，最优解长度均为：

```text
31
```

### 2.2 实验入口

本阶段使用以下运行入口：

```text
stud.g01.test.Phase2AStarTest
stud.g01.runner.Phase2Experiment
stud.g01.runner.Phase3AstarExperiment
```

正确性测试运行结果：

```text
ALL PASS: 123 Phase2 A* checks
```

`Phase3AstarExperiment` 的运行结果：

```text
Phase 3 A* experiment completed.
进程已结束，退出代码为 0
```

`Phase2Experiment` 正常完成并输出阶段二原始数据和汇总数据。

### 2.3 实验输出文件

阶段二实验输出：

```text
experiments/astar-phase2-raw.csv
experiments/astar-phase2-summary.csv
```

阶段三实验输出：

```text
experiments/astar-phase3-raw.csv
experiments/astar-phase3-summary.csv
```

每种实验配置均运行 10 次，汇总文件中的
`median_ms` 表示 10 次正式运行时间的中位数。

---

## 三、最终优先队列实现

### 3.1 PqFrontier 的功能

阶段二和阶段三使用：

```text
stud.g01.queue.PqFrontier
```

替换第一阶段使用的：

```text
ListFrontier
```

`PqFrontier` 使用 Java `PriorityQueue` 保存待扩展结点，并按照：

```text
f(n) = g(n) + h(n)
```

对结点排序。

其中：

- `g(n)` 表示从初始状态到当前结点的实际路径代价；
- `h(n)` 表示启发函数估计的剩余代价；
- `f(n)` 表示当前结点的评价值。

每次搜索从 Open 表中取出评价值最小的有效结点。

### 3.2 Open 表重复状态处理

`PqFrontier` 使用：

```java
Map<State, Set<Entry>> pending
```

保存 Open 表中每个状态对应的有效条目。

当启用 Open best-g 时：

1. 如果状态尚未出现在 Open 表中，接受新结点；
2. 如果新结点的路径代价不小于已有结点，拒绝新结点；
3. 如果新结点的路径代价更小，使原结点失效；
4. 将新结点加入优先队列；
5. 保留原结点直到它到达队首时再删除。

由于 Java `PriorityQueue` 不支持高效的任意位置删除，
本实现使用延迟删除。被替代的旧条目仍可能暂时存在于物理队列中，
但已经不属于逻辑 Open 表。

### 3.3 过期结点过滤

当被替代的旧条目到达优先队列队首时，`poll()` 会检查其有效性。

如果条目已经失效，则：

1. 从物理优先队列中删除；
2. 增加 `staleSkipped` 统计；
3. 继续检查下一个队首条目。

这样可以避免扩展已经被更优路径替代的结点。

### 3.4 Closed 表处理

当启用 Closed best-g 时，使用：

```java
Map<State, Integer> closedBestG
```

记录每个已经扩展状态的最小路径代价。

生成后继时：

- 如果 Closed 表中不存在该状态，继续处理；
- 如果新路径的 `g` 不小于 Closed 表记录的 `g`，拒绝该路径；
- 如果新路径的 `g` 更小，则删除原 Closed 记录；
- 将该状态重新放入 Open 表。

最后一种情况称为 reopen。

`Phase2AStar` 在取出结点、确认该结点不是目标状态并准备扩展时，
调用：

```java
open.markExpanded(node);
```

因此 Closed 表只记录真正被扩展的非目标状态。

### 3.5 PqFrontier 测试

`PqFrontier` 专项测试结果如下：

```text
测试组数：6 组
检查项目：125 项
通过情况：全部通过
```

A* 综合测试结果如下：

```text
ALL PASS: 123 Phase2 A* checks
```

测试覆盖：

- 优先队列排序；
- 状态相等判断；
- Open best-g；
- Closed best-g；
- 过期结点处理；
- Closed reopen；
- 路径恢复；
- 最优解长度；
- 不可解状态处理；
- 统计数据记录。

---

## 四、第一阶段性能验收

### 4.1 验收入口

第一阶段使用课程框架入口进行验收：

```text
stud.g01.runner.B_Phase3ExperimentRunner
```

测试使用两个指定的 8-Puzzle 实例，并分别使用：

- 错位将牌数；
- 曼哈顿距离。

### 4.2 验收结果

第一阶段单次验收结果如下：

| case_id | 启发函数    | 状态   | 解长度 | wall time/ms | CPU time/ms | 生成结点 | 扩展结点 |
| ------- | ----------- | ------ | -----: | -----------: | ----------: | -------: | -------: |
| case-1  | `MISPLACED` | SOLVED |     31 |     305.1660 |    281.2500 |  385,076 |  143,848 |
| case-2  | `MISPLACED` | SOLVED |     31 |     222.3268 |    203.1250 |  385,076 |  143,848 |
| case-1  | `MANHATTAN` | SOLVED |     31 |      32.7121 |     31.2500 |   55,925 |   21,197 |
| case-2  | `MANHATTAN` | SOLVED |     31 |      23.6215 |     31.2500 |   55,925 |   21,197 |

四次运行的 wall time 均低于 1 秒，因此第一阶段的性能要求满足。

与错位将牌数相比，曼哈顿距离将扩展结点数从：

```text
143,848
```

降低到：

```text
21,197
```

扩展结点减少比例为：

```text
(143848 - 21197) / 143848 × 100%
≈ 85.27%
```

两个启发函数均返回长度为 31 的合法解。

目标状态为：

```text
1 2 3
4 5 6
7 8 #
```

### 4.3 验收数据与阶段实验数据的区别

第一阶段验收使用课程框架的单次运行入口，主要用于验证
1 秒性能要求。

阶段二和阶段三实验使用：

```text
Phase2Experiment
Phase3AstarExperiment
```

每种配置运行 10 次，并使用中位数进行比较。

因此，第一阶段验收表和阶段二、阶段三汇总表属于不同实验记录，
不能直接将单次 wall time 与 10 次运行的中位数混合计算。

---

## 五、Open/Closed 搜索模式

### 5.1 四种搜索模式

`Phase2AStar` 实现了以下四种搜索模式：

| 搜索模式         | Open best-g | Closed best-g | 支持 reopen | 用途                     |
| ---------------- | ----------: | ------------: | ----------: | ------------------------ |
| `BASELINE`       |          否 |            否 |          否 | 未优化对照               |
| `OPEN_BEST_G`    |          是 |            否 |          否 | 只处理 Open 表重复状态   |
| `CLOSED_BEST_G`  |          否 |            是 |          是 | 只处理 Closed 表最优代价 |
| `FULL_OPTIMIZED` |          是 |            是 |          是 | 最终优化配置             |

四种模式均使用 Java `PriorityQueue`。

本实验比较的是重复状态处理策略，而不是顺序表和优先队列之间
的性能差异。

### 5.2 阶段二 Open/Closed 实验结果

以下数据来自最新的：

```text
experiments/astar-phase2-summary.csv
```

实验固定使用曼哈顿距离，每种模式运行 10 次。

| 实例   | 模式             | 中位时间/ms |  生成结点 |  扩展结点 | 重复剪枝 | 过期跳过 | reopen | 峰值 Open | 峰值 Closed | 解长度 |
| ------ | ---------------- | ----------: | --------: | --------: | -------: | -------: | -----: | --------: | ----------: | -----: |
| case-1 | `BASELINE`       |    4945.677 | 6,439,569 | 2,531,417 |        0 |        0 |      0 | 3,908,153 |           0 |     31 |
| case-1 | `OPEN_BEST_G`    |      28.246 |    78,094 |    29,669 |   25,138 |        0 |      0 |    23,288 |           0 |     31 |
| case-1 | `CLOSED_BEST_G`  |      23.785 |    55,925 |    21,197 |   23,263 |    2,067 |      0 |     9,423 |      21,197 |     31 |
| case-1 | `FULL_OPTIMIZED` |      22.771 |    55,925 |    21,197 |   25,879 |        0 |      0 |     8,864 |      21,197 |     31 |
| case-2 | `BASELINE`       |    5166.113 | 6,439,569 | 2,531,417 |        0 |        0 |      0 | 3,908,153 |           0 |     31 |
| case-2 | `OPEN_BEST_G`    |      39.862 |    78,094 |    29,669 |   25,138 |        0 |      0 |    23,288 |           0 |     31 |
| case-2 | `CLOSED_BEST_G`  |      22.643 |    55,925 |    21,197 |   23,263 |    2,067 |      0 |     9,421 |      21,197 |     31 |
| case-2 | `FULL_OPTIMIZED` |      23.892 |    55,925 |    21,197 |   25,879 |        0 |      0 |     8,863 |      21,197 |     31 |

### 5.3 Open/Closed 实验分析

`BASELINE` 没有启用 Open best-g 和 Closed best-g。

在该模式下：

- 生成结点数为 6,439,569；
- 扩展结点数为 2,531,417；
- case-1 中位运行时间为 4945.677 ms；
- case-2 中位运行时间为 5166.113 ms。

因此，`BASELINE` 不满足第一阶段的 1 秒性能要求。
该模式用于展示不处理重复状态时的搜索开销。

`FULL_OPTIMIZED` 在两个实例上的结果为：

- case-1：22.771 ms；
- case-2：23.892 ms；
- 生成结点数：55,925；
- 扩展结点数：21,197；
- 解长度：31。

相较 `BASELINE`，`FULL_OPTIMIZED` 的扩展结点数从
2,531,417 降低到 21,197，减少比例为：

```text
(2531417 - 21197) / 2531417 × 100%
≈ 99.16%
```

生成结点数从 6,439,569 降低到 55,925，减少比例为：

```text
(6439569 - 55925) / 6439569 × 100%
≈ 99.13%
```

`FULL_OPTIMIZED` 的中位运行时间远低于 1 秒，满足第一阶段要求。

四种模式均返回长度为 31 的解，且路径合法，说明重复状态处理
没有改变当前测试实例的最优解结果。

### 5.4 阶段三 Open/Closed 复测结果

以下数据来自最新的：

```text
experiments/astar-phase3-summary.csv
```

阶段三实验同样固定使用曼哈顿距离，每种模式运行 10 次。

| 实例   | 模式             | 中位时间/ms |  生成结点 |  扩展结点 | 重复剪枝 | 过期跳过 | 峰值 Open | 峰值 Closed | 解长度 |
| ------ | ---------------- | ----------: | --------: | --------: | -------: | -------: | --------: | ----------: | -----: |
| case-1 | `BASELINE`       |    5068.523 | 6,439,569 | 2,531,417 |        0 |        0 | 3,908,153 |           0 |     31 |
| case-1 | `OPEN_BEST_G`    |      35.892 |    78,094 |    29,669 |   25,138 |        0 |    23,288 |           0 |     31 |
| case-1 | `CLOSED_BEST_G`  |      29.039 |    55,925 |    21,197 |   23,263 |    2,067 |     9,423 |      21,197 |     31 |
| case-1 | `FULL_OPTIMIZED` |      24.684 |    55,925 |    21,197 |   25,879 |        0 |     8,864 |      21,197 |     31 |
| case-2 | `BASELINE`       |    4783.269 | 6,439,569 | 2,531,417 |        0 |        0 | 3,908,153 |           0 |     31 |
| case-2 | `OPEN_BEST_G`    |      34.785 |    78,094 |    29,669 |   25,138 |        0 |    23,288 |           0 |     31 |
| case-2 | `CLOSED_BEST_G`  |      23.306 |    55,925 |    21,197 |   23,263 |    2,067 |     9,421 |      21,197 |     31 |
| case-2 | `FULL_OPTIMIZED` |      22.841 |    55,925 |    21,197 |   25,879 |        0 |     8,863 |      21,197 |     31 |

阶段二和阶段三的结点数量完全一致，运行时间存在少量差异。
差异主要可能来自：

- JVM 即时编译状态；
- 垃圾回收；
- 操作系统调度；
- 运行时系统负载；
- 程序启动顺序。

因此，阶段二和阶段三应分别引用各自 CSV 中的汇总数据，
不应将两个文件中的时间混合后重新计算中位数。

---

## 六、三种启发函数比较

### 6.1 启发函数

本阶段比较以下三种主要启发函数：

1. `MISPLACED`：错位将牌数；
2. `MANHATTAN`：曼哈顿距离；
3. `DISJOINT_PATTERN`：Disjoint Pattern Database。

三种启发函数均固定使用：

```text
FULL_OPTIMIZED
```

进行比较。

### 6.2 阶段二最新启发函数比较结果

以下数据来自：

```text
experiments/astar-phase2-summary.csv
```

每种配置运行 10 次。

| 实例   | 启发函数           | 中位时间/ms | 生成结点 | 扩展结点 | 重复剪枝 | 解长度 |
| ------ | ------------------ | ----------: | -------: | -------: | -------: | -----: |
| case-1 | `MISPLACED`        |     214.250 |  385,076 |  143,848 |  223,199 |     31 |
| case-1 | `MANHATTAN`        |      22.771 |   55,925 |   21,197 |   25,879 |     31 |
| case-1 | `DISJOINT_PATTERN` |       5.891 |   16,342 |    6,055 |    6,905 |     31 |
| case-2 | `MISPLACED`        |     242.365 |  385,076 |  143,848 |  223,199 |     31 |
| case-2 | `MANHATTAN`        |      23.892 |   55,925 |   21,197 |   25,879 |     31 |
| case-2 | `DISJOINT_PATTERN` |       5.925 |   16,648 |    6,141 |    7,050 |     31 |

### 6.3 阶段三最新启发函数比较结果

以下数据来自：

```text
experiments/astar-phase3-summary.csv
```

| 实例   | 启发函数           | 中位时间/ms | 生成结点 | 扩展结点 | 重复剪枝 | 解长度 |
| ------ | ------------------ | ----------: | -------: | -------: | -------: | -----: |
| case-1 | `MISPLACED`        |     244.044 |  385,076 |  143,848 |  223,199 |     31 |
| case-1 | `MANHATTAN`        |      24.684 |   55,925 |   21,197 |   25,879 |     31 |
| case-1 | `DISJOINT_PATTERN` |       5.868 |   16,342 |    6,055 |    6,905 |     31 |
| case-2 | `MISPLACED`        |     238.526 |  385,076 |  143,848 |  223,199 |     31 |
| case-2 | `MANHATTAN`        |      22.841 |   55,925 |   21,197 |   25,879 |     31 |
| case-2 | `DISJOINT_PATTERN` |       5.915 |   16,648 |    6,141 |    7,050 |     31 |

阶段二和阶段三的搜索规模完全一致，说明本次实验中的状态处理
和路径结果稳定。两次运行的时间略有差异，但启发函数之间的性能
排序一致：

```text
DISJOINT_PATTERN < MANHATTAN < MISPLACED
```

### 6.4 启发函数性能分析

错位将牌数只判断数字是否处于目标位置，不考虑数字距离目标位置
还有多少行或多少列，因此启发信息较弱。

曼哈顿距离计算所有数字当前位置与目标位置之间的行列距离。
它能够更准确地估计剩余移动代价，因此比错位将牌数扩展更少结点。

Disjoint Pattern Database 使用抽象状态空间中的精确最短距离，
能够提供更强的下界，因此进一步减少搜索规模。

以阶段二数据为例，曼哈顿距离相较错位将牌数减少的扩展结点比例为：

```text
(143848 - 21197) / 143848 × 100%
≈ 85.27%
```

Disjoint Pattern 相较曼哈顿距离的扩展结点减少比例为：

case-1：

```text
(21197 - 6055) / 21197 × 100%
≈ 71.43%
```

case-2：

```text
(21197 - 6141) / 21197 × 100%
≈ 71.03%
```

Disjoint Pattern 相较错位将牌数的扩展结点减少比例为：

- case-1：

```text
(143848 - 6055) / 143848 × 100%
≈ 95.79%
```

- case-2：

```text
(143848 - 6141) / 143848 × 100%
≈ 95.73%
```

三种启发函数均得到长度为 31 的合法解。对于当前两个测试实例，
三种启发函数都保持了 A* 的最优解结果。



---

## 七、修改和新增文件

### 7.1 `PqFrontier.java`

文件：

```text
WattAi/src/stud/g01/queue/PqFrontier.java
```

主要功能：

- 基于 `PriorityQueue` 的 Open 表；
- Open best-g；
- Closed best-g；
- 重复状态拒绝；
- 过期结点过滤；
- Closed 状态 reopen；
- 生成和扩展结点统计；
- 重复剪枝统计；
- 过期结点统计；
- Open 和 Closed 峰值统计。

### 7.2 `Phase2AStar.java`

文件：

```text
WattAi/src/stud/g01/solver/Phase2AStar.java
```

主要功能：

- 实现 A* 搜索主循环；
- 接入 `PqFrontier`；
- 支持四种搜索模式；
- 接入三种启发函数；
- 调用 `markExpanded()` 管理 Closed 表；
- 恢复最终解路径；
- 返回生成结点和扩展结点统计；
- 返回重复剪枝、过期跳过和 reopen 统计；
- 验证搜索结果和路径长度。

### 9.3 `Phase2Experiment.java`

文件：

```text
WattAi/src/stud/g01/runner/Phase2Experiment.java
```

主要功能：

- 执行阶段二实验；
- 比较四种 Open/Closed 模式；
- 比较三种启发函数；
- 执行实验预热；
- 执行 10 次正式运行；
- 检查路径合法性；
- 检查统计数据稳定性；
- 输出原始 CSV；
- 输出中位数汇总 CSV。

### 7.4 `Phase3AstarExperiment.java`

文件：

```text
WattAi/src/stud/g01/runner/Phase3AstarExperiment.java
```

主要功能：

- 调用阶段二实验逻辑；
- 保持 `Phase2Experiment.java` 源码不变；
- 使用独立的阶段三输出文件；
- 使用独立的 PDB 目录；
- 输出阶段三原始数据和汇总数据。

运行输出：

```text
Raw results: experiments/astar-phase3-raw.csv
Summary results: experiments/astar-phase3-summary.csv
```

### 7.5 `B_Phase3ExperimentRunner.java`

文件：

```text
WattAi/src/stud/g01/runner/B_Phase3ExperimentRunner.java
```

主要功能：

- 使用课程框架的主搜索接口；
- 读取指定问题文件；
- 运行两个 8-Puzzle 实例；
- 测试错位将牌数和曼哈顿距离；
- 输出 wall time；
- 输出 CPU time；
- 输出生成结点和扩展结点；
- 输出完整解路径。

---

## 八、实验数据文件

本阶段最终保留以下数据文件：

```text
experiments/astar-phase2-raw.csv
experiments/astar-phase2-summary.csv
experiments/astar-phase3-raw.csv
experiments/astar-phase3-summary.csv
```

其中：

- `raw.csv` 保存每次运行的原始结果；
- `summary.csv` 保存每组实验的中位时间和固定统计值；
- `solvable` 表示问题是否可解；
- `solved` 表示搜索是否成功；
- `path_valid` 表示输出路径是否合法；
- `solution_length` 表示解路径长度；
- `elapsed_ms` 或 `median_ms` 表示运行时间；
- `generated` 表示生成结点数；
- `expanded` 表示扩展结点数；
- `duplicate_pruned` 表示被重复状态规则拒绝的结点数；
- `stale_skipped` 表示从队列中跳过的过期结点数；
- `reopened` 表示重新打开的 Closed 状态数；
- `peak_open` 表示 Open 表峰值；
- `peak_closed` 表示 Closed 表峰值。

---

## 九、阶段验收结论

本阶段已经完成以下交付目标：

- `PqFrontier` 已接入最终 A* 搜索；
- 第一阶段的 `ListFrontier` 已被替换；
- Java `PriorityQueue` 已用于 Open 表；
- Open best-g 已实现；
- Closed best-g 已实现；
- 过期结点过滤已实现；
- Closed 状态 reopen 已实现；
- `PqFrontier` 的 125 项专项检查全部通过；
- `Phase2AStarTest` 的 123 项检查全部通过；
- 两个指定 8-Puzzle 均成功求解；
- 两个实例均得到长度为 31 的解；
- 第一阶段课程入口下的四次验收运行均低于 1 秒；
- `FULL_OPTIMIZED` 在阶段二和阶段三实验中均低于 1 秒；
- 完成四种 Open/Closed 模式比较；
- 完成错位将牌数、曼哈顿距离和 Disjoint Pattern 比较；
- 完成 PDB 构建、保存、加载和查询验证；
- 完成阶段二原始数据和汇总数据输出；
- 完成阶段三原始数据和汇总数据输出；
- 验证了所有实验路径的合法性；
- 验证了所有实验实例的最优解长度。

最终性能结论如下：

```text
FULL_OPTIMIZED + MANHATTAN：

case-1：22.771 ms，扩展 21,197 个结点
case-2：23.892 ms，扩展 21,197 个结点
```

```text
FULL_OPTIMIZED + DISJOINT_PATTERN：

case-1：5.891 ms，扩展 6,055 个结点
case-2：5.925 ms，扩展 6,141 个结点
```

三种启发函数均得到长度为 31 的合法解。

未优化的 `BASELINE` 配置在阶段二实验中约需 4.95–5.17 秒，
在阶段三实验中约需 4.78–5.07 秒。该结果是关闭重复状态处理的
消融对照，用于说明 Open/Closed 优化的效果，不能作为最终优化版
A* 的性能结论。

最终 `FULL_OPTIMIZED` 配置将扩展结点数从 2,531,417 降低到
21,197，减少约 99.16%，并满足第一阶段 1 秒内求解要求。