# PDB第二阶段：成员B/C接入与验收

2026-10-26。正式实现支持3×3和4×4；4×4默认5+5+5，旧PatternDatabase仍仅为第一阶段原型。数据库是二进制文件，无需安装数据库软件。

## 直接交给成员C的代码

```java
import java.nio.file.Path;
import core.solver.algorithm.heuristic.Predictor;
import stud.g01.heuristic.pdb.DisjointPatternDatabase;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

// goalBoard使用当前问题的真实目标，不能随便替换为另一个目标。
// 在main或初始化代码中执行一次；main可声明throws Exception。
DisjointPatternDatabase pdb = DisjointPatternDatabase.loadOrBuild(
        goalBoard, Path.of(".local/pdb-phase2/data"));
Predictor predictor = pdb.predictor();
IdAStar solver = new IdAStar(new StackFrontier(), predictor);
// Deque<Node> path = solver.search(problem);
```

片段中的goalBoard/problem由调用者提供。IDEA运行目录通常为WattAi，脚本运行则使用lab1下的固定绝对缓存目录；两处相对路径可能不同，不代表加载失败。需要共享同一份本地缓存时，传入同一个绝对目录。

成员C不需要修改构造器，不需要在搜索器里读文件。每个目标只加载/创建一个PDB对象；所有节点共享Predictor。初始化可能抛IOException：损坏文件应停止并报告，不能记为无解。首次会生成，随后只加载。不要在heuristics方法内执行loadOrBuild。

组长提供数据库与Predictor；C维护IDA*搜索、剪枝、阈值和统计；B维护优先队列/A*实验。改动公共入口PuzzleHeuristics/Feeder前由组长与A协调，本次未改A的文件。

## A*接入

```java
import core.solver.algorithm.searcher.BestFirstSearcher;
import core.solver.queue.Node;
import core.solver.queue.EvaluationType;
import stud.queue.ListFrontier;

BestFirstSearcher aStar = new BestFirstSearcher(
        new ListFrontier(Node.evaluator(EvaluationType.FULL)), predictor);
```

上面使用课程已有ListFrontier，只证明接入与路径正确，不代表B的优先队列性能验收。B整合已验收的PqFrontier后，只需替换frontier，predictor不变。不修改core框架。

## 公开接口

| 方法 | 约定 |
| --- | --- |
| loadOrBuild(PuzzleBoard goal, Path directory) | 默认分组；精确缓存不存在才生成并保存，已有损坏文件抛IOException |
| load(PuzzleBoard goal, Path directory) | 只加载默认分组，不自动建表 |
| build(PuzzleBoard goal, int[]... groups) | 自定义分组，各组1..5个非零数字，不交叠并覆盖全部数字 |
| save(Path directory) | 保存当前分组，临时文件写完后替换目标文件 |
| load(PuzzleBoard goal, Path directory, int[]... groups) | 显式加载自定义分组 |
| estimate(PuzzleBoard board) | 返回非负整数下界；错误规模抛异常 |
| predictor() | 返回框架Predictor；检查目标必须完全匹配 |
| cachePath(PuzzleBoard goal, Path directory) | 默认分组对应的精确文件路径，用于查看是否已有缓存 |
| distanceBytes() | 距离数组字节数，不含对象、建表队列、文件头或加载缓冲区 |

分组顺序与组内顺序也参与缓存标识。文件名含格式版本及目标+分组的SHA-256摘要片段，文件内部还校验完整目标、分组、版本、长度及CRC32。不同分组不会误覆盖。CRC用于发现意外损坏，不是防恶意篡改认证。

默认4×4为{1,2,3,4,5}/{6,7,8,9,10}/{11,12,13,14,15}，3×3为{1,2,3}/{4,5,6}/{7,8}。移动本组数字计1，其他数字计0；空格参与定位但不计入数字分组。不能在PDB总和外再直接加整盘曼哈顿。

## 组长/组员操作

从AI2根目录执行：

```powershell
# 自动编译，全量正确性与BFS/BiBFS测试
.\lab1\tools\run-pdb-phase2.ps1 -Mode Test
# 首次生成正式表；已有表则直接加载
.\lab1\tools\run-pdb-phase2.ps1 -Mode Build
# 4×4已知最优4步：A*、IDA*与路径合法性
.\lab1\tools\run-pdb-phase2.ps1 -Mode Demo
# 指定困难实例，C现有IDA*，搜索时间预算60秒
.\lab1\tools\run-pdb-phase2.ps1 -Mode Hard
# 相同8数码题上的BFS与BiBFS比较
.\lab1\tools\run-pdb-phase2.ps1 -Mode Compare
```

Test会使用独立临时子目录并故意损坏测试文件，不修改正式data缓存。Hard使用stud.g01.solver.IdAStar，兼容层stud.solver.IdAStar尚未转发限时设置方法，不要混淆导入。C的时间检查不是实时强制中断，输出实际wall时间；超过60秒才发现目标也不能算达标。

正式缓存、class及原始日志位于lab1/.local/pdb-phase2，均已被.gitignore排除。共享的是源码、脚本和整理后的实验结果，组员可自行生成数据库。私人工作记录位于repo/工作记录，也不上传。

## 当前验收结论

- 正式5+5+5构建与跨进程重载通过；距离数据17,297,280字节。
- 181,440个真实8数码状态的可采纳性/一致性、与独立编码原型交叉检查、重载一致性通过。
- 正式4×4检查1806个独立BFS浅层状态及10000条固定种子随机边，检查曼哈顿下界、一致性和重载一致性。
- A*/IDA*真实4×4四步最优路径与逐步合法性通过。
- 指定困难实例60秒目标尚未达到，详见experiments/PDB第二阶段实测.md。不能宣称第二阶段全部验收完成。

## 现在交给C的联调任务

1. 获取本次代码后先运行Test、Demo，确认本机SDK25和UTF-8无误。
2. 用上述Predictor替换曼哈顿；保留曼哈顿模式，方便同题对照。
3. 对同一难例记录状态、实际用时、CPU计时可用性、生成/展开节点和阈值。超时不输出伪造解路径。
4. 在C分支评估路径去环、后继排序和减少重复分配等优化，改动后仍验证最优性及路径合法性；不承诺任何一种优化必然达标。
