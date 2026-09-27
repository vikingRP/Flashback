package com.moulberry.flashback.mixin.playback;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {
    @Shadow @Final private Minecraft minecraft;
    @Shadow public abstract float getDepthFar();

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void flashback$fov(Camera camera, float partialTick, boolean useSetting, CallbackInfoReturnable<Double> cir) {
        if (!Flashback.isInReplay()) return;
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.isPanoramic()) {
            cir.setReturnValue(90.0);
            return;
        }
        var state = EditorStateManager.getCurrent();
        cir.setReturnValue(state != null && state.replayVisuals.overrideFov
            ? (double) state.replayVisuals.overrideFovAmount : (double) minecraft.options.fov().get());
    }

    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void flashback$projection(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        var job = Flashback.EXPORT_JOB;
        if (job == null) return;
        float depth = getDepthFar();
        if (job.getSettings().projection() == ExportProjection.ORTHOGRAPHIC) {
            float height = (float)(Math.tan(Math.toRadians(fov / 2)) * depth / 4) / job.getSettings().orthographicZoom();
            float width = (float)minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight() * height;
            cir.setReturnValue(new Matrix4f().setOrtho(-width/2, width/2, -height/2, height/2, -depth, depth));
        } else if (job.isPanoramic()) {
            cir.setReturnValue(new Matrix4f().setPerspective((float)Math.PI / 2, 1, 0.05f, depth));
        }
    }

    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"), cancellable = true)
    private void flashback$panoramicBobbing(PoseStack pose, float partialTick, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.isPanoramic()) ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0, remap = false), cancellable = true)
    private void flashback$hideGui(float partialTick, long time, boolean renderLevel, CallbackInfo ci) {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().noGui()) ci.cancel();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void flashback$panoramicHands(PoseStack pose, Camera camera, float partialTick, CallbackInfo ci) {
        if (Flashback.EXPORT_JOB != null && Flashback.EXPORT_JOB.isPanoramic()) ci.cancel();
    }

    @WrapOperation(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType flashback$spectatingMode(MultiPlayerGameMode mode, Operation<GameType> original) {
        var player = Flashback.getSpectatingPlayer();
        if (player != null && minecraft.getConnection() != null) {
            var info = minecraft.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) return info.getGameMode();
        }
        return original.call(mode);
    }

    @Inject(method = "tryTakeScreenshotIfNeeded", at = @At("HEAD"), cancellable = true)
    private void flashback$noReplayWorldIcon(CallbackInfo ci) {
        if (Flashback.isInReplay()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void flashback$blockOutline(CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            var camera = minecraft.getCameraEntity();
            if (camera != null && camera == minecraft.player && camera.isSpectator()) cir.setReturnValue(false);
        }
    }
}
