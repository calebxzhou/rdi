/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package calebxzhou.rdi.earlydisplay.internal;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.IntBuffer;

final class EarlyFramebuffer implements AutoCloseable {
    private final int framebuffer;
    private final int texture;
    private final RenderElement.DisplayContext context;

    EarlyFramebuffer(RenderElement.DisplayContext context) {
        this.context = context;
        framebuffer = glGenFramebuffers();
        texture = glGenTextures();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, context.scaledWidth(), context.scaledHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, (IntBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    void activate() { glBindFramebuffer(GL_FRAMEBUFFER, framebuffer); }
    void deactivate() { glBindFramebuffer(GL_FRAMEBUFFER, 0); }
    int texture() { return texture; }

    @Override
    public void close() {
        glDeleteTextures(texture);
        glDeleteFramebuffers(framebuffer);
    }
}
