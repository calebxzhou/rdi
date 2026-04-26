package calebxzhou.rdi.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthV3Project(
    val id: String,
    val slug: String,
    @SerialName("project_types") val projectTypes: List<String> = emptyList(),
    val games: List<String> = emptyList(),
    @SerialName("team_id") val teamId: String? = null,
    val name: String,
    val summary: String? = null,
    val description: String? = null,
    val published: String? = null,
    val updated: String? = null,
    val status: String? = null,
    val downloads: Long = 0,
    val followers: Long = 0,
    val categories: List<String> = emptyList(),
    @SerialName("additional_categories") val additionalCategories: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    @SerialName("icon_url") val iconUrl: String? = null,
    @SerialName("link_urls") val linkUrls: Map<String, ModrinthV3ProjectLink> = emptyMap(),
    val gallery: List<ModrinthV3GalleryItem> = emptyList(),
    val color: Int? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList()
)

@Serializable
data class ModrinthV3ProjectLink(
    val platform: String,
    val donation: Boolean = false,
    val url: String
)

@Serializable
data class ModrinthV3GalleryItem(
    val url: String,
    @SerialName("raw_url") val rawUrl: String? = null,
    val featured: Boolean = false,
    val name: String? = null,
    val description: String? = null,
    val created: String? = null,
    val ordering: Int = 0
)

@Serializable
data class ModrinthV3Version(
    val id: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("author_id") val authorId: String? = null,
    val featured: Boolean = false,
    val name: String,
    @SerialName("version_number") val versionNumber: String,
    @SerialName("project_types") val projectTypes: List<String> = emptyList(),
    val games: List<String> = emptyList(),
    val changelog: String? = null,
    @SerialName("date_published") val datePublished: String,
    val downloads: Long = 0,
    @SerialName("version_type") val versionType: String? = null,
    val status: String? = null,
    @SerialName("requested_status") val requestedStatus: String? = null,
    val files: List<ModrinthV3VersionFile> = emptyList(),
    val dependencies: List<ModrinthDependency> = emptyList(),
    val loaders: List<String> = emptyList(),
    val ordering: Int? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList()
)

@Serializable
data class ModrinthV3VersionFile(
    val id: String? = null,
    val hashes: ModrinthV3VersionHashes = ModrinthV3VersionHashes(),
    val url: String,
    val filename: String,
    val primary: Boolean = false,
    val size: Long? = null,
    @SerialName("file_type") val fileType: String? = null
)

@Serializable
data class ModrinthV3VersionHashes(
    val sha1: String? = null,
    val sha512: String? = null
)
