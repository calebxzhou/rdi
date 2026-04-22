package calebxzhou.rdi.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * calebxzhou @ 2026-01-25 22:34
 */
@Serializable
data class ModrinthModpackIndex(
    val formatVersion: Int,
    val game: String,
    val versionId: String,
    val name: String,
    val summary: String? = null,
    val files: List<FileEntry> = emptyList(),
    val dependencies: Map<String, String> = emptyMap()
) {
    @Serializable
    data class FileEntry(
        val path: String,
        val hashes: Hashes,
        val env: Env? = null,
        val downloads: List<String> = emptyList(),
        val fileSize: Long
    )

    @Serializable
    data class Hashes(
        val sha1: String,
        val sha512: String
    )

    @Serializable
    data class Env(
        val client: EnvSide,
        val server: EnvSide?=null
    )

    @Serializable
    enum class EnvSide {
        @SerialName("required")
        required,
        @SerialName("optional")
        optional,
        @SerialName("unsupported")
        unsupported
    }
}

/**
 * calebxzhou @ 2025-12-23 11:36
 */
@Serializable
data class ModrinthDependency(
    @SerialName("version_id") val versionId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("dependency_type") val dependencyType: String? = null
)
@Serializable
data class ModrinthFileInfo(
    val filename: String,
    val url: String,
    val primary: Boolean = false,
    val size: Long? = null,
    val hashes: Map<String, String> = emptyMap(),
    @SerialName("file_type") val fileType: String? = null
)

@Serializable
data class ModrinthProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("client_side") val clientSide: String? = null,
    @SerialName("server_side") val serverSide: String? = null,
    val body: String? = null,
    val status: String? = null,
    @SerialName("requested_status") val requestedStatus: String? = null,
    @SerialName("additional_categories") val additionalCategories: List<String> = emptyList(),
    @SerialName("issues_url") val issuesUrl: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("wiki_url") val wikiUrl: String? = null,
    @SerialName("discord_url") val discordUrl: String? = null,
    @SerialName("donation_urls") val donationUrls: List<ModrinthDonationLink> = emptyList(),
    @SerialName("project_type") val projectType: String,
    val downloads: Long = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val color: Int? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("monetization_status") val monetizationStatus: String? = null,
    val team: String,
    @SerialName("body_url") val bodyUrl: String? = null,
    @SerialName("moderator_message") val moderatorMessage: ModrinthModeratorMessage? = null,
    val published: String,
    val updated: String,
    val approved: String? = null,
    val queued: String? = null,
    val followers: Long = 0,
    val license: ModrinthLicense? = null,
    val versions: List<String> = emptyList(),
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val gallery: List<ModrinthGalleryItem> = emptyList()
)

@Serializable
data class ModrinthLicense(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class ModrinthModeratorMessage(
    val message: String? = null,
    val body: String? = null
)

@Serializable
data class ModrinthDonationLink(
    val id: String,
    val platform: String,
    val url: String
)

@Serializable
data class ModrinthGalleryItem(
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val created: String? = null,
    val featured: Boolean? = null,
    val ordering: Int? = null
)

@Serializable
data class ModrinthVersionInfo(
    val id: String,
    val name: String,
    @SerialName("version_number") val versionNumber: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("author_id") val authorId: String? = null,
    val changelog: String? = null,
    @SerialName("changelog_url") val changelogUrl: String? = null,
    @SerialName("version_type") val versionType: String? = null,
    val status: String? = null,
    @SerialName("requested_status") val requestedStatus: String? = null,
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val featured: Boolean? = null,
    val downloads: Long? = null,
    @SerialName("date_published") val datePublished: String? = null,
    val files: List<ModrinthFileInfo> = emptyList(),
    val dependencies: List<ModrinthDependency> = emptyList()
)
@Serializable
data class ModrinthVersionLookupRequest(
    val hashes: List<String>,
    val algorithm: String,
)


