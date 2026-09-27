package com.moulberry.flashback.playback;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
/** Unified view of the separate Login and Respawn fields in protocol 763. */
public record CommonPlayerSpawnInfo(Holder<DimensionType> dimensionType, ResourceKey<Level> dimension, long seed, boolean isDebug, boolean isFlat) {
    public static CommonPlayerSpawnInfo from(ClientboundLoginPacket packet, RegistryAccess registries) {
        return new CommonPlayerSpawnInfo(registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(packet.dimensionType()), packet.dimension(), packet.seed(), packet.isDebug(), packet.isFlat());
    }
    public static CommonPlayerSpawnInfo from(ClientboundRespawnPacket packet, RegistryAccess registries) {
        return new CommonPlayerSpawnInfo(registries.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(packet.getDimensionType()), packet.getDimension(), packet.getSeed(), packet.isDebug(), packet.isFlat());
    }
}
