package com.moulberry.flashback.compat;

import com.moulberry.flashback.Flashback;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.List;

/** Optional Bobby bridge; Forge does not ship Bobby or its Fabric classes. */
public final class BobbyUtil {
    public static void addBobbyChunks(ClientChunkCache cache, List<LevelChunk> chunks, LongOpenHashSet seen) {
        try {
            Object manager = cache.getClass().getMethod("bobby_getFakeChunkManager").invoke(cache);
            if (manager == null) return;
            Iterable<?> fakeChunks = (Iterable<?>) manager.getClass().getMethod("getFakeChunks").invoke(manager);
            for (Object candidate : fakeChunks) {
                if (candidate instanceof LevelChunk chunk && seen.add(chunk.getPos().toLong())) chunks.add(chunk);
            }
        } catch (ReflectiveOperationException | ClassCastException error) {
            throw new IllegalStateException("Installed Bobby version is incompatible with Flashback chunk capture", error);
        }
    }
}
