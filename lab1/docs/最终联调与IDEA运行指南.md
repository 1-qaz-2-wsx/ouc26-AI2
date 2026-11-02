# 最终联调与IDEA运行指南

本轮统一运行和文件契约，不替换成员搜索器、不修改课程core。统一总入口为 `stud.g01.runner.LabRunner`，终端配套 `tools/run-final-tests.ps1`。旧实验入口留作历史对照。

## IDEA先运行什么

1. IDEA打开 `lab1/WattAi`，Project SDK与Module SDK均为JDK25，源码编码UTF-8。src应标记为Sources Root；模块名为WattAi。
2. 运行配置位于工程的 `.run` 目录，版本库共享。重新打开工程或从运行列表选择配置；没有出现时手动创建Application：Main class填LabRunner，module填WattAi，工作目录填WattAi工程目录，VM options填 `-Xmx2g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`，参数见下表。
3. 先运行01正确性回归，再运行02准备PDB，再运行普通求解或08正式一分钟。准备PDB是离线过程，不混入正式Hard。
4. 每次Solve自动新建输出目录，控制台打印PLAYER_INPUT与RESULTS_CSV。路径文件只含经验证的成功或确认不可解结果，超时不伪装成无解。不要在播放器中选择原始问题输入文件。
5. 若出现PDB缺失，先Build；新目标也需针对该目标Build。目录可用 `--cache` 或VM属性 `-Dnpuzzle.pdb.dir=...` 指定，避免复用不同目标的错误表。

| IDEA配置 | 程序参数 | 用途 |
|---|---|---|
| 01 正确性回归 | Verify | 队列、A*、通用/紧凑IDA*、旧/新PDB、BFS及入口集成 |
| 02 准备PDB | Build | 默认final-smoke中的3阶/4阶标准目标数据库，已有表加载 |
| 03 A星曼哈顿 | Solve --algorithm ASTAR --heuristic MANHATTAN | 基础与可视化导出 |
| 04 通用IDA曼哈顿 | Solve --algorithm IDA --heuristic MANHATTAN | 保留通用搜索器 |
| 05 A星PDB | Solve --algorithm ASTAR --heuristic PDB | PDB接入A* |
| 06 通用IDAPDB | Solve --algorithm IDA --heuristic PDB | PDB接入通用IDA* |
| 07 紧凑IDAPDB | Solve --algorithm PACKED_IDA --heuristic PDB | PDB与对称快路径，导出播放文件 |
| 08 正式一分钟 | Hard | 原题、555及对称、完整身份TT1048576、预算60000ms |
| 09 同机比较 | Compare | 现有2题、四种Open/Closed筛选及三启发/IDA比较 |
| 10 PDB查询 | Query | 完整Predictor查询微基准 |

默认final-smoke只含四个边界用例：3阶两步、4阶两步、零步、不可解，不代替性能验收。两道指定8数码使用 `--input resources/test_8puzzle.txt`（工作目录WattAi）；终端从仓库根使用 `-InputPath ./lab1/WattAi/resources/test_8puzzle.txt`。配套4阶全集含困难题，选择紧凑IDA+PDB，不用普通A*盲跑。

## 终端对应操作

在仓库AI2根目录：

```powershell
.\lab1\tools\run-final-tests.ps1 -Mode Verify
.\lab1\tools\run-final-tests.ps1 -Mode Build
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm ASTAR -Heuristic PDB
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm PACKED_IDA -Heuristic PDB
1..3 | ForEach-Object { .\lab1\tools\run-final-tests.ps1 -Mode Hard }
```

默认缓存统一为 `lab1/.local/leader-pdb`，日志与结果为 `lab1/.local/final-tests`。原缓存不改写，必要时显式传目录。Build脚本强制30分钟上限；IDEA离线构建若异常长时间未完成应停止排查，当前Build仅用默认小分组。Java单进程堆上限2GiB，未关闭其他软件。

Solve可以设置 `--budget-ms`，用于普通联调；修改它不代表修改正式一分钟标准。Hard不接受algorithm/heuristic/budget/tt参数，固定调用原LeaderBenchmarkRunner。总时间包含加载、搜索恢复、校验，导出发生于校验之后。成功后另复制路径到新目录，打印PLAYER_INPUT。

A*选择现有Phase2AStar，默认BASELINE；可用 `--astar-mode FULL_OPTIMIZED` 显式切换。通用IDA默认完整身份置换表1048576，`--tt 0`关闭；紧凑IDA默认同容量并启用目标保持对称。普通IDA与紧凑IDA的启发/生成计数口径不同，比较时必须写明。

A*若预算超时，生成/展开填-1表示未获得中断现场统计，不编造0或已完成值。CPU使用当前线程计数；极短任务可能0。任何非SOLVED/UNSOLVABLE状态不导出播放文件，运行返回非零。输出目录非空直接拒绝，防止旧成功路径冒充本次结果。

## D如何接可视化

搜索与播放器仍通过NP_PATH_V1解耦，无需实时联动。默认播放器已包含五个solver前缀真实样例，也可以使用LabRunner新导出的文件。

在AI2根目录先构建并验证：

```powershell
.\lab1\tools\run-visualization.ps1 -Mode Test
.\lab1\tools\run-visualization.ps1 -Mode Build -FetchSFML
.\lab1\tools\run-visualization.ps1 -Mode Check
.\lab1\tools\run-visualization.ps1 -Mode Run
```

需要CMake3.24+、C++17编译器；SFML固定2.6.2。已有匹配SFML可设置SFML_DIR。首次-FetchSFML需要网络；已有CLion工具链可加-ToolchainRoot。默认GUI构建目录为lab1/.local/player-build/gui。

运行Solve/Hard后，将PLAYER_INPUT目录传给 `-Mode Check -InputPath '<目录>'` 和 `-Mode Run -InputPath '<目录>'`。只读NP_PATH_V1的txt，不把CSV或题目输入当播放文件。多个文件按文件名排序。

验收3阶/4阶、切题、暂停/播放、单步、到目标停止、零步与无解禁播，以及空/坏文件拒绝，保存真实截图和版本。底层测试已通过，仍需签收尚未实际确认的窗口操作；见[可视化修复与验收](可视化修复与验收.md)。

## 与各成员分支的协调

本分支已同步99d97e3，包含A的PR #13、C的PR #15、B的PR #16。只补统一入口、公共PDB适配、固定测试输入、IDEA配置与说明。未改IdAStar、PackedIdAStar、B队列或core。

同步时已保留A的随机/空位与曼哈顿缓存功能，并加入本次DISJOINT_PATTERN分支；没有用旧文件覆盖A。A的IdaStarTester已独立运行通过，原始stage2_records.csv和stage2_output按工作目录精准忽略；共享整理数据仍放experiments。

Verify是集成冒烟与主要回归；A随机研究、C模块实验、D图形验收各自保留。统一入口通过独立PR进入main，最终图形验收仍需在可视化修复合入后的同一版本完成。
