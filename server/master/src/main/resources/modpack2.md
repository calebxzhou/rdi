Native phrasing: I want to build my own launcher with a single-file modpack container that supports incremental updates. Give me a complete solution.

推荐直接做成：

```text
modpack.pack
└── SQLite
    ├── metadata
    ├── files            路径 → hash
    └── blobs            hash → 文件内容
```

核心设计是：

> **Content-addressed immutable blobs + SQLite + per-file compression**

不要直接做：

```text
path → compressed BLOB
```

而是：

```text
path
 ↓
SHA-256
 ↓
blob
```

这样增量更新、去重、事务和回滚都会简单很多。

---

## 1. 整体架构

```text
                 Update Server
                     │
             manifest.json/cbor
                     │
              missing blob list
                     │
                     ▼
┌─────────────────────────────────────┐
│ modpack.pack                        │
│              SQLite                │
│                                     │
│ metadata                            │
│ files                               │
│ blobs                               │
└─────────────────────────────────────┘
                     │
                     │ materialize
                     ▼
Minecraft Instance Directory
├── mods/
├── config/
├── kubejs/
├── resourcepacks/
└── ...
```

`modpack.pack` 是 Launcher 管理的源数据。

Minecraft **不要直接从 SQLite 运行**，启动前同步到正常实例目录。

---

# 2. SQLite schema

我建议第一版直接这样：

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE metadata (
    key   TEXT PRIMARY KEY,
    value BLOB NOT NULL
);

CREATE TABLE blobs (
    hash          BLOB PRIMARY KEY,        -- SHA-256, 32 bytes
    raw_size      INTEGER NOT NULL,
    stored_size   INTEGER NOT NULL,

    codec         INTEGER NOT NULL,        -- 0 = raw, 1 = zstd
    data          BLOB NOT NULL,

    CHECK(length(hash) = 32)
);

CREATE TABLE files (
    path          TEXT PRIMARY KEY,

    hash          BLOB NOT NULL,

    policy        INTEGER NOT NULL DEFAULT 0,
    flags         INTEGER NOT NULL DEFAULT 0,

    FOREIGN KEY(hash)
        REFERENCES blobs(hash)
);

CREATE INDEX idx_files_hash
ON files(hash);
```

例如：

```text
files
------------------------------------------------
mods/jei.jar                    → A93F...
mods/create.jar                 → C820...
config/create-common.toml      → 14AF...
kubejs/server_scripts/main.js  → DD20...
```

而：

```text
blobs

A93F... → raw   → jei.jar
C820... → raw   → create.jar
14AF... → zstd  → create-common.toml
DD20... → zstd  → main.js
```

---

# 3. 为什么一定要分 `files` 和 `blobs`

不要：

```text
files
path
data
```

而应该：

```text
files
path → hash

blobs
hash → data
```

因为 Minecraft modpack 非常适合 CAS（Content Addressed Storage）。

假设：

```text
mods/jei.jar
mods/sodium.jar
config/a.toml
```

更新之后只有：

```text
config/a.toml
```

改变。

旧版本：

```text
jei.jar       → hash A
sodium.jar    → hash B
a.toml        → hash C
```

新版：

```text
jei.jar       → hash A
sodium.jar    → hash B
a.toml        → hash D
```

只需要下载：

```text
blob D
```

然后：

```sql
UPDATE files
SET hash = ?
WHERE path = 'config/a.toml';
```

完全不用碰 A/B。

---

# 4. Blob 永远不要 UPDATE

这是这个设计很重要的一点。

不要：

```sql
UPDATE blobs
SET data = ...
```

应该：

```text
旧文件
hash C
   ↓

新文件
hash D
```

执行：

```sql
INSERT INTO blobs (...) VALUES (...D...);

UPDATE files
SET hash = D
WHERE path = 'config/a.toml';
```

然后如果 C 已经没人引用：

```sql
DELETE FROM blobs
WHERE hash = C;
```

这样 `blobs` 实际上是 immutable。

### 优点

减少很多 SQLite BLOB 修改产生的问题：

```text
大 BLOB resize
overflow page relocation
in-place replacement
```

整个数据模型就是：

```text
INSERT new
change reference
DELETE old
```

非常适合增量更新。

---

# 5. 压缩策略

Minecraft 整合包不要所有文件都 Zstd。

### 直接 RAW 保存

这些通常已经压缩：

```text
.jar
.zip
.png
.jpg
.jpeg
.webp
.avif
.ogg
.mp3
.flac
.mp4
.gz
.7z
```

例如：

```text
mods/create.jar

