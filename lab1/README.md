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

## D播放器当前运行条件

本次统一入口PR提供Java求解与导出，**不包含可视化修复分支**。当前main原播放器仍有个人路径写死，暂时没有 `run-visualization.ps1`，不能直接套用修复版命令。

D在自己的修复分支完成目录/文件参数、输入校验和SFML2构建说明，让播放器读取上面的PLAYER_INPUT。已有VS2022/SFML2工程可以继续用于本机修复测试；正式代码不能依赖成员个人绝对路径。

等可视化修复PR合入、仓库中存在run-visualization.ps1后，按其README构建，再在AI2根目录执行：

```powershell
.\lab1\tools\run-visualization.ps1 -Mode Check -InputPath '<复制PLAYER_INPUT目录>'
.\lab1\tools\run-visualization.ps1 -Mode Run -InputPath '<同一个目录>'
```

以上两条为修复版接口，未合入修复前不可用。图形验收检查：3/4阶、多题切换、暂停/播放、单步、终点停止、零步与无解禁播；另测空文件/坏文件拒绝，保存真实截图和版本。编译与文件解析通过不替代窗口交互验收。

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
| examples/paths | 原有手工格式样例，不是求解器性能证据 |
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
