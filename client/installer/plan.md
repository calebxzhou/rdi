# `client/installer`Rust迁移详细计划

## 1.目标与完成定义

在`client/installer`新增独立的Windows x64 Rust命令行安装器，最终替代`client/ui/installer`中的.NET NativeAOT实现。Rust版本必须保持当前用户可见功能和安装语义，同时把最终单文件EXE的非资源开销压到尽可能小。

本计划只描述迁移，不授权实现、构建或切换发布流程。

完成必须同时满足：

- 仍然发布单个`rdi-installer.exe`，安装时不依赖.NET、外置`zstd.exe`、外置TAR工具或其他随包DLL。
- 用户流程、默认目录选择、现代目录选择器、快捷方式、暂存解压、覆盖合并、警告和退出码与当前实现等价。
- 内嵌包仍是由`lib/`和`start.exe`组成的`client.tar.zst`，解压过程保持流式，不把解压后的约120MiB内容一次性放入内存。
- 以同一个`client.tar.zst`比较时，Rust EXE必须小于当前NativeAOT EXE；目标是把壳层开销控制在`1.5MiB`以内。
- `makeShipPackZst`先生成内嵌包，`makeShipInstaller`再构建Rust安装器，依赖顺序不能改变。
- Windows功能测试、安装结果对比和体积门槛全部通过后，才能切换Gradle发布入口。
- 旧.NET安装器不能直接删除；切换完成并再次获得批准后，按仓库规则移动到`DEL/client/ui/installer-dotnet`。

## 2.当前truth source与基线

### 2.1源码truth source

迁移时以这些当前文件为行为真相源：

- `client/ui/installer/Program.cs`：同步STA入口、UTF-8控制台、Y/N交互、目录选择循环、进度、警告和退出码。
- `client/ui/installer/InstallPathSelector.cs`：非C盘固定SSD优先及Documents回退。
- `client/ui/installer/FolderPicker.cs`：`IFileOpenDialog`、默认目录、取消语义和置顶隐藏owner窗口。
- `client/ui/installer/InstallerService.cs`：暂存目录、Zstandard+TAR解包、路径安全、`start.exe`校验、覆盖合并和快捷方式降级。
- `client/ui/installer/ShortcutService.cs`：当前用户桌面与开始菜单`.lnk`创建合同。
- `client/ui/installer/installer.csproj`和`app.manifest`：单文件、图标、版本信息、`asInvoker`和Windows x64目标。
- `client/ui/build.gradle.kts`中的`makeShipPackZst`与`makeShipInstaller`：发布包输入、`zstd -22`及任务先后关系。

`Form1.cs`、`Form1.Designer.cs`和`Win32InstallerApp.cs`已被`installer.csproj`排除，不属于功能等价范围。

### 2.2在2026-08-09实测的发布基线

| 指标 | 当前值 |
|---|---:|
| `client.tar.zst` | `118,763,257`字节 |
| 解压后TAR | `126,253,568`字节 |
| NativeAOT `rdi-installer.exe` | `121,898,496`字节 |
| 当前壳层开销 | `3,135,239`字节，约`2.99MiB` |
| Zstandard帧 | 1帧，无字典，窗口`8MiB`，带XXH64校验 |
| TAR条目 | 169个：`lib/`及其内容168个，`start.exe`1个 |
| 最长条目名 | 54个字符 |
| 当前包SHA-256 | `8e01c00e2a3c26069f94e96d64789d928e82a7a28a7fe2672a44c17504a56748` |

当前EXE元数据：

- File Description：`RDI Client Installer`
- Product：`rdi`
- Copyright：`calebxzau`
- 图标：`client/ui/assets/src/main/resources/icon.ico`

体积比较必须固定同一个内嵌包哈希，否则无法判断变化来自Rust壳层还是发布包内容。

## 3.必须保持的功能合同

### 3.1启动与交互

