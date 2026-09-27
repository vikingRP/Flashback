package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.moulberry.flashback.ext.ServerLevelExt;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {
    @WrapOperation(method = {
        "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
        "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;obfuscateSeed(J)J"))
    private long recordedDimensionSeed(long seed, Operation<Long> original, @Local(argsOnly = true) ServerLevel destination) {
        return destination.getServer() instanceof ReplayServer
            ? ((ServerLevelExt)destination).flashback$getSeedHash() : original.call(seed);
    }
}
