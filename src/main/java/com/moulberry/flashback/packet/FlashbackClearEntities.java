package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackPayload;

public class FlashbackClearEntities implements FlashbackPayload {
    public static final Type<FlashbackClearEntities> TYPE = new Type<>(Flashback.createIdentifier("clear_entities"));
    public static final FlashbackClearEntities INSTANCE = new FlashbackClearEntities();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

}