1. 保持Windows控制台程序，不引入WinForms、WinUI、Tauri或其他GUI框架。
2. 启动时将控制台输出代码页设为UTF-8，中文提示和重定向输出都应可读。
3. 所有确认继续使用单键Y/N，用户不需要按Enter。
4. 非Y/N按键输出`请按Y或N。`并重新等待。
5. 成功退出码为`0`，安装失败输出`安装失败:{message}`并返回`1`。
6. 不新增命令行参数、配置文件、联网步骤或后台进程。

### 3.2默认安装目录

保持当前选择算法及排序：

1. 枚举已就绪的固定磁盘。
2. 排除C盘。
3. 通过`IOCTL_VOLUME_GET_VOLUME_DISK_EXTENTS`取得唯一物理磁盘；跨多个物理磁盘的卷不作为候选。
4. 通过`StorageDeviceSeekPenaltyProperty`确认不产生寻道惩罚，即视为SSD。
5. 按剩余空间降序、盘符升序选择第一个候选，路径为`<盘符>:\mc\rdi`。
6. 没有合格磁盘时回退到当前用户`Documents\rdi`。
7. 单个磁盘探测失败只跳过该磁盘，不能让整个安装器启动失败。

### 3.3安装目录选择器

1. 先询问是否使用默认路径。
2. 用户按N时打开现代Windows文件夹选择器，禁止退化成手工输入路径。
3. 使用`IFileOpenDialog`和`FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST`。
4. 标题保持`选择rdi安装文件夹`。
5. 如果默认路径不存在，从它向上寻找最近的现有父目录，作为选择器初始位置。
6. 使用`WS_EX_TOPMOST | WS_EX_TOOLWINDOW`隐藏owner窗口让选择器保持在前。
7. 用户取消后输出`未选择安装文件夹。`，然后重新询问默认路径；取消不算安装失败。
8. 所有Windows路径在内部使用`PathBuf`/UTF-16，不能用有损UTF-8字符串执行文件操作。

### 3.4快捷方式

1. 安装前分别询问桌面图标和开始菜单图标，均用单键Y/N。
2. 桌面快捷方式为当前用户桌面的`rdi.lnk`。
3. 开始菜单快捷方式为当前用户Programs目录下的`rdi\rdi.lnk`。
4. 目标为安装目录中的`start.exe`，工作目录为安装目录，描述为`rdi`，图标使用`start.exe`索引0。
5. 使用Windows Shell Link COM接口直接创建，不调用PowerShell脚本或WScript。
6. 快捷方式失败不能回滚成功的主体安装；将失败作为warning逐条输出。

### 3.5解压、暂存和覆盖合并

1. 安装路径先转换为绝对路径。
2. 暂存目录创建在目标目录的父目录中，保持同卷移动；名称保留`.rdi-installing-`前缀。
3. 暂存名只需保证并发唯一，不引入仅用于名称的`uuid`依赖；使用进程ID、时间和`create_new`重试即可。这里不是业务模型，不涉及UUID模型合同。
4. 从EXE内嵌`client.tar.zst`流式解码并逐条安装，逐条输出`正在安装:{entry}`。
5. 只接受目录、普通文件和contiguous file；拒绝符号链接、硬链接、设备、FIFO及其他条目类型。
6. 拒绝绝对路径、UNC路径、盘符前缀、`..`及任何逃逸暂存根目录的条目。
7. 保留普通文件的TAR修改时间。
8. 解压完成后必须确认暂存根目录存在`start.exe`，否则整次安装失败。
9. 如果目标目录不存在，直接把整个暂存目录移动为目标目录。
10. 如果目标目录已存在，保持merge-overwrite语义：覆盖包内同名文件、创建新目录、保留包外旧文件。
11. 覆盖前先以禁止共享方式预打开所有已存在的冲突文件；任何文件不可写时，在开始覆盖前失败。
12. 使用Windows原生可覆盖移动语义替代可能因目标已存在而失败的普通`std::fs::rename`。
13. 无论成功或失败都清理仍存在的暂存目录。
14. 不借迁移机会改成删除旧文件、全目录替换或完整回滚事务；这些都不是当前行为。

## 4.目标模块设计

### 4.1外部seam

Rust安装器保持一个小的安装interface：

```text
install(InstallRequest, ProgressSink) -> Result<InstallResult, InstallError>
```

