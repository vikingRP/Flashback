package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.MouseHandledBy;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.editor.ui.ReplayUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Gui.class)
public abstract class MixinHud {

    @Shadow
    @Nullable
    protected abstract Player getCameraPlayer();

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"), require = 0)
    public float renderCrosshair_getAttackStrengthScale(LocalPlayer instance, float partialTick, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.getAttackStrengthScale(partialTick);
            }
        }
        return original.call(instance, partialTick);
    }

    @WrapOperation(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getCurrentItemAttackStrengthDelay()F"), require = 0)
    public float renderCrosshair_getCurrentItemAttackStrengthDelay(LocalPlayer instance, Operation<Float> original) {
        if (Flashback.isInReplay()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                return player.getCurrentItemAttackStrengthDelay();
            }
        }
        return original.call(instance);
    }

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    public void renderVignette(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        // The vignette ruins the transparency when trying to export with alpha
        // The vignette is also probably unwanted in general when trying to record, so lets just get rid of it
        if (Flashback.isInReplay()) {
            ci.cancel();
        }
    }

    @Inject(method = "canRenderCrosshairForSpectator", at = @At("HEAD"), cancellable = true, require = 0)
    public void canRenderCrosshairForSpectator(HitResult hitResult, CallbackInfoReturnable<Boolean> cir) {
        if (Flashback.isInReplay()) {
            if (!Flashback.isExporting() && ReplayUI.isActive() && ReplayUI.imguiWindower.getMouseHandledBy() == MouseHandledBy.GAME) {
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(Flashback.getSpectatingPlayer() != null);
        }
    }

}
