package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.visuals.WorldRenderHook;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void flashback$worldOverlay(PoseStack poses, float partialTick, long finishTime, boolean outline,
        Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        WorldRenderHook.renderHook(poses, camera);
    }
}
