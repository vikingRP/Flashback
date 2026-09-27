package com.moulberry.flashback.mixin.visuals;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public class MixinLightmapRenderStateExtractor {
    private static boolean flashback$nightVision() {
        var state = EditorStateManager.getCurrent();
        return state != null && state.replayVisuals.overrideNightVision;
    }
    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/world/effect/MobEffect;)Z"))
    private boolean flashback$hasNightVision(LocalPlayer player, MobEffect effect, Operation<Boolean> original) {
        return effect == MobEffects.NIGHT_VISION && flashback$nightVision() || original.call(player, effect);
    }
    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F"))
    private float flashback$nightVisionScale(LivingEntity entity, float partialTick, Operation<Float> original) {
        return flashback$nightVision() ? 1 : original.call(entity, partialTick);
    }
}