codec = 0
data = original jar
```

### Zstd 保存

这些很适合：

```text
.json
.toml
.cfg
.conf
.properties
.txt
.lang
.js
.kts
.lua
.xml
.mcmeta
```

例如：

```text
config/create-common.toml

codec = 1
data = ZSTD(...)
```

---

## 更推荐自动判断

不要完全依赖扩展名。

对于可能可压缩的数据：

```text
compressed = zstd(raw)

if compressed_size < raw_size * 0.95
    → ZSTD
else
    → RAW
```

例如：

```text
100 KB raw
↓
97 KB zstd
```

只省 3%，那就不压。

而：

```text
100 KB raw
↓
20 KB zstd
```

就保存 Zstd。

建议 Zstd：

```text
level 3 ~ 6
```

Launcher 这种场景没必要追求 level 19。

---

# 6. Hash

推荐：

```text
SHA-256
```

计算：

```text
raw original file
       │
       ├── SHA-256 ────→ blob hash
       │
       └── compression → stored data
```

**hash 原始内容，不要 hash 压缩后的内容。**

即：

```text
hash = SHA256(raw)
```

而不是：

```text
SHA256(zstd(raw))
```

这样以后修改：

```text
zstd level
zstd version
compression strategy
```

不会改变文件 identity。

---

# 7. Server manifest

服务器每个 modpack 版本提供一个很小的 manifest。

例如：

```json
{
  "pack": "my-modpack",
  "version": "1.4.2",
  "files": [
    {
      "path": "mods/create.jar",
      "hash": "aabbcc...",
      "size": 22442435
    },
    {
      "path": "config/create-common.toml",
      "hash": "112233...",
      "size": 18343
    }
  ]
}
```

注意 manifest 最好描述：

```text
raw file hash
```

而不是 SQLite 内部具体 offset。

服务器可以提供：

```text
GET /packs/mypack/1.4.2/manifest

GET /blobs/<sha256>
```

例如：

```text
GET /blobs/a8d10c...
```

---

# 8. 增量更新算法

本地：

```text
Version 1.4.1
```

服务器：

```text
Version 1.4.2
```

Launcher 下载 manifest。

### 第一步：比较 manifest

服务器需要：

```text
A
B
C
D
E
```

本地数据库有：

```text
A
B
C
```

那么：

```text
missing =
D
E
```

只下载：

```text
D
E
```

---

## 第二步：写入 blobs

```sql
INSERT OR IGNORE INTO blobs
(hash, raw_size, stored_size, codec, data)
VALUES (?, ?, ?, ?, ?);
```

---

## 第三步：更新 file mapping

例如：

```sql
BEGIN;

INSERT INTO blobs ...;

INSERT INTO files(path, hash)
VALUES (?, ?)
ON CONFLICT(path)
DO UPDATE SET hash = excluded.hash;

COMMIT;
```

整个版本切换最好在一个 transaction 中完成。

这样：

```text
下载过程中断
```

不会导致 pack manifest 半新半旧。

---

# 9. 删除文件

比如新版移除了：

```text
mods/oldmod.jar
```

只需要：

```sql
DELETE FROM files
WHERE path = 'mods/oldmod.jar';
```

然后 GC。

---

# 10. Garbage Collection

删除映射之后，旧 blob 可能没有任何文件使用。

找到：

```sql
SELECT hash
FROM blobs
WHERE NOT EXISTS (
    SELECT 1
    FROM files
    WHERE files.hash = blobs.hash
);
```

删除：

```sql
DELETE FROM blobs
WHERE NOT EXISTS (
    SELECT 1
    FROM files
    WHERE files.hash = blobs.hash
);
```

于是：

```text
files
     │
     ├── A
     ├── B
     └── D

blobs
     ├── A
     ├── B
     ├── C ← unreachable
     └── D
