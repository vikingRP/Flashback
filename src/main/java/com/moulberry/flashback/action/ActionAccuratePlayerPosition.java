package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionAccuratePlayerPosition implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/accurate_player_position_optional");
    public static final ActionAccuratePlayerPosition INSTANCE = new ActionAccuratePlayerPosition();
    private ActionAccuratePlayerPosition() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        replayServer.handleAccuratePlayerPosition(friendlyByteBuf);
    }

}
