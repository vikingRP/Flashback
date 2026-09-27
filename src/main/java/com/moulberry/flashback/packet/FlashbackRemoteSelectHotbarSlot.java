package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;

public record FlashbackRemoteSelectHotbarSlot(int entityId, int slot) implements FlashbackPayload {
    public static final Type<FlashbackRemoteSelectHotbarSlot> TYPE = new Type<>(Flashback.createIdentifier("remote_select_hotbar_slot"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackRemoteSelectHotbarSlot> STREAM_CODEC = new RemoteSelectHotbarSlotPacketCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class RemoteSelectHotbarSlotPacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackRemoteSelectHotbarSlot> {
        @Override
        public FlashbackRemoteSelectHotbarSlot decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int slot = friendlyByteBuf.readByte();
            return new FlashbackRemoteSelectHotbarSlot(entityId, slot);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteSelectHotbarSlot remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeByte(remoteHotbarSlot.slot);
        }
    }

}
