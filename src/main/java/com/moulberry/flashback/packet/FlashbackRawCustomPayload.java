package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;

import net.minecraft.world.item.ItemStack;

public record FlashbackRawCustomPayload(byte[] packetBytes, boolean configPhase) implements FlashbackPayload {
    public static final Type<FlashbackRawCustomPayload> TYPE = new Type<>(Flashback.createIdentifier("raw_custom_payload"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackRawCustomPayload> STREAM_CODEC = new ProcessPacketRawPacketCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class ProcessPacketRawPacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackRawCustomPayload> {
        @Override
        public FlashbackRawCustomPayload decode(FriendlyByteBuf friendlyByteBuf) {
            byte[] packetBytes = friendlyByteBuf.readByteArray();
            boolean configPhase = friendlyByteBuf.readBoolean();
            return new FlashbackRawCustomPayload(packetBytes, configPhase);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRawCustomPayload packet) {
            friendlyByteBuf.writeByteArray(packet.packetBytes());
            friendlyByteBuf.writeBoolean(packet.configPhase());
        }
    }

}
