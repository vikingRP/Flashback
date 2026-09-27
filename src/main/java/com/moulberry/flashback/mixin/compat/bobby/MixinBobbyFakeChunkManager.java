package com.moulberry.flashback.mixin.compat.bobby;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.mixinconstraints.annotations.IfModLoaded;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@IfModLoaded("bobby")
@Pseudo
@Mixin(targets = "de.johni0702.minecraft.bobby.FakeChunkManager", remap = false)
public class MixinBobbyFakeChunkManager {

    @Inject(method = "getCurrentWorldOrServerName", require = 0, at = @At("HEAD"), cancellable = true)
    private static void getCurrentWorldOrServerName(ClientPacketListener networkHandler, CallbackInfoReturnable<String> cir) {
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer != null) {
            FlashbackMeta meta = replayServer.getMetadata();
            if (meta != null && meta.bobbyWorldName != null) {
                Flashback.LOGGER.info("Overriding Bobby World Name: {}", meta.bobbyWorldName);
                cir.setReturnValue(meta.bobbyWorldName);
            } else {
                cir.setReturnValue("unknown_flashback");
            }
        }
    }

    @Inject(method = "save", at = @At("HEAD"), require = 0, cancellable = true)
    public void save(LevelChunk chunk, CallbackInfoReturnable<Supplier<LevelChunk>> cir) {
        if (Flashback.isInReplay()) {
            try {
                Class<?> serializer = Class.forName("de.johni0702.minecraft.bobby.ChunkSerializer");
                Pair<?, ?> copy = (Pair<?, ?>) serializer.getMethod("shallowCopy", LevelChunk.class).invoke(null, chunk);
                @SuppressWarnings("unchecked") Supplier<LevelChunk> supplier = (Supplier<LevelChunk>) copy.getRight();
                cir.setReturnValue(supplier);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Bobby chunk serializer is incompatible with replay playback", e);
            }
        }
    }

}
