package calebxzau.rdi.client.modcatalog

import java.net.URI

internal data class SourcePage(
    val items: List<CatalogProjectSource>,
    val nextOffset: Int?,
    val totalCount: Long?
)

internal data class AdapterResult<T>(
    val found: Map<String, T>,
    val missing: Set<String>
)

internal data class SlugResolution(
    val found: Map<String, CatalogProjectSource>,
    val missing: Set<String>
)

internal sealed interface AdapterFileList {
    val items: List<CatalogFile>

    data class Complete(override val items: List<CatalogFile>) : AdapterFileList

    data class Page(
        override val items: List<CatalogFile>,
        val nextOffset: Int?
    ) : AdapterFileList
}

internal interface PlatformAdapter {
    val platform: ModPlatform

    suspend fun search(
        query: String,
        target: CatalogTarget,
        sort: CatalogSort,
        offset: Int,
        limit: Int
    ): SourcePage

    suspend fun resolveSlugs(slugs: List<String>, target: CatalogTarget): SlugResolution

    suspend fun getProjects(ids: Set<String>): AdapterResult<CatalogProjectSource>

    suspend fun getDetails(ref: CatalogProjectRef): CatalogModDetailsSource

    suspend fun listFiles(
        project: CatalogProjectRef,
        target: CatalogTarget,
        offset: Int,
        limit: Int
    ): AdapterFileList

    suspend fun getFiles(ids: Set<String>): AdapterResult<CatalogFile>

    suspend fun matchFiles(files: List<CatalogFileHashes>): Map<String, CatalogFile>

    suspend fun matchLocalFiles(files: List<LocalFileHashes>): Map<java.nio.file.Path, CatalogFile> {
        if (files.isEmpty()) return emptyMap()
        val hashesByKey = files.mapIndexed { index, file ->
            val key = "local-file-$index"
            key to file
        }.toMap()
        val matches = matchFiles(
            hashesByKey.map { (key, file) ->
                CatalogFileHashes(key, file.sha1, file.curseForgeFingerprint)
            }
        )
        return matches.mapNotNull { (key, file) ->
            hashesByKey[key]?.path?.let { path -> path to file }
        }.toMap()
    }

    suspend fun getChangelog(file: CatalogFileRef): String

    suspend fun resolveDownload(file: CatalogFile): ResolvedDownload
}

internal data class CatalogModDetailsSource(
    val project: CatalogProjectSource,
    val description: String,
    val authors: List<String>,
    val categories: List<String>,
    val sourceUrl: String?,
    val issuesUrl: String?,
    val wikiUrl: String?
)

internal fun String.validatedDownloadUrl(platform: ModPlatform): String {
    val uri = try {
        URI(this)
    } catch (cause: Exception) {
        throw CatalogException.InvalidResponse(platform, cause)
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
        throw CatalogException.InvalidResponse(platform)
    }
    return this
}
