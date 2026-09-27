package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Recorded time packets, rather than the live server clock, control replay time. */
@Mixin(MinecraftServer.class)
public abstract class MixinServerClockManager {
    @Inject(method="synchronizeTime", at=@At("HEAD"), cancellable=true)
    private void recordedTime(ServerLevel level, CallbackInfo ci) { if ((Object)this instanceof ReplayServer) ci.cancel(); }
}
