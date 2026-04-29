package calebxzhou.rdi.client.model

enum class RemoteModSource {
    MODRINTH,
    CURSEFORGE
}

data class RemoteModCardVo(
    val source: RemoteModSource,
    val projectId: String,
    val slug: String?,
    val title: String,
    val author: String,
    val summary: String,
    val iconUrl: String?,
    val downloadsText: String,
    val followsText: String?,
    val modifiedText: String?,
    val categories: List<ModrinthProjectCategoryVo>,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val clientSide: String?,
    val serverSide: String?
)

data class RemoteModSearchResult(
    val mods: List<RemoteModCardVo>,
    val offset: Int,
    val limit: Int,
    val totalHits: Int
)
