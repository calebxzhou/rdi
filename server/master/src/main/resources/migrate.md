# Host2迁移与详细设计

> 状态：Host2首轮实现已写入源码，尚未执行Gradle、Javac或静态检查。
>
> 本文前28节保留访谈过程。后续确认推翻旧决定时，以第29节“最终实现覆盖条款”和当前源码为准。

## 1.目标

在`server/master`中新增与旧`Host`并行运行的`Host2`：

- 旧`Host`继续使用现有MongoDB、目录、接口和运行逻辑。
- 新`Host2`使用PostgreSQL18持久化结构化数据。
- 新`Host2`的数据文件全部保存在`StorageConfig.host2Dir`下。
- 玩家不再选择平台内置`modpackId`或`packVer`，而是上传完整server pack ZIP。
- 玩家必须显式选择`McVersion`和`ModLoader`，服务端不自动检测。
- 服务端始终使用项目`McVersion.loaderVersions`中当前固定的loader版本。
- `Host2.id`使用原生UUIDv7。
- Host2不迁移旧Host数据，也不替代旧Host。

## 2.明确不做

Host2 v1不包含以下旧Host能力：

- 不迁移旧Host或旧World数据。
- 不单独持久化`World`，也不使用`worldId`。
- 不持久化`modpackId`、`packVer`或精确loader版本。
- 不持久化`difficulty`、`gameMode`、`levelType`、`gameRules`或`allowCheats`。
- 不持久化runtime状态。
- 不持久化`extraMods`、`disabledMods`或`banlist`。
- 不保留玩家上传的原始ZIP。
- 不提供整包替换能力；READY后不能重新上传server pack。
- 不提供World分离、复用、detach、surface cache或玩家数据备份。
- 不提供旧Host的“名称包含公共”特殊Host能力。
- 不使用Minecraft原生whitelist、proxy或RDI Mod强制阻止直连玩家。
- 不让玩家自定义CPU、内存、swap或`-Xmx`。
- 不在Host2 v1中提供`server.properties`专用编辑器。

## 3.并行架构边界

`Host`与`Host2`必须保持清晰隔离：

| 范围 | 旧Host | Host2 |
| --- | --- | --- |
| CRUD API | `/host` | `/host2` |
| 结构化存储 | MongoDB | PostgreSQL18 |
| ID | MongoDB`ObjectId` | UUIDv7 |
| Host端口 | `50000..<60000` | `30000..<40000` |
| Host文件 | 现有`hostsDir`等目录 | `host2Dir/<uuid>/` |
| World | 独立World模型/目录 | `host2Dir/<uuid>/world/` |
| Mod来源 | modpack+extra/disabled记录 | `mods/`目录文件 |

旧的object service和MongoDB service不做Koin重构。Koin只负责新PostgreSQL和Host2模块。

现有游戏端与proxy协议保持兼容：

- `/host/play/{id}`继续保留。
- `id`是24位ObjectId字符串时路由到旧Host。
- `id`是UUID字符串时路由到Host2。
- `/host/route?port=...`继续保留。
- `30000..39999`路由到Host2。
- `50000..59999`路由到旧Host。
- proxy不新增玩家身份或whitelist校验。

## 4.PostgreSQL、Koin和启动顺序

### 4.1技术栈

按照`../craftrdi`的master server模式引入：

- Koin`4.2.2`
- Exposed`1.3.1`
- HikariCP`7.1.0`
- Flyway`12.11.0`
- PostgreSQL JDBC`42.7.13`
- PostgreSQL`18`

MongoDB的`DatabaseConfig`继续保留。另增独立`PostgresConfig`，不要让两个数据库共享同一个配置类型。

配置结构草图：

```kotlin
@Serializable
data class PostgresConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 10
)

@Serializable
data class StorageConfig(
    // Existing fields remain unchanged.
    val host2Dir: String? = null
)

@Serializable
data class AppConfig(
    // Existing fields remain unchanged.
    val postgres: PostgresConfig,
    val storage: StorageConfig = StorageConfig()
)
```

`PostgresConfig`的具体默认地址、数据库名、用户名和secret来源尚未在访谈中确定；实现时必须继续从`config.toml`读取，不得硬编码secret。

### 4.2启动生命周期

启动顺序必须是：

```text
读取config.toml
    ->创建HikariDataSource
    ->执行Flyway validate/migrate
    ->连接Exposed
    ->安装Host2 Koin module
    ->注册Host2 routes
    ->启动Host2后台服务
```

规则：

