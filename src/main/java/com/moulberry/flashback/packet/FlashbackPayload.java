package com.moulberry.flashback.packet;

import net.minecraft.resources.ResourceLocation;

/** Flashback's internal messages, transported by a Forge SimpleChannel. */
public interface FlashbackPayload {
    Type<? extends FlashbackPayload> type();
    record Type<T extends FlashbackPayload>(ResourceLocation id) {}
}
