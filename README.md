# AI2 人工智能课程小组实验

本仓库由五人小组共同维护，保存人工智能课程实验的资料、代码、测试与研究报告。后续实验新增 `lab2`、`lab3` 等目录，继续使用同一个仓库。

## 文件夹说明

| 目录 | 作用 |
| --- | --- |
| `lab1/` | 实验一 N-Puzzle；目录中的 Word/PDF 是课程要求、讲解和最终研究报告模板 |
| `lab1/WattAi/` | Java搜索工程；IDEA打开此目录，使用JDK 25 |
| `lab1/WattAi/src/core/` | 老师提供的搜索框架，改动由组长集中协调 |
| `lab1/WattAi/src/stud/g01/` | 本组棋盘、Feeder、优先队列、IDA*等实现的主要位置 |
| `lab1/WattAi/src/stud/`的其他目录 | 寻路示例和队列参考实现 |
| `lab1/WattAi/src/algs4/` | 课程提供的计时及随机数工具 |
| `lab1/WattAi/resources/` | 课程测试输入 |
| `lab1/docs/` | Git协作、棋盘接口、第一阶段任务及编译问题记录 |
| `lab1/examples/paths/` | 可视化可直接读取的路径格式样例，非求解器实测结果 |
| `lab1/tools/` | 编译诊断工具，不是求解程序 |
| `lab1/repo/` | 小组分工与阶段DDL材料；目录名repo不代表另一个Git仓库 |

`out`、`.idea`、`.local`及游戏引擎缓存只保留本机，不上传。可视化、实验数据和报告章节目录由对应成员开发时新增。

## 开始开发

```powershell
git clone https://github.com/1-qaz-2-wsx/ouc26-AI2.git
cd ouc26-AI2
git config core.autocrlf false
```

IDEA打开 `lab1/WattAi`，Project SDK和模块SDK设为JDK 25。原始骨架目前存在混合编码及IDA*接入问题，详见编译记录；不能以旧 `out` 文件作为编译通过的依据。

实验一先阅读 `lab1/README.md`、`lab1/docs/协作与环境.md`、`lab1/docs/棋盘与路径接口.md` 和 `lab1/docs/第一阶段任务.md`。

## Git规则

- `main`用于整合，各成员在功能分支开发并提交合并请求。
- 当前五个功能分支服务于lab1：feature/model、feature/astar、feature/idastar、feature/visualization-tests、feature/pdb。
- lab2开始后从最新main建立带实验编号的分支，例如 `lab2/model`，不把lab2改动放入正在进行的lab1分支。
- 每个实验保留自身的环境与接口说明，不默认lab2必须沿用lab1的JDK或框架。
- 不提交编译产物、个人IDEA设置、游戏引擎缓存、Word锁文件和大型生成数据库。
- 多人不同时修改最终Word报告；先提交个人章节，由组长合稿。
- 每次合并请求写明改动内容、运行方式、测试结果和未完成问题。

## 历史版本

原有两次提交和 `course-baseline` 标签完整保留。标签对应迁移前的目录结构，其 `WattAi` 位于历史树根目录；迁移后的主分支位于 `lab1/WattAi`。无需重写历史。

仓库地址：https://github.com/1-qaz-2-wsx/ouc26-AI2
