package com.moulberry.flashback.action;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public class ActionRealTimeClock implements Action {

    private static final ResourceLocation NAME = Flashback.createIdentifier("action/real_time_clock_optional");
    public static final ActionRealTimeClock INSTANCE = new ActionRealTimeClock();
    private ActionRealTimeClock() {
    }

    @Override
    public ResourceLocation name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf) {
        byte delta = friendlyByteBuf.readByte();

        if (delta == 0) {
            replayServer.setTimeRtc(friendlyByteBuf.readLong());
        } else {
            replayServer.updateTimeRtc(delta);
        }
    }

}