```

GC 后：

```text
A
B
D
```

---

# 11. 不要每次更新后 VACUUM

GC：

```sql
DELETE FROM blobs ...
```

之后，SQLite 文件可能还是：

```text
8 GB
```

即使实际只剩：

```text
7.5 GB
```

没关系。

这些 SQLite freelist pages 会以后复用。

所以：

```text
update
update
update
update
```

过程中不要：

```sql
VACUUM;
```

否则每次可能重写整个 pack。

可以偶尔查看：

```sql
PRAGMA page_count;
PRAGMA freelist_count;
PRAGMA page_size;
```

计算：

```text
free ratio =
freelist_count / page_count
```

例如：

```text
> 20~30%
```

再考虑 compact。

---

# 12. 可以启用 incremental vacuum

创建数据库**最开始**：

```sql
PRAGMA auto_vacuum = INCREMENTAL;
VACUUM;
```

之后偶尔：

```sql
PRAGMA incremental_vacuum(1000);
```

但它主要用于缩减可以从尾部释放的 page。

不是传统意义上的：

```text
defragment everything
```

---

# 13. Minecraft 文件更新策略

这个问题非常重要。

不要让 Launcher 无脑覆盖所有文件。

建议 `files.policy`：

```text
0 = managed
1 = preserve
2 = install-if-missing
```

例如：

### managed

Launcher 完全控制：

```text
mods/*
kubejs/*
scripts/*
resourcepacks/*
shaderpacks/*
```

更新时：

```text
server version wins
```

### preserve

用户自己的：

```text
options.txt
servers.dat
```

通常**根本不要放 pack**。

### install-if-missing

例如：

```text
config/client-default.toml
```

第一次安装：

```text
不存在 → 写入
```

之后用户修改：

```text
存在 → 保留
```

---

# 14. 不要把这些放进 pack

运行时数据：

```text
saves/
logs/
crash-reports/
screenshots/
journeymap/
backups/
options.txt
servers.dat
```

应该留在 instance filesystem。

你的 pack 主要管理：

```text
mods/
config defaults
kubejs/
scripts/
resourcepacks/
shaderpacks/
libraries/custom files
```

---

# 15. Materialize 到游戏目录

启动前：

```text
modpack.pack
      │
      ▼
instance/
```

遍历：

```sql
SELECT path, hash
FROM files;
```

查：

```sql
SELECT codec, raw_size, data
FROM blobs
WHERE hash = ?;
```

如果：

```text
codec = RAW
```

直接：

```text
BLOB → filesystem
```

如果：

```text
codec = ZSTD
```

：

```text
BLOB
 ↓
Zstd streaming decompression
 ↓
filesystem
```

---

# 16. 不要每次启动全部解包

这是 Launcher 性能的关键。

维护一个 materialized state：

```sql
CREATE TABLE materialized (
    path          TEXT PRIMARY KEY,
    hash          BLOB NOT NULL
);
```

例如：

```text
mods/create.jar        → A
mods/jei.jar           → B
config/test.toml       → D
```

pack 目前也是：

```text
create.jar → A
jei.jar    → B
test.toml  → D
```

那么：

```text
全部 skip
```

更新后：

```text
test.toml → E
```

只 materialize：

```text
test.toml
```

---

# 17. 更安全的文件写入

不要直接：

```text
write config/a.toml
```

应该：

```text
config/a.toml.tmp
        ↓
write
        ↓
fsync
        ↓
atomic rename
        ↓
config/a.toml
```

这样 Launcher 崩掉也不容易产生半个文件。

---

# 18. Update transaction

我建议整个流程：

```text
1. 下载 manifest

2. 比较 local blobs

3. 下载 missing blobs
       ↓
   temp/cache

4. verify SHA-256

5. BEGIN TRANSACTION

6. INSERT missing blobs

7. UPDATE files mapping

8. DELETE removed mappings

9. update metadata version

10. COMMIT

11. GC unreferenced blobs

12. materialize changed files

13. launch Minecraft
```

尤其第 4 步必须做：

```text
download
 ↓
decompress if needed
 ↓
SHA256(raw)
 ↓
compare expected hash
```

---

# 19. 支持 rollback

如果你想以后支持：

```text
1.4.2 → 1.4.1
```

可以增加：

```sql
CREATE TABLE releases (
    id TEXT PRIMARY KEY,
    created_at INTEGER NOT NULL
);

CREATE TABLE release_files (
    release_id TEXT NOT NULL,
    path       TEXT NOT NULL,
    hash       BLOB NOT NULL,

    PRIMARY KEY(release_id, path),

    FOREIGN KEY(release_id)
        REFERENCES releases(id),

    FOREIGN KEY(hash)
        REFERENCES blobs(hash)
);
```

于是：

```text
1.4.1
├── create → A
└── config → B

1.4.2
├── create → A
└── config → C
```

A 只保存一次。

Blob：

```text
A
B
C
```

rollback 只需要切 manifest。

但如果不需要 rollback，**第一版不要做这个**。

只维护：

```text
current files
```

会简单很多。

---

# 20. 推荐最终 Schema

第一版我会直接使用：

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE metadata (
    key   TEXT PRIMARY KEY,
    value BLOB NOT NULL
);

CREATE TABLE blobs (
    hash        BLOB PRIMARY KEY,
    raw_size    INTEGER NOT NULL,
    stored_size INTEGER NOT NULL,
    codec       INTEGER NOT NULL,
    data        BLOB NOT NULL,

    CHECK(length(hash) = 32),
    CHECK(codec IN (0, 1))
);

CREATE TABLE files (
    path    TEXT PRIMARY KEY,
    hash    BLOB NOT NULL,
    policy  INTEGER NOT NULL DEFAULT 0,
    flags   INTEGER NOT NULL DEFAULT 0,

    FOREIGN KEY(hash)
        REFERENCES blobs(hash)
);

CREATE INDEX idx_files_hash
ON files(hash);
```

metadata：

```text
format_version = 1
pack_id        = "gtnh-custom"
pack_version   = "2.8.4"
minecraft      = "1.7.10"
loader         = "forge"
```

---

# 21. SQLite 设置

创建 pack 时我会考虑：

```sql
PRAGMA page_size = 16384;
PRAGMA auto_vacuum = INCREMENTAL;
VACUUM;
```

运行：

```sql
PRAGMA foreign_keys = ON;
PRAGMA synchronous = FULL;
```

如果你使用：

```sql
PRAGMA journal_mode = WAL;
```

性能会很好，但更新期间会出现：

```text
modpack.pack
modpack.pack-wal
modpack.pack-shm
```

所以严格来说此时不是“只有一个文件”。

如果你的要求只是：

> 用户下载、复制、分享时只有一个 `.pack`

那完全没问题，更新完成后执行 checkpoint：

```sql
PRAGMA wal_checkpoint(TRUNCATE);
```

关闭数据库后正常只剩：

```text
modpack.pack
```

如果你要求**任何时候磁盘上绝对只能存在一个文件**，SQLite 的 crash-safe transaction 就会受到限制，我不建议为了这个要求禁用 journal。

---

# 22. HTTP 更新接口

服务器甚至可以非常简单：

```text
GET /packs/{id}/manifest

GET /blobs/{sha256}
```

例如：

```text
GET /packs/gtnh/manifest

{
    "version": "2.9.0",
    ...
}
```

Launcher：

```text
missingHashes = remoteHashes - localHashes
```

然后并发：

```text
GET /blobs/A
GET /blobs/B
GET /blobs/C
```

非常适合 CDN。

而且 Blob URL：

```text
/blobs/<SHA256>
```

天然 immutable，可以：

```http
Cache-Control: public, max-age=31536000, immutable
```

---

## 最终结构

我建议你的 Launcher 架构最终是：

```text
                    CDN / Update Server
                           │
               manifest + CAS blobs
                           │
                           ▼
                ┌──────────────────┐
                │   Launcher       │
                └────────┬─────────┘
                         │
                         ▼
              ┌──────────────────────┐
              │ modpack.pack         │
              │                      │
              │ SQLite               │
              │ ├─ metadata          │
              │ ├─ files             │
              │ └─ blobs             │
              │      ├─ RAW JAR      │
              │      ├─ RAW PNG      │
              │      ├─ ZSTD JSON    │
              │      └─ ZSTD TOML    │
              └──────────┬───────────┘
                         │
                  materialize delta
                         │
                         ▼
               Minecraft instance/
               ├─ mods/
               ├─ config/
               ├─ kubejs/
               └─ ...
```

其中最关键的三个设计决定是：

```text
1. hash → immutable blob
2. path → hash
3. 一个文件一个独立 compression unit
```

这样你就能同时得到：

**单 `.pack` 文件 + 单文件增量更新 + 去重 + 随机访问 + 事务安全 + 不需要重写整个 pack。**
