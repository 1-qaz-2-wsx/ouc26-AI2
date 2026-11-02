# N-Puzzle实验一 最终联调

统一Java入口为 `stud.g01.runner.LabRunner`，使用JDK25、UTF-8和不超过2GiB堆。保留A/B/C已集成代码，本次不修改课程core。

## 同步与IDEA测试

保存个人分支工作后，同步本机main：

```powershell
git switch main
git pull --ff-only origin main
```

IDEA打开 `lab1/WattAi`，Project SDK和Module SDK设为JDK25。共享配置在 `WattAi/.run`：先运行 **01 正确性回归**，再运行 **02 准备PDB**，然后选择 **03—07** 求解并导出、**08 正式一分钟**或**09 同机比较**。VM options固定 `-Xmx2g`；工作目录为WattAi工程目录。

若配置未出现，创建Application：Main class填 `stud.g01.runner.LabRunner`，模块填WattAi。参数见[最终联调与IDEA运行指南](docs/最终联调与IDEA运行指南.md)。

## D先取得真实播放文件

不需要等待A，也不要求播放器与Java实时联动。在仓库根目录AI2的PowerShell运行：

```powershell
# 编译、求解、导出四类用例；无需PDB
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm ASTAR -Heuristic MANHATTAN
```

控制台打印 `PLAYER_INPUT=本次路径目录` 和 `RESULTS_CSV=本次结果表`。目录中的case-1至case-4.txt分别为3×3两步、4×4两步、零步、不可解，由现有搜索器实际导出并校验。播放器只读NP_PATH_V1路径txt，CSV不是路径，resources/final-smoke.txt是原始问题输入，也不能拿来播放。

需要真实70步难题路径时：

```powershell
# 离线准备3阶/4阶目标PDB；已有完整缓存则加载
.\lab1\tools\run-final-tests.ps1 -Mode Build
# 固定原题、555及对称、TT1048576和60秒总预算
.\lab1\tools\run-final-tests.ps1 -Mode Hard
```

Hard成功后也打印独立PLAYER_INPUT目录。计时包含加载、搜索恢复、路径校验；写文件和额外复制在计时后。每次默认输出到新目录，避免旧文件冒充本次结果。

## D播放器现在如何运行

播放器已支持文件/目录参数、相对路径发现、严格输入校验和可重复构建，不再使用成员个人路径。需要CMake3.24+、C++17编译器；SFML固定2.6.2。首次源码构建需要网络，代码与校验值固定，不安装系统软件。已有VS2022可使用匹配的CMake/编译环境；已有CLion但工具不在PATH时，加 `-ToolchainRoot '<CLion安装目录>'`。

在AI2根目录执行：

```powershell
# 先检查解析和播放状态，不需要SFML
.\lab1\tools\run-visualization.ps1 -Mode Test
# 首次构建图形播放器
.\lab1\tools\run-visualization.ps1 -Mode Build -FetchSFML
# 校验并打开仓库中的真实求解样例
.\lab1\tools\run-visualization.ps1 -Mode Check
.\lab1\tools\run-visualization.ps1 -Mode Run
```

默认加载examples/paths，包含原有两个手工格式例子，以及solver前缀的五个真实求解样例：3阶、4阶、零步、无解和70步难题。来源见[路径来源](examples/paths/求解器样例来源.md)。已有匹配SFML2.6.2时可用SFML_DIR，具体见[播放器README](visualization/README.md)。

测试刚由LabRunner生成的结果时，把目录替换为控制台PLAYER_INPUT：

```powershell
.\lab1\tools\run-visualization.ps1 -Mode Check -InputPath '<PLAYER_INPUT目录>'
.\lab1\tools\run-visualization.ps1 -Mode Run -InputPath '<同一个目录>'
```

左右键切题，上下键单步，空格暂停/播放，Home/End首末帧，+/-调速，Esc关闭；也有鼠标按钮。初始暂停，到目标停止，切题重置；零步和无解不播放。默认Build/Run/Check使用.local/player-build/gui；Test使用.local/player-build/parser，已有其他构建用-BuildDirectory指定。MinGW运行库自动复制到构建目录，编译产物不提交。

D重点完成实际窗口验收和截图：播放/暂停、鼠标、速度、窗口缩放、零步及无解；底层解析/状态测试和已记录的单步/切题观察不替代所有界面操作验收。[修复与验收记录](docs/可视化修复与验收.md)明确列出已验证和待确认项。

## 其他最终测试

```powershell
.\lab1\tools\run-final-tests.ps1 -Mode Verify
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm ASTAR -Heuristic PDB
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm IDA -Heuristic PDB
.\lab1\tools\run-final-tests.ps1 -Mode Solve -Algorithm PACKED_IDA -Heuristic PDB
.\lab1\tools\run-final-tests.ps1 -Mode Compare
.\lab1\tools\run-final-tests.ps1 -Mode Query
```

缓存统一在 `lab1/.local/leader-pdb`，结果和日志在 `lab1/.local/final-tests`。Solve和公共PDB接口只加载预建表，缺表或新目标先Build；不会在正式求解中隐式建表。普通Solve预算不代表固定Hard验收。完整参数见[运行指南](docs/最终联调与IDEA运行指南.md)，证据见[接入与验收记录](docs/统一入口接入与验收记录.md)。

## 文件夹与材料

| 目录 | 用途 |
|---|---|
| WattAi/src/core | 课程框架 |
| WattAi/src/stud/g01 | 棋盘、启发函数、PDB、搜索器、入口与测试 |
| WattAi/.run | 共享IDEA配置；个人.idea不上传 |
| WattAi/resources | 课程原题及四类联调输入 |
| examples/paths | 两个手工格式样例与五个真实求解样例，来源分别记录 |
| visualization | D的SFML播放器，当前条件见上文 |
| tools | 最终测试、PDB实验与诊断脚本 |
| experiments | 整理后的共享数据 |
| report/sections | 个人报告材料，最终报告由组长合稿 |
| docs | 环境、接口、验收、工作记录与运行指南 |
| repo | 分工与阶段安排，私人工作记录不上传 |

## 历史与协作

原始版本保存在course-baseline标签。旧文档中的“未接入/60秒未达标”指当时版本，以最新验收记录为准，不删除历史失败数据。

[PDB第一阶段](docs/PDB第一阶段说明.md)、[第二阶段历史接口](docs/PDB第二阶段接口与验收.md)、[一分钟验收](docs/PDB一分钟验收.md)、[组长第三阶段记录](docs/组长第三阶段工作记录.md)、[分工](repo/初始分工.md)。

所有变更通过开发分支、PR、验收后合并main。数据库、日志、编译产物及私人记录继续忽略；不能依据旧out目录宣称构建通过。
