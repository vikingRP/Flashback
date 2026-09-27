package com.moulberry.flashback.record;
import net.minecraft.network.protocol.game.ClientboundResourcePackPacket;
public final class RecordedResourcePack {
    private static volatile ClientboundResourcePackPacket current;
    public static ClientboundResourcePackPacket current() { return current; }
    public static void set(ClientboundResourcePackPacket packet) { current = packet; }
    public static void clear() { current = null; }
}
