package calebxzhou.rdi.common.model

import calebxzhou.mykotutils.std.urlEncoded
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Path

/**
 * calebxzhou @ 2025-12-23 13:13
 */

data class CurseForgeModpackData(
    val manifest: CurseForgePackManifest,
    val file: File,
)

@Serializable
data class CurseForgePackManifest(
    var name: String,
    var version: String,
    val minecraft: Mc,
    val overrides: String? = null,
    val files: List<CurseForgePackManifest.File> = arrayListOf(),
){
    @Serializable
    data class Mc(
        val version: String,
        val modLoaders: List<ModLoader> = emptyList()
    )

    @Serializable
    data class ModLoader(
        val id: String,
        val primary: Boolean = false
    )
    @Serializable
    data class File(
        @SerialName("projectID") val projectId: Int,
        @SerialName("fileID") val fileId: Int,
        val required: Boolean = true,
    )
}



data class CurseForgeLocalResult(
    val matched: List<ModCardMatch> = emptyList(),
    val unmatched: List<File> = emptyList()
)





@Serializable
data class CurseForgeModInfo(
    val id: Int,
    val gameId: Int? = null,
    val name: String,
    val slug: String,
    val links: CurseForgeModLinks? = null,
    val summary: String? = null,
    val status: Int? = null,
    val downloadCount: Long? = null,
    val isFeatured: Boolean? = null,
    val primaryCategoryId: Long? = null,
    val categories: List<CurseForgeCategoryInfo> = emptyList(),
    val classId: Long? = null,//6才是mc mod
    val authors: List<CurseForgeAuthor> = emptyList(),
    val logo: CurseForgeAsset? = null,
    val screenshots: List<CurseForgeAsset> = emptyList(),
    val mainFileId: Long? = null,
    val latestFiles: List<CurseForgeFile> = emptyList(),
    val latestFilesIndexes: List<CurseForgeFileIndex> = emptyList(),
    val latestEarlyAccessFilesIndexes: List<CurseForgeFileIndex> = emptyList(),
    val dateCreated: String? = null,
    val dateModified: String? = null,
    val dateReleased: String? = null,
    val allowModDistribution: Boolean? = null,
    val gamePopularityRank: Long? = null,
    val isAvailable: Boolean? = null,
    val thumbsUpCount: Long? = null,
    val rating: Double? = null
){
    val isMod = classId == 6L
}

@Serializable
data class CurseForgeModLinks(
    val websiteUrl: String? = null,
    val wikiUrl: String? = null,
    val issuesUrl: String? = null,
    val sourceUrl: String? = null
)

@Serializable
data class CurseForgeCategoryInfo(
    val id: Long? = null,
    val gameId: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val url: String? = null,
    val iconUrl: String? = null,
    val dateModified: String? = null,
    val isClass: Boolean? = null,
    val classId: Long? = null,
    val parentCategoryId: Long? = null,
    val displayIndex: Long? = null
)

@Serializable
data class CurseForgeAuthor(
    val id: Long? = null,
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class CurseForgeAsset(
    val id: Long? = null,
    val modId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val url: String? = null
)

@Serializable
data class CurseForgeFileIndex(
    val gameVersion: String? = null,
    val fileId: Long? = null,
    val filename: String? = null,
    val releaseType: Int? = null,
    val gameVersionTypeId: Long? = null,
    val modLoader: Int? = null
)



@Serializable
data class CurseForgeFileResponse(
    val data: CurseForgeFile? = null
)
@Serializable
data class CurseForgeFileListResponse(
    val data: List<CurseForgeFile> = emptyList(),
    val pagination: CurseForgePagination? = null
)

@Serializable
data class CurseForgeModSearchResponse(
    val data: List<CurseForgeModInfo> = emptyList(),
    val pagination: CurseForgePagination? = null
)

@Serializable
data class CurseForgePagination(
    val index: Int = 0,
    val pageSize: Int = 0,
    val resultCount: Int = 0,
    val totalCount: Int = 0
)

@Serializable
data class CurseForgeStringResponse(
    val data: String? = null
)


@Serializable
data class CurseForgeFileDependency(
    val modId: Long? = null,
    val relationType: Int? = null
)

@Serializable
data class CurseForgeFileModule(
    val name: String? = null,
    val fingerprint: Long? = null
)


@Serializable
data class CurseForgeFingerprintResponse(
    val data: CurseForgeFingerprintData? = null
)

@Serializable
data class CurseForgeFingerprintData(
    val isCacheBuilt: Boolean = false,
    val exactMatches: List<CurseForgeFingerprintMatch> = emptyList(),
    val exactFingerprints: List<Long> = emptyList(),
    val partialMatches: List<CurseForgeFingerprintMatch> = emptyList(),
    val partialMatchFingerprints: Map<String, List<Long>> = emptyMap(),
    val installedFingerprints: List<Long> = emptyList(),
    val unmatchedFingerprints: List<Long> = emptyList()
)

@Serializable
data class CurseForgeFingerprintMatch(
    val id: Int = 0,
    val file: CurseForgeFile,
    val latestFiles: List<CurseForgeFile> = emptyList()
)

@Serializable
data class CurseForgeFile(
    val id: Int = 0,
    val gameId: Int = 0,
    val modId: Int = 0,
    val isAvailable: Boolean = false,
    val displayName: String? = null,
    val fileName: String? = null,
    val releaseType: Int? = null,
    val fileStatus: Int? = null,
    val hashes: List<CurseForgeFileHash> = emptyList(),
    val fileDate: String? = null,
    val fileLength: Long? = null,
    val downloadCount: Long? = null,
    val fileSizeOnDisk: Long? = null,
    val downloadUrl: String? = null,
    val gameVersions: List<String> = emptyList(),
    val sortableGameVersions: List<CurseForgeSortableGameVersion> = emptyList(),
    val dependencies: List<CurseForgeDependency> = emptyList(),
    val exposeAsAlternative: Boolean? = null,
    val parentProjectFileId: Long? = null,
    val alternateFileId: Long? = null,
    val isServerPack: Boolean? = null,
    val serverPackFileId: Long? = null,
    val isEarlyAccessContent: Boolean? = null,
    val earlyAccessEndDate: String? = null,
    val fileFingerprint: Long,
    val modules: List<CurseForgeModule>? = emptyList()
){
    val realDownloadUrl
        get() = downloadUrl?:"https://mediafilez.forgecdn.net/files/${id.toString().substring(0..3).toInt()}/${id.toString().substring(4).toInt()}/${fileName?.urlEncoded}"
}

@Serializable
data class CurseForgeFileHash(
    val value: String? = null,
    val algo: Int? = null
)

@Serializable
data class CurseForgeSortableGameVersion(
    val gameVersionName: String? = null,
    val gameVersionPadded: String? = null,
    val gameVersion: String? = null,
    val gameVersionReleaseDate: String? = null,
    val gameVersionTypeId: Int? = null
)

@Serializable
data class CurseForgeDependency(
    val modId: Long? = null,
    val relationType: Int? = null
)

@Serializable
data class CurseForgeModule(
    val name: String? = null,
    val fingerprint: Long? = null
)

@Serializable
data class CFDownloadMod(
    val projectId: Int,
    val fileId: Int,
    val slug: String,
    val path: Path
)