`InstallRequest`只包含安装路径及两个快捷方式布尔值；`InstallResult`只返回warnings。调用侧不需要知道Zstandard、TAR、暂存目录、COM或覆盖策略。这样删除`Installer`模块时，复杂度会重新散落到CLI、文件系统和Windows调用中，说明它是有实际depth的模块，而不是转发层。

默认路径和文件夹选择归入另一个深模块`WindowsShell`，它向CLI暴露：

- `default_install_path()`
- `pick_install_folder(default_path)`

快捷方式创建只在`Installer`内部调用，不向CLI额外暴露。测试需要的归档reader和Windows fake adapter作为内部seam存在，不扩张生产interface。

### 4.2建议目录结构

```text
client/installer/
├── plan.md
├── Cargo.toml
├── Cargo.lock
├── .gitignore
├── build.rs
├── app.rc
├── app.manifest
├── assets/
│   └── client.tar.zst
└── src/
    ├── main.rs
    ├── cli.rs
    ├── installer.rs
    ├── archive.rs
    └── windows.rs
```

先保持5个Rust源文件。只有当`windows.rs`实际变得难以导航时，才按`storage.rs`、`shell.rs`和`com.rs`拆分；不要预先制造大量浅模块。

各文件职责：

- `main.rs`：Windows平台门禁、UTF-8控制台、顶层错误处理和退出码。
- `cli.rs`：Y/N读取、默认目录确认、选择器循环、两个快捷方式确认和提示文字。
- `installer.rs`：暂存生命周期、`start.exe`校验、预检、合并、warning聚合。
- `archive.rs`：内嵌资源reader、Zstandard流式解码、TAR条目白名单和路径安全。
- `windows.rs`：已知目录、磁盘/SSD探测、COM生命周期、`IFileOpenDialog`、Shell Link、文件独占预检及覆盖移动。
- `build.rs`、`app.rc`、`app.manifest`：图标、manifest、版本信息和`client.tar.zst`资源嵌入。

### 4.3COM与Windows资源生命周期

- 入口保持同步，不引入async runtime。
- 文件夹选择器调用期间在当前线程初始化STA，RAII对象负责成对`CoUninitialize`。
- Shell Link创建也使用明确的COM apartment生命周期，不能依赖选择器曾经初始化过COM。
- COM interface指针、`PWSTR`、owner窗口和文件句柄都用小型RAII包装，正常返回与错误路径都只释放一次。
- `RPC_E_CHANGED_MODE`必须有回归测试或Windows smoke test，防止重现旧版async入口导致的线程模型错误。

## 5.依赖与体积策略

### 5.1运行时依赖预算

首选只保留3个runtime crate：

| 依赖 | 用途 | 约束 |
|---|---|---|
| `windows-sys 0.61.x` | 原始Win32/COM声明 | 只开实际使用的feature；不使用较厚的`windows`封装 |
| `zstd 0.13.3` | 流式Zstandard解码 | `default-features = false`，不带legacy、字典构建和编码侧默认feature |
| `tar 0.4.46` | 流式TAR读取及元数据 | 关闭不需要的默认feature，只接受白名单条目 |

构建期可使用`embed-resource 3.x`处理`.rc`；它不能进入最终runtime依赖图。

`windows-sys`初始feature白名单为`Win32_Foundation`、`Win32_Storage_FileSystem`、`Win32_System_Com`、`Win32_System_Console`、`Win32_System_IO`、`Win32_UI_Shell`和`Win32_UI_WindowsAndMessaging`。如果实际符号要求父feature，再只补对应父项；不能直接启用整个`Win32`feature集。

明确不引入：

- `tokio`、`async-std`或任何异步运行时。
- `clap`、`crossterm`、`dialoguer`或GUI toolkit。
- `serde`、日志框架、DI框架、`walkdir`、`dirs`。
- `anyhow`/`thiserror`；错误种类有限，使用本地小型`InstallError`即可。
- `uuid`；暂存目录唯一性不值得引入随机数依赖链。
- 外置`zstd.dll`或外置`zstd.exe`；Windows侧`zstd.exe`只服务于构建和诊断。

