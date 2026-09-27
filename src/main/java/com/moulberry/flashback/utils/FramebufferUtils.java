package com.moulberry.flashback.utils;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;

public final class FramebufferUtils {
    public static final Vector4f TRANSPARENT_CLEAR_COLOUR = new Vector4f(0);
    public static final Vector4f BLACK_CLEAR_COLOUR = new Vector4f(0, 0, 0, 1);

    public static void clear(RenderTarget target, Vector4f colour) {
        target.setClearColor(colour.x, colour.y, colour.z, colour.w);
        target.clear(Minecraft.ON_OSX);
    }

    public static RenderTarget resizeOrCreateFramebuffer(RenderTarget target, int width, int height, boolean depth) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (target == null) return new TextureTarget(width, height, depth, Minecraft.ON_OSX);
        if (target.width != width || target.height != height) target.resize(width, height, Minecraft.ON_OSX);
        return target;
    }

    public static void blitTo(int texture, RenderTarget target, float x1, float y1, float x2, float y2) {
        ShaderManager.blit(texture, target.frameBufferId, target.width, target.height, x1, y1, x2, y2, true, false, false, 0, 0);
    }
}