- PostgreSQL连接失败时整个master fail-fast。
- Flyway校验或迁移失败时整个master fail-fast。
- 不允许master在Host2数据库不可用时以“仅旧Host可用”模式继续启动。
- Hikari pool size为`10`。
- JDBC/Exposed使用UTC。
- transaction isolation使用`READ_COMMITTED`。
- 在Ktor`ApplicationStopping`阶段关闭HikariDataSource。
- 启动恢复时，数据库中遗留的`PROCESSING`任务一律转为`FAILED`，清理对应staging并向OWNER发Mail。

接线草图：

```kotlin
fun Application.configureHost2() {
    val databaseProvider = DatabaseProvider(CONF.postgres)
    databaseProvider.start() // Hikari -> Flyway -> Exposed

    install(Koin) {
        modules(host2Module(databaseProvider))
    }

    monitor.subscribe(ApplicationStopping) {
        databaseProvider.close()
    }
}
```

这只是启动顺序草图；实际接线要保留现有`RDI.kt`中的自定义HTTP/HTTPS connector和旧route安装方式。

## 5.ID与未来Account迁移

### 5.1Host2 ID

- `host2.id`类型为PostgreSQL`UUID`。
- ID由PostgreSQL18生成：`DEFAULT uuidv7()`。
- API与游戏端统一使用带连字符的标准UUID字符串。

### 5.2Account ID

当前Account仍然使用MongoDB`ObjectId`。Host2表里的`owner_id`和`player_id`使用现有可逆映射后的PostgreSQL`UUID`：

```kotlin
fun ObjectId.toUUID(): UUID {
    val objectIdBytes = toByteArray()
    val bytes = ByteBuffer.wrap(ByteArray(16))
    bytes.put(objectIdBytes)
    return UUID(bytes.getLong(0), bytes.getLong(8))
}
```

映射结果由12字节ObjectId加4个`0`字节组成。选择PostgreSQL`UUID`而不是`VARCHAR(24)`的原因：

- Host2关系字段从第一天就使用统一UUID类型。
- 未来Account迁到PostgreSQL时无需重写Host2的owner/member外键值。
- 旧Account迁移后保留这个派生UUID。
- 未来新建Account可以直接使用UUIDv7。

Account仍在MongoDB期间，`owner_id`和`player_id`不能建立到Account表的数据库FK；存在性由service通过`PlayerService`校验。

## 6.PostgreSQL数据模型

### 6.1Setup状态

```kotlin
enum class Host2SetupStatus {
    AWAITING_UPLOAD,
    PROCESSING,
    READY,
    FAILED
}
```

含义：

- `AWAITING_UPLOAD`：metadata已创建，等待首次server pack ZIP。
- `PROCESSING`：ZIP已完整接收，后台校验/解压中。
- `READY`：目录可运行。
- `FAILED`：setup失败，可以修改版本组合并重试上传。

setup状态与runtime状态严格分开。现有`HostStatus`继续由真实runtime计算，不写入PostgreSQL。

### 6.2Member角色

```kotlin
enum class Host2MemberRole {
    ADMIN,
    MEMBER
}
```

- OWNER只存于`host2.owner_id`，不重复写入`host2_member`。
- GUEST不持久化。
- member表只保存`ADMIN`和`MEMBER`。

### 6.3Flyway初始schema草图

下面只使用已经确认的字段和约束。字段长度、审计时间戳等尚未确认，因此不在草图中擅自添加。

```sql
CREATE TABLE host2 (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name TEXT NOT NULL,
    intro TEXT NOT NULL DEFAULT '暂无简介',
    owner_id UUID NOT NULL,
    mc_version TEXT NOT NULL,
    mod_loader TEXT NOT NULL,
    port INTEGER NOT NULL UNIQUE,
    whitelist BOOLEAN NOT NULL,
    setup_status TEXT NOT NULL,
    CONSTRAINT host2_port_range
        CHECK (port BETWEEN 30000 AND 39999),
    CONSTRAINT host2_setup_status_value
        CHECK (
            setup_status IN (
                'AWAITING_UPLOAD',
                'PROCESSING',
                'READY',
                'FAILED'
            )
        )
);

CREATE INDEX host2_owner_id_idx ON host2(owner_id);

CREATE TABLE host2_member (
    host_id UUID NOT NULL
        REFERENCES host2(id)
        ON DELETE CASCADE,
    player_id UUID NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY (host_id, player_id),
    CONSTRAINT host2_member_role_value
        CHECK (role IN ('ADMIN', 'MEMBER'))
);

CREATE INDEX host2_member_player_id_idx
    ON host2_member(player_id);
```

`mc_version`与`mod_loader`的合法组合由service根据当前`McVersion.loaderVersions`校验，不在数据库写死，以免每次更新项目固定loader版本都要修改schema。