不要一开始手写TAR解析器。`tar`crate已经覆盖GNU/PAX变体、流式读取和路径安全。只有在功能通过后，若`cargo bloat`证明它占用显著壳层体积，并且替换至少能净减`64KiB`，才另提计划评估受控的只读TAR解析器；不能为理论上的几KiB节省增加归档兼容风险。

### 5.2Release配置

在独立`Cargo.toml`根配置：

- `edition = "2024"`，与`client/updater`一致。
- `opt-level = "z"`作为起点。
- `lto = "fat"`。
- `codegen-units = 1`。
- `panic = "abort"`。
- `strip = "symbols"`。
- 关闭release增量编译。
- Windows MSVC链接器启用`/OPT:REF`和`/OPT:ICF`；先确认没有重复配置再加入。
- 发布目标固定`x86_64-pc-windows-msvc`，最低支持Windows 10，与Rust官方MSVC目标合同一致。
- 为保持单文件无额外VC Runtime，发布构建使用`+crt-static`，并在最终EXE上实际检查依赖，不能只相信配置。

Rust官方文档明确提醒`"s"`、`"z"`和数字优化级别不保证谁一定最小。因此在同一源码和同一资源包下，至少比较`"z"`、`"s"`和`2`，最终把实测最小且解压性能可接受的配置写回`Cargo.toml`，不能凭经验固定结论。

### 5.3资源嵌入

首选把`client.tar.zst`作为Win32 `RCDATA`嵌入`.rsrc`，运行时用`FindResourceW`、`SizeofResource`、`LoadResource`和`LockResource`取得只读slice，再交给流式decoder。图标、manifest和VERSIONINFO也由同一个`app.rc`生成。

选择RCDATA而不是EXE尾部overlay的原因：

- 原始`cargo build --release`产物本身就是完整可运行安装器，不需要额外append步骤。
- 签名、杀毒扫描、版本资源和发布工具看到的是常规PE结构。
- PE section alignment相对118MiB资源最多只浪费几KiB，收益不足以换取额外封装协议。

选择RCDATA而不是直接把巨大`include_bytes!`编入Rust对象，是为了让资源归资源编译器管理，减少Rust对象和调试产物膨胀。若`rc.exe`对118MiB资源出现已验证的工具链限制，再退回`include_bytes!`；不能预先同时保留两套嵌入路径。

### 5.4内嵌包体积

最终EXE几乎全是`client.tar.zst`，所以同时记录两种指标：

1. 壳层开销：`exe_bytes - archive_bytes`。
2. 最终交付大小：`exe_bytes`。

保持当前`zstd -22`为功能基线，再用Windows侧`zstd.exe`对同一TAR做一次受控比较：

- 当前参数`-22`。
- `--ultra -22 --long=27`，仅作为候选。

只有候选至少缩小最终包`1%`，且Windows实测解压内存和时间仍可接受，才修改生产参数。不能解包后重压JAR、改JAR字节、删除library或使用UPX；这些会改变内容、签名或增加安全软件误报风险。

## 6.分阶段实施计划

### 阶段0：冻结行为与基线

1. 把第3节整理成可勾选的parity清单。
2. 保存同一`client.tar.zst`的长度、SHA-256、帧参数、条目数和最长路径。
3. 保存当前NativeAOT EXE长度和版本资源。
4. 准备小型测试归档，不使用118MiB真实包跑每个单元测试。
5. 当前.NET源码保持不动，作为迁移期间的对照实现。

退出条件：所有后续测试都能指向明确的当前行为，而不是凭记忆判断“功能一样”。

### 阶段1：建立最小Rust骨架

1. 在`client/installer`建立独立Cargo package，binary名为`rdi-installer`。
2. 固定最小依赖和release profile，生成并纳入`Cargo.lock`。
3. 增加`cfg(windows)`入口；非Windows构建只输出“不支持”并退出，不尝试模拟Windows安装。
4. 配置console subsystem、`asInvoker`manifest、现有ICO和版本信息。
5. 先用极小测试资源验证RCDATA读取，避免每次骨架迭代都链接118MiB包。

