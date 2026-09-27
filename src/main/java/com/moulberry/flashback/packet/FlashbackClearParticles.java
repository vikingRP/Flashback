package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.packet.FlashbackPayload;

public class FlashbackClearParticles implements FlashbackPayload {
    public static final Type<FlashbackClearParticles> TYPE = new Type<>(Flashback.createIdentifier("clear_particles"));
    public static final FlashbackClearParticles INSTANCE = new FlashbackClearParticles();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

}
