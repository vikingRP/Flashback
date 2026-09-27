package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class MixinPlayerRenderer {
    @WrapOperation(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Scoreboard;getDisplayObjective(I)Lnet/minecraft/world/scores/Objective;"))
    private Objective flashback$belowName(Scoreboard scoreboard, int slot, Operation<Objective> original, @Local(argsOnly = true) AbstractClientPlayer player) {
        var state = EditorStateManager.getCurrent();
        return state != null && state.hideBelowName.contains(player.getUUID()) ? null : original.call(scoreboard, slot);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void flashback$hidePlayerName(AbstractClientPlayer player, net.minecraft.network.chat.Component name,
            com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        var state = EditorStateManager.getCurrent();
        if (state != null && (!state.replayVisuals.renderNametags || state.hideNametags.contains(player.getUUID()) || state.isEntityHidden(player))) ci.cancel();
    }
}
