可以，而且很适合。现在大量重处理其实已经在client完成，server又扫描和重打包一次，存在重复和规则漂移。

当前职责：

| 处理 | 当前所在位置 | 建议 |
|---|---|---|
| 识别CurseForge/Modrinth包 | client | 保留client |
| 解析Mod、匹配文件、判断side | client+server重复 | client处理，server重新验证 |
| PNG/OGG压缩、过滤缓存和无用资源 | client | 保留client |
| 构建完整上传包 | client | 保留client |
| 从完整包提取客户端包 | server | 移到client |
| 路径安全、大小、hash验证 | server | 必须保留server |
| 下载server-side Mod | server | 必须保留server |
| 保存、发布、权限、状态 | server | 必须保留server |

client已经处理了资源压缩、缓存过滤、`.mca`过滤、`shaderpacks`过滤等复杂逻辑：[ModpackUploadService.kt](/mnt/c/Users/calebxzhou/Documents/coding/rdi5/client/ui/src/main/kotlin/calebxzhou/rdi/client/service/ModpackUploadService.kt:975)

server随后又扫描完整包并构建`-client.tar.zst`：[ModpackService.kt](/mnt/c/Users/calebxzhou/Documents/coding/rdi5/server/master/src/main/kotlin/calebxzhou/rdi/master/service/ModpackService.kt:1352)

建议的目标流程：

```text
client读取原始整合包
  ├─生成serverArchive.tar.zst
  ├─生成clientArchive.tar.zst
  ├─生成Mod列表和artifact manifest
  └─上传两个artifact

server
  ├─验证路径、格式、大小、hash
  ├─重新执行可信Mod规则
  ├─保存两个artifact
  ├─准备server-side Mod
  └─标记版本OK
```

最合适的seam不是继续扩大`ModpackUploadService.kt`，而是在`common`建立一个deep module，例如`ModpackArtifactProcessor`。它隐藏：

- `overrides/`和`gtnh/`路径映射
- client/server内容选择
- 禁止路径规则
- `.mca`、缓存、资源限制
- `tar.zst`构建
- artifact的SHA-1和size计算

对外只暴露类似：

```kotlin
suspend fun prepare(source: File): Result<PreparedModpackArtifacts>
```

`PreparedModpackArtifacts`返回server artifact、client artifact、Mod列表及manifest。client使用正式实现；server使用同一套纯规则验证artifact，而不是维护另一份路径选择代码。

必须留在server的部分：

- 不能相信client提交的hash、Mod side或文件路径
- server必须防止路径穿越和超限文件
- server必须重新运行`ModpackModProcessor`
- server-side Mod应由server从可信来源下载
- server必须管理上传者权限、版本状态和下载配额

这还能直接解决刚才的分享bug：client生成`clientArchive.tar.zst`后，将它保存为：

```text
dl-packs/{modpackId}_{version}.tar.zst
```

自动安装和分享使用同一个artifact；server保存同一份客户端artifact，不再重新生成内容不同的版本。

建议迁移顺序：

1. 把客户端包路径映射和过滤规则提取到`common`。
2. 为同一个输入构建server/client两个artifact。
3. 扩展上传请求，同时接收client artifact及manifest。
4. server验证后直接保存`{version}-client.tar.zst`。
5. client把同一文件提升为正式缓存，再执行安装。
6. 暂时保留server的`buildClientPack()`作为旧版本和修复任务的fallback。
7. 稳定后让普通上传路径不再执行server重打包。

`codebase-design`对本方案的主要影响是：把规则集中到一个deep module，通过一个小interface供client构建、server验证和fallback重建共同使用，避免两端分别维护浅层归档逻辑。

本轮只是设计建议，没有修改代码。