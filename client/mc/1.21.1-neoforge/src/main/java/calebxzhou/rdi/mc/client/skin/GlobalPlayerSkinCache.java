package calebxzhou.rdi.mc.client.skin;

import calebxzhou.rdi.mc.client.mixin.ASkinManager;
import calebxzhou.rdi.mc.common.RDI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.SignatureState;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTextures;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalPlayerSkinCache {
    private static final Gson GSON = new Gson();
    private static final Type TEXTURE_MAP_TYPE = new TypeToken<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>>() {
    }.getType();
    private static final Map<UUID, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();

    private GlobalPlayerSkinCache() {
    }

    public static PlayerSkin skin(UUID playerId) {
        PlayerSkin skin = SKINS.get(playerId);
        if (skin != null) {
            return skin;
        }
        load(playerId);
        return DefaultPlayerSkin.get(playerId);
    }

    private static void load(UUID playerId) {
        if (!LOADING.add(playerId)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> fetchTextures(playerId), Util.backgroundExecutor())
                .thenCompose(textures -> ((ASkinManager) Minecraft.getInstance().getSkinManager()).invokeRegisterTextures(playerId, textures))
                .thenAccept(skin -> SKINS.put(playerId, skin))
                .exceptionally(throwable -> {
                    LOADING.remove(playerId);
                    return null;
                });
    }

    private static MinecraftProfileTextures fetchTextures(UUID playerId) {
        try (InputStream input = URI.create(RDI.getTextureQueryUrl(playerId, "4")).toURL().openStream()) {
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures = GSON.fromJson(json, TEXTURE_MAP_TYPE);
            if (textures == null) {
                return MinecraftProfileTextures.EMPTY;
            }
            return new MinecraftProfileTextures(
                    textures.get(MinecraftProfileTexture.Type.SKIN),
                    textures.get(MinecraftProfileTexture.Type.CAPE),
                    textures.get(MinecraftProfileTexture.Type.ELYTRA),
                    SignatureState.SIGNED
            );
        } catch (Exception e) {
            throw new IllegalStateException("加载RDI玩家皮肤失败:" + playerId, e);
        }
    }
}
