package calebxzhou.rdi.earlydisplay.internal;

import static org.lwjgl.opengl.GL32C.*;

final class RdiCompositor implements AutoCloseable {
    private final ElementShader shader;
    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder(256);

    RdiCompositor(ElementShader shader) {
        this.shader = shader;
    }

    void draw(int windowWidth, int windowHeight, int videoTexture, int progressTexture, RenderElement.DisplayContext context) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, windowWidth, windowHeight);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);
        shader.activate();
        shader.updateScreenSizeUniform(windowWidth, windowHeight);
        shader.updateTextureUniform(0);
        shader.updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        if (videoTexture != 0) drawVideo(windowWidth, windowHeight, videoTexture);
        drawProgress(windowWidth, windowHeight, progressTexture, context);
        glBindTexture(GL_TEXTURE_2D, 0);
        shader.clear();
    }

    private void drawVideo(int width, int height, int texture) {
        float windowAspect = (float) width / height;
        float videoAspect = (float) RdiVideoTexture.WIDTH / RdiVideoTexture.HEIGHT;
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
        glBindTexture(GL_TEXTURE_2D, texture);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        QuadHelper.loadQuad(buffer, 0, width, 0, height, u0, u1, v0, v1, 0xFFFFFFFF);
        buffer.draw();
    }

    private void drawProgress(int width, int height, int texture, RenderElement.DisplayContext context) {
        float scale = Math.min((float) width / context.width(), (float) height / context.height());
        float drawWidth = context.width() * scale;
        float drawHeight = context.height() * scale;
        float left = (width - drawWidth) / 2f;
        float bottom = (height - drawHeight) / 2f;
        glBindTexture(GL_TEXTURE_2D, texture);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        QuadHelper.loadQuad(buffer, left, left + drawWidth, bottom, bottom + drawHeight, 0, 1, 1, 0, 0xFFFFFFFF);
        buffer.draw();
    }

    @Override
    public void close() {
        buffer.close();
    }
}