退出条件：Windows release骨架是单EXE，版本信息、图标、manifest、退出码和静态CRT依赖检查通过，并记录空壳大小。

### 阶段2：实现`WindowsShell`

1. 实现Known Folder读取：Documents、Desktop、Programs。
2. 实现固定磁盘枚举、剩余空间排序和SSD判断。
3. 实现同步STA的`IFileOpenDialog`，包括最近现有父目录。
4. 实现置顶隐藏owner窗口和取消返回`None`。
5. 实现Shell Link创建及目录创建。
6. 实现独占打开和可覆盖文件移动原语。
7. 所有handle和COM pointer引入最小RAII，Windows错误码在模块内转换为可理解的安装错误。

退出条件：fake磁盘测试、Known Folder测试、选择器Windows smoke test、取消循环及临时目录`.lnk`检查通过。

### 阶段3：实现深`Installer`模块

1. 实现`InstallRequest`、`InstallResult`和稳定的小interface。
2. 实现同卷唯一暂存目录及finally等价清理guard。
3. 接入RCDATA reader、`zstd::stream::read::Decoder`和`tar::Archive`。
4. 在解包前验证每个entry类型和规范化路径。
5. 流式写入并保留mtime，逐条上报进度。
6. 校验根目录`start.exe`。
7. 实现目标不存在时整目录移动。
8. 实现目标存在时冲突文件全量预检、目录创建和逐文件覆盖移动。
9. 主体安装完成后创建快捷方式，将错误转成warnings。

退出条件：第8节的自动化安装语义测试全部通过，失败路径不残留`.rdi-installing-*`。

### 阶段4：接回CLI

1. 按当前顺序接线：默认路径Y/N→必要时目录选择器→桌面Y/N→开始菜单Y/N→安装。
2. 使用`ReadConsoleInputW`读取按键按下事件，不引入终端crate。
3. 保持现有中文提示、进度格式、warning格式和成功/失败退出码。
4. 输入、路径和错误输出分别验证真实控制台与重定向场景。

退出条件：完整人工流程与当前安装器逐步对照无用户可见差异。

### 阶段5：接入真实资源和Gradle发布链

此阶段才切换资源和任务路径：

1. 先把现有`client.tar.zst`从`client/ui/installer/assets`移动到`client/installer/assets`，不能复制出第二份活动资源。
2. 将Rust `app.rc`的资源路径从迁移期旧位置切到本地`assets/client.tar.zst`。
3. 将`makeShipPackZst`输出从`client/ui/installer/assets/client.tar.zst`改为`client/installer/assets/client.tar.zst`。
4. 保持输入严格为`Documents/rdi5ship/lib`和`Documents/rdi5ship/start.exe`。
5. 保持先TAR、再Windows侧`zstd -22`、finally删除临时TAR。
6. 将`makeShipInstaller`从`dotnet publish`改为在`client/installer`执行`cargo build --release --locked`。
7. 构建后明确检查`target/release/rdi-installer.exe`存在、内嵌资源长度正确、EXE元数据正确。
8. Gradle任务遇到非0退出码必须失败，不能留下看似成功的旧产物。
9. 保持任务名`makeShipPackZst`和`makeShipInstaller`不变，减少调用方迁移面。

根据仓库规则，Gradle任务只在用户明确授权后执行。仅修改任务源码不能宣称发布链已验证。

### 阶段6：功能、体积和运行时验收

1. 用同一SHA-256的真实`client.tar.zst`分别构建NativeAOT和Rust版本。
2. 计算总大小与壳层开销；Rust必须小于NativeAOT，目标壳层不超过`1.5MiB`。
3. 用`cargo tree -e features`确认没有意外default feature。
4. 用`cargo bloat --release --crates`定位剩余体积，只处理有实测收益的依赖。
5. 比较`opt-level="z"`、`"s"`和`2`，固定最小合格构建。
6. 检查EXE imports，只允许Windows系统DLL，不允许.NET、`zstd.dll`或VC Runtime旁置文件。
7. 在干净Windows 10/11用户环境各运行一次完整安装。
8. 比较安装结果的相对路径、文件长度、SHA-256和mtime；允许文件系统时间精度差异，不允许内容差异。
9. 记录解压总时间、峰值工作集和失败时残留。

