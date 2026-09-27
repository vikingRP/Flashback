package com.moulberry.flashback.mixin.playback;

import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientPacketListener.class, priority = 1100)
public class MixinClientPacketListener {

    /*
     * When in a replay, sometimes the replay will have recorded a packet which opens a screen on the client
     * This doesn't happen in vanilla, but it can happen with mods (e.g. Traveler's Backpack)
     * This code will essentially prevent a custom payload from opening a screen while in a replay
     */

    @Unique
    private Screen screenBeforeHandleCustomPayload = null;

    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    public void handleCustomPayloadHead(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        // Forge dispatches its own channels on Netty before vanilla's main-thread guard.
        // Recorded payloads are replayed on the client thread, where their UI can be restored safely.
        if (Flashback.isInReplay() && minecraft.isSameThread()) {
            this.screenBeforeHandleCustomPayload = minecraft.screen;
        }
    }

    @Inject(method = "handleCustomPayload", at = @At("RETURN"))
    public void handleCustomPayloadReturn(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Flashback.isInReplay() && minecraft.isSameThread()
            && minecraft.screen != this.screenBeforeHandleCustomPayload) {
            minecraft.setScreen(this.screenBeforeHandleCustomPayload);
        }
    }

}
