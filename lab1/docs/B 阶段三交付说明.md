- - # B 阶段三交付说明：合入最终优先队列，完成第一阶段验收，比较 Open/Closed 优化及三种启发函数

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

    ## 二、最终优先队列实现

    阶段二和阶段三使用：

    ```text
    stud.g01.queue.PqFrontier
    ```

    替换了第一阶段使用的：

    ```text
    ListFrontier
    ```

    `PqFrontier` 基于 Java `PriorityQueue` 实现 A* 的 Open 表，按照：

    ```text
    f(n) = g(n) + h(n)
    ```

    对搜索结点进行排序。

    主要实现内容如下：

    - 通过优先队列获取当前评价值最小的结点；
    - 使用 `activeNodes` 记录每个状态当前已知的最优 `g` 值；
    - 当同一状态出现更小的路径代价时，保留新结点；
    - 当同一状态的旧结点从队列中取出时，判断其是否已经过期；
    - 跳过已经被更优路径替代的过期结点；
    - 支持不同 Open/Closed 优化策略；
    - 记录生成结点数、扩展结点数和重复结点处理情况。

    `PqFrontier` 专项测试结果如下：

    ```text
    测试组数：6 组
    检查项目：125 项
    通过情况：全部通过
    ```

    ## 三、第一阶段验收结果

    通过以下测试入口完成验收：

    ```text
    WattAi/src/core/runner/SearchTester.java
    ```

    测试使用两个指定的 8-Puzzle 实例，并分别使用错位将牌数和曼哈顿距离作为启发函数。

    两个实例均成功求得长度为 `31` 的解。

    | case_id | heuristic | status | moves | wall_time_ms | cpu_time_ms | generated | expanded |
    | ------- | --------- | ------ | ----: | -----------: | ----------: | --------: | -------: |
    | case-1  | MISPLACED | SOLVED |    31 |   313.151600 |  296.875000 |   385,076 |  143,848 |
    | case-2  | MISPLACED | SOLVED |    31 |   199.274400 |  187.500000 |   385,076 |  143,848 |
    | case-1  | MANHATTAN | SOLVED |    31 |    34.616000 |   46.875000 |    55,925 |   21,197 |
    | case-2  | MANHATTAN | SOLVED |    31 |    45.346500 |   31.250000 |    55,925 |   21,197 |

    与错位将牌数相比，曼哈顿距离将扩展结点数从：

    ```text
    143,848
    ```

    减少到：

    ```text
    21,197
    ```

    减少约 `85.3%`，两个指定实例均满足第一阶段的求解要求。

    ## 四、Open/Closed 优化比较

    `Phase2AStar` 中实现了以下四种搜索模式：

    | 搜索模式         | Open 表 best-g | Closed 表 best-g | 支持重新打开 Closed 结点 |
    | ---------------- | -------------: | ---------------: | -----------------------: |
    | `BASELINE`       |             否 |               否 |                       否 |
    | `OPEN_BEST_G`    |             是 |               否 |                       否 |
    | `CLOSED_BEST_G`  |             否 |               是 |                       是 |
    | `FULL_OPTIMIZED` |             是 |               是 |                       是 |

    四种模式使用相同的 A* 框架和相同的测试实例，主要区别在于重复状态的保存和过滤方式。

    阶段三实验中，四种模式在曼哈顿距离下均得到：

    ```text
    解长度：31
    生成结点：55,925
    扩展结点：21,197
    ```

    由于曼哈顿距离具有一致性，较差路径通常会在结点扩展前被过滤，因此不同 Open/Closed 模式的最终生成结点数和扩展结点数相同。优化效果主要体现在：

    - 优先队列中的重复结点管理；
    - 过期结点过滤；
    - Open 表和 Closed 表的 best-g 判断；
    - 运行时间和队列操作开销。

    这表明 Open/Closed 优化保持了搜索结果的正确性，同时完善了重复状态处理机制。

    ## 五、三种启发函数比较

    在 `FULL_OPTIMIZED` 模式下比较了三种主要启发函数：

    - `MISPLACED`：错位将牌数；
    - `MANHATTAN`：曼哈顿距离；
    - `DISJOINT_PATTERN`：Disjoint Pattern 数据库启发函数。

    实验结果如下：

    | 启发函数           | 解长度 |         生成结点 |       扩展结点 | 运行时间表现  |
    | ------------------ | -----: | ---------------: | -------------: | ------------- |
    | `MISPLACED`        |     31 |          385,076 |        143,848 | 约 180–240 ms |
    | `MANHATTAN`        |     31 |           55,925 |         21,197 | 约 18–31 ms   |
    | `DISJOINT_PATTERN` |     31 | 约 16,342–16,648 | 约 6,055–6,141 | 约 5–7 ms     |

    三种启发函数均得到长度为 `31` 的解，说明它们都能够在当前测试中保持 A* 的最优解结果。

    从扩展结点数量来看：

    ```text
    MISPLACED：143,848
    MANHATTAN：21,197
    DISJOINT_PATTERN：约 6,055–6,141
    ```

    曼哈顿距离相较于错位将牌数显著缩小了搜索空间，Disjoint Pattern 的搜索规模进一步降低，说明更强的启发函数能够有效提高 A* 的搜索效率。

    ## 六、PDB 构建和加载结果

    Disjoint Pattern 使用的 PDB 能够完成构建、保存、加载和查询。

    本次实验记录如下：

    ```text
    case-1 PDB 构建时间：79.869 ms
    case-2 PDB 加载时间：0.013 ms
    PDB 文件大小：6552 bytes
    ```

    实验使用独立的 PDB 目录：

    ```text
    lab1/.local/pdb-phase3
    ```

    第一次运行时构建并保存 PDB，后续测试直接加载已有数据库，从而避免重复构建。

    ## 七、修改和新增文件

    ### 1. `WattAi/src/stud/g01/queue/PqFrontier.java`

    完成最终 A* 优先队列实现，包括：

    - 基于 `PriorityQueue` 的 Open 表；
    - `activeNodes` best-g 管理；
    - 重复状态处理；
    - 过期结点跳过；
    - 队列及搜索统计支持。

    ### 2. `WattAi/src/stud/g01/solver/Phase2AStar.java`

    完成阶段二 A* 搜索实现，包括：

    - 接入 `PqFrontier`；
    - 支持四种 Open/Closed 搜索模式；
    - 实现 Open 表和 Closed 表的 best-g 判断；
    - 支持必要的 Closed 结点重新打开；
    - 接入三种启发函数；
    - 记录生成结点、扩展结点和运行时间；
    - 恢复并验证最终解路径。

    ### 3. `WattAi/src/core/runner/SearchTester.java`

    更新第一阶段验收程序，包括：

    - 使用最终 `PqFrontier`；
    - 运行两个指定 8-Puzzle 实例；
    - 测试 `MISPLACED` 和 `MANHATTAN`；
    - 输出运行时间、生成结点、扩展结点和解长度。

    ### 4. `WattAi/src/stud/g01/runner/Phase2Experiment.java`

    完成包路径调整：

    ```text
    WattAi/src/stud/runner/Phase2Experiment.java
    →
    WattAi/src/stud/g01/runner/Phase2Experiment.java
    ```

    上次放到外面的runner文件夹了

    ### 5. `WattAi/src/stud/g01/runner/Phase3AstarExperiment.java`

    新增阶段三实验入口，用于：

    - 比较四种 Open/Closed 模式；
    - 比较三种启发函数；
    - 执行预热和正式运行；
    - 输出阶段三独立实验结果；
    - 使用独立的 PDB 目录；
    - 保存原始数据和汇总数据。

    

    ## 八、阶段验收结论

    本阶段已经完成以下交付目标：

    - `PqFrontier` 已接入最终 A* 搜索；
    - 第一阶段旧 `ListFrontier` 已被替换；
    - `activeNodes` best-g 重复状态处理已实现；
    - 过期结点能够在 `poll` 时被跳过；
    - `PqFrontier` 6 组测试、125 项检查全部通过；
    - 两个指定 8-Puzzle 均求得长度为 31 的解；
    - 完成错位将牌数和曼哈顿距离的第一阶段验收；
    - 完成四种 Open/Closed 模式比较；
    - 完成三种启发函数比较；
    - 完成 PDB 的构建、保存、加载和查询验证；
    - 阶段二和阶段三实验数据已经分别输出并保存。