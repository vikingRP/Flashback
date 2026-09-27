package com.moulberry.flashback.io;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;

/** Keeps registry context alongside the vanilla 1.20.1 packet buffer. */
public class ReplayBuffer extends FriendlyByteBuf {
    private final RegistryAccess registries;

    public ReplayBuffer(ByteBuf buffer, RegistryAccess registries) {
        super(buffer);
        this.registries = registries;
    }

    public RegistryAccess registryAccess() { return registries; }
}
