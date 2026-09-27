package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayTickRateManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientLevel.class)
public abstract class MixinTickRateManager {
    @Inject(method = "tickTime", at = @At("HEAD"), cancellable = true)
    private void freezeTime(CallbackInfo ci) {
        if (Flashback.isInReplay() && !ReplayTickRateManager.client().runsNormally()) ci.cancel();
    }
    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "tickEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;tickBlockEntities()V"))
    private boolean freezeBlockEntities(ClientLevel level) {
        return !Flashback.isInReplay() || ReplayTickRateManager.client().runsNormally();
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void freezeEntity(Entity entity, CallbackInfo ci) {
        if (Flashback.isInReplay() && ReplayTickRateManager.client().isEntityFrozen(entity)) ci.cancel();
    }
    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void freezePassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (Flashback.isInReplay() && ReplayTickRateManager.client().isEntityFrozen(passenger)) ci.cancel();
    }
}
