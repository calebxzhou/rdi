# Host2工作说明

本文件适用于`server/master/src/main/kotlin/calebxzhou/rdi/master/service/host2/`及其子目录，并继承仓库根目录`AGENTS.md`。Host2在用户界面中称为“新版房间”或“房间”，不要向玩家暴露容器、部署编排等实现细节。

## 系统边界

- Host2是新版Minecraft房间后端，负责房间、成员、整合包来源、内容版本、服务端安装、运行时、日志和有限文件管理。
- 只支持`MC1.20.1+Forge`和`MC1.21.1+NeoForge`。
- 房间只能引用现有Modpack2整合包版本：`PackSource.Modpack2(versionId)`。
- 不接受服务端整合包上传。不要恢复旧`/server-pack`或`/mods`接口。
- Host2保存精确`PackSource`和内容修订，不保存重复的MC版本、加载器或整合包哈希快照。展示时动态解析来源。
- `Host2PackStatus`使用`Busy`、`Ok`、`Fail`表示安装状态；运行状态继续使用`HostStatus`。

## Truth source与主要入口

- 共享房间DTO：`common/model/src/main/kotlin/calebxzhou/rdi/common/model/Host2.kt`
- 共享内容DTO：`common/model/src/main/kotlin/calebxzau/rdi/common/model/Content.kt`
- PostgreSQL结构：`server/master/src/main/resources/db/migration/V5__redesign_host2_pack_source_and_content.sql`
- 路由：`Host2Routes.kt`
- Koin注册：`Host2Module.kt`
- 数据访问、操作日志和删除墓碑：`Host2Repository.kt`
- 来源解析和版本限制：`Host2PackSourceService.kt`
- 房间、成员和权限：`Host2Service.kt`
- 初次安装、换包、恢复和原子部署：`Host2SetupService.kt`
- 内容修订、启停和发布：`Host2ContentsService.kt`
- 外部内容解析和下载：`Host2ModDownloadService.kt`
- 客户端manifest和客户端包：`Host2ClientPackService.kt`
- 启停、命令和部署校验：`Host2RuntimeService.kt`
- 文件管理：`Host2FileService.kt`
- 容量和空闲检查：`Host2QuotaService.kt`

不要修改已有V5迁移；数据库结构继续变化时新增V6或更高版本迁移。

## 内容模型

- 每次内容版本都是完整不可变快照；使用`activeContentRevision`和可选`pendingContentRevision`。
- 所有内容修改请求必须携带`expectedRevision`并做乐观并发检查。
- 房间停止时，修改可立即发布为active；房间运行时，修改写入pending。
- start、stop、restart和显式apply会在适当时机发布pending，发布前必须重新验证。
- `ContentOrigin.Pack`来自整合包，只允许启用或停用，不能删除。
- `ContentOrigin.Extra`由房间管理员添加，可删除和启停。
- 稳定身份是`origin+platform+projectId`。Extra按平台项目身份或目标路径覆盖Pack；不要用文件名作为主身份。
- 客户端manifest只返回启用且生效、side为`Client`或`Both`的内容。
- `hash`按`ContentPlatform`解释；不要新增`hashAlgorithm`或把下载URL固化进manifest。
- 外部内容目标必须经过服务端校验。`DataPack`和`Other`必须显式给安全目标；世界目录仅允许`world/datapacks/**`。
- 不允许内容覆盖服务端运行时拥有的文件或目录，例如`server.properties`、`eula.txt`、`libraries`、`logs`、`server.jar`和普通world文件。

## KotlinForForge规则

- 不做平台自动注入。
- 只检查`mods`直属文件，文件名大小写不敏感，并忽略`.disabled`文件。
- 初次安装允许0个；发布或启动时不能超过1个。
- 如果当前唯一启用的KotlinForForge来自Extra，不能删除或停用到0个。
- 启动仍要求最终部署中恰好1个；缺失时阻止启动并通知房主。

## 来源不可用语义

- 已安装的active内容快照仍是管理页面和客户端manifest的truth source。
- 来源暂时不可解析时，不应把已有可用active版本改成`Fail`。
- 已运行的房间不受影响，但start、restart、换包、内容修改和pending发布必须阻止。
- 客户端包依赖来源归档；不可用时返回稳定的503错误，而不是伪造包或静默降级。

## 安装与文件系统

- 房间根目录是`HOST2_DIR/<hostId>`。
- 构建使用`HOST2_DIR/.staging/<operationId>`，替换前备份到`.backup`，延迟删除使用`.deleting`和数据库墓碑。
- 部署必须使用原子move；不要添加跨文件系统复制降级。部署标记`.rdi-host2-deployment.json`必须匹配房间、来源和active修订。
- 重新安装、换包和内容发布必须保留world，不得把整合包内world或运行时文件覆盖到现有房间。
- 所有失败都应留下可恢复的操作状态；后台恢复负责继续或回滚未完成操作并重试清理。

## 文件管理合同

- 只允许管理`config`、`datapacks`、`tacz`、`kubejs`和`scripts`。
- 拒绝路径逃逸和任何软链接；同时保护active与pending快照管理的目标文件。
- 文本读写上限4MiB，单文件上传上限2GiB，房间总容量上限8GiB。
- 文件写入和上传使用原子替换。运行中允许稳定状态下的文件修改，但目录rename/delete仅在房间停止时允许。
- `tacz`只允许根目录直属ZIP文件，不允许子目录。

## 权限

- 房主负责换包、转移所有权和删除房间。
- 房主与管理员可管理成员允许的操作、内容、运行时、日志和文件。
- 所有有状态修改都应经过`Host2Service.withMutation`串行化，并在真正执行前重新检查权限和状态。

## 修改要求

- 修改共享模型时先改`common/model`，再改server调用者。
- 不要重新引入Host2自己的MC版本、加载器、`hashAlgorithm`、服务端包上传或旧Mod列表模型。
- 保持完整内容快照、active/pending修订、原子部署和world保留语义。
- 新增来源类型时，必须同时处理序列化、数据库字段、来源解析、部署标记、引用保护和恢复流程。
- 面向玩家的错误信息使用“房间”“整合包”“内容”等词，不使用Docker或内部部署术语。
- 可失败的磁盘、网络、归档和远程平台操作必须显式传播或记录异常，不要用`getOrNull()`静默吞错。

## 验证

重点测试：

- `server/master/src/test/kotlin/calebxzhou/rdi/master/service/host2/Host2ContentRulesTest.kt`
- `server/master/src/test/kotlin/calebxzhou/rdi/master/service/host2/Host2ModDownloadServiceTest.kt`
- `common/model/src/test/kotlin/calebxzhou/rdi/common/model/Host2Test.kt`

在WSL中从`server/master`模块通过Windows PowerShell运行Gradle，例如：

```powershell
Set-Location 'C:\Users\calebxzhou\Documents\Coding\rdi5\server\master'
.\gradlew.bat compileKotlin compileTestKotlin modpack2ApiTest :model:test :misc:test
```

PostgreSQL仓库集成测试使用`modpack2RepositoryIntegrationTest`，依赖可用的Docker/Testcontainers环境。没有仓库根级`gradlew`；除非用户明确要求，不要运行Git命令。
