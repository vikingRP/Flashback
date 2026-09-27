package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;

public record FlashbackRemoteFoodData(int entityId, int foodLevel, float saturationLevel) implements FlashbackPayload {
    public static final Type<FlashbackRemoteFoodData> TYPE = new Type<>(Flashback.createIdentifier("remote_food_data"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackRemoteFoodData> STREAM_CODEC = new FlashbackRemoteFoodDataPacketCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class FlashbackRemoteFoodDataPacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackRemoteFoodData> {
        @Override
        public FlashbackRemoteFoodData decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            int foodLevel = friendlyByteBuf.readVarInt();
            float saturationLevel = friendlyByteBuf.readFloat();
            return new FlashbackRemoteFoodData(entityId, foodLevel, saturationLevel);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteFoodData remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.foodLevel);
            friendlyByteBuf.writeFloat(remoteHotbarSlot.saturationLevel);
        }
    }

}
