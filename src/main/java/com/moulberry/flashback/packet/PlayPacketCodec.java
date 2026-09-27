package com.moulberry.flashback.packet;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

/** Vanilla protocol 763 serialization, shared by recording, playback and chunk caches. */
public final class PlayPacketCodec implements PacketCodec<ByteBuf, Packet<? super ClientGamePacketListener>> {
    public static final PlayPacketCodec INSTANCE = new PlayPacketCodec();
    private PlayPacketCodec() {}

    public static int customPayloadPacketId() {
        return ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND,
            new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(
                net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket.BRAND,
                new FriendlyByteBuf(io.netty.buffer.Unpooled.EMPTY_BUFFER)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Packet<? super ClientGamePacketListener> decode(ByteBuf source) {
        FriendlyByteBuf buffer = source instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(source);
        int id = buffer.readVarInt();
        Packet<?> packet = ConnectionProtocol.PLAY.createPacket(PacketFlow.CLIENTBOUND, id, buffer);
        if (packet == null) throw new DecoderException("Unknown replay packet id: " + id);
        return (Packet<? super ClientGamePacketListener>) packet;
    }

    @Override
    public void encode(ByteBuf target, Packet<? super ClientGamePacketListener> packet) {
        FriendlyByteBuf buffer = target instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(target);
        int id = packet instanceof RecordedCustomPayload ? customPayloadPacketId() : ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet);
        if (id < 0) throw new EncoderException("Unregistered replay packet: " + packet.getClass().getName());
        buffer.writeVarInt(id);
        packet.write(buffer);
    }
}
