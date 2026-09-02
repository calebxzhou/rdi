package calebxzau.rdi.common.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.uuid.Uuid

/** Database records for the Modpack2 PostgreSQL schema. */
data class Modpack2(
    val id: UUID,
    val name: String,
    val ownerId: UUID,
    val intro: String,
    val mc: Int,
    val loader: Modpack2Loader,
    val iconUrl: String,
    val sourceUrl: String? = null,
)

data class Modpack2Category(
    val modpackId: UUID,
    val category: ModpackCategory,
)

data class Modpack2Stats(
    val modpackId: UUID,
    val playCount: Int = 0,
    val playTimeSec: Long = 0,
)

data class Modpack2Version(
    val id: UUID,
    val modpackId: UUID,
    val uploaderId: UUID?,
    val name: String,
    val changelog: String,
    val status: Modpack2VersionStatus,
    val totalSize: Long,
    val serverMode: Modpack2ServerMode = Modpack2ServerMode.Generated,
    val baseVersionId: UUID? = null,
)

/** The installer manifest format used to publish a Modpack2 version. */
@Serializable
enum class Modpack2ManifestFormat {
    CurseForge,
    Modrinth,
}

/** A complete, server-verified installer manifest submission. */
@Serializable
data class Modpack2VersionManifestDto(
    val format: Modpack2ManifestFormat,
    val manifestJson: String,
    val bindings: List<Modpack2ContentBindingDto>,
)

/** Exact identity of one submitted platform content declaration. */
@Serializable
data class Modpack2ContentKeyDto(
    val platform: ContentPlatform,
    val type: ContentType,
    val projectId: String,
    val fileId: String,
    val hash: String,
    val targetPath: String? = null,
    val side: ContentSide,
)

/** Associates one submitted content with its authoritative manifest/raw source. */
@Serializable
data class Modpack2ContentBindingDto(
    val contentKey: Modpack2ContentKeyDto,
    val source: Modpack2ContentSourceDto,
)

@Serializable
sealed class Modpack2ContentSourceDto {
    @Serializable
    @SerialName("curseforge_manifest_entry")
    data class CurseForgeManifestEntry(
        val projectId: String,
        val fileId: String,
    ) : Modpack2ContentSourceDto()

    @Serializable
    @SerialName("modrinth_manifest_entry")
    data class ModrinthManifestEntry(
        val path: String,
        val sha1: String,
    ) : Modpack2ContentSourceDto()

    @Serializable
    @SerialName("raw_source")
    data class RawSource(
        val root: Modpack2RawFileRoot,
        val path: String,
        val sha1: String,
        val size: Long,
    ) : Modpack2ContentSourceDto()
}

@Serializable
data class Modpack2Content(
    @Contextual
    val versionId: UUID,
    val platform: ContentPlatform,
    val type: ContentType,
    val projectId: String,
    val fileId: String,
    val slug: String,
    val hash: String,
    val targetPath: String? = null,
    val side: ContentSide,
    val required: Boolean = true,
    val fileSize: Long,
)

@Serializable
enum class Modpack2VersionStatus {
    Fail, Ok, Building,
}

@Serializable
enum class Modpack2Loader {
    Forge,
    NeoForge,
}

/** How the server side of a published version is produced. */
@Serializable
enum class Modpack2ServerMode {
    Provided,
    Generated,
}

/** The three roots accepted by a Modpack2 raw source archive. */
@Serializable
enum class Modpack2RawFileRoot {
    Client,
    Shared,
    Server,
}

/** A raw file materialized in a version source snapshot. */
@Serializable
data class Modpack2RawFile(
    val root: Modpack2RawFileRoot,
    val path: String,
    val sha1: String,
    val size: Long,
)

/** A declaration used when a Generated append inherits a server file. */
@Serializable
data class Modpack2InheritedRawFile(
    val path: String,
    val sha1: String,
)

enum class Modpack2Sort {
    Updated,
    Popular,
    Name,
}

data class Modpack2Search(
    val keyword: String? = null,
    val category: ModpackCategory? = null,
    val ownerId: UUID? = null,
    val sort: Modpack2Sort = Modpack2Sort.Updated,
    val offset: Long = 0,
    val limit: Int = 50,
)

@Serializable
data class Modpack2Page<T>(
    val items: List<T>,
    val total: Long,
    val offset: Long,
    val limit: Int,
    val hasMore: Boolean,
)

/** A database projection used by search, owned lists, and detail views. */
data class Modpack2Detail(
    val modpack: Modpack2,
    val categories: List<Modpack2Category>,
    val stats: Modpack2Stats,
    val currentVersion: Modpack2Version?,
    val contentCount: Long = 0,
)

