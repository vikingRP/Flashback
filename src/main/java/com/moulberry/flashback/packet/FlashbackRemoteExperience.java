package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;

public record FlashbackRemoteExperience(int entityId, float experienceProgress, int totalExperience, int experienceLevel) implements FlashbackPayload {
    public static final Type<FlashbackRemoteExperience> TYPE = new Type<>(Flashback.createIdentifier("remote_experience"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackRemoteExperience> STREAM_CODEC = new FlashbackRemoteExperiencePacketCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class FlashbackRemoteExperiencePacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackRemoteExperience> {
        @Override
        public FlashbackRemoteExperience decode(FriendlyByteBuf friendlyByteBuf) {
            int entityId = friendlyByteBuf.readVarInt();
            float experienceProgress = friendlyByteBuf.readFloat();
            int totalExperience = friendlyByteBuf.readVarInt();
            int experienceLevel = friendlyByteBuf.readVarInt();
            return new FlashbackRemoteExperience(entityId, experienceProgress, totalExperience, experienceLevel);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackRemoteExperience remoteHotbarSlot) {
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.entityId);
            friendlyByteBuf.writeFloat(remoteHotbarSlot.experienceProgress);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.totalExperience);
            friendlyByteBuf.writeVarInt(remoteHotbarSlot.experienceLevel);
        }
    }

}