若壳层超过目标，不删除功能来过门槛。先依次检查：意外crate feature→重复Windows绑定→动态错误/格式化代码→TAR实现占比→图标资源。任何进一步定制解析器或图标裁剪都要有单独测量和回归测试。

### 阶段7：切换与旧实现归档

1. 先让发布调用方使用Rust `makeShipInstaller`并完成一次真实发布演练。
2. 文档和错误提示中不再提.NET安装器。
3. 获得明确批准后，将旧C#实现移动到`DEL/client/ui/installer-dotnet`，不能删除。
4. 确认活动树中只有`client/installer/assets/client.tar.zst`这一份生产资源；DEL中的历史副本不能被任何任务引用。
5. 搜索并清除活动构建脚本中的`dotnetInstallerReleaseCmd`和旧路径引用；DEL目录中的历史引用不算活动调用方。

退出条件：生产只有一个活动安装器truth source和一个活动内嵌包路径，旧实现可从DEL恢复但不会被发布任务调用。

## 7.文件级改动清单

### 新增

- `client/installer/Cargo.toml`
- `client/installer/Cargo.lock`
- `client/installer/.gitignore`
- `client/installer/build.rs`
- `client/installer/app.rc`
- `client/installer/app.manifest`
- `client/installer/src/main.rs`
- `client/installer/src/cli.rs`
- `client/installer/src/installer.rs`
- `client/installer/src/archive.rs`
- `client/installer/src/windows.rs`
- 小型测试fixture；不要复制真实118MiB包作为第二份fixture。

### 修改

- `client/ui/build.gradle.kts`：资源输出路径、`makeShipInstaller`工作目录和构建命令。

### 发布链切换时移动

- `client/ui/installer/assets/client.tar.zst`→`client/installer/assets/client.tar.zst`

### 最终移动，需单独批准

- `client/ui/installer/**`→`DEL/client/ui/installer-dotnet/**`

## 8.测试矩阵

### 8.1自动化测试

| 范围 | 用例 | 必须观察到的结果 |
|---|---|---|
| 路径安全 | `../x`、绝对路径、`C:\x`、UNC、混合分隔符 | 解包前拒绝，暂存被清理 |
| 条目类型 | 普通文件、目录、contiguous file | 成功 |
| 条目类型 | symlink、hardlink、device、FIFO | 明确失败，不创建目标 |
| 归档 | 损坏Zstandard帧、截断TAR | 失败且无暂存残留 |
| 归档 | 缺少`start.exe` | 主体安装失败 |
| 新安装 | 目标不存在 | 整个暂存目录移动成功 |
| 覆盖安装 | 目标已有同名文件 | 包内文件覆盖 |
| 覆盖安装 | 目标有包外额外文件 | 额外文件保留 |
| 文件锁 | 任一冲突文件被独占锁定 | 覆盖前失败，其他冲突文件未更新 |
| 快捷方式 | 创建失败 | 主体成功，返回warning |
| 清理 | 解压、校验、合并任一步失败 | `.rdi-installing-*`不残留 |
| Unicode | 中文安装路径和文件名 | 路径无乱码、内容正确 |
| 默认磁盘 | 多个SSD候选 | 剩余空间降序、盘符升序 |
| 默认磁盘 | 探测失败或只有C盘 | 回退Documents |
| CLI | Y、N及无效键 | 不需Enter，顺序和重试正确 |

测试通过`Installer`interface观察文件系统结果，不断言私有函数或内部状态。Windows设备、Known Folder和Shell Link使用生产adapter与测试adapter两种实际实现，内部seam因此有真实价值。

### 8.2Windows人工smoke test

