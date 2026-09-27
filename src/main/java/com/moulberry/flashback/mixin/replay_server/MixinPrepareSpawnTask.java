package com.moulberry.flashback.mixin.replay_server;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Player construction moved from configuration tasks to PlayerList in 1.20.1. */
@Mixin(PlayerList.class)
public abstract class MixinPrepareSpawnTask {
    @Inject(method="getPlayerForLogin", at=@At("HEAD"), cancellable=true)
    private void replayViewer(GameProfile profile, CallbackInfoReturnable<ServerPlayer> cir) {
        if (((PlayerList)(Object)this).getServer() instanceof ReplayServer replay)
            cir.setReturnValue(replay.createPlayer(replay.overworld(), profile));
    }
}