/** Payload used to create the first version of a Modpack2 from an upload session. */
@Serializable
data class Modpack2CreateDto(
    @Contextual
    val uploadId: UUID,
    val modpack: Modpack2CreateModpackDto,
    val version: Modpack2CreateVersionDto,
)

@Serializable
data class Modpack2CreateModpackDto(
    val name: String,
    val intro: String,
    val mc: Int,
    val loader: Modpack2Loader,
    val iconUrl: String,
    val sourceUrl: String? = null,
    val categories: List<ModpackCategory>,
)

@Serializable
data class Modpack2CreateVersionDto(
    val name: String,
    val manifest: Modpack2VersionManifestDto,
    val changelog: String = "",
    val contents: List<Modpack2ContentDto>? = null,
    val serverMode: Modpack2ServerMode = Modpack2ServerMode.Generated,
    val rawFiles: List<Modpack2RawFile> = emptyList(),
    val inheritedServerFiles: List<Modpack2InheritedRawFile> = emptyList(),
)

/** Content metadata supplied for a new version; the server assigns versionId. */
@Serializable
data class Modpack2ContentDto(
    val platform: ContentPlatform,
    val type: ContentType,
    val projectId: String,
    val fileId: String,
    val slug: String,
    val hash: String,
    val targetPath: String? = null,
    val side: ContentSide,
    val required: Boolean = true,
    val fileSize: Long,
)

@Serializable
data class Modpack2CreateResult(
    @Contextual
    val modpackId: UUID,
    @Contextual
    val versionId: UUID,
    val status: Modpack2VersionStatus = Modpack2VersionStatus.Building,
)

/** Request used to verify that an append is based on the current Ok version. */
@Serializable
data class Modpack2AppendPreflightDto(
    @Contextual val baseVersionId: UUID,
)

/** Result of append preflight. Invalid append states are returned as API errors. */
@Serializable
data class Modpack2AppendPreflightVo(
    @Contextual val modpackId: UUID,
    @Contextual val baseVersionId: UUID,
    @Contextual val currentVersionId: UUID?,
    val canAppend: Boolean = true,
)

/**
 * Creates a version under an existing URL parent. An owner may append to that
 * parent; a non-owner gets a new Modpack2 copied from the parent metadata.
 */
@Serializable
data class Modpack2VersionCreateFromUploadDto(
    @Contextual val uploadId: UUID,
    @Contextual val baseVersionId: UUID? = null,
    val name: String,
    val manifest: Modpack2VersionManifestDto,
    val changelog: String = "",
    val serverMode: Modpack2ServerMode = Modpack2ServerMode.Generated,
    val contents: List<Modpack2ContentDto> = emptyList(),
    val rawFiles: List<Modpack2RawFile> = emptyList(),
    val inheritedServerFiles: List<Modpack2InheritedRawFile> = emptyList(),
    val modpack: Modpack2CreateModpackDto? = null,
)

/** Shorter name for callers that treat the endpoint as an append operation. */
typealias Modpack2AppendVersionDto = Modpack2VersionCreateFromUploadDto

/** Version list/detail projection returned by the Modpack2 API. */
@Serializable
data class Modpack2VersionDetailVo(
    @Contextual val id: UUID,
    @Contextual val modpackId: UUID,
    @Contextual val uploaderId: UUID?,
    val name: String,
    val changelog: String,
    val status: Modpack2VersionStatus,
    val totalSize: Long,
    val serverMode: Modpack2ServerMode,
    @Contextual val baseVersionId: UUID? = null,
    val contents: List<Modpack2Content> = emptyList(),
    val rawFiles: List<Modpack2RawFile> = emptyList(),
    val manifest: Modpack2VersionManifestDto? = null,
)

typealias Modpack2VersionVo = Modpack2VersionDetailVo

/** Platform-resolved client/shared manifest; server-only entries are omitted. */
@Serializable
data class Modpack2ClientManifestVo(
    @Contextual val modpackId: UUID,
    @Contextual val versionId: UUID,
    val mc: Int,
    val loader: Modpack2Loader,
    val contents: List<Modpack2Content> = emptyList(),
)

typealias Modpack2ClientManifest = Modpack2ClientManifestVo

//vos
@Serializable
data class Modpack2BriefVo(
    val id: Uuid,
    val name: String,
    val intro: String,
    val iconUrl: String,
    val playTimeSec: Long = 0,
    val categories: List<ModpackCategory>,
)

/** Owned-pack projection used by authenticated upload/update selectors. */
@Serializable
data class Modpack2OwnedVo(
    @Contextual val id: UUID,
    val name: String,
    val intro: String,
    val mc: Int,
    val loader: Modpack2Loader,
    val iconUrl: String,
    val categories: List<ModpackCategory>,
    @Contextual val currentVersionId: UUID? = null,
    val currentVersionName: String? = null,
    val currentVersionStatus: Modpack2VersionStatus? = null,
)
