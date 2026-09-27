package com.moulberry.flashback.playback;

import com.moulberry.flashback.ext.MinecraftExt;
import com.moulberry.flashback.ext.ServerTickRateManagerExt;
import com.moulberry.flashback.packet.FlashbackNetworking;
import com.moulberry.flashback.packet.FlashbackTickRate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Replay clock for 1.20.1, which has no vanilla tick-rate manager. */
public final class ReplayTickRateManager implements ServerTickRateManagerExt {
    private static final ReplayTickRateManager CLIENT = new ReplayTickRateManager(null);
    private final ReplayServer server;
    private float tickrate = 20;
    private boolean frozen;
    private boolean runGameElements = true;
    private int frozenTicksToRun;
    private boolean suppressUpdates;
    private boolean dirty;

    public ReplayTickRateManager(ReplayServer server) { this.server = server; }
    public static ReplayTickRateManager client() { return CLIENT; }
    public float tickrate() { return tickrate; }
    public float millisecondsPerTick() { return 1000.0f / tickrate; }
    public long nanosecondsPerTick() { return (long)(1_000_000_000.0 / tickrate); }
    public boolean isFrozen() { return frozen; }
    public boolean runsNormally() { return runGameElements; }
    public void setFrozenTicksToRun(int ticks) { frozenTicksToRun = Math.max(0, ticks); }
    public void setTickRate(float rate) {
        if (!Float.isFinite(rate) || rate <= 0) throw new IllegalArgumentException("Invalid replay tick rate: " + rate);
        if (tickrate != rate) { tickrate = rate; changed(); }
    }
    public void setFrozen(boolean frozen) {
        if (this.frozen != frozen) { this.frozen = frozen; changed(); }
    }
    public void tick() {
        runGameElements = !frozen || frozenTicksToRun > 0;
        if (frozenTicksToRun > 0) frozenTicksToRun--;
    }
    public boolean isEntityFrozen(Entity entity) {
        if (server != null) return !(entity instanceof ReplayPlayer);
        Minecraft minecraft = Minecraft.getInstance();
        if (entity == minecraft.player) {
            MinecraftExt timers = (MinecraftExt)minecraft;
            return timers.flashback$overridingLocalPlayerTimer() && !timers.flashback$isTickingLocalPlayer();
        }
        return !runGameElements;
    }
    private void changed() {
        dirty = true;
        if (!suppressUpdates) synchronize();
    }
    private void synchronize() {
        if (dirty && server != null) {
            var message = new FlashbackTickRate(tickrate, frozen);
            for (ReplayPlayer viewer : server.getReplayViewers()) FlashbackNetworking.send(viewer, message);
        }
        dirty = false;
    }
    @Override public void flashback$setSuppressClientUpdates(boolean suppress) {
        suppressUpdates = suppress;
        if (!suppress) synchronize();
    }
}
