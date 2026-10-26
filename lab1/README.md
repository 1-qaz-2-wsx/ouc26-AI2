# N-Puzzle 小组实验

本仓库保存课程资料、Java 搜索工程、小组协作约定和报告材料。开发环境为 IDEA + JDK 25，课程最终截止日期为 2026-11-09。

## 从这里开始

1. 克隆 AI2 仓库，在 IDEA 中打开 `lab1/WattAi`，不要将仓库根目录误当作 Java 模块。
2. 设置 Project SDK 为 JDK 25，模块继承项目 SDK；`src` 为源目录。
3. 阅读 `docs/协作与环境.md`、`docs/棋盘与路径接口.md`、`docs/编译问题记录.md` 和 `docs/第一阶段任务.md`。
4. 在自己分支中进行任务推进，申请合并到main。已有分工保存在 `repo/初始分工.md`。

## 目录

- `WattAi/src`：课程框架与学生实现；主要开发在 `stud/g01`。
- `WattAi/resources`：课程输入实例，保留原始文件。
- `docs`：协作、接口与诊断记录。
- `examples/paths`：成员 D 可独立使用的路径格式样例，属于格式测试数据，不是求解器实测结果。
- `tools`：可重复的诊断工具。
- `repo`：现有分工材料。
- 后续可新增 `visualization`、`report/sections`、`experiments`；大型 PDB 生成文件不直接放入 Git。

## 当前状态

课程原始源码与资料已保存在 `course-baseline` 标签。2026-10-19已完成源码UTF-8统一，正式源码编译和现有IDA*自测通过。A的基础实现与C的IDA*已整合，B的优先队列和其他模块仍须继续验收；不能使用附带的旧 `out` 文件宣称构建通过。最新诊断见 `docs/编译问题记录.md`。

远程仓库为 https://github.com/1-qaz-2-wsx/ouc26-AI2 。各成员克隆同一仓库，在本人分支开发，由组长合并到main。

执行诊断：在 PowerShell 中从 AI2 仓库根目录运行 `./lab1/tools/check-baseline.ps1`，或进入 lab1 后运行 `./tools/check-baseline.ps1`。脚本只转换诊断副本的编码，不改课程源码，日志存入已忽略的 `lab1/.local`。

## 组长第一阶段PDB原型

从AI2根目录执行 `./lab1/tools/run-pdb-prototype.ps1`，编译并运行3×3模式数据库验收和演示。IDEA也可直接运行 `stud.g01.runner.PdbPrototypeDemo`。详细原理、文件职责及验收证据见 `docs/PDB第一阶段说明.md`。这是第一阶段小规模原型，不是正式15-Puzzle数据库；生成文件写入被忽略的 `.local`，不上传。

## 组长第二阶段正式PDB

正式4×4默认5+5+5不相交PDB已提供。成员B/C接入、运行命令和当前验收边界见 [PDB第二阶段接口与验收](docs/PDB第二阶段接口与验收.md)。从AI2根目录运行 `./lab1/tools/run-pdb-phase2.ps1 -Mode Test` 验证，`-Mode Demo` 展示4×4的A*/IDA*接入，`-Mode Hard` 实测指定困难实例。数据库会在本机生成，不上传Git。

实测摘要位于 [experiments/PDB第二阶段实测.md](experiments/PDB第二阶段实测.md)，组长报告初稿位于 [report/sections/PDB与BFS比较初稿.md](report/sections/PDB与BFS比较初稿.md)。当前指定困难实例60秒目标未达到，不等同第二阶段全部通过。
