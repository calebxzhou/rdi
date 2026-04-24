package calebxzhou.rdi.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthSearchHit> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 0,
    @SerialName("total_hits") val totalHits: Int = 0
)

@Serializable
data class ModrinthSearchHit(
    val slug: String,
    val title: String,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    @SerialName("client_side") val clientSide: String? = null,
    @SerialName("server_side") val serverSide: String? = null,
    @SerialName("project_type") val projectType: String,
    val downloads: Long = 0,
    @SerialName("icon_url") val iconUrl: String? = null,
    val color: Int? = null,
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("monetization_status") val monetizationStatus: String? = null,
    @SerialName("project_id") val projectId: String,
    val author: String,
    @SerialName("display_categories") val displayCategories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val follows: Long = 0,
    @SerialName("date_created") val dateCreated: String,
    @SerialName("date_modified") val dateModified: String,
    @SerialName("latest_version") val latestVersion: String? = null,
    val license: String,
    val gallery: List<String> = emptyList(),
    @SerialName("featured_gallery") val featuredGallery: String? = null
)

enum class ModrinthSearchIndex(val apiValue: String) {
    RELEVANCE("relevance"),
    DOWNLOADS("downloads"),
    FOLLOWS("follows"),
    NEWEST("newest"),
    UPDATED("updated")
}
