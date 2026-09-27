
package com.moulberry.flashback.packet;

import com.moulberry.flashback.Flashback;
import net.minecraft.network.FriendlyByteBuf;
import com.moulberry.flashback.packet.PacketCodec;
import com.moulberry.flashback.packet.FlashbackPayload;

public record FlashbackSetBorderLerpStartTime(long time) implements FlashbackPayload {
    public static final Type<FlashbackSetBorderLerpStartTime> TYPE = new Type<>(Flashback.createIdentifier("set_border_lerp_start_time"));

    public static final PacketCodec<FriendlyByteBuf, FlashbackSetBorderLerpStartTime> STREAM_CODEC = new SetBorderLerpStartTimeCodec();

    @Override
    public Type<? extends FlashbackPayload> type() {
        return TYPE;
    }

    public static class SetBorderLerpStartTimeCodec implements PacketCodec<FriendlyByteBuf, FlashbackSetBorderLerpStartTime> {
        @Override
        public FlashbackSetBorderLerpStartTime decode(FriendlyByteBuf friendlyByteBuf) {
            long millis = friendlyByteBuf.readLong();
            return new FlashbackSetBorderLerpStartTime(millis);
        }

        @Override
        public void encode(FriendlyByteBuf friendlyByteBuf, FlashbackSetBorderLerpStartTime setBorderLerpStartTime) {
            friendlyByteBuf.writeLong(setBorderLerpStartTime.time);
        }
    }

}