### 6.4明确不进入host2表的字段

`host2`只持久化Host2本身的metadata和port，不加入：

```text
modpack_id
pack_ver
loader_version
world_id
save_world
difficulty
game_mode
level_type
game_rules
allow_cheats
runtime_status
setup_error
extra_mods
disabled_mods
banlist
public
```

setup错误不写数据库，使用现有Mail功能通知OWNER。

## 7.Shared model与DTO

Shared DTO/model应先放入`common/model`，再适配client和server。

### 7.1CreateDto

最终确认的player-provided字段只有5个：

```kotlin
data class CreateDto(
    val name: String,
    val intro: String = "暂无简介",
    val mcVersion: McVersion,
    val modLoader: ModLoader,
    val whitelist: Boolean
)
```

创建时由server生成：

- `id`：UUIDv7。
- `ownerId`：当前登录Account的派生UUID。
- `port`：`30000..39999`内的未占用随机端口。
- `setupStatus`：`AWAITING_UPLOAD`。

CreateDto不接收filesystem path、server properties或任何旧modpack/world字段。

### 7.2OptionsDto

访谈暂停前最后确认的决策：

```kotlin
data class OptionsDto(
    val name: String? = null,
    val intro: String? = null,
    val whitelist: Boolean? = null,
    val mcVersion: McVersion? = null,
    val modLoader: ModLoader? = null
)
```

权限和状态规则：

- OWNER和ADMIN可随时修改`name`、`intro`、`whitelist`。
- 只有OWNER可修改`mcVersion`和`modLoader`。
- `mcVersion`与`modLoader`必须同时提交或同时省略。
- 修改后的组合必须存在于`McVersion.loaderVersions`。
- 版本字段只能在`AWAITING_UPLOAD`或`FAILED`状态修改。
- `PROCESSING`和`READY`状态禁止修改版本字段。

### 7.3Host2返回模型草图

runtime状态是读取时计算出的字段，不是数据库字段：

```kotlin
data class Host2(
    val id: UUID,
    val name: String,
    val intro: String,
    val ownerId: UUID,
    val mcVersion: McVersion,
    val modLoader: ModLoader,
    val port: Int,
    val whitelist: Boolean,
    val setupStatus: Host2SetupStatus,
    val status: HostStatus
)
```

该返回模型的Brief/Detail拆分以及最终字段命名尚未单独确认；以上只表达已确认的数据边界。

## 8.支持的Minecraft与ModLoader组合

玩家必须选择`mcVersion`和`modLoader`，禁止从ZIP自动识别。当前项目支持组合来自：

`common/model/src/main/kotlin/calebxzhou/rdi/common/model/McVersion.kt`

| Minecraft | ModLoader | 当前项目固定版本 |
| --- | --- | --- |
| `1.21.1` | NeoForge | `neoforge-21.1.233` |
| `1.20.1` | Forge | `1.20.1-forge-47.4.20` |
| `1.19.2` | Forge | `1.19.2-forge-43.5.2` |
| `1.12.2` | Cleanroom | `cleanroom-0.5.14-alpha` |
| `1.7.10` | Forge | `1.7.10-Forge10.13.4.1614-1.7.10` |

规则：

- Host2不持久化上表中的精确loader版本。
- “latest modloader”指当前源码里固定的版本，不访问网络查询最新版。
- 每次start/restart都重新读取当前`McVersion.loaderVersions`。
- 项目以后更新映射并重新部署master后，已有Host2下一次启动也使用新映射。
- server pack内自带的libraries、installer或启动脚本可以保留，但不作为启动truth source。
- 共享`GAME_LIBS_DIR`是loader/runtime library truth source。

## 9.Host2文件系统

### 9.1根目录

所有Host2持久文件都位于`StorageConfig.host2Dir`：

```text
<host2Dir>/
├── .staging/
│   └── <hostId>-<operation>/
└── <hostId>/
    ├── mods/
    ├── config/
    ├── world/
    ├── logs/
    ├── server.properties
    ├── eula.txt
    └── <server pack的其他文件>
```

- `<hostId>`使用标准带连字符UUID目录名。
- 不再引用`worldsDir`、`worldCacheDir`或`worldBackupDir`。
- 所有删除目录操作必须使用`deleteRecursivelyNoSymlink()`，禁止`File.deleteRecursively()`。

### 9.2World

- World固定为`<hostDir>/world/`。
- server pack包含`world/`时直接使用。
- server pack没有`world/`时允许首次启动自动生成。
- start前强制`level-name=world`。
- 删除Host2时World随整个Host2目录删除。
- 不提供`deleteWorld`选项。
- 不运行旧`HostPlayerDataBackupService`或其他World/playerdata backup流程。

