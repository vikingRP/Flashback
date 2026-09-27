package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackPayload;

public class FlashbackInstantlyLerp implements FlashbackPayload {
    public static final Type<FlashbackInstantlyLerp> TYPE = new Type<>(Flashback.createIdentifier("instantly_lerp"));
    public static final FlashbackInstantlyLerp INSTANCE = new FlashbackInstantlyLerp();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

}
