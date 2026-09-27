package com.moulberry.flashback.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.utils.FramebufferUtils;
import com.moulberry.flashback.visuals.ShaderManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class MixinLevelRenderer {
    @Unique private RenderTarget flashback$roundAlpha;

    @WrapOperation(method = "compileChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher$RenderChunk;rebuildChunkAsync(Lnet/minecraft/client/renderer/chunk/ChunkRenderDispatcher;Lnet/minecraft/client/renderer/chunk/RenderRegionCache;)V"))
    private void flashback$finishChunk(net.minecraft.client.renderer.chunk.ChunkRenderDispatcher.RenderChunk chunk,
            net.minecraft.client.renderer.chunk.ChunkRenderDispatcher dispatcher,
            net.minecraft.client.renderer.chunk.RenderRegionCache cache, Operation<Void> original) {
        if (com.moulberry.flashback.exporting.PerfectFrames.isEnabled()) {
            dispatcher.rebuildChunkSync(chunk, cache);
            dispatcher.uploadAllPendingUploads();
        } else original.call(chunk, dispatcher, cache);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void flashback$projection(PoseStack poses, float partialTick, long time, boolean outline, Camera camera,
            GameRenderer renderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        ReplayUI.lastProjectionMatrix = new Matrix4f(projection);
        // 1.20.1 cameras look down +Z using rotationYXZ(-yaw, pitch, 0); ReplayUI expects the newer
        // -Z convention, rotationYXZ(PI - yaw, -pitch, 0), which is the same rotation followed by a half-turn around Y
        ReplayUI.lastViewQuaternion = new Quaternionf(camera.rotation()).rotateY((float) Math.PI);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void flashback$depth(CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.isRunning()) Flashback.EXPORT_JOB.tryDepthDownload();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void flashback$skyClear(CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !state.replayVisuals.renderSky) {
            if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
                RenderSystem.clearColor(0f, 0f, 0f, 0f);
            } else {
                float[] colour = state.replayVisuals.skyColour;
                RenderSystem.clearColor(colour[0], colour[1], colour[2], 1f);
            }
        }
    }

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void flashback$blocks(RenderType layer, PoseStack poses, double x, double y, double z, Matrix4f projection, CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !state.replayVisuals.renderBlocks) { ci.cancel(); return; }
        if (layer == RenderType.translucent() && Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().transparent()) {
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            flashback$roundAlpha = FramebufferUtils.resizeOrCreateFramebuffer(flashback$roundAlpha, main.width, main.height, false);
            ShaderManager.blit(main.getColorTextureId(), flashback$roundAlpha.frameBufferId, main.width, main.height,
                0, 0, 1, 1, false, false, false, 0, 0);
            ShaderManager.blit(flashback$roundAlpha.getColorTextureId(), main.frameBufferId, main.width, main.height,
                0, 0, 1, 1, false, false, true, 0, 0);
        }
    }

    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private boolean flashback$blockEntities(BlockEntityRenderDispatcher dispatcher, BlockEntity block, float partialTick, PoseStack poses, MultiBufferSource buffers) {
        var state = EditorStateManager.getCurrent();
        return state == null || state.replayVisuals.renderBlocks;
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;destructionProgress:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;"))
    private it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.SortedSet<net.minecraft.server.level.BlockDestructionProgress>> flashback$blockDamage(
            it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.SortedSet<net.minecraft.server.level.BlockDestructionProgress>> original) {
        var state = EditorStateManager.getCurrent();
        return state != null && !state.replayVisuals.renderBlocks ? it.unimi.dsi.fastutil.longs.Long2ObjectMaps.emptyMap() : original;
    }

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void flashback$blockOutline(CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !state.replayVisuals.renderBlocks) ci.cancel();
    }

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void flashback$entities(Entity entity, double x, double y, double z, float partialTick, PoseStack poses, MultiBufferSource buffers, CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !(entity instanceof Player ? state.replayVisuals.renderPlayers : state.replayVisuals.renderEntities)) ci.cancel();
    }

    @Inject(method = {"renderSky", "renderClouds"}, at = @At("HEAD"), cancellable = true)
    private void flashback$sky(CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && !state.replayVisuals.renderSky) ci.cancel();
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.getSettings().projection() == ExportProjection.ORTHOGRAPHIC) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void flashback$close(CallbackInfo ci) {
        if (flashback$roundAlpha != null) { flashback$roundAlpha.destroyBuffers(); flashback$roundAlpha = null; }
    }
}
