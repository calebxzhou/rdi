package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.common.RDI
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.mojang.authlib.GameProfile
import com.mojang.authlib.HttpAuthenticationService
import com.mojang.authlib.SignatureState
import com.mojang.authlib.exceptions.AuthenticationException
import com.mojang.authlib.exceptions.AuthenticationUnavailableException
import com.mojang.authlib.exceptions.MinecraftClientException
import com.mojang.authlib.minecraft.InsecurePublicKeyException
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.authlib.minecraft.MinecraftProfileTextures
import com.mojang.authlib.minecraft.MinecraftSessionService
import com.mojang.authlib.minecraft.client.MinecraftClient
import com.mojang.authlib.properties.Property
import com.mojang.authlib.yggdrasil.ProfileActionType
import com.mojang.authlib.yggdrasil.ProfileResult
import com.mojang.authlib.yggdrasil.response.MinecraftProfilePropertiesResponse
import com.mojang.authlib.yggdrasil.response.MinecraftTexturesPayload
import com.mojang.util.UUIDTypeAdapter
import com.mojang.util.UndashedUuid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.Proxy
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

/**
 * calebxzhou @ 2026-01-10 21:33
 */
class RMcSessionService : MinecraftSessionService {
    private val gson: Gson = GsonBuilder().registerTypeAdapter(UUID::class.java, UUIDTypeAdapter()).create()
    private val client: MinecraftClient = MinecraftClient.unauthenticated(Proxy.NO_PROXY)

    @Throws(AuthenticationException::class)
    override fun joinServer(profileId: UUID?, authenticationToken: String?, serverId: String?) = Unit

    @Throws(AuthenticationUnavailableException::class)
    override fun hasJoinedServer(profileName: String?, serverId: String?, address: InetAddress?): ProfileResult? = null

    override fun getPackedTextures(profile: GameProfile): Property? = profile.properties["textures"].firstOrNull()

    override fun unpackTextures(packedTextures: Property): MinecraftProfileTextures {
        val value = packedTextures.value()
        val result = try {
            val json = String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
            gson.fromJson(json, MinecraftTexturesPayload::class.java)
        } catch (e: JsonParseException) {
            LGR.error("Could not decode textures payload", e)
            null
        } catch (e: IllegalArgumentException) {
            LGR.error("Could not decode textures payload", e)
            null
        } ?: return MinecraftProfileTextures.EMPTY
        val textures = result.textures()
        if (textures.isNullOrEmpty()) return MinecraftProfileTextures.EMPTY

        return MinecraftProfileTextures(
            textures[MinecraftProfileTexture.Type.SKIN],
            textures[MinecraftProfileTexture.Type.CAPE],
            textures[MinecraftProfileTexture.Type.ELYTRA],
            SignatureState.SIGNED
        )
    }

    override fun fetchProfile(profileId: UUID, requireSecure: Boolean): ProfileResult? {
        try {
            val url = HttpAuthenticationService.constantURL(
                RDI.IHQ_URL + "/session/minecraft/profile/" + UndashedUuid.toString(profileId)
            )
            val response = client.get<MinecraftProfilePropertiesResponse>(url, MinecraftProfilePropertiesResponse::class.java)
            if (response == null) {
                LGR.debug("NO PROFILE {} ", profileId)
                return null
            }
            val profile = response.toProfile()
            return ProfileResult(profile, mutableSetOf<ProfileActionType>())
        } catch (e: MinecraftClientException) {
            LGR.warn("Can't get profile for {}", profileId, e)
            return null
        } catch (e: IllegalArgumentException) {
            LGR.warn("Can't get profile for {}", profileId, e)
            return null
        }
    }

    @Throws(InsecurePublicKeyException::class)
    override fun getSecurePropertyValue(property: Property): String? = property.value()

    companion object {
        private val LGR: Logger = LoggerFactory.getLogger(RMcSessionService::class.java)
    }
}