## 10.Server pack上传与setup

### 10.1两阶段创建

```text
POST /host2
    ->创建metadata、UUIDv7、port
    ->setupStatus=AWAITING_UPLOAD

PUT /host2/{id}/server-pack
    ->接收完整ZIP
    ->setupStatus=PROCESSING
    ->后台校验和解压
    ->READY或FAILED
```

- server pack上传和失败后的重试只允许OWNER。
- 同一个Host2同时只允许一个`PROCESSING`任务。
- HTTP请求只负责完整接收ZIP并启动后台任务，不等待解压结束。
- client通过Host2详情轮询`setupStatus`。
- 后台成功或失败后使用Mail通知OWNER。
- 成功后状态为`READY+STOPPED`，不自动启动。
- `READY`后禁止重新上传完整server pack。
- 未来若要替换整包，应设计独立feature，不复用初始上传接口。

### 10.2ZIP限制

- 压缩ZIP最大`2GiB`。
- 解压时按实际写出字节累计，最大`3GiB`。
- ZIP entry最大`100000`个。
- 原始ZIP在成功或失败后都不保留。
- 超限、格式错误或校验失败时删除staging、设置`FAILED`并发Mail。
- Host2整体quota为`3GiB`，后续World增长也计入。
- runtime运行期间越过quota时的即时处理策略尚未在访谈中确定；不能把World排除在统计外。

### 10.3ZIP目录归一化

接受两种结构：

```text
pack.zip
├── mods/
├── config/
└── server.properties
```

或：

```text
pack.zip
└── MyServerPack/
    ├── mods/
    ├── config/
    └── server.properties
```

当ZIP恰好只有一个顶层目录且没有顶层文件时，自动flatten一层。其他结构保持原样。

### 10.4安全与原子切换

处理顺序：

```text
上传临时文件
    ->校验压缩大小和entry数量
    ->安全解压到.staging
    ->累计实际解压字节
    ->归一化wrapper目录
    ->校验reserved mod ID
    ->补齐mods/和必要平台文件
    ->写入/patch properties与eula
    ->原子切换为<host2Dir>/<hostId>
    ->setupStatus=READY
```

必须拒绝：

- absolute path。
- `..` path traversal。
- 解压目标逃出staging root。
- symlink或其他可用于逃出Host目录的entry。
- 超过大小或entry限制的ZIP。

失败清理必须使用`deleteRecursivelyNoSymlink()`。

## 11.server.properties与EULA

玩家上传的`server.properties`是基础配置：

- pack有文件时保留其其他键和值。
- pack没有文件时创建最小空properties。
- 不复制旧Host的大型默认模板。
- 每次start前都覆盖平台控制的键。

每次start强制patch：

```kotlin
val platformProperties = mapOf(
    "server-port" to host.port.toString(),
    "level-name" to "world",
    "online-mode" to "false",
    "server-ip" to "",
    "white-list" to "false",
    "enforce-whitelist" to "false",
    "enable-rcon" to "false",
    "enable-status" to "true",
    "hide-online-players" to "false"
)
```

同时保证：

```properties
eula=true
```

不得覆盖：

```text
difficulty
gamemode
level-type
```

以后通过独立`server.properties`editor管理这些设置。

## 12.Mod管理

### 12.1Truth source

`mods/`目录是唯一truth source，不建立Mod inventory table：

```text
mods/example.jar           -> enabled
mods/example.jar.disabled  -> temporarily disabled
```

启用/禁用通过同目录atomic rename完成：

```text
example.jar
    <-> example.jar.disabled
```

规则：

- 只列出`mods/`直属regular files，不递归。
- 只识别`.jar`和`.jar.disabled`。
- identity严格使用filename。
- disabled DTO对外显示原始`.jar`文件名。
- list可以在任何runtime状态执行。
- upload/delete/enable/disable仅允许`READY+STOPPED`。
- 同名冲突同时检查active和disabled形式。
- 上传同名Mod时拒绝，不自动覆盖。
- Mod上传只接受`.jar`，单文件最大`64MiB`。
- 上传先写staging，再atomic move。
- OWNER和ADMIN可管理Mod；MEMBER和GUEST不能管理。

DTO草图：

```kotlin
data class ModFileDto(
    val fileName: String,
    val size: Long,
    val modifiedAt: Long,
    val disabled: Boolean
)
```

### 12.2平台Mod隔离

