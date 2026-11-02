# PDB第二阶段实施计划

用户已于2026-10-26确认实施：5+5+5、统一Predictor、保留原型。

## 约束与设计

Java SDK25、UTF-8、无外部依赖。不修改core及成员C算法；第一阶段PatternDatabase继续保留。4×4默认分组为1..5、6..10、11..15，3×3默认1..3、4..6、7..8。空格参与抽象状态但不属于收费分组；反向0–1 BFS，每次真实移动只在所属组收费一次。

新增DisjointPatternDatabase对外暴露loadOrBuild(PuzzleBoard,Path)、load(PuzzleBoard,Path)、build(PuzzleBoard,int[]...)、save(Path)、estimate(PuzzleBoard)、predictor()。内部DensePatternTable负责无重复位置排列排名、byte距离表、原始整数双端队列建表。文件带目标、分组、版本及CRC32；只对缺失数据库自动构建，已有损坏文件报错，不静默替换。生成数据及私人工作记录沿用.gitignore忽略。

## 执行与验收

- [x] 新建DisjointPdbTest：已知距离、与旧原型交叉验证、可采纳性/一致性、持久化往返、损坏拒绝、目标不匹配、A*/IDA*合法路径。已观察缺失实现失败，并完成实现后验证。
- [x] 新增DensePatternTable和DisjointPatternDatabase，正式测试1069281项性质断言通过，第一阶段671354项断言回归通过。
- [x] 新增PdbPhase2Runner和run-pdb-phase2.ps1：Test、Build、Demo、Hard、Compare。生成数据固定放lab1/.local/pdb-phase2，重复启动不重新建表。
- [x] 正式5+5+5建表、再次加载、实际4×4短路径A*/IDA*联调；困难实例已使用C现有算法限时实测并记录TIMEOUT、用时、节点和机器信息。此项仅代表已实测，不代表一分钟达标。
- [x] BFS/BiBFS在相同单位代价8数码问题上比较；已说明不可直接用普通BiBFS替代全表0–1 BFS。接口说明、报告初稿、实测记录及本地私人工作记录已补充。
- [ ] 指定困难实例60秒内求解：当前未达到，需组长与C继续联合优化复测。

提交和推送须经组长授权，不自动合并main。困难实例未在60秒内求解时明确记未达标，不将接入成功等同性能达标。
