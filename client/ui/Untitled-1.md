整体评价：约7/10。核心架构是好的，但目前更像“几个设计不错的Module被装进同一个Gradle module”，顶层Interface和分页语义还需要收敛。

做得好的地方：

- `PlatformAdapter`是合理的内部seam。CurseForge与Modrinth确实存在两套Adapter，平台DTO、endpoint规则没有泄漏给调用方。[PlatformAdapter.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/PlatformAdapter.kt:16>)
- `DefaultModCatalog`隐藏了并行查询、batch lookup、partial failure和本地文件匹配，删除它后这些复杂度会扩散到大量调用方，因此它确实是deep module。[DefaultModCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/DefaultModCatalog.kt:24>)
- `PlatformHttpTransport`集中处理header、no-retry、HTTP status和反序列化错误，locality很好。[PlatformHttpTransport.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/PlatformHttpTransport.kt:23>)
- `ModBriefCatalog`只有4个查询方法，SQLite、SQLDelight、锁、read-only连接和数据库验证都藏在实现内部，这是整个module里最干净的Interface。[ModBriefCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/brief/ModBriefCatalog.kt:8>)[SqliteModBriefCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/brief/database/SqliteModBriefCatalog.kt:18>)
- 测试覆盖了平台降级、pagination、download fallback、hash matching、cancellation和brief合并，测试方向总体正确。

主要问题按优先级排序：

1. `UnifiedModCatalog.search()`的分页Interface不可靠。它分别向两个平台请求`pageSize`条，再interleave并去重，所以请求20条可能返回接近40条；同一Mod还可能因两个平台排名不同而跨页重复。`hasNextPage`只是任一平台还有下一页，也不能描述统一结果的真实分页状态。[UnifiedModCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/UnifiedModCatalog.kt:68>)

   这是我认为唯一必须优先修正的设计问题。统一搜索更适合使用携带各平台offset和已见identity的cursor，而不是公开一个看起来普通、实际上语义不同的`page/pageSize`。

2. Gradle module职责过多。当前同时包含：

   - CurseForge/Modrinth的Mod catalog
   - 本地中文brief运行时
   - MC百科抓取、HTML解析、JSON merge、SQLite conversion工具
   - resource pack/shader pack的`ModrinthContentCatalog`

   因此runtime module直接依赖Jsoup、SQLDelight、SQLite和各种builder工具，[build.gradle.kts](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/build.gradle.kts:11>)里还注册了3个数据构建任务。建议至少把`brief`抓取、merge、converter和对应测试迁到独立`:mod-brief-tools`；运行时brief可以继续留在catalog内部，因为它确实参与统一搜索。`ModrinthContentCatalog`则属于modpack content领域，最好移动到对应Module。

3. 外部seam有两层且命名倒置。DI同时注册原始`ModCatalog`和具体类`UnifiedModCatalog`，[AppModule.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/app/src/main/kotlin/calebxzau/craftrdi/client/app/AppModule.kt:104>)而真实调用方主要依赖具体的`UnifiedModCatalog`。[ModpackModManager.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/modpack/src/main/kotlin/calebxzau/craftrdi/client/modpack/ModpackModManager.kt:216>)

   更自然的结构是：

   - 对调用方公开`ModCatalog`Interface，返回统一、本地化后的模型。
   - 当前`ModCatalog`改成内部`RemoteModCatalog`。
   - 当前`UnifiedModCatalog`变成公开Interface的默认实现。
   - Adapter、platform DTO和brief lookup全部留在实现内部。

4. `ModFile`含有不参与构造、copy和equals的隐藏可变字段`knownDownloadUrl`。[ModCatalogModels.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/ModCatalogModels.kt:33>)这会让相同的domain value因创建路径不同而拥有不同隐藏状态。download URL应成为明确的不可变数据，或者完全存放在Adapter内部cache。

5. `UnifiedModCatalog`遍历`ModPlatform.entries`，但实际启用的平台由`DefaultModCatalog`配置决定。[UnifiedModCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/UnifiedModCatalog.kt:69>)关闭某个平台后，每次统一搜索都会把“未配置”当作partial failure。启用平台集合应该来自内部catalog，而不是全局enum。

6. 默认CurseForge credential被编码在源码中。[ModCatalog.kt](</mnt/c/Users/calebxzhou/Documents/coding/craftrdi/client/mod-catalog/src/main/kotlin/calebxzau/craftrdi/client/modcatalog/ModCatalog.kt:40>)桌面客户端里的key无法真正保密，这种byte-array写法只增加维护成本并制造“已经隐藏”的错觉。至少应明确它是可公开的client credential，并从构建配置生成。

所以我的结论是：Adapter层、错误模型和本地brief catalog设计得不错，值得保留；问题主要集中在统一搜索语义和Gradle module边界。先修分页，再拆builder工具，最后收敛顶层Interface，整体会从“能工作且结构不错”变成真正稳定的deep module。

本次只做了current source静态评审，没有改代码，也没有运行Git或Gradle。历史记录仅用于还原原始设计意图，最终判断以当前源码为准。