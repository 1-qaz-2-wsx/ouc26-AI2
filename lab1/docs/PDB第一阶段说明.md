# PDB第一阶段原型 从建表到查询

组长技术模块。本阶段目标为3×3棋盘上的小规模模式数据库：生成、保存、加载、查询、正确性验证。正式15-Puzzle、不相交分组组合与性能优化留到第二阶段。

## 先理解工作内容

搜索每到一个棋盘都要估计还需要多少步。PDB将部分计算提前完成，保存为“抽象状态 -> 最少移动代价”的表。这里数据库只是程序生成的二进制文件，不需要MySQL或其他数据库服务。

本例选择数字组 {1,2,3}，记录空格与这三个数字的位置，把其他数字视为不可区分。标准目标：

```text
1 2 3
4 5 6
7 8 0
```

抽象后：

```text
1 2 3
* * *
* * 0
```

星号只是说明符号，不是完整棋盘中真正存储的数字。代码使用位置元组 `[空格位置, 1的位置, 2的位置, 3的位置]`，位置从0开始；标准目标为 `[8,0,1,2]`。

## 为何需要0–1 BFS

空格与本组数字交换，计1；与其他数字交换，计0。计算的是本组数字的移动次数，不是所有动作次数。

```text
1 2 3      1 2 3
4 5 6  ->  4 5 6     移动数字8，本组代价0
7 0 8      7 8 0
```

这个棋盘到目标实际只需1步，但本模式的估值为0。这是合法的低估，不是程序漏算。

```text
1 2 0      1 2 3      1 2 3
4 5 3  ->  4 5 0  ->  4 5 6
7 8 6      7 8 6      7 8 0
```

实际解需2步，移动数字3计1、数字6计0，因此模式估值为1。

由于存在0、1两种边权，从抽象目标反向搜索时使用双端队列：0代价改进放队首、1代价改进放队尾，只在找到更小代价时更新。普通无权BFS的“首次访问即固定距离”不能直接用于这里。

以后把不重叠模式的距离相加时，每次真实动作只归属于一个数字组，避免重复计费。不能把每组都按所有动作计1的表随意相加，也不能未经证明再加上整盘曼哈顿距离。

## 文件职责

| 文件 | 作用 |
| --- | --- |
| `WattAi/src/stud/g01/heuristic/pdb/PatternDatabase.java` | 状态投影与编码、0–1 BFS、保存、加载、查询及Predictor适配 |
| `WattAi/src/stud/g01/runner/PdbPrototypeDemo.java` | 展示建表、落盘、重载、查询，并用重载PDB完成已知2步IDA*样例 |
| `WattAi/src/stud/g01/test/PdbPrototypeTest.java` | 独立Dijkstra、完整8数码BFS、文件往返和边界测试；失败会抛异常 |
| `tools/run-pdb-prototype.ps1` | 编译正式源码，运行测试和演示，将产物写入本地忽略目录 |

## 运行方法

在AI2根目录，终端执行：

```powershell
.\lab1\tools\run-pdb-prototype.ps1
```

默认测试与演示一起运行。只看演示：

```powershell
.\lab1\tools\run-pdb-prototype.ps1 -Mode Demo
```

只跑验收：

```powershell
.\lab1\tools\run-pdb-prototype.ps1 -Mode Test
```

IDEA打开lab1/WattAi，JDK25、UTF-8，运行 `stud.g01.runner.PdbPrototypeDemo.main` 或 `stud.g01.test.PdbPrototypeTest.main`。无参默认输出到工作目录的 `.local`。脚本从源码编译，不依赖旧out，也不需要额外Java依赖。

预期演示关键输出：

```text
Pattern: [1, 2, 3]
Reachable abstract states: 3024
Database bytes: 26320
goal: h=0
untracked tile 8 moved: h=0
tile 3 moved, plus untracked tile 6: h=1
IDA* with loaded PDB: moves=2 generated=8 expanded=4
```

3024 = 9×8×7×6，为空格和3个指定数字的不同位置排列数。代码以9为基数编码，分配9^4=6561个int槽位，重复位置槽位不用；文件包含目标、模式、表及CRC32校验值。

## 如何交给搜索算法使用

```java
PatternDatabase pdb = PatternDatabase.build(goalBoard, 1, 2, 3);
pdb.save(Path.of(".local/pdb/group-123.pdb"));
PatternDatabase loaded = PatternDatabase.load(Path.of(".local/pdb/group-123.pdb"));
int h = loaded.estimate(initialBoard);
Predictor predictor = loaded.predictor();
IdAStar solver = new IdAStar(new StackFrontier(), predictor);
```

该片段展示已有API，所需imports参见演示类。数据库目标必须等于实际搜索目标，适配器会检查；换目标需要相应数据库。原型只支持3×3及1至3个非零模式数字，不会对4×4静默返回错误估值。

尚未改成员A维护的 `PuzzleHeuristics.DISJOINT_PATTERN` 全局入口，也未合入成员B的优先队列；此阶段通过直接传入Predictor完成IDA*接入验证，后续再统一整合，避免把原型误用于15-Puzzle。

## 正确性与当前验证

任意真实解路径都能投影为抽象路径。本组移动代价不大于真实总步数，而PDB取所有抽象路径的最小代价，因此h不会高于真实最短距离。

对任意一步有 `h(s) <= c_group(s,s') + h(s')`，其中本组代价为0或1，因此也满足通常的 `h(s) <= 1 + h(s')`。证明依赖准确的代价划分和精确最短路，不是“查表就一定正确”。

2026-10-19已验证：

- 3024个抽象状态的距离全部等于独立Dijkstra计算值。基准使用显式棋盘字符串和优先队列，未复用生产状态编码或0–1 BFS。
- 保存再加载后，3024个距离全部保持一致。
- 独立BFS覆盖181440个真实可达8数码状态，逐个检查低估性质，并检查全部邻接边的本组代价一致性。
- 目标估值、0代价移动、1/2/3个模式数字、非标准目标、输入防御性复制、格式损坏、规模不匹配及IDA*简单接入通过。
- 一次完整验收输出 `ALL PASS: 671354 assertions`。这些是循环中的性质断言，不是671354种独立测试设计。

单个模式表没有涵盖全部数字，不能认为它一定比整盘曼哈顿更强，亦不能以本次原型宣称正式15-Puzzle性能达标。下一阶段研究覆盖完整数字集的不相交分组、紧凑索引、构建成本及与A*/IDA*的公平性能比较。

## 报告材料与提交范围

本说明可用于模板2.4.3的性质论证准备和3.3.3的原型实现部分。正式报告应在15-Puzzle与完整分组实现后补充并修订，不将3×3原型写成最终方案。

提交源码、测试、脚本和此说明；生成的 `.pdb`、class和本地工作记录继续忽略。不在本次运行中自动commit、push或合并main。
