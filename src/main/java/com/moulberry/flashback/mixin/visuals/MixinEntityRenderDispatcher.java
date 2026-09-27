package com.moulberry.flashback.mixin.visuals;

import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.LevelReader;

@Mixin(value = EntityRenderDispatcher.class, priority = 990)
public abstract class MixinEntityRenderDispatcher {
    private static boolean flashback$hidden(Entity entity) {
        var state = EditorStateManager.getCurrent();
        return state != null && (state.isEntityHidden(entity) || state.filteredEntities.contains(entity.getType().builtInRegistryHolder().key().location().toString()));
    }
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void flashback$filter(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (flashback$hidden(entity)) cir.setReturnValue(false);
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void flashback$hide(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (flashback$hidden(entity)) ci.cancel();
    }
    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void flashback$shadow(PoseStack pose, MultiBufferSource buffers, Entity entity, float strength, float partialTick, LevelReader level, float radius, CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !state.replayVisuals.renderBlocks) ci.cancel();
    }
}
