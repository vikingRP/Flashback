package com.moulberry.flashback.mixin.replay_server;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.Flashback;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerImpl {
    @WrapWithCondition(method="onDisconnect", at=@At(value="INVOKE", target="Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap=false))
    private boolean replayDisconnectLog(Logger logger, String message, Object first, Object second) { return !Flashback.isInReplay(); }
}
