package com.moulberry.flashback;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

public class CachedChunkPacket {
    private static final VarHandle LONG_ARRAY = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
    private final int x;
    private final int z;
    private final byte[] bigHash;
    public final long longHashCode;
    public int index;

    public CachedChunkPacket(ClientboundLevelChunkWithLightPacket packet, int index) {
        //keysmash random numbers used
        this.x = packet.getX();
        this.z = packet.getZ();
        this.bigHash = computePacketBigHash(packet);
        if (this.bigHash.length == 64) {//sha-512
            long hash = 982374698276290847L;
            for (int i = 0; i < 8; i++) {
                hash ^= (long)LONG_ARRAY.get(this.bigHash, i * Long.BYTES);
                hash *= 209648290153981L;
                hash += 164923702968709L;
            }
            this.longHashCode = hash;
        } else if (this.bigHash.length == 32) {//sha-256
            long hash = 150939871908751L;
            for (int i = 0; i < 4; i++) {
                hash ^= (long)LONG_ARRAY.get(this.bigHash, i * Long.BYTES);
                hash *= 209648290153981L;
                hash += 164923702968709L;
            }
            this.longHashCode = hash;
        } else {//Something went horrifically wrong
            this.longHashCode = Arrays.hashCode(this.bigHash);
        }
        this.index = index;
    }

    private static byte[] computePacketBigHash(ClientboundLevelChunkWithLightPacket packet) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e2) {
                throw new RuntimeException(e2);
            }
        }

        digest.update(intToByteArray(packet.getX()));
        digest.update(intToByteArray(packet.getZ()));
        FriendlyByteBuf sectionBuffer = packet.getChunkData().getReadBuffer();
        try { digest.update(sectionBuffer.nioBuffer()); }
        finally { sectionBuffer.release(); }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.getLightData().write(buffer);
            buffer.writeNbt(packet.getChunkData().getHeightmaps());
            digest.update(buffer.nioBuffer(0, buffer.writerIndex()));
            buffer.clear();

            record BlockEntitySnapshot(net.minecraft.core.BlockPos position, BlockEntityType<?> type, net.minecraft.nbt.CompoundTag tag) {}
            var blockEntities = new ArrayList<BlockEntitySnapshot>();
            packet.getChunkData().getBlockEntitiesTagsConsumer(packet.getX(), packet.getZ()).accept((position, type, tag) ->
                blockEntities.add(new BlockEntitySnapshot(position.immutable(), type, tag)));
            blockEntities.sort(Comparator.comparingLong(value -> value.position().asLong()));
            for (BlockEntitySnapshot blockEntity : blockEntities) {
                buffer.writeBlockPos(blockEntity.position());
                buffer.writeResourceLocation(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type()));
                buffer.writeNbt(blockEntity.tag());
                digest.update(buffer.nioBuffer(0, buffer.writerIndex()));
                buffer.clear();
            }
        } finally {
            buffer.release();
        }

        return digest.digest();
    }

    private static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }

    @Override
    public int hashCode() {
        return (int) this.longHashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CachedChunkPacket that)) return false;
        if (this.longHashCode != that.longHashCode) {
            return false;
        }
        if (this.x != that.x || this.z != that.z) {
            return false;
        }

        return Arrays.equals(this.bigHash, that.bigHash);
    }
}
