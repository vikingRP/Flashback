package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionCreateLocalPlayer implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/create_local_player");
    public static final ActionCreateLocalPlayer INSTANCE = new ActionCreateLocalPlayer();
    private ActionCreateLocalPlayer() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        replayServer.handleCreateLocalPlayer(friendlyByteBuf);
    }

}
