package com.moulberry.flashback.platform;

import net.minecraft.resources.ResourceLocation;

/** The texture and arm model are separate values in Minecraft 1.20.1. */
public record PlayerSkin(ResourceLocation texture, String model) {}
