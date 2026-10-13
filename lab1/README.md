# N-Puzzle 小组实验

本仓库保存课程资料、Java 搜索工程、小组协作约定和报告材料。开发环境为 IDEA + JDK 25，课程最终截止日期为 2026-11-09。

## 从这里开始

1. 克隆 AI2 仓库，在 IDEA 中打开 `lab1/WattAi`，不要将仓库根目录误当作 Java 模块。
2. 设置 Project SDK 为 JDK 25，模块继承项目 SDK；`src` 为源目录。
3. 阅读 `docs/协作与环境.md`、`docs/棋盘与路径接口.md`、`docs/编译问题记录.md` 和 `docs/第一阶段任务.md`。
4. 从最新 `main` 建立或更新自己的功能分支。已有分工保存在 `repo/初始分工.md`。

## 目录

- `WattAi/src`：课程框架与学生实现；主要开发在 `stud/g01`。
- `WattAi/resources`：课程输入实例，保留原始文件。
- `docs`：协作、接口与诊断记录。
- `examples/paths`：成员 D 可独立使用的路径格式样例，属于格式测试数据，不是求解器实测结果。
- `tools`：可重复的诊断工具。
- `repo`：现有分工材料。
- 后续可新增 `visualization`、`report/sections`、`experiments`；大型 PDB 生成文件不直接放入 Git。

## 当前状态

课程原始源码与资料已保存在 `course-baseline` 标签。原始工程尚不能完整编译，也尚未实现求解器；不能使用附带的旧 `out` 文件宣称构建通过。

远程地址尚未配置。组长选择 GitHub 或 Gitee 的私有仓库并邀请成员后，将地址写入协作文档。

执行诊断：在 PowerShell 中从 AI2 仓库根目录运行 `./lab1/tools/check-baseline.ps1`，或进入 lab1 后运行 `./tools/check-baseline.ps1`。脚本只转换诊断副本的编码，不改课程源码，日志存入已忽略的 `lab1/.local`。