1. 默认路径按Y，完成安装。
2. 默认路径按N，选择器置顶显示，初始目录正确。
3. 取消选择器，回到路径确认循环。
4. 中文及空格目录安装。
5. 桌面和开始菜单四种Y/N组合。
6. 对已有安装目录再次安装，确认覆盖与旧额外文件保留。
7. 锁定一个将被覆盖的JAR，确认覆盖前失败。
8. 在非管理员用户运行，确认manifest不会请求提权。
9. 从Windows Terminal、传统Console Host及双击EXE启动各测试一次。

涉及真实桌面/开始菜单的测试只在专用测试用户或明确授权环境运行；普通自动化测试把`.lnk`写到临时目录。

### 8.3发布验收记录

每个候选release记录：

- Rust/Cargo版本和target triple。
- `Cargo.lock`哈希。
- `client.tar.zst`长度与SHA-256。
- EXE长度、壳层开销和SHA-256。
- EXE版本信息、图标、manifest和imports。
- 真实包解压时间、峰值工作集、安装后文件总数及内容哈希对比结果。
- Windows 10/11 smoke test结论。

## 9.风险与对应措施

| 风险 | 对应措施 |
|---|---|
| COM apartment错误重现`RPC_E_CHANGED_MODE` | 同步入口；选择器STA初始化；Windows回归测试 |
| Rust普通rename不能覆盖Windows目标文件 | 在`WindowsShell`内使用明确的Win32 replace move语义 |
| TARcrate默认接受链接或特殊条目 | 调用unpack前做类型白名单 |
| 路径escape或Unicode处理错误 | 使用`PathBuf`/UTF-16并同时做组件级校验与`unpack_in`约束 |
| 为了体积关闭过多Zstandard能力导致当前包无法解压 | 用当前真实帧做构建门禁，保留标准现代Zstandard解码 |
| 静态CRT或zstd-sys使壳层超目标 | 先看imports和`cargo bloat`，再做有数据支持的feature/链接优化 |
| RCDATA大资源让RC工具失败 | 先做真实118MiB资源构建验证；仅在失败后改用唯一的`include_bytes!`方案 |
| 新旧资源路径同时活跃导致装入旧包 | Gradle切换时只保留一个活动输出路径，并校验内嵌资源哈希 |
| 快捷方式失败让用户误以为安装失败 | 保持warning降级，不回滚主体安装 |
| 迁移顺手改变覆盖语义 | parity测试明确“覆盖包内文件、保留包外文件” |

## 10.明确不做

- 不把安装器改成GUI。
- 不加入下载、更新、卸载、修复、日志上传或遥测。
- 不改变`start.exe`和`lib`内容。
- 不在迁移中清理用户安装目录里的旧文件。
- 不引入管理员权限。
- 不引入手工路径输入fallback。
- 不使用UPX或其他EXE packer。
- 不在功能等价前移动或停用旧.NET实现。
- 不执行Git命令。
- 未获得明确授权前不执行Gradle任务。

## 11.建议实施批次

为便于逐批审查，实施时按以下批次交付，每批都保持可复核：

1. Rust骨架、资源和版本信息。
2. `WindowsShell`默认路径与目录选择器。
3. `Installer`归档、暂存和merge-overwrite。
4. Shell Link、CLI和warning合同。
5. 自动化测试及真实包parity。
6. 体积测量与release profile定稿。
7. Gradle发布链切换。
8. 经单独批准后移动旧实现到DEL。

只有第1至6批全部通过后才进入第7批；第8批永远不能和首次切换合并执行。

## 12.参考资料

- Rust Cargo profile设置与`"s"`/`"z"`实测要求：<https://doc.rust-lang.org/cargo/reference/profiles.html>
- Rust MSVC目标支持：<https://doc.rust-lang.org/stable/rustc/platform-support/windows-msvc.html>
- Rust静态CRT链接合同：<https://doc.rust-lang.org/reference/linkage.html#static-and-dynamic-c-runtimes>
- `windows-sys`零额外抽象的原始Windows绑定：<https://docs.rs/crate/windows-sys/latest>
- `zstd`流式decoder：<https://docs.rs/zstd/latest/zstd/stream/read/struct.Decoder.html>
- `tar`流式读取与安全说明：<https://docs.rs/tar/latest/tar/>
