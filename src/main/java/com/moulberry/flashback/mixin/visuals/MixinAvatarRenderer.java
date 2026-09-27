package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public class MixinAvatarRenderer {
    @WrapOperation(method = "setModelProperties", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"))
    private boolean flashback$modelParts(AbstractClientPlayer player, PlayerModelPart part, Operation<Boolean> original) {
        var state = EditorStateManager.getCurrent();
        var hidden = state == null ? null : state.hiddenModelParts.get(player.getUUID());
        return hidden == null ? original.call(player, part) : !hidden.contains(part);
    }
}
