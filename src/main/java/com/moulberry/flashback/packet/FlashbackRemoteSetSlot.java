package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

public record FlashbackRemoteSetSlot(int entityId, int slot, ItemStack itemStack) implements FlashbackPayload {
    public static final Type<FlashbackRemoteSetSlot> TYPE = new Type<>(Flashback.createIdentifier("remote_set_slot"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackRemoteSetSlot> STREAM_CODEC = new RemoteSetSlotPacketCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class RemoteSetSlotPacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackRemoteSetSlot> {
        @Override
        public FlashbackRemoteSetSlot decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int slot = friendlyByteBuf.readByte();
            ItemStack itemStack = friendlyByteBuf.readItem();
            return new FlashbackRemoteSetSlot(entityId, slot, itemStack);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteSetSlot remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeByte(remoteHotbarSlot.slot);
            friendlyByteBuf.writeItem(remoteHotbarSlot.itemStack);
        }
    }

}
