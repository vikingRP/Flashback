package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionLevelChunkCached implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/level_chunk_cached");
    public static final ActionLevelChunkCached INSTANCE = new ActionLevelChunkCached();
    private ActionLevelChunkCached() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        replayServer.handleLevelChunkCached(friendlyByteBuf.readVarInt());
    }

}
