package calebxzhou.rdi.mc.client.skin

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.SkinManager
import net.minecraft.resources.ResourceLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * calebxzhou @ 2026-06-11 13:48
 */

object GlobalPlayerSkinCache {
    private val SKINS = ConcurrentHashMap<UUID, ResourceLocation>()
    private val LOADING: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun skin(playerId: UUID, playerName: String?): ResourceLocation {
        SKINS[playerId]?.let { return it }
        load(playerId, playerName)
        return DefaultPlayerSkin.getDefaultSkin(playerId)
    }

    private fun load(playerId: UUID, playerName: String?) {
        if (!LOADING.add(playerId)) {
            return
        }
        Minecraft.getInstance().skinManager.registerSkins(
            GameProfile(playerId, playerName),
            SkinManager.SkinTextureCallback { type, location, _ ->
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    SKINS[playerId] = location
                }
            },
            false
        )
    }
}
