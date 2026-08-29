/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package calebxzhou.rdi.mc.client.loading;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import net.minecraftforge.client.loading.NoVizFallback;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30C;

/**
 * Forge 1.20.1's late loading overlay.  The early window owns the texture
 * and decoder; this overlay keeps calling back into that session after GLFW
 * has been handed to Minecraft, so the startup video does not stop at the
 * handoff.
 */
public final class RdiLoadingOverlay extends LoadingOverlay {
    private static final long FADE_MILLIS = 2000L;
    private static final int SUPPORTED_PROTOCOL = 1;

    private final Minecraft minecraft;
    private final ReloadInstance reload;
    private final Consumer<Optional<Throwable>> onFinish;
    private final SessionAccess session;
    private final ProgressMeter progressMeter;
    private float currentProgress;
    private long fadeOutStart = -1L;

    private RdiLoadingOverlay(
            Minecraft minecraft,
            ReloadInstance reload,
            Consumer<Optional<Throwable>> onFinish,
            SessionAccess session) {
        super(minecraft, reload, onFinish, false);
        this.minecraft = minecraft;
        this.reload = reload;
        this.onFinish = onFinish;
        this.session = session;
        this.progressMeter = StartupNotificationManager.prependProgressBar("Minecraft Progress", 1000);
    }

    public static Supplier<LoadingOverlay> newInstance(
            Supplier<Minecraft> minecraft,
            Supplier<ReloadInstance> reload,
            Consumer<Optional<Throwable>> onFinish,
            Object session) {
        final SessionAccess access;
        try {
            access = SessionAccess.open(session);
            if (access.protocolVersion() != SUPPORTED_PROTOCOL) {
                access.close();
                return NoVizFallback.loadingOverlay(minecraft, reload, onFinish, false);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            closeQuietly(session);
            return NoVizFallback.loadingOverlay(minecraft, reload, onFinish, false);
        }
        return () -> new RdiLoadingOverlay(minecraft.get(), reload.get(), onFinish, access);
    }

    private static void closeQuietly(Object session) {
        try {
            session.getClass().getMethod("close").invoke(session);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float fadeSeconds = fadeOutStart == -1L ? -1f : (now - fadeOutStart) / 1000f;
        float alpha = fadeOutStart == -1L
                ? 1f
                : 1f - Mth.clamp((now - fadeOutStart) / (float) FADE_MILLIS, 0f, 1f);
        currentProgress = Mth.clamp(currentProgress * 0.95f + reload.getActualProgress() * 0.05f, 0f, 1f);
        progressMeter.setAbsolute(Mth.ceil(currentProgress * 1000));

        if (fadeSeconds >= 0f && minecraft.screen != null) {
            minecraft.screen.render(graphics, 0, 0, partialTick);
        } else {
            GlStateManager._clearColor(0f, 0f, 0f, 1f);
            GlStateManager._clear(GlConst.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        }

        // The session renders the latest decoder frame into its framebuffer.
        session.renderProgress(0xFF);
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        GL30C.glViewport(0, 0, width, height);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlConst.GL_SRC_ALPHA, GlConst.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.getModelViewMatrix().identity();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0f, width, 0f, height, 0.1f, -0.1f),
                VertexSorting.ORTHOGRAPHIC_Z);
        GlStateManager.glActiveTexture(GlConst.GL_TEXTURE0);

        if (session.hasVideoFrame()) {
            drawVideo(width, height, alpha);
        }
        drawProgress(width, height, alpha);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (fadeSeconds >= 2f) {
            progressMeter.complete();
            minecraft.setOverlay(null);
            session.close();
        }

        if (fadeOutStart == -1L && reload.isDone()) {
            fadeOutStart = now;
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                onFinish.accept(Optional.of(throwable));
            }
            if (minecraft.screen != null) {
                minecraft.screen.init(
                        minecraft,
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight());
            }
        }
    }

