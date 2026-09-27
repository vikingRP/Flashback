package com.moulberry.flashback.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Loader services shared by the client and replay code. */
public final class ForgePlatform {
    private static final ForgePlatform INSTANCE = new ForgePlatform();

    private ForgePlatform() {}

    public static ForgePlatform getInstance() {
        return INSTANCE;
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    public boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    public String getModVersion(String id) {
        return ModList.get().getModContainerById(id)
            .map(mod -> mod.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
