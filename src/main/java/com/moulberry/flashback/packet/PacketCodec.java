package com.moulberry.flashback.packet;

/** Bidirectional packet serializer independent of Minecraft's post-1.20 codec API. */
public interface PacketCodec<B, T> {
    T decode(B buffer);
    void encode(B buffer, T value);

    static <B, T> PacketCodec<B, T> unit(T value) {
        return new PacketCodec<>() {
            public T decode(B buffer) { return value; }
            public void encode(B buffer, T encoded) {
                if (encoded != value) throw new IllegalArgumentException("Unexpected unit packet");
            }
        };
    }
}
