import com.moulberry.flashback.CachedChunkPacket;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.io.ReplayBuffer;
import com.moulberry.flashback.io.ReplayReader;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.packet.FlashbackTickRate;
import com.moulberry.flashback.packet.PlayPacketCodec;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.playback.ReplayTickRateManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.BitSet;

public class ReplayProtocolSelfTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    private static final class ProbeAction implements Action {
        int decoded;
        public ResourceLocation name() { return new ResourceLocation("test", "probe"); }
        public void handle(ReplayServer ignored, ReplayBuffer buffer) { decoded = buffer.readVarInt(); }
    }
    public static void main(String[] args) throws Exception {
        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FlashbackTickRate message = new FlashbackTickRate(59.94f, true);
            FlashbackTickRate.CODEC.encode(packetBuffer, message);
            check(message.equals(FlashbackTickRate.CODEC.decode(packetBuffer)), "Tick rate payload must round-trip exactly");
            check(!packetBuffer.isReadable(), "Tick rate decoder must consume its full frame");
            packetBuffer.clear();
            var time = new ClientboundSetTimePacket(234567L, 12000L, false);
            PlayPacketCodec.INSTANCE.encode(packetBuffer, time);
            var decoded = (ClientboundSetTimePacket)PlayPacketCodec.INSTANCE.decode(packetBuffer);
            check(decoded.getGameTime() == 234567L && decoded.getDayTime() == -12000L, "Protocol 763 time packet loses stopped-clock state");
            check(!packetBuffer.isReadable(), "Vanilla decoder must consume its full frame");
        } finally { packetBuffer.release(); }

        FriendlyByteBuf originalPayload = new FriendlyByteBuf(Unpooled.buffer());
        originalPayload.writeLong(0x0102030405060708L);
        var ownedPayload = new net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket(new ResourceLocation("test", "payload"), originalPayload);
        var recordedPayload = com.moulberry.flashback.packet.RecordedCustomPayload.copyOf(ownedPayload);
        originalPayload.release();
        FriendlyByteBuf encodedPayload = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PlayPacketCodec.INSTANCE.encode(encodedPayload, recordedPayload);
            check(encodedPayload.readVarInt() == PlayPacketCodec.customPayloadPacketId(), "Deferred custom payload must retain vanilla protocol ID");
            check(encodedPayload.readResourceLocation().equals(new ResourceLocation("test", "payload")), "Deferred custom payload must retain channel");
            check(encodedPayload.readLong() == 0x0102030405060708L && !encodedPayload.isReadable(), "Custom payload bytes must survive release of Forge's original buffer");
        } finally { encodedPayload.release(); }

        var clock = new ReplayTickRateManager(null);
        clock.setTickRate(40);
        check(clock.nanosecondsPerTick() == 25_000_000L, "Tick duration must match requested playback speed");
        clock.setFrozen(true); clock.tick();
        check(!clock.runsNormally(), "Freeze must suppress game ticks");
        clock.setFrozenTicksToRun(2); clock.tick(); check(clock.runsNormally(), "Freeze easing first tick missing");
        clock.tick(); check(clock.runsNormally(), "Freeze easing second tick missing");
        clock.tick(); check(!clock.runsNormally(), "Freeze easing must expire after requested ticks");
        boolean rejected = false;
        try { clock.setTickRate(Float.NaN); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Nonfinite replay speed must be rejected");

        ProbeAction action = new ProbeAction();
        ActionRegistry.register(action);
        ReplayWriter writer = new ReplayWriter(null);
        writer.startSnapshot(); writer.startAction(action); writer.friendlyByteBuf().writeVarInt(7); writer.finishAction(action); writer.endSnapshot();
        writer.startAction(action); writer.friendlyByteBuf().writeVarInt(123456); writer.finishAction(action);
        var bytes = Unpooled.wrappedBuffer(writer.popBytes());
        try {
            ReplayReader reader = new ReplayReader(bytes, null);
            check(reader.isAtStart(), "Replay must begin after snapshot framing");
            check(reader.handleNextAction(null) && action.decoded == 123456, "Recorded action must survive writer/reader round-trip");
            check(!reader.handleNextAction(null), "Replay must end exactly at last action");
            reader.resetToStart();
            check(reader.handleNextAction(null) && action.decoded == 123456, "Seeking back must replay the same action");
        } finally { bytes.release(); writer.friendlyByteBuf().release(); }

        var chunkA = new CachedChunkPacket(chunk(1), 0);
        var chunkB = new CachedChunkPacket(chunk(1), 1);
        var differentHeightmap = new CachedChunkPacket(chunk(2), 2);
        check(chunkA.equals(chunkB) && chunkA.hashCode() == chunkB.hashCode(), "Identical chunk content must deduplicate regardless of cache index");
        check(!chunkA.equals(differentHeightmap), "Different heightmaps must never alias in chunk cache");
        java.nio.file.Path cleanupRoot = java.nio.file.Files.createTempDirectory("flashback-cleanup-test-");
        java.nio.file.Path world = java.nio.file.Files.createDirectories(cleanupRoot.resolve("saves/replay"));
        java.nio.file.Path configs = java.nio.file.Files.createDirectory(world.resolve("serverconfig"));
        java.nio.file.Path region = java.nio.file.Files.createDirectory(world.resolve("region"));
        java.nio.file.Path config = java.nio.file.Files.writeString(configs.resolve("forge-server.toml"), "# Forge handshake config");
        java.nio.file.Path sessionLock = java.nio.file.Files.writeString(world.resolve("session.lock"), "lock");
        java.nio.file.Path pid = java.nio.file.Files.writeString(cleanupRoot.resolve("flashback_pid"), "123");
        java.nio.file.Path chunkFile = java.nio.file.Files.writeString(region.resolve("r.0.0.mca"), "old chunk");
        try {
            com.moulberry.flashback.io.ReplayTempCleanup.clearSavedData(cleanupRoot);
            check(java.nio.file.Files.readString(config).equals("# Forge handshake config"), "Snapshot cleanup must retain Forge configs for the subsequent LOGIN handshake");
            check(java.nio.file.Files.exists(sessionLock) && java.nio.file.Files.exists(pid), "Snapshot cleanup must retain world and process locks");
            check(!java.nio.file.Files.exists(chunkFile) && java.nio.file.Files.isDirectory(region), "Snapshot cleanup must remove old world data while retaining directories");
        } finally {
            java.nio.file.Files.deleteIfExists(config); java.nio.file.Files.deleteIfExists(sessionLock);
            java.nio.file.Files.deleteIfExists(pid); java.nio.file.Files.deleteIfExists(chunkFile);
            java.nio.file.Files.deleteIfExists(configs); java.nio.file.Files.deleteIfExists(region);
            java.nio.file.Files.deleteIfExists(world); java.nio.file.Files.deleteIfExists(world.getParent());
            java.nio.file.Files.deleteIfExists(cleanupRoot);
        }
        System.out.println("PASS: " + assertions + " replay protocol, framing, clock, chunk-cache and cleanup assertions");
    }
    private static ClientboundLevelChunkWithLightPacket chunk(int height) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(4); buffer.writeInt(-9);
            CompoundTag heightmap = new CompoundTag(); heightmap.putLongArray("MOTION_BLOCKING", new long[]{height});
            buffer.writeNbt(heightmap); buffer.writeVarInt(0); buffer.writeVarInt(0);
            for (int i=0;i<4;i++) buffer.writeBitSet(new BitSet());
            buffer.writeVarInt(0); buffer.writeVarInt(0);
            return new ClientboundLevelChunkWithLightPacket(buffer);
        } finally { buffer.release(); }
    }
}
