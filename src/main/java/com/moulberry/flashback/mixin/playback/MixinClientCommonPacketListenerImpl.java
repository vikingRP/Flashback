package com.moulberry.flashback.mixin.playback;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class MixinClientCommonPacketListenerImpl {
    @Inject(method="handleResourcePack", at=@At("HEAD"), cancellable=true)
    private void replayResourcePack(ClientboundResourcePackPacket packet, CallbackInfo ci) {
        if (Flashback.isInReplay()) {
            Minecraft minecraft = Minecraft.getInstance();
            PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener)(Object)this, minecraft);
            try {
                java.net.URL url = new java.net.URL(packet.getUrl());
                if (url.getProtocol().equals("https") || url.getProtocol().equals("http"))
                    minecraft.getDownloadedPackSource().downloadAndSelectResourcePack(url, packet.getHash(), true);
            } catch (java.net.MalformedURLException exception) { Flashback.LOGGER.warn("Invalid replay resource pack URL", exception); }
            ci.cancel();
        }
    }
}
