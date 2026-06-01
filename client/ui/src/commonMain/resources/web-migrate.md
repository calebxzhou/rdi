可以加 Web target，但不能直接把现在的 client/ui 完整搬到浏览器。可行方向是：先做“管理端 Web
UI（management web app）”，复用账号、房间、资源浏览、远程文件管理这些纯网络功能；Minecraft安装、启动、
Java校验、本地文件选择、更新、自建快捷方式这些必须继续留在 Desktop/Android，因为浏览器没有本地进程和真实
文件系统权限。

关键证据：

- 当前 client/ui 已是 Kotlin Multiplatform（KMP），但只声明了 desktop JVM 和 androidTarget，没有 wasmJs/
  js target：client/ui/build.gradle.kts:59。

- commonMain 里依赖了多项不适合 Web 的 JVM/Native库，例如 MinecraftAuth、kotlin-logging-jvm、oshi-core：
  client/ui/build.gradle.kts:91。

- common 模块本身现在是纯 JVM Gradle module，不是 multiplatform；而 client/ui 的 commonMain 直接依赖它：
  common/build.gradle.kts:14。

- 项目已经有一些平台隔离（expect/actual），比如 PlatformUtils，但接口还暴露了 java.io.File、InputStream
  这类 Web 不可用类型：client/ui/src/commonMain/kotlin/calebxzhou/rdi/client/ui/PlatformUtils.kt:6。

- 导航里已经有 Desktop-only route 机制，这对拆 Web 功能有帮助：client/ui/src/commonMain/kotlin/
  calebxzhou/rdi/client/ui/AppNavigation.kt:75。

结论

可行，但建议不要目标定成“同一个完整 launcher 跑在 Web”。更现实的目标是：

- Web版能登录、查看/管理host、浏览资源、编辑host文件、查看任务和状态。
- Web版不负责启动Minecraft、不负责安装modpack、不处理本地Java/文件夹/快捷方式。
- Desktop继续做完整launcher；Web只是轻量管理面板。

JetBrains目前 Compose Multiplatform for Web 已进入可用阶段，官方也明确 Web/Wasm target 是 Compose
Multiplatform 的方向；但这不解决你项目里的 JVM 文件系统和启动器能力问题，只说明
UI技术路线可行。参考：JetBrains Compose Web Beta说明
https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta ，以及2026
Compose Multiplatform 1.11更新 https://blog.jetbrains.com/en/kotlin/2026/05/compose-multiplatform-1-11-0
。

实施计划

1. 先定义 Web功能边界
   保留：登录、host列表、host详情、远程文件管理、资源浏览、设置里的账号/网络项。
   禁用或隐藏：McPlayScreen、本地modpack导入/导出、本地版本管理、Java路径设置、桌面更新、快捷方式、打开本
   地文件夹。

2. 先拆 common 模块
   把 common 改成 KMP，至少分成 commonMain 和 jvmMain。
   DTO、序列化模型、纯HTTP路径放 commonMain；File、压缩包、NBT/region读写、OkHttp、logback、commons-
   compress、zstd 放 jvmMain。

3. 清理 client/ui/commonMain 的平台泄漏
   把 PlatformUtils 中返回 File/InputStream 的接口拆成两层：
   commonMain 只保留可表达的业务动作，例如“选择上传源”“导出日志”；实际 File 实现放 desktopMain/
   androidMain，Web actual 返回不可用状态或使用 browser file picker。

4. 新增 webMain target
   在 client/ui/build.gradle.kts 增加 wasmJs { browser() } 或当前Compose推荐的 Web target。
   新建 src/webMain/kotlin，补齐 PlatformUtils.web.kt、ResourceLoader.web.kt、Fonts.web.kt、
   LocalCredentials.web.kt 等 actual 实现。

5. 调整导航和页面能力
   复用现有 isDesktop 思路，但改成更清晰的能力模型（capability model），例如
   PlatformCapabilities.canLaunchGame、canPickLocalModpack、canEditRemoteFiles。
   Web上不要让按钮点进去才失败，而是入口直接隐藏或显示“请使用客户端启动游戏”。

6. 网络层适配
   Web target 用浏览器 HTTP engine，避免 OkHttp-only API。
   需要检查服务器 CORS、cookie/token存储、SSE/file upload接口是否浏览器可访问。

7. 最小可运行里程碑
   第一版只做：登录 -> 菜单 -> host列表 -> host详情 -> HostFileExplorer。
   这条链最像 Web管理面板，且不依赖本地Minecraft安装。

我建议第一步先做“Web可编译性审计”：列出阻塞 wasmJsMain 编译的文件和依赖，按 common、client/ui
commonMain、页面入口三类拆。不要一开始就改全量页面，否则会被本地启动器逻辑拖住。