Host2运行时自动注入匹配MC版本的RDI server Mod及其强制依赖，例如需要时注入KotlinForForge。

- 平台Mod来自共享runtime。
- 平台Mod不复制到Host2的`mods/`。
- 平台Mod不出现在玩家Mod列表。
- 玩家不能disable、delete或replace平台Mod。
- setup必须读取JAR metadata检查Mod ID，不能只看filename。
- server pack只要包含`rdi`或当前版本的其他platform-reserved Mod ID，setup就失败并发Mail。

## 13.受限File Manager

Host2提供与旧Host类似的generic file manager，但只能操作以下root：

```kotlin
val HOST2_OPERABLE_DIRS = mapOf(
    "config" to "配置",
    "datapacks" to "数据包",
    "tacz" to "TaCZ",
    "kubejs" to "KJS",
    "scripts" to "CrT"
)
```

沿用旧Host的extension allowlist：

```kotlin
val HOST2_ALLOWED_FILE_EXTENSIONS = setOf(
    "txt", "js", "json", "json5", "jsonc", "md",
    "ini", "toml", "yaml", "yml", "cfg", "zs",
    "properties", "snbt", "mcmeta", "bak", "lang",
    "lua", "mcfunction", "xml"
)
```

特殊规则：

- `tacz/`只允许直接子级ZIP。
- `mods/`只能走专用Mod API。
- 不允许访问`world/`、`libraries/`、`logs/`、server root或`server.properties`。
- 所有path必须相对于允许root解析并再次验证最终路径。
- 禁止symlink和path traversal。
- OWNER和ADMIN可以使用；MEMBER和GUEST不能使用。
- read/list/search在任意runtime状态可用。
- write/upload/create/rename/delete在运行中也允许。
- setup为`PROCESSING`时禁止写操作。
- 所有写入都必须纳入Host2的`3GiB`quota。

generic file manager的最终单文件大小限制尚未单独确认；不能擅自把Mod`64MiB`限制当成所有文件的限制。

## 14.权限与可见性

### 14.1角色能力

| 操作 | OWNER | ADMIN | MEMBER | GUEST |
| --- | --- | --- | --- | --- |
| 查看自己的Host2 | 是 | 是 | 是 | 仅可见公开列表项 |
| 启动 | 是 | 是 | 是 | 否 |
| 加入已运行Host2 | 是 | 是 | 是 | 仅`whitelist=false` |
| 停止/重启 | 是 | 是 | 否 | 否 |
| command | 是 | 是 | 否 | 否 |
| 实时/历史log | 是 | 是 | 否 | 否 |
| 初始pack上传/失败重试 | 是 | 否 | 否 | 否 |
| 修改name/intro/whitelist | 是 | 是 | 否 | 否 |
| 修改mcVersion/modLoader | 仅未READY时 | 否 | 否 | 否 |
| Mod管理 | 是 | 是 | 否 | 否 |
| File Manager | 是 | 是 | 否 | 否 |
| 邀请成员 | 是 | 是 | 否 | 否 |
| 修改角色/移除成员 | 是 | 否 | 否 | 否 |
| 转移ownership | 是 | 否 | 否 | 否 |
| 删除Host2 | 是 | 否 | 否 | 否 |

### 14.2whitelist语义

Host2没有单独`public`字段，也不使用旧`Host.isPublic`的名称关键字逻辑。

- `whitelist=true`：UI对非成员隐藏并阻止进入。
- `whitelist=false`：GUEST可以在UI看到Host2，并在Host2已经`PLAYABLE`时进入。
- GUEST永远不能启动Host2。
- 这只是UI访问规则，不是security boundary。
- `server.properties`中的Minecraft原生whitelist始终关闭。
- 知道地址和port的玩家可能绕过UI直连，这是明确接受的v1行为。

### 14.3列表

- `GET /host2/my`返回当前玩家拥有或加入的所有Host2，包括`AWAITING_UPLOAD`、`PROCESSING`和`FAILED`。
- `GET /host2/list`返回`READY+whitelist=false`的Host2，以及当前玩家自己的Host2。
- private Host2对非成员不可见。
- GUEST看到STOPPED公开Host2时也不能启动；只有`PLAYABLE`时可通过UI进入。

## 15.成员限制、邀请与转移

### 15.1数量限制

- 每个Account最多拥有`3`个Host2。
- `AWAITING_UPLOAD`、`PROCESSING`、`READY`和`FAILED`都计入owner limit。
- Host2 limit与旧Host limit分开计算。
- 每个Host2最多`10`名participant，即OWNER+最多`9`条member row。
- 每个Account最多加入`10`个不属于自己的Host2。
- joined limit与旧Host membership分开计算。

