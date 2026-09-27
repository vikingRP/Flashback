package com.moulberry.flashback.action;

import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.io.ReplayBuffer;
import net.minecraft.resources.ResourceLocation;

public interface Action {

    ResourceLocation name();
    void handle(ReplayServer replayServer, ReplayBuffer friendlyByteBuf);

}
