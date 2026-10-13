# AI2 人工智能课程小组实验

本仓库由五人小组共同维护，以实验目录组织课程资料、代码、测试、可视化和报告。Git根目录为AI2，后续实验直接新增lab2、lab3等目录，使用同一个远程仓库。

## 现有实验

| 目录 | 内容 | 开发入口 |
| --- | --- | --- |
| lab1 | N-Puzzle搜索实验，最终提交研究报告 | IDEA打开 `lab1/WattAi`，JDK 25 |

实验一先阅读 `lab1/README.md`、`lab1/docs/协作与环境.md`、`lab1/docs/棋盘与路径接口.md` 和 `lab1/docs/第一阶段任务.md`。

## Git规则

- `main`用于整合，各成员在功能分支开发并提交合并请求。
- 当前五个功能分支服务于lab1：feature/model、feature/astar、feature/idastar、feature/visualization-tests、feature/pdb。
- lab2开始后从最新main建立带实验编号的分支，例如 `lab2/model`，不把lab2改动放入正在进行的lab1分支。
- 每个实验保留自身的环境与接口说明，不默认lab2必须沿用lab1的JDK或框架。
- 不提交编译产物、个人IDEA设置、游戏引擎缓存、Word锁文件和大型生成数据库。
- 多人不同时修改最终Word报告；先提交个人章节，由组长合稿。

## 历史版本

原有两次提交和 `course-baseline` 标签完整保留。标签对应迁移前的目录结构，其 `WattAi` 位于历史树根目录；迁移后的主分支位于 `lab1/WattAi`。无需重写历史。

2026-10-13将仓库根从lab1迁移到AI2。远程GitHub空仓库由组长创建，真实地址确认后再配置origin并推送；目前本地准备不代表已经上传。