### 15.2邀请

- 继续使用QQ邀请。
- 通过`PlayerService.getByQQ`解析目标Account。
- 将目标Mongo`ObjectId`转换为派生UUID后写入`host2_member`。
- OWNER和ADMIN可以邀请。
- 新邀请默认角色为`MEMBER`。
- 只有OWNER可以修改角色或移除成员。
- 邀请必须同时校验Host participant limit和目标joined limit。

### 15.3Ownership transfer

转移必须在一个PostgreSQL transaction内完成：

```text
校验发起者是OWNER
    ->校验目标已经是member
    ->校验目标当前owned Host2数量<3
    ->校验旧OWNER当前joined Host2数量<10
    ->更新host2.owner_id为目标
    ->删除目标的host2_member row
    ->把旧OWNER插入为ADMIN
```

任一条件失败时整个transaction回滚。

## 16.Port

- port在创建Host2时随机分配一次。
- 范围为`30000..39999`。
- port写入PostgreSQL并保持不变。
- start、stop和restart都不重新分配。
- `AWAITING_UPLOAD`和`FAILED`仍占用port。
- 删除Host2后port才释放。
- 数据库`UNIQUE`与range`CHECK`是最终并发保护。
- allocator在transaction中选择随机空闲port；并发碰撞时以unique conflict为准重新选择。
- 旧Host继续使用`50000..<60000`，两个范围不重叠。

## 17.Runtime构建与资源

### 17.1启动truth source

每次start/restart：

```text
读取Host2 metadata
    ->确认setupStatus=READY
    ->确认当前无runtime
    ->读取McVersion.loaderVersions[modLoader]
    ->校验共享GAME_LIBS_DIR和平台Mod
    ->patch server.properties/eula.txt
    ->按固定资源创建runtime
    ->绑定持久port
    ->启动并监控
```

server pack里的libraries和启动脚本不参与launch args生成。

### 17.2固定资源

| Minecraft | CPU | Memory | Swap | JVM heap |
| --- | ---: | ---: | ---: | ---: |
| `1.7.10`、`1.12.2` | `2` | `8GiB` | `16GiB` | `-Xmx8G` |
| `1.19.2`、`1.20.1`、`1.21.1` | `4` | `8GiB` | `16GiB` | `-Xmx8G` |

玩家不能修改这些资源。

### 17.3Start前置失败

以下preflight错误同步返回给调用者，不发Mail：

- setup不是`READY`。
- 调用者无start权限。
- runtime已经存在或状态冲突。
- 共享loader library缺失。
- 平台RDI Mod或依赖缺失。
- Host2目录/必要文件不可用。

可能失败的disk/network/runtime函数应返回`Result<T>`；调用者必须`getOrElse`记录异常或`getOrThrow`传播，避免静默`getOrNull`。

## 18.Stop、Restart、断线与Idle

### 18.1普通Stop/Restart

- normal stop先发送graceful stop。
- 最多等待`30秒`。
- 超时后force stop。
- restart是`graceful stop -> remove runtime -> rebuild -> start`。
- restart读取最新项目固定loader映射。
- restart继续使用原port。

### 18.2删除时Stop

删除不要求用户先手动stop：

- delete立即force stop，不等待`30秒`。
- delete也允许发生在`PROCESSING`。
- PROCESSING时先cancel并join setup job，再清理staging。
- 然后删除Host2目录。
- 最后删除PostgreSQL row，member通过FK cascade删除，port随row删除而释放。

### 18.3RDI gameplay WebSocket断线

- RDI gameplay WebSocket断开后等待`30秒`重连。
- `30秒`内恢复则继续运行。
- 未恢复则停止runtime。

### 18.4Idle stop

- 每`1分钟`ping一次Host2。
- 连续`10`次成功ping且online player count为`0`后graceful stop。
- 任一次成功ping发现online player>0时计数归零。
- ping失败不增加空闲计数。
- `enable-status=true`与`hide-online-players=false`确保能够取得player count。

## 19.启动崩溃与Mail

- process实际启动后的前`5分钟`是startup monitoring window。
- `5分钟`内崩溃：force stop，`setupStatus`保持`READY`，给OWNER发Mail。
- Mail包含简短原因与查看log的提示，不把setup/runtime错误持久化到数据库。
- `5分钟`之后的普通退出不发送“启动失败”Mail。
- setup后台成功/失败使用Mail通知。
- master重启发现遗留`PROCESSING`时转`FAILED`、清理staging并发Mail。
- preflight同步错误不发Mail。

## 20.Log

