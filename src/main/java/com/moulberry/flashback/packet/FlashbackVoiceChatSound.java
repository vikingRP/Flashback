package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public interface FlashbackVoiceChatSound extends FlashbackPayload {
    Type<FlashbackVoiceChatSound> TYPE = new Type<>(Flashback.createIdentifier("voice_chat_sound"));
    PacketCodec<FriendlyByteBuf, FlashbackVoiceChatSound> STREAM_CODEC = new FlashbackVoiceChatSoundPacketCodec();

    @Override
    default Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    UUID source();
    short[] samples();
    void writeExtraData(FriendlyByteBuf friendlyByteBuf);

    byte TYPE_STATIC_SOUND = 0;
    byte TYPE_LOCATIONAL_SOUND = 1;
    byte TYPE_ENTITY_SOUND = 2;

    record SoundStatic(UUID source, short[] samples) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_STATIC_SOUND);
        }
    }

    record SoundLocational(UUID source, short[] samples, Vec3 position, float distance) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_LOCATIONAL_SOUND);
            friendlyByteBuf.writeDouble(this.position.x());
            friendlyByteBuf.writeDouble(this.position.y());
            friendlyByteBuf.writeDouble(this.position.z());
            friendlyByteBuf.writeFloat(this.distance);
        }
    }

    record SoundEntity(UUID source, short[] samples, boolean whispering, float distance) implements FlashbackVoiceChatSound {
        @Override
        public void writeExtraData(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(TYPE_ENTITY_SOUND);
            friendlyByteBuf.writeBoolean(this.whispering);
            friendlyByteBuf.writeFloat(this.distance);
        }
    }

    class FlashbackVoiceChatSoundPacketCodec implements PacketCodec<FriendlyByteBuf, FlashbackVoiceChatSound> {
        @Override
        public FlashbackVoiceChatSound decode(FriendlyByteBuf friendlyByteBuf) {
            UUID uuid = friendlyByteBuf.readUUID();

            int sampleCount = friendlyByteBuf.readVarInt();
            short[] samples = new short[sampleCount];
            for (int i = 0; i < sampleCount; i++) {
                samples[i] = friendlyByteBuf.readShort();
            }

            byte type = friendlyByteBuf.readByte();

            switch (type) {
                case TYPE_STATIC_SOUND -> {
                    return new SoundStatic(uuid, samples);
                }
                case TYPE_LOCATIONAL_SOUND -> {
                    Vec3 position = new Vec3(
                        friendlyByteBuf.readDouble(),
                        friendlyByteBuf.readDouble(),
                        friendlyByteBuf.readDouble()
                    );
                    float distance = friendlyByteBuf.readFloat();
                    return new SoundLocational(uuid, samples, position, distance);
                }
                case TYPE_ENTITY_SOUND -> {
                    boolean whispering = friendlyByteBuf.readBoolean();
                    float distance = friendlyByteBuf.readFloat();
                    return new SoundEntity(uuid, samples, whispering, distance);
                }
                default -> throw new DecoderException("Unknown voice chat type: " + type);
            }
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackVoiceChatSound packet) {
            friendlyByteBuf.writeUUID(packet.source());

            short[] samples = packet.samples();
            friendlyByteBuf.writeVarInt(samples.length);
            for (short sample : samples) {
                friendlyByteBuf.writeShort(sample);
            }

            packet.writeExtraData(friendlyByteBuf);
        }
    }

}
