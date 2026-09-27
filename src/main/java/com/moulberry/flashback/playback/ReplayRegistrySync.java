package com.moulberry.flashback.playback;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.level.GameType;

/** Compares the exact network registry contents, including numeric IDs and Forge datapack registries. */
public final class ReplayRegistrySync {
    private Tag previous;
    public boolean update(RegistryAccess incoming) {
        Tag encoded = RegistrySynchronization.NETWORK_CODEC.encodeStart(
            RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)), incoming)
            .getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
        boolean changed = !encoded.equals(previous);
        previous = encoded;
        return changed;
    }
    public static ClientboundLoginPacket forViewer(ClientboundLoginPacket recorded, int viewerId, GameType mode,
                                                   GameType previousMode, int viewDistance, int simulationDistance) {
        return new ClientboundLoginPacket(viewerId, recorded.hardcore(), mode, previousMode, recorded.levels(),
            recorded.registryHolder(), recorded.dimensionType(), recorded.dimension(), recorded.seed(), 1,
            viewDistance, simulationDistance, recorded.reducedDebugInfo(), recorded.showDeathScreen(),
            recorded.isDebug(), recorded.isFlat(), recorded.lastDeathLocation(), recorded.portalCooldown());
    }
}
