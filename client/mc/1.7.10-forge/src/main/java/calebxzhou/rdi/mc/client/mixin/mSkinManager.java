package calebxzhou.rdi.mc.client.mixin;

import calebxzhou.rdi.mc.client.RDIClient;
import calebxzhou.rdi.mc.common.RDI;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkinManager.class)
public abstract class mSkinManager {
    @Shadow
    @Final
    private static ExecutorService field_152794_b;

    @Shadow
    public abstract ResourceLocation func_152789_a(
        MinecraftProfileTexture profileTexture,
        MinecraftProfileTexture.Type textureType,
        SkinManager.SkinAvailableCallback skinAvailableCallback);

    @Unique
    private static final Gson RDI$GSON = new Gson();

    @Inject(method = "func_152790_a", at = @At("HEAD"), cancellable = true)
    private void RDI$FetchRegisterSkins(
        final GameProfile profile,
        final SkinManager.SkinAvailableCallback skinAvailableCallback,
        final boolean requireSecure,
        CallbackInfo ci) {
        field_152794_b.submit(
            () -> {
                final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                    RDI$fetchTextures(profile);
                if (textures.isEmpty()) {
                    return;
                }

                Minecraft.getMinecraft()
                    .func_152344_a(
                        () -> {
                            if (textures.containsKey(MinecraftProfileTexture.Type.SKIN)) {
                                func_152789_a(
                                    textures.get(MinecraftProfileTexture.Type.SKIN),
                                    MinecraftProfileTexture.Type.SKIN,
                                    skinAvailableCallback);
                            }
                            if (textures.containsKey(MinecraftProfileTexture.Type.CAPE)) {
                                func_152789_a(
                                    textures.get(MinecraftProfileTexture.Type.CAPE),
                                    MinecraftProfileTexture.Type.CAPE,
                                    skinAvailableCallback);
                            }
                        });
            });
        ci.cancel();
    }

    @Unique
    private static Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> RDI$fetchTextures(
        GameProfile profile) {
        UUID profileId = profile.getId();
        if (profileId == null) {
            profileId = RDI.PLAYER_ID;
        }
        if (profileId == null) {
            return Collections.emptyMap();
        }

        String queryUrl = RDI.getTextureQueryUrl(profileId, "4");
        try {
            String response = RDI$readUrl(queryUrl);
            java.lang.reflect.Type responseType =
                new TypeToken<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>>() {
                }.getType();
            Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> textures =
                RDI$GSON.fromJson(response, responseType);
            return textures == null
                ? Collections.<MinecraftProfileTexture.Type, MinecraftProfileTexture>emptyMap()
                : textures;
        } catch (Exception exception) {
            RDIClient.LOG.warn("获取RDI皮肤失败(profile={}): {}", profile.getName(), exception.getMessage());
            return Collections.emptyMap();
        }
    }

    @Unique
    private static String RDI$readUrl(String queryUrl) throws IOException {
        URLConnection connection = new URL(queryUrl).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        try (InputStream inputStream = connection.getInputStream();
             InputStreamReader inputReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(inputReader)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
