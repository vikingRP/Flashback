package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FogRenderer.class, priority = 900)
public class MixinFogRenderer {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void flashback$fogDistance(CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && state.replayVisuals.overrideFog) {
            RenderSystem.setShaderFogStart(state.replayVisuals.overrideFogStart);
            RenderSystem.setShaderFogEnd(state.replayVisuals.overrideFogEnd);
        }
    }

    @Inject(method = "setupColor", at = @At("RETURN"))
    private static void flashback$fogColour(CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && state.replayVisuals.overrideFogColour) {
            var colour = state.replayVisuals.fogColour;
            fogRed = colour[0]; fogGreen = colour[1]; fogBlue = colour[2];
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0);
        }
    }
}
