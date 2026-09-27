package com.moulberry.flashback.exporting;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.moulberry.flashback.Flashback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class PerfectFrames {
    private static boolean enabled;
    static List<Consumer<Boolean>> frexFlawlessFrames = new ArrayList<>();
    private static final Set<UUID> ignored = new HashSet<>();
    private static final Map<UUID, CompletableFuture<Map<MinecraftProfileTexture.Type, MinecraftProfileTexture>>> profiles = new HashMap<>();

    public static void enable() {
        enabled = true;
        ignored.clear(); profiles.clear();
        frexFlawlessFrames.forEach(callback -> callback.accept(true));
    }
    public static void disable() {
        enabled = false;
        ignored.clear(); profiles.clear();
        frexFlawlessFrames.forEach(callback -> callback.accept(false));
    }
    public static boolean isEnabled() { return enabled; }

    public static void waitUntilFrameReady() {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (!isFrameReady(deadline)) {
            LockSupport.parkNanos("waiting for replay skins", 100_000L);
            Minecraft.getInstance().runAllTasks();
            RenderSystem.replayQueue();
        }
        Minecraft.getInstance().runAllTasks();
        RenderSystem.replayQueue();
    }

    private static boolean isFrameReady(long deadline) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return true;
        for (var player : minecraft.level.players()) {
            if (player == minecraft.player || ignored.contains(player.getUUID())) continue;
            if (System.nanoTime() >= deadline) {
                ignored.add(player.getUUID());
                Flashback.LOGGER.warn("Skin loading timed out for {}", player.getUUID());
                continue;
            }
            var info = player.getPlayerInfo();
            if (info == null) continue;
            var future = profiles.computeIfAbsent(player.getUUID(), id -> {
                GameProfile profile = new GameProfile(info.getProfile().getId(), info.getProfile().getName());
                profile.getProperties().putAll(info.getProfile().getProperties());
                return CompletableFuture.supplyAsync(() -> {
                    var service = minecraft.getMinecraftSessionService();
                    var textures = service.getTextures(profile, false);
                    if (textures.isEmpty()) {
                        service.fillProfileProperties(profile, false);
                        textures = service.getTextures(profile, false);
                    }
                    return textures;
                }, Util.backgroundExecutor());
            });
            if (!future.isDone()) return false;
            try {
                for (var entry : future.join().entrySet()) {
                    var location = minecraft.getSkinManager().registerTexture(entry.getValue(), entry.getKey());
                    var texture = minecraft.getTextureManager().getTexture(location);
                    if (texture instanceof HttpTexture http && http.future != null && !http.future.isDone()) return false;
                }
                // Populate PlayerInfo's skin and cape callbacks as well.
                player.getSkinTextureLocation();
                player.getCloakTextureLocation();
            } catch (CompletionException error) {
                ignored.add(player.getUUID());
                Flashback.LOGGER.warn("Unable to prepare replay skin for {}", player.getUUID(), error.getCause());
            }
        }
        return true;
    }
}
