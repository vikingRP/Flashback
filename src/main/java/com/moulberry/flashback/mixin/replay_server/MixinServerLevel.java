package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.ext.ServerLevelExt;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.playback.ReplayPlayer;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(value = ServerLevel.class, priority = 900)
public abstract class MixinServerLevel implements ServerLevelExt {

    @Shadow
    @NotNull
    public abstract MinecraftServer getServer();


    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    /** Recorded packets own world state; only the viewer and chunk/entity transport advance. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tickReplay(java.util.function.BooleanSupplier haveTime, CallbackInfo ci) {
        ServerLevel level = (ServerLevel)(Object)this;
        if (!(level.getServer() instanceof ReplayServer)) return;
        level.getWorldBorder().tick();
        level.getChunkSource().tick(haveTime, true);
        for (var player : level.players()) {
            if (player instanceof ReplayPlayer && !player.isRemoved() && !player.isPassenger())
                level.guardEntityTick(level::tickNonPassenger, player);
        }
        this.entityManager.tick();
        ci.cancel();
    }

    @Unique
    private long seedHash = 0;

    @Override
    public void flashback$setSeedHash(long seedHash) {
        this.seedHash = seedHash;
    }

    @Override
    public long flashback$getSeedHash() {
        return this.seedHash;
    }

    @Unique
    private final LongSet validChunks = new LongOpenHashSet();

    @Override
    public boolean flashback$shouldSendChunk(long pos) {
        return this.validChunks.contains(pos);
    }

    @Override
    public void flashback$markChunkAsSendable(long pos) {
        this.validChunks.add(pos);
    }

    @Unique
    private boolean canSpawnEntities = true;

    @Override
    public void flashback$setCanSpawnEntities(boolean canSpawnEntities) {
        this.canSpawnEntities = canSpawnEntities;
    }

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    public void addFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player) && !this.canSpawnEntities) {
            cir.setReturnValue(false);
        }
    }

    // Fix for worldgen mods injecting on getGeneratorState to add custom worldgen properties
    // Lets just nuke the whole line
    // An empty state is returned instead of null because other mods (i.e. ModernFix's stronghold cache)
    // also wrap ensureStructuresGenerated and dereference the state before our wrapper runs

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;getGeneratorState()Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;"))
    public ChunkGeneratorStructureState getGeneratorState(ServerChunkCache instance, Operation<ChunkGeneratorStructureState> original) {
        if (this.getServer() instanceof ReplayServer) {
            return ChunkGeneratorStructureState.createForFlat(instance.randomState(), 0L,
                instance.getGenerator().getBiomeSource(), Stream.empty());
        } else {
            return original.call(instance);
        }
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkGeneratorStructureState;ensureStructuresGenerated()V"))
    public void ensureStructuresGenerated(ChunkGeneratorStructureState instance, Operation<Void> original) {
        if (!(this.getServer() instanceof ReplayServer)) {
            original.call(instance);
        }
    }


}
