package calebxzhou.rdi.mc.client

import calebxzhou.rdi.mc.client.mixin.ASkinManager
import calebxzhou.rdi.mc.common.RDI
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mojang.authlib.SignatureState
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.authlib.minecraft.MinecraftProfileTextures
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import java.lang.reflect.Type
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object GlobalPlayerSkinCache {
    private val GSON = Gson()
    private val TEXTURE_MAP_TYPE: Type =
        object : TypeToken<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>>() {}.type
    private val SKINS: MutableMap<UUID, PlayerSkin> = ConcurrentHashMap()
    private val LOADING: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun skin(playerId: UUID): PlayerSkin {
        SKINS[playerId]?.let { return it }
        load(playerId)
        return DefaultPlayerSkin.get(playerId)
    }

    private fun load(playerId: UUID) {
        if (!LOADING.add(playerId)) {
            return
        }
        CompletableFuture.supplyAsync(
            { fetchTextures(playerId) },
            Util.backgroundExecutor()
        )
            .thenCompose { textures ->
                (Minecraft.getInstance().skinManager as ASkinManager).invokeRegisterTextures(playerId, textures)
            }
            .thenAccept { skin -> SKINS[playerId] = skin }
            .exceptionally {
                LOADING.remove(playerId)
                null
            }
    }

    private fun fetchTextures(playerId: UUID): MinecraftProfileTextures {
        try {
            URI.create(RDI.getTextureQueryUrl(playerId, "4")).toURL().openStream().use { input ->
                val json = String(input.readAllBytes(), StandardCharsets.UTF_8)
                val textures = GSON.fromJson<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>>(
                    json,
                    TEXTURE_MAP_TYPE
                ) ?: return MinecraftProfileTextures.EMPTY
                return MinecraftProfileTextures(
                    textures[MinecraftProfileTexture.Type.SKIN],
                    textures[MinecraftProfileTexture.Type.CAPE],
                    textures[MinecraftProfileTexture.Type.ELYTRA],
                    SignatureState.SIGNED
                )
            }
        } catch (e: Exception) {
            throw IllegalStateException("加载RDI玩家皮肤失败:" + playerId, e)
        }
    }
}
