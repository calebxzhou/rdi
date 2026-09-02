package calebxzau.rdi.common.model

import kotlinx.serialization.Serializable

/** The layer that owns a Host2 content entry. */
@Serializable
enum class ContentOrigin {
    Pack,
    Extra,
}

/** Stable identity used by Host2 content mutation requests. */
@Serializable
data class ContentKey(
    val origin: ContentOrigin,
    val platform: ContentPlatform,
    val projectId: String,
)

/** Complete content snapshot entry returned by Host2 management APIs. */
@Serializable
data class ContentVo(
    val origin: ContentOrigin,
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
    val enabled: Boolean = true,
)

/** Fields accepted when an Extra Content is added to a Host2. */
@Serializable
data class ContentInput(
    val platform: ContentPlatform,
    val type: ContentType,
    val projectId: String,
    val fileId: String,
    val side: ContentSide,
    val targetPath: String? = null,
)

/** Effective client-side entry; enabled is implicit because disabled entries are omitted. */
@Serializable
data class ClientContentVo(
    val origin: ContentOrigin,
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
data class Content(
    val platform: ContentPlatform,
    val type: ContentType,
    val projectId: String,
    val fileId: String,
    val slug: String,
    val hash: String,
    // For mrpack, null means the platform default path.
    val path: String? = null,
    val side: ContentSide,
    val required: Boolean = true,
    val size: Long
)

@Serializable
enum class ContentPlatform {
    CurseForge,
    Modrinth,
    GitHub,
}

@Serializable
enum class ContentSide {
    Client,
    Server,
    Both,
}

@Serializable
enum class ContentType {
    Mod,
    ShaderPack,
    ResPack,
    DataPack,
    Other
}