- 只有OWNER和ADMIN可读取log。
- Host2停止时读取`logs/latest.log`末尾`200`行。
- Host2运行时先发送最近`200`行，再follow新输出。
- MEMBER与GUEST都不能读取历史或实时log。
- 最终HTTP/WebSocket endpoint名称尚未在访谈中确认。

## 21.Global player list与Chat兼容

Host2加入与旧Host相同的global player list/chat网络。

Host2发送：

```kotlin
RGlobalPlayerList.Player(
    hostId = host2.id.toString(),
    hostName = host2.name,
    modpackName = "",
    packVer = ""
)
```

规则：

- `hostId`沿用现有String wire field，不改变协议类型。
- `sourceHostId`继续使用String。
- Host2的`modpackName`和`packVer`必须为空字符串。
- 各受支持MC版本的client overlay要在字段为空时隐藏对应separator和文本。
- 最终只显示`hostName`。
- 旧Host的非空modpack显示保持不变。

## 22.API边界

已经确认的route contract：

```text
POST /host2
PUT  /host2/{id}/server-pack
GET  /host2/my
GET  /host2/list

/host/play/{id}     # 旧游戏端兼容入口，按ID格式分流
/host/route?port=   # 旧proxy兼容入口，按port范围分流
```

还需要Host2详情、options、delete、member、transfer、start/stop/restart、command、Mod、file manager和log接口，但它们的最终HTTP method/path尚未逐项确认。恢复grilling后应先固定这些route，不能把草拟path当成已确认API。

## 23.Setup与Runtime状态机

```text
Create
  |
  v
AWAITING_UPLOAD
  | upload accepted
  v
PROCESSING
  | success              | validation/extraction/master-restart failure
  v                      v
READY                  FAILED
  |                      |
  | start                | owner may change version pair and upload again
  v                      +---------------------> PROCESSING
STOPPED/RUNNING
```

约束：

- 只有`READY`能start。
- `READY`不会因为runtime crash变成`FAILED`。
- READY后禁止整包重传。
- `AWAITING_UPLOAD`和`FAILED`不会自动过期。
- 不完整Host2持续占用owner limit和port，直到OWNER删除。
- 只自动清理staging，不自动清理Host2 row。

## 24.删除流程

删除由OWNER发起，目标解析为明确Host2 UUID后执行：

```text
禁止新的setup/runtime操作
    ->若PROCESSING则cancel+join
    ->立即force stop runtime
    ->清理.staging中的目标任务
    ->deleteRecursivelyNoSymlink(<host2Dir>/<id>)
    ->删除host2 row
    ->FK cascade删除host2_member
    ->释放port
```

删除失败时的跨filesystem/DB补偿策略尚未逐项确认。实现不能在Host目录删除失败后静默报告成功。

## 25.实现模块边界草图

这是后续实现顺序建议，不代表用户已经授权开始编码：

1.在`common/model`增加Host2 model、DTO、setup status、member role和Mod DTO。
2.在`server/master`增加PostgreSQL dependencies、`PostgresConfig`、Hikari/Flyway/Exposed provider和Koin module。
3.增加Flyway`V1` Host2 schema。
4.增加Host2 repository和transactional member/port操作。
5.增加Host2 filesystem、quota、ZIP setup和safe extraction service。
6.增加Host2 Mod与受限file manager service。
7.增加Host2 runtime builder、properties patch、idle/startup/disconnect monitor。
8.注册`/host2` CRUD/management routes。
9.给旧`/host/play/{id}`和`/host/route?port`增加Host2分流。
10.适配global list/chat wire值与各MC client overlay空字段显示。
11.适配client UI的创建、上传进度、列表、权限、Mod和file manager。

实现时保持service职责分开，不把database、ZIP、filesystem、runtime和HTTP处理塞进单个大类。

## 26.建议测试范围

项目约束是不自动运行Gradle；实现后只有用户明确要求时才执行测试命令。应准备的focused tests：

- ObjectId派生UUID稳定性和可逆性。
- CreateDto/OptionsDto序列化。
- `mcVersion+modLoader`组合校验。
- Options版本字段必须成对出现。
- port范围、唯一冲突与持久性。
- owner limit、joined limit和participant limit。
- ownership transfer transaction。
- setup状态转换与master重启恢复。
- ZIP wrapper flatten。
- ZIP path traversal、absolute path、symlink、entry count和实际字节限制。
- reserved Mod ID metadata识别。
- `.jar <-> .jar.disabled`映射和同名冲突。
- allowed root、extension和最终canonical path校验。
- server.properties只覆盖平台键。
- guest不能start、member不能stop、admin不能上传初始pack。
- `/host/play/{id}`按ObjectId/UUID分流。
- `/host/route`按port范围分流。
- idle计数只累计成功的零玩家ping。
- startup`5分钟`窗口和Mail触发条件。
- global overlay隐藏空modpack字段且不影响旧Host。

