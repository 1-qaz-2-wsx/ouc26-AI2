# N-Puzzle 路径播放器

D 原始实现：zhang-xinrui1234，提交 `7be526863b8768ca4789d94b3b8210093e512c24`（`origin/feature/visualization-tests`）。原实现提供多文件、棋盘绘制和按键交互。组长接手修复输入校验、目录与字体、边界和构建配置；不将原作者贡献归为组长原创。原提交保留不变，本机原始文件证据位于忽略目录 `lab1/.local/D-original-7be5268/`。

## 独立解析器测试（不需要 SFML）

需要 CMake 3.24+ 和支持 C++17 的编译器。在仓库根目录运行：

```powershell
cmake -S lab1/visualization -B lab1/.local/player-build/parser -DNP_BUILD_PLAYER=OFF
cmake --build lab1/.local/player-build/parser --config Release
ctest --test-dir lab1/.local/player-build/parser -C Release --output-on-failure
```

`path_check [目录或文件]` 提供无图形校验；不传参数时从当前目录及程序目录向上寻找 `lab1/examples/paths`。例如在仓库根目录运行：

```powershell
lab1/.local/player-build/parser/Release/path_check.exe lab1/examples/paths
```

单配置构建（Ninja/Makefiles）的程序直接位于构建目录，省略 `Release/`。校验器全部合法返回 0，混合目录中有拒绝文件返回 2，无合法文件或输入错误返回 1。图形播放器记录并跳过坏文件；目录没有任何有效路径时退出并打印原因，不创建空播放器。

## SFML 2.6.2 图形构建

必须使用 **SFML 2.6.2**（SFML 3 的接口不同）。已有匹配编译器的 SFML 时，设置 `SFML_DIR` 指向包含 `SFMLConfig.cmake` 的目录：

```powershell
cmake -S lab1/visualization -B lab1/.local/player-build/gui -DSFML_DIR="<SFML的cmake目录>"
cmake --build lab1/.local/player-build/gui --config Release
```

也可显式启用联网源码构建，不安装任何系统软件。CMake 获取 SFML 2.6.2 的固定提交源码包 `5383d2b3948f805af55c9f8a4587ac72ec5981d1`，不跟随最新版本：

```powershell
cmake -S lab1/visualization -B lab1/.local/player-build/gui -DNP_FETCH_SFML=ON
cmake --build lab1/.local/player-build/gui --config Release
ctest --test-dir lab1/.local/player-build/gui -C Release --output-on-failure
```

首次需网络；后续构建可复用本地依赖。MinGW 建议 Ninja 生成器并确保编译器 `bin` 在 PATH 中。使用预装 SFML 的动态链接版本时，也须按其说明让相同编译器版本的 SFML DLL 可被找到。示例路径中的占位文字须替换为本机安装位置。

## 运行与字体

```powershell
npuzzle_player.exe [目录或文件] [--font 字体文件.ttf]
npuzzle_player.exe lab1/visualization/test_cases --font C:/Windows/Fonts/arial.ttf
```

默认输入为仓库 `lab1/examples/paths`，按文件名排序。支持一个文件或目录中的 `.txt` 文件。字体优先使用 `--font`；未指定时依次寻找当前目录/程序目录的 `resources/DejaVuSans.ttf`，Windows再尝试微软雅黑、黑体、Arial的系统字体。仓库不捆绑系统字体。字体不存在则给出明确错误并退出；Linux/macOS请显式传入本机字体。文件名按UTF-8显示，超长文件名缩略显示。没有任何个人用户目录写死在代码中。

左右键切换题目，上下键逐帧，空格播放/暂停，Esc退出；Home回到开头，End跳到终点，+/-调节速度（每步0.125至2秒）。界面提供对应鼠标按钮和进度条，窗口缩放保持棋盘比例。初始暂停；播放到目标停止，再按空格从头播放。切题重置第0帧并暂停。零步题只显示目标；无解题明确显示 `No solution - display only`，空格不会启动播放。播放器不重新求解，不以TIMEOUT替代无解。

默认加载的目录包含五个solver前缀的真实求解器样例，覆盖3×3、4×4、零步、无解和70步难题；来源见examples/paths/求解器样例来源.md。原有one-move文件继续作为人工格式样例。

## PowerShell工具入口

从仓库根目录运行，CMake和编译器需在PATH中；已有Windows CLion工具链时可通过 `-ToolchainRoot '<CLion安装目录>'` 显式指定，不需修改系统PATH。

```powershell
.\lab1\tools\run-visualization.ps1 -Mode Test
.\lab1\tools\run-visualization.ps1 -Mode Build -FetchSFML
.\lab1\tools\run-visualization.ps1 -Mode Check
.\lab1\tools\run-visualization.ps1 -Mode Run
```

默认Test使用.local/player-build/parser，Build/Run/Check使用.local/player-build/gui，可用-BuildDirectory指定已有构建。首次-FetchSFML需要网络，版本和源码校验值固定。MinGW构建自动将现有编译器运行库复制到程序旁，解决从终端外启动缺少DLL的问题；不把DLL或系统字体提交Git。输出均在被忽略的.local内。

## 输入契约

遵循 `lab1/docs/棋盘与路径接口.md`：`NP_PATH_V1`、依次一个 size/status/moves/states/goal、按 0 起连续序号的 state 行。size 只接受 3/4；棋盘与目标均为 `0..n²-1` 的完整排列。SOLVED 的帧数必须等于 moves+1，每次只交换空格及上下左右邻格，末帧必须等于传入目标（支持自定义目标）。零步必须一帧且等于目标。UNSOLVABLE 只接受 moves=-1、一帧；它表示导出方已经判定无解，播放器不重新求解或证明不可达。TIMEOUT/ERROR 不属于本路径格式，明确拒绝，不能伪造为无解。

空文件、缺字段、重复字段、额外标记、数字溢出、短棋盘、乱序帧、非法跃迁和尾随数据均拒绝，输出文件与错误行号。支持 UTF-8 BOM、CRLF 和空白行。`test_01.txt` 已由原始错误的“0 步非目标”修正为真实零步目标；`test_unsolvable.txt` 保留 D 的有效无解示例。

本轮实际运行证据与未验收部分见 [可视化修复与验收](../docs/可视化修复与验收.md)；仅编译或状态测试通过不等同于全部图形交互验收。
