package com.moulberry.flashback.editor.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.utils.FramebufferUtils;
import imgui.moulberry90.ImDrawData;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Minecraft 1.20.1's OpenGL implementation of the editor compositor. */
public final class CustomImGuiImplB3D implements CustomImGuiRenderer {
    private final CustomImGuiImplGl3 renderer = new CustomImGuiImplGl3();
    private RenderTarget target;

    public void init() { renderer.init("#version 150"); }
    public void updateFontsTexture() { renderer.updateFontsTexture(); }
    public long getTextureId(int textureId) { return Integer.toUnsignedLong(textureId); }

    private void setFilter(long id, int filter) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, (int) id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
    }
    public void setSampleLinear(long id) { setFilter(id, GL11.GL_LINEAR); }
    public void setSampleNearest(long id) { setFilter(id, GL11.GL_NEAREST); }

    public RenderTarget renderDrawData(ImDrawData data) {
        renderer.newFrame();
        int width = (int) (data.getDisplaySizeX() * data.getFramebufferScaleX());
        int height = (int) (data.getDisplaySizeY() * data.getFramebufferScaleY());
        if (width <= 0 || height <= 0) return null;
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            target = FramebufferUtils.resizeOrCreateFramebuffer(target, width, height, false);
            FramebufferUtils.clear(target, FramebufferUtils.TRANSPARENT_CLEAR_COLOUR);
            target.bindWrite(true);
            renderer.renderDrawData(data);
            return target;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