## 27.恢复grilling后仍需确认

以下内容没有在本轮访谈中最终定稿：

- Host2 Brief/Detail DTO的最终字段。
- 除已确认route之外的完整HTTP method/path。
- name和intro的长度/字符校验。
- generic file manager的单文件大小上限。
- runtime增长导致总目录超过`3GiB`时的即时处置。
- PostgreSQL config的具体默认值和部署secret方式。
- Mail标题、正文和是否对setup成功发送Mail的最终文案。
- 删除过程中filesystem成功但DB失败等partial failure补偿。
- 平台reserved Mod ID完整映射表。
- client UI具体screen和交互布局。
- 是否增加`createdAt`、`updatedAt`等审计字段；当前schema草图没有添加。

## 28.最终确认摘要

本次访谈已经确认：

- Host2与Host并行，不迁移旧数据。
- PostgreSQL18+Koin+Exposed+Hikari+Flyway只服务新模块。
- Host2为DB生成UUIDv7；Account引用用ObjectId零填充派生UUID。
- metadata与server pack分两阶段创建。
- 玩家显式选择MC版本和ModLoader，server始终用源码固定loader版本。
- 原始ZIP不保留，安全解压后Host2目录是真相源。
- World只在Host2的`world/`内。
- Mod只由`mods/`文件管理，临时禁用用`.jar.disabled`。
- port随机分配一次并持久保持在`30000..39999`。
- 数据库不保存Minecraft properties，只保存平台必须的port等Host2 metadata。
- 没有旧“公共Host”特殊逻辑，`whitelist`只控制UI可见/进入。
- GUEST绝不能start，但`whitelist=false`且Host2已运行时可从UI进入。
- OWNER/ADMIN/MEMBER权限、数量限制、QQ邀请和ownership transfer规则已确定。
- setup、runtime、删除、restart、断线、idle和startup crash规则已确定。
- 受限file manager、log和global player list兼容策略已确定。
- 最后的CreateDto与OptionsDto字段和修改权限已确认。

下一步只有在用户恢复grilling或明确授权实现后继续；本文本身不是实现授权。

## 29.最终实现覆盖条款

本节记录访谈暂停后继续逐项确认的决定，并覆盖前文冲突内容：

- server pack输入改为玩家选择解压后的server根目录；client过滤无关内容后流式压缩为`tar.zst`上传，不接受ZIP和wrapper flatten。
- 压缩包上限为`4GiB`，解压及Host2总quota为`8GiB`，entry上限仍为`100000`。
- `media`目录、常见媒体文件、`.db`和`world/session.lock`不写入Host2目录；`libraries`、cache、旧log、crash report和启动脚本等非必要内容也不上传。
- client上传前匹配CurseForge/Modrinth Mod。匹配成功的server Mod不进入压缩包，由server根据manifest重新查询平台API并下载；CLIENT-only Mod既不上传也不下载；未匹配JAR保留在压缩包。
- Host2不允许直接上传单个Mod JAR。运行后的受管Mod只能从CurseForge、Modrinth或GitHub Release选择。
- 新增`host2_mod`表。受管Mod以`platform+projectId+fileId+slug+hash+side`持久化，不保存filename、enabled或download URL；download URL在操作时查询平台API。
- 受管Mod文件名固定为`slug_hash.jar`，禁用形式为`slug_hash.jar.disabled`。初始server pack的基础Mod不写入`host2_mod`，不能逐个管理。
- Mod操作状态名固定为`MOD_ADD`、`MOD_DEL`、`MOD_CHG`。
- PostgreSQL默认连接为`jdbc:postgresql://127.0.0.1:5432/rdi`，默认用户名为`rdi`，password默认空字符串，pool size为`10`。
- Host2目录超过`8GiB`时，运行中按分钟检查并graceful stop，同时给OWNER发Mail；只有超过quota时才要求玩家删除内容，不自动回收World。
- ownership route固定为`PUT /host2/{id}/ownership`。ownership transfer、owner/joined limit校验和成员行转换在同一个PostgreSQL transaction中完成。
- 不增加`createdAt`或其他审计时间戳；Host2 UUIDv7已经包含创建时间排序信息。
- client独立入口名称固定为“新版房间”，使用Material3。加入PLAYABLE Host2时，玩家从本机已安装且MC版本、ModLoader匹配的整合包中选择游戏实例。
