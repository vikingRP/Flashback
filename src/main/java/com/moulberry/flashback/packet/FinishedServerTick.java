package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackPayload;

public class FinishedServerTick implements FlashbackPayload {
    public static final Type<FinishedServerTick> TYPE = new Type<>(Flashback.createIdentifier("finished_server_tick"));
    public static final FinishedServerTick INSTANCE = new FinishedServerTick();

    private FinishedServerTick() {
    }

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

}
