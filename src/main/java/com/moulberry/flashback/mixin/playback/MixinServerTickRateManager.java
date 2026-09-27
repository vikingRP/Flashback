package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLevel.class)
public abstract class MixinServerTickRateManager {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void freezeEntity(Entity entity, CallbackInfo ci) {
        if (((ServerLevel)(Object)this).getServer() instanceof ReplayServer server && server.tickRateManager().isEntityFrozen(entity)) ci.cancel();
    }
    @Inject(method = "tickPassenger", at = @At("HEAD"), cancellable = true)
    private void freezePassenger(Entity vehicle, Entity passenger, CallbackInfo ci) {
        if (((ServerLevel)(Object)this).getServer() instanceof ReplayServer server && server.tickRateManager().isEntityFrozen(passenger)) ci.cancel();
    }
}
