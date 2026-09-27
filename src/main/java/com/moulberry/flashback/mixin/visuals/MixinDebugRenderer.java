package com.moulberry.flashback.mixin.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.visuals.FlashbackDebugRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class MixinDebugRenderer {
    @Unique private final FlashbackDebugRenderer flashback$renderer = new FlashbackDebugRenderer(Minecraft.getInstance());
    @Inject(method = "render", at = @At("RETURN"))
    private void flashback$render(PoseStack poses, MultiBufferSource.BufferSource buffers, double x, double y, double z, CallbackInfo ci) {
        flashback$renderer.render(poses, buffers, x, y, z);
    }
}
