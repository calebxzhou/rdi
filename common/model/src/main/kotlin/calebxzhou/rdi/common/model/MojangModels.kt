package calebxzhou.rdi.common.model

import kotlinx.serialization.Serializable

/**
 * calebxzhou @ 2025-12-24 23:12
 */

@Serializable
data class MojangProfileResponse(
    val id: String,
    val name: String,
    val properties: List<MojangProperty> = emptyList(),
)

@Serializable
data class MojangProperty(
    val name: String,
    val value: String,
    val signature: String? = null,
)

@Serializable
data class MojangTexturesPayload(
    val timestamp: Long? = null,
    val profileId: String? = null,
    val profileName: String? = null,
    val textures: Map<String, MojangTexture> = emptyMap(),
)

@Serializable
data class MojangTexture(
    val url: String,
    val metadata: MojangTextureMetadata? = null,
)

@Serializable
data class MojangTextureMetadata(
    val model: String? = null,
)

@Serializable
data class MojangSkin(
    val id: String,
    val state: String,
    val url: String,
    val variant: String,
)

@Serializable
data class MojangCape(
    val id: String,
    val state: String,
    val url: String,
    val alias: String,
)

@Serializable
data class MojangPlayerProfile(
    val id: String,
    val name: String,
    val skins: List<MojangSkin> = emptyList(),
    val capes: List<MojangCape> = emptyList(),
)