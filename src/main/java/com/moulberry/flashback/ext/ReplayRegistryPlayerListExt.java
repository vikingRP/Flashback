package com.moulberry.flashback.ext;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
public interface ReplayRegistryPlayerListExt {
    void flashback$replaceRegistries(LayeredRegistryAccess<RegistryLayer> registries);
}
