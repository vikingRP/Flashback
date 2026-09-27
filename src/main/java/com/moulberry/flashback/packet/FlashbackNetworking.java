package com.moulberry.flashback.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/** Internal replay-server messages. The real remote server does not need Flashback. */
public final class FlashbackNetworking {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("flashback", "replay"), () -> "1", version -> true, version -> true);
    private static final Map<ResourceLocation, PacketCodec<FriendlyByteBuf, ? extends FlashbackPayload>> CODECS = new HashMap<>();
    private static final Map<ResourceLocation, BiConsumer<FlashbackPayload, Context>> RECEIVERS = new HashMap<>();

    static {
        register(FlashbackClearResourcePack.TYPE, FlashbackClearResourcePack.CODEC, (packet, minecraft) -> minecraft.getDownloadedPackSource().clearServerPack());
        register(FlashbackTickRate.TYPE, FlashbackTickRate.CODEC, (packet, minecraft) -> {
            var clock = com.moulberry.flashback.playback.ReplayTickRateManager.client();
            clock.setTickRate(packet.rate());
            clock.setFrozen(packet.frozen());
        });
        CHANNEL.registerMessage(0, Envelope.class, Envelope::write, Envelope::read, (envelope, supplier) -> {
            var context = supplier.get();
            if (envelope.payload instanceof FinishedServerTick) {
                var export = com.moulberry.flashback.Flashback.EXPORT_JOB;
                if (export != null) export.onFinishedServerTick();
                context.setPacketHandled(true);
                return;
            }
            context.enqueueWork(() -> {
                var receiver = RECEIVERS.get(envelope.payload.type().id());
                if (receiver != null) receiver.accept(envelope.payload, new Context(Minecraft.getInstance()));
            });
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private FlashbackNetworking() {}
    public record Context(Minecraft client) {
        public net.minecraft.client.player.LocalPlayer player() { return client.player; }
    }

    public static <T extends FlashbackPayload> void register(FlashbackPayload.Type<T> type, PacketCodec<FriendlyByteBuf, T> codec) {
        if (CODECS.putIfAbsent(type.id(), codec) != null) throw new IllegalStateException("Duplicate payload " + type.id());
    }

    public static <T extends FlashbackPayload> void register(FlashbackPayload.Type<T> type, PacketCodec<FriendlyByteBuf, T> codec, BiConsumer<T, Minecraft> receiver) {
        register(type, codec);
        registerGlobalReceiver(type, (payload, context) -> receiver.accept(payload, context.client()));
    }

    @SuppressWarnings("unchecked")
    public static <T extends FlashbackPayload> void registerGlobalReceiver(FlashbackPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        RECEIVERS.put(type.id(), (payload, context) -> receiver.accept((T) payload, context));
    }

    public static void send(ServerPlayer player, FlashbackPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Envelope(payload));
    }

    public static Packet<?> toVanillaPacket(FlashbackPayload payload) {
        return CHANNEL.toVanillaPacket(new Envelope(payload), NetworkDirection.PLAY_TO_CLIENT);
    }

    public static Packet<?> createS2CPacket(FlashbackPayload payload) { return toVanillaPacket(payload); }

    private record Envelope(FlashbackPayload payload) {
        @SuppressWarnings("unchecked")
        void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(payload.type().id());
            var codec = (PacketCodec<FriendlyByteBuf, FlashbackPayload>) CODECS.get(payload.type().id());
            if (codec == null) throw new IllegalArgumentException("Unregistered Flashback payload: " + payload.type().id());
            codec.encode(buffer, payload);
        }

        static Envelope read(FriendlyByteBuf buffer) {
            var id = buffer.readResourceLocation();
            var codec = CODECS.get(id);
            if (codec == null) throw new IllegalArgumentException("Unknown Flashback payload: " + id);
            return new Envelope(codec.decode(buffer));
        }
    }
}
