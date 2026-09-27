package com.moulberry.flashback.mixin.replay_server;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCache {

    @Shadow @Final ServerLevel level;
    @Shadow @Final public ChunkMap chunkMap;

    @Inject(method = "tickChunks", at = @At("HEAD"), cancellable = true)
    private void tickReplayChunks(CallbackInfo ci) {
        if (!(this.level.getServer() instanceof ReplayServer)) return;
        for (ChunkHolder holder : this.chunkMap.getChunks()) {
            var chunk = holder.getTickingChunk();
            if (chunk != null) holder.broadcastChanges(chunk);
        }
        this.chunkMap.tick();
        ci.cancel();
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    public void save(boolean bl, CallbackInfo ci) {
        if (this.level.getServer() instanceof ReplayServer) {
            ci.cancel();
        }
    }

}
