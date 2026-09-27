package com.moulberry.flashback.mixin.replay_server;
import com.moulberry.flashback.Flashback;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;
/** Replay chunks supply recorded light data, so generation must not overwrite it. */
@Mixin(ThreadedLevelLightEngine.class)
public abstract class MixinChunkStatusTasks {
    @Inject(method={"initializeLight", "lightChunk"}, at=@At("HEAD"), cancellable=true)
    private void recordedLight(ChunkAccess chunk, boolean alreadyLit, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (Flashback.isInReplay()) cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}
