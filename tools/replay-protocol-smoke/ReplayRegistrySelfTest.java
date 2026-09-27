package com.moulberry.flashback.smoketest;

import com.moulberry.flashback.packet.PlayPacketCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Optional Forge-transformed smoke tests, excluded from release source sets. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "flashback", bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ReplayRegistrySelfTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(ReplayRegistrySelfTest::run);
    }
    public static void run() {
        var registrySync = new com.moulberry.flashback.playback.ReplayRegistrySync();
        var firstRegistries = dimensionRegistries(256);
        check(registrySync.update(firstRegistries), "Initial registry contents must synchronize");
        check(!registrySync.update(dimensionRegistries(256)), "Identical registry contents reconstructed from another snapshot must not reconnect viewers");
        check(registrySync.update(dimensionRegistries(384)), "Dimension type changes must reconnect viewers");
        var recordedLogin = new net.minecraft.network.protocol.game.ClientboundLoginPacket(7, false,
            net.minecraft.world.level.GameType.SURVIVAL, null, java.util.Set.of(net.minecraft.world.level.Level.OVERWORLD), firstRegistries,
            net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD, net.minecraft.world.level.Level.OVERWORLD,
            1234L, 10, 8, 6, false, true, false, false, java.util.Optional.empty(), 0);
        var viewerLogin = com.moulberry.flashback.playback.ReplayRegistrySync.forViewer(recordedLogin, 123456,
            net.minecraft.world.level.GameType.SPECTATOR, null, 12, 8);
        FriendlyByteBuf loginBytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayPacketCodec.INSTANCE.encode(loginBytes, viewerLogin);
            var restored = (net.minecraft.network.protocol.game.ClientboundLoginPacket)PlayPacketCodec.INSTANCE.decode(loginBytes);
            check(restored.playerId() == 123456 && restored.gameType() == net.minecraft.world.level.GameType.SPECTATOR,
                "Registry resync Login must preserve viewer entity identity and camera mode");
            check(restored.seed() == 1234L && restored.dimension().equals(recordedLogin.dimension()) &&
                restored.registryHolder().registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                    .getHolderOrThrow(restored.dimensionType()).value().height() == 256,
                "Registry resync Login must carry recorded dimension contents and seed");
        } finally { loginBytes.release(); }

        com.moulberry.flashback.Flashback.LOGGER.info("PASS: {} registry-change and viewer Login assertions", assertions);
    }
    private static net.minecraft.core.RegistryAccess.Frozen dimensionRegistries(int height) {
        var registry = new net.minecraft.core.MappedRegistry<net.minecraft.world.level.dimension.DimensionType>(
            net.minecraft.core.registries.Registries.DIMENSION_TYPE, com.mojang.serialization.Lifecycle.stable());
        var dimension = new net.minecraft.world.level.dimension.DimensionType(java.util.OptionalLong.empty(), true, false, false, true,
            1.0, true, false, 0, height, height,
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("minecraft", "infiniburn_overworld")),
            new ResourceLocation("minecraft", "overworld"), 0,
            new net.minecraft.world.level.dimension.DimensionType.MonsterSettings(false, true, net.minecraft.util.valueproviders.ConstantInt.of(0), 0));
        registry.register(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD, dimension, com.mojang.serialization.Lifecycle.stable());
        registry.freeze();
        return new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.List.of(registry)).freeze();
    }
}
