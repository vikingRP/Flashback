package com.moulberry.flashback.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record FlashbackTickRate(float rate, boolean frozen) implements FlashbackPayload {
    public static final Type<FlashbackTickRate> TYPE = new Type<>(new ResourceLocation("flashback", "tick_rate"));
    public static final PacketCodec<FriendlyByteBuf, FlashbackTickRate> CODEC = new PacketCodec<>() {
        public FlashbackTickRate decode(FriendlyByteBuf buffer) { return new FlashbackTickRate(buffer.readFloat(), buffer.readBoolean()); }
        public void encode(FriendlyByteBuf buffer, FlashbackTickRate value) { buffer.writeFloat(value.rate()); buffer.writeBoolean(value.frozen()); }
    };
    @Override public Type<FlashbackTickRate> type() { return TYPE; }
}
