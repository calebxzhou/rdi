package calebxzhou.rdi.master.ygg

import calebxzhou.rdi.common.util.encodeBase64
import calebxzhou.rdi.common.json
import calebxzhou.rdi.common.model.RAccount
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class GameProfile(
    @Contextual val id: String,
    val name: String,
    val properties: List<Property>
) {
    companion object{
        fun getDefault(id: String): GameProfile{
            val default = RAccount.DEFAULT
            return GameProfile(
                id = id,
                name = "RDI${id.take(5)}",
                properties = listOf(
                    Property(
                        name = "textures",
                        value =
                                MinecraftTexturesPayload(
                                    timestamp = System.currentTimeMillis(),
                                    profileId = id,
                                    profileName = "RDI${id.take(5)}",
                                    isPublic = true,
                                    textures = mapOf(
                                        MinecraftProfileTexture.Type.SKIN to MinecraftProfileTexture(
                                            url = default.cloth.skin
                                        )
                                    )
                                ).json.encodeBase64

                        )
                    )

            )
        }
    }
}
@Serializable
data class Property(
    val name: String,
    val value: String
) {
}
@Serializable
data class MinecraftTexturesPayload(
    val timestamp: Long,

    val profileId: String,
    val profileName: String,
    val isPublic: Boolean,
    val textures: Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>
)
@Serializable
data class MinecraftProfileTexture(
    val url: String,
    val metadata: Map<String, String> = emptyMap()
){
    enum class Type {
        SKIN,
        CAPE,
        ELYTRA
    }
}
@Serializable
data class MinecraftProfileTextures(
    val skin: MinecraftProfileTexture?,
    val cape: MinecraftProfileTexture?,
    val elytra: MinecraftProfileTexture?=null,
    val signatureState: SignatureState = SignatureState.SIGNED
) {
    companion object {
        val EMPTY: MinecraftProfileTextures = MinecraftProfileTextures(null, null, null, SignatureState.SIGNED)
    }
}
enum class SignatureState {
    UNSIGNED,
    INVALID,
    SIGNED,
}