    private void drawVideo(int width, int height, float alpha) {
        float windowAspect = (float) width / height;
        float videoAspect = (float) session.videoWidth() / session.videoHeight();
        float u0 = 0f;
        float u1 = 1f;
        float v0 = 1f;
        float v1 = 0f;
        if (windowAspect > videoAspect) {
            float visible = videoAspect / windowAspect;
            v0 = (1f + visible) / 2f;
            v1 = (1f - visible) / 2f;
        } else {
            float visible = windowAspect / videoAspect;
            u0 = (1f - visible) / 2f;
            u1 = (1f + visible) / 2f;
        }
        drawTexture(session.videoTextureId(), 0, width, 0, height, u0, u1, v0, v1, alpha, GlConst.GL_LINEAR);
    }

    private void drawProgress(int width, int height, float alpha) {
        float scale = Math.min(
                (float) width / session.logicalWidth(),
                (float) height / session.logicalHeight());
        float drawWidth = session.logicalWidth() * scale;
        float drawHeight = session.logicalHeight() * scale;
        float left = (width - drawWidth) / 2f;
        float bottom = (height - drawHeight) / 2f;
        drawTexture(
                session.progressTextureId(),
                left,
                left + drawWidth,
                bottom,
                bottom + drawHeight,
                0f,
                1f,
                1f,
                0f,
                alpha,
                GlConst.GL_NEAREST);
    }

    private static void drawTexture(
            int texture,
            float x0,
            float x1,
            float y0,
            float y1,
            float u0,
            float u1,
            float v0,
            float v1,
            float alpha,
            int filter) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        GL30C.glTexParameteri(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MIN_FILTER, filter);
        GL30C.glTexParameteri(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MAG_FILTER, filter);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.vertex(x0, y0, 0f).uv(u0, v0).color(1f, 1f, 1f, alpha).endVertex();
        buffer.vertex(x1, y0, 0f).uv(u1, v0).color(1f, 1f, 1f, alpha).endVertex();
        buffer.vertex(x1, y1, 0f).uv(u1, v1).color(1f, 1f, 1f, alpha).endVertex();
        buffer.vertex(x0, y1, 0f).uv(u0, v1).color(1f, 1f, 1f, alpha).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private record SessionAccess(
            Object target,
            Method protocolVersionMethod,
            Method progressTextureIdMethod,
            Method videoTextureIdMethod,
            Method videoWidthMethod,
            Method videoHeightMethod,
            Method logicalWidthMethod,
            Method logicalHeightMethod,
            Method hasVideoFrameMethod,
            Method renderProgressMethod,
            Method closeMethod) {
        static SessionAccess open(Object target) throws ReflectiveOperationException {
            Class<?> type = target.getClass();
            return new SessionAccess(
                    target,
                    type.getMethod("protocolVersion"),
                    type.getMethod("progressTextureId"),
                    type.getMethod("videoTextureId"),
                    type.getMethod("videoWidth"),
                    type.getMethod("videoHeight"),
                    type.getMethod("logicalWidth"),
                    type.getMethod("logicalHeight"),
                    type.getMethod("hasVideoFrame"),
                    type.getMethod("renderProgress", int.class),
                    type.getMethod("close"));
        }

        int protocolVersion() { return invokeInt(protocolVersionMethod); }
        int progressTextureId() { return invokeInt(progressTextureIdMethod); }
        int videoTextureId() { return invokeInt(videoTextureIdMethod); }
        int videoWidth() { return invokeInt(videoWidthMethod); }
        int videoHeight() { return invokeInt(videoHeightMethod); }
        int logicalWidth() { return invokeInt(logicalWidthMethod); }
        int logicalHeight() { return invokeInt(logicalHeightMethod); }
        boolean hasVideoFrame() { return (boolean) invoke(hasVideoFrameMethod); }
        void renderProgress(int alpha) { invoke(renderProgressMethod, alpha); }
        void close() { invoke(closeMethod); }

        private int invokeInt(Method method) {
            return (int) invoke(method);
        }

        private Object invoke(Method method, Object... arguments) {
            try {
                return method.invoke(target, arguments);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("RDI early display bridge failed", exception);
            }
        }
    }
}
