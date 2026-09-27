package com.moulberry.flashback.mixin.record;
import com.moulberry.flashback.record.RecordedResourcePack;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public abstract class MixinRecordedResourcePack {
    @Inject(method="handleResourcePack", at=@At("HEAD"))
    private void rememberPack(ClientboundResourcePackPacket packet, CallbackInfo ci) { RecordedResourcePack.set(packet); }
    @Inject(method="onDisconnect", at=@At("HEAD"))
    private void forgetPack(Component reason, CallbackInfo ci) { RecordedResourcePack.clear(); }
}
