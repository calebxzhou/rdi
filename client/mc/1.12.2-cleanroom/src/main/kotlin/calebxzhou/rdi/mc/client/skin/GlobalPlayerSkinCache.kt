package calebxzhou.rdi.mc.client.skin

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.SkinManager.SkinAvailableCallback
import net.minecraft.util.ResourceLocation
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GlobalPlayerSkinCache {
    private val skins = ConcurrentHashMap<UUID, ResourceLocation>()
    private val loading = ConcurrentHashMap.newKeySet<UUID>()

    @JvmStatic
    fun skin(playerId: UUID, playerName: String?): ResourceLocation? {
        skins[playerId]?.let { return it }
        load(playerId, playerName)
        return DefaultPlayerSkin.getDefaultSkin(playerId)
    }

    private fun load(playerId: UUID, playerName: String?) {
        if (!loading.add(playerId)) {
            return
        }
        Minecraft.getMinecraft().skinManager.loadProfileTextures(
            GameProfile(playerId, playerName),
            SkinAvailableCallback { type, location, _ ->
                if (type == MinecraftProfileTexture.Type.SKIN && location != null) {
                    skins[playerId] = location
                }
            },
            true
        )
    }
}
