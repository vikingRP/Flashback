package com.moulberry.flashback.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;

/** Immutable copy independent of Forge's reference-counted incoming packet buffer. */
public final class RecordedCustomPayload implements Packet<ClientGamePacketListener> {
    private final ResourceLocation channel;
    private final byte[] bytes;
    private RecordedCustomPayload(ResourceLocation channel, byte[] bytes) {
        this.channel = channel;
        this.bytes = bytes;
    }
    public static RecordedCustomPayload copyOf(ClientboundCustomPayloadPacket packet) {
        FriendlyByteBuf copy = packet.getData();
        try {
            byte[] bytes = new byte[copy.readableBytes()];
            copy.readBytes(bytes);
            return new RecordedCustomPayload(packet.getIdentifier(), bytes);
        } finally { copy.release(); }
    }
    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(channel);
        buffer.writeBytes(bytes);
    }
    @Override public void handle(ClientGamePacketListener listener) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write(buffer);
            new ClientboundCustomPayloadPacket(buffer).handle(listener);
        } finally { buffer.release(); }
    }
}
