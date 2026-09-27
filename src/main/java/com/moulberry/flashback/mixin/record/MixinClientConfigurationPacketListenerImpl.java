package com.moulberry.flashback.mixin.record;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Registry synchronization occurs during Login in 1.20.1. */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientConfigurationPacketListenerImpl {
    @Inject(method="handleLogin", at=@At("RETURN"))
    private void updateRecordingRegistries(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (Flashback.RECORDER != null) Flashback.RECORDER.setRegistryAccess(((ClientPacketListener)(Object)this).registryAccess());
    }
}
