package com.moulberry.flashback.mixin.replay_server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.screen.select_replay.PendingSelectionEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class MixinServerLoginPacketListenerImpl {

    @Shadow
    @Final
    MinecraftServer server;

    @Shadow public GameProfile gameProfile;

    @Inject(method = "handleHello", at = @At("RETURN"))
    public void handleHello(ServerboundHelloPacket serverboundHelloPacket, CallbackInfo ci) {
        if (this.server instanceof ReplayServer) {
            UUID replayViewerUUID = UUID.nameUUIDFromBytes(serverboundHelloPacket.name().getBytes(StandardCharsets.UTF_8));
            this.gameProfile = new GameProfile(replayViewerUUID, ReplayServer.REPLAY_VIEWER_NAME);
        }
    }


    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerLoginPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"))
    public boolean dontDisconnectInReplay(ServerLoginPacketListenerImpl instance, Component component) {
        Flashback.updateIsInReplay();
        return !Flashback.isInReplay();
    }
}
