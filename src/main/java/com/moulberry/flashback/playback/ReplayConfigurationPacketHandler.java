package com.moulberry.flashback.playback;

import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.WorldDataConfiguration;
import java.util.*;

/** In protocol 763 registry data travels in Login and tags/features in PLAY. */
public final class ReplayConfigurationPacketHandler {
    private final ReplayServer server;
    private final ReplayRegistrySync registrySync = new ReplayRegistrySync();
    public ReplayConfigurationPacketHandler(ReplayServer server) { this.server = server; }
    public boolean applyRegistries(RegistryAccess.Frozen incoming) {
        boolean changed = registrySync.update(incoming);
        // Kept levels retain their RegistryAccess. Preserve those instances so subsequent
        // tag packets update the registries used by their chunks and entities as well.
        if (!changed) return false;
        // Replace synchronized registries inside their original layers, retaining worldgen-only registries.
        List<RegistryAccess.Frozen> layers = new ArrayList<>();
        Set<ResourceKey<? extends Registry<?>>> used = new HashSet<>();
        for (RegistryLayer layer : RegistryLayer.values()) {
            List<Registry<?>> replacement = new ArrayList<>();
            server.registries().getLayer(layer).registries().forEach(entry -> {
                Registry<?> registry = incoming.registry(entry.key()).orElse(null);
                replacement.add(registry != null ? registry : entry.value());
                used.add(entry.key());
            });
            if (layer == RegistryLayer.RELOADABLE) incoming.registries().filter(entry -> !used.contains(entry.key())).forEach(entry -> replacement.add(entry.value()));
            layers.add(new RegistryAccess.ImmutableRegistryAccess(replacement).freeze());
        }
        server.replaceReplayRegistries(server.registries().replaceFrom(RegistryLayer.STATIC, layers));
        return changed;
    }
    public void applyFeatures(FeatureFlagSet features) {
        server.getWorldData().setDataConfiguration(new WorldDataConfiguration(server.getWorldData().getDataConfiguration().dataPacks(), features));
    }
    public void applyTags(ClientboundUpdateTagsPacket packet) {
        packet.getTags().forEach((key, payload) -> applyTagsUnchecked(key, payload));
        server.getPlayerList().broadcastAll(packet);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyTagsUnchecked(ResourceKey key, TagNetworkSerialization.NetworkPayload payload) {
        Registry registry = server.registryAccess().registryOrThrow(key);
        Map tags = new HashMap();
        TagNetworkSerialization.deserializeTagsFromNetwork(key, registry, payload, tags::put);
        registry.bindTags(tags);
    }
}
