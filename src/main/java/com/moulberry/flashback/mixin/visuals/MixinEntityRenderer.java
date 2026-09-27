package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    private static boolean flashback$hideName(Entity entity) {
        var state = EditorStateManager.getCurrent();
        return state != null && (!state.replayVisuals.renderNametags || state.hideNametags.contains(entity.getUUID()) || state.isEntityHidden(entity));
    }
    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void flashback$name(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (flashback$hideName(entity)) cir.setReturnValue(false);
    }
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void flashback$nameRender(Entity entity, Component text, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (flashback$hideName(entity)) ci.cancel();
    }
}
