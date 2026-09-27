package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionMoveEntities implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/move_entities");
    public static final ActionMoveEntities INSTANCE = new ActionMoveEntities();
    private ActionMoveEntities() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        replayServer.handleMoveEntities(friendlyByteBuf);
    }

}
