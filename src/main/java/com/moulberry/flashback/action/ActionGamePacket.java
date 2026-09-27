package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionGamePacket implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/game_packet");
    public static final ActionGamePacket INSTANCE = new ActionGamePacket();
    private ActionGamePacket() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        replayServer.handleGamePacket(friendlyByteBuf);
    }

}
