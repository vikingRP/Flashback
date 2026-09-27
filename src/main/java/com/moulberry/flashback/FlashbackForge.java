package com.moulberry.flashback;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Forge entry point. Replay servers run inside the client; dedicated servers need no mod. */
@Mod("flashback")
public final class FlashbackForge {
    public FlashbackForge() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> FlashbackClientBootstrap::register);
    }
}
