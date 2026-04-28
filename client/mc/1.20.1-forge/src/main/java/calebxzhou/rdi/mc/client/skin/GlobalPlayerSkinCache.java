package calebxzhou.rdi.mc.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalPlayerSkinCache {
    private static final Map<UUID, ResourceLocation> SKINS = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();

    private GlobalPlayerSkinCache() {
    }

    public static ResourceLocation skin(UUID playerId, String playerName) {
        ResourceLocation skin = SKINS.get(playerId);
        if (skin != null) {
            return skin;
        }
        load(playerId, playerName);
        return DefaultPlayerSkin.getDefaultSkin(playerId);
    }

    private static void load(UUID playerId, String playerName) {
        if (!LOADING.add(playerId)) {
            return;
        }
        Minecraft.getInstance().getSkinManager().registerSkins(
                new GameProfile(playerId, playerName),
                (type, location, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SKINS.put(playerId, location);
                    }
                },
                false
        );
    }
}
