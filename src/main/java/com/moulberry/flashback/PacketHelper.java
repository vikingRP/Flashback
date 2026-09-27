package com.moulberry.flashback;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;

public final class PacketHelper {
    public static boolean shouldIgnoreEntity(Entity entity) {
        return entity == null || entity.isRemoved() || entity instanceof EnderDragonPart || entity.getType().clientTrackingRange() <= 0;
    }
    public static Packet<ClientGamePacketListener> createTeleportForUnknown(int id, double x, double y, double z, byte yRot, byte xRot, boolean onGround) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(id);
            buffer.writeDouble(x); buffer.writeDouble(y); buffer.writeDouble(z);
            buffer.writeByte(yRot); buffer.writeByte(xRot); buffer.writeBoolean(onGround);
            return new ClientboundTeleportEntityPacket(buffer);
        } finally { buffer.release(); }
    }
    public static Packet<ClientGamePacketListener> createAddEntity(Entity entity) {
        try { return entity.getAddEntityPacket(); }
        catch (Exception exception) { return createAddEntity(entity, 0); }
    }
    public static ClientboundAddEntityPacket createAddEntity(Entity entity, int data) {
        return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
            entity.getXRot(), entity.getYRot(), entity.getType(), data, entity.getDeltaMovement(), entity.getYHeadRot());
    }
}
