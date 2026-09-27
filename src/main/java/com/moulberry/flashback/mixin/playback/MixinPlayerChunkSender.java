package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.ext.ServerLevelExt;
import com.moulberry.flashback.playback.FlashbackFakePlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 1.20.1 sends chunks immediately from ChunkMap, without batching. */
@Mixin(ChunkMap.class)
public abstract class MixinPlayerChunkSender {
    @Inject(method="playerLoadedChunk", at=@At("HEAD"), cancellable=true)
    private void onlyRecordedChunks(ServerPlayer player, MutableObject<ClientboundLevelChunkWithLightPacket> packet, LevelChunk chunk, CallbackInfo ci) {
        if (chunk.getLevel().getServer() instanceof ReplayServer && (player instanceof FlashbackFakePlayer || !((ServerLevelExt)chunk.getLevel()).flashback$shouldSendChunk(chunk.getPos().toLong()))) ci.cancel();
    }
}
