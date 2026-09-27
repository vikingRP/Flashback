package com.moulberry.flashback.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
public record FlashbackClearResourcePack() implements FlashbackPayload {
    public static final FlashbackClearResourcePack INSTANCE = new FlashbackClearResourcePack();
    public static final Type<FlashbackClearResourcePack> TYPE = new Type<>(new ResourceLocation("flashback", "clear_resource_pack"));
    public static final PacketCodec<FriendlyByteBuf, FlashbackClearResourcePack> CODEC = new PacketCodec<>() {
        public FlashbackClearResourcePack decode(FriendlyByteBuf buffer) { return INSTANCE; }
        public void encode(FriendlyByteBuf buffer, FlashbackClearResourcePack value) {}
    };
    @Override public Type<FlashbackClearResourcePack> type() { return TYPE; }
}
