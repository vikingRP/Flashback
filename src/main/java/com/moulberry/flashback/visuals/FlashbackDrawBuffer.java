package com.moulberry.flashback.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;

/** Retained GPU geometry for editor camera paths. */
public final class FlashbackDrawBuffer implements AutoCloseable {
    private final VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
    public FlashbackDrawBuffer() {}
    public void upload(BufferBuilder.RenderedBuffer data) {
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
    }
    public VertexBuffer getVertexBuffer() { return buffer; }
    public void drawRenderType(RenderType type) {
        type.setupRenderState();
        try {
            buffer.bind();
            buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        } finally {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
    }
    public void draw() { drawRenderType(RenderType.lines()); }
    public void close() { buffer.close(); }
}
