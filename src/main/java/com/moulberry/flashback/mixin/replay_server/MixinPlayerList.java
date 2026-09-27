package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.playback.FlashbackFakePlayer;
import com.moulberry.flashback.playback.FlashbackFakePlayerPacketListener;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
public class MixinPlayerList implements com.moulberry.flashback.ext.ReplayRegistryPlayerListExt {
    @Shadow @Final @org.spongepowered.asm.mixin.Mutable
    private net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> registries;
    @Shadow @Final @org.spongepowered.asm.mixin.Mutable
    private net.minecraft.core.RegistryAccess.Frozen synchronizedRegistries;

    @Override public void flashback$replaceRegistries(net.minecraft.core.LayeredRegistryAccess<net.minecraft.server.RegistryLayer> registries) {
        this.registries = registries;
        this.synchronizedRegistries = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(
            net.minecraft.core.RegistrySynchronization.networkedRegistries(registries)).freeze();
    }


    @Shadow
    @Final
    private MinecraftServer server;

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;obfuscateSeed(J)J"))
    private long recordedLoginSeed(long seed, Operation<Long> original,
                                  @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) ServerPlayer player) {
        if (this.server instanceof ReplayServer)
            return ((com.moulberry.flashback.ext.ServerLevelExt)player.serverLevel()).flashback$getSeedHash();
        return original.call(seed);
    }

    @WrapWithCondition(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V", remap = false))
    public boolean placeNewPlayer_logInfo(Logger instance, String s, Object[] objects) {
        return !(this.server instanceof ReplayServer);
    }

    @WrapOperation(method = "placeNewPlayer", at = @At(value = "NEW", target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"))
    public ServerGamePacketListenerImpl placeNewPlayer_newServerGamePacketListener(MinecraftServer minecraftServer, Connection connection, ServerPlayer serverPlayer, Operation<ServerGamePacketListenerImpl> original) {
        if (serverPlayer instanceof FlashbackFakePlayer) {
            return new FlashbackFakePlayerPacketListener(minecraftServer, connection, serverPlayer);
        }
        return original.call(minecraftServer, connection, serverPlayer);
    }

}
