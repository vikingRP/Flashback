package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackPayload;

public class FlashbackForceClientTick implements FlashbackPayload {
    public static final Type<FlashbackForceClientTick> TYPE = new Type<>(Flashback.createIdentifier("force_client_tick"));
    public static final FlashbackForceClientTick INSTANCE = new FlashbackForceClientTick();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

}
