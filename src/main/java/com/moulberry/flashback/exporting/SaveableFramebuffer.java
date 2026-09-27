package com.moulberry.flashback.exporting;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL32.*;

/** An asynchronous OpenGL pixel-pack transfer; the mapped storage never escapes the frame copy. */
public final class SaveableFramebuffer implements AutoCloseable {
    public @Nullable FloatBuffer audioBuffer;
    private final int width, height;
    private int pbo;
    private long fence;
    private ImageFrame.Format format;

    public SaveableFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        int previous = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        pbo = glGenBuffers();
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        glBufferData(GL_PIXEL_PACK_BUFFER, 4L * width * height, GL_STREAM_READ);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, previous);
    }

    public void startDownload(int framebuffer, boolean depth) {
        if (fence != 0 || pbo == 0) throw new IllegalStateException("Framebuffer transfer already pending or closed");
        format = depth ? ImageFrame.Format.GRAY_F32 : ImageFrame.Format.RGBA_U8;
        int previousBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int previousFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glPixelStorei(GL_PACK_SKIP_ROWS, 0);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
            glReadPixels(0, 0, width, height, depth ? GL_RED : GL_RGBA, depth ? GL_FLOAT : GL_UNSIGNED_BYTE, 0L);
            fence = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        } finally {
            glPixelStorei(GL_PACK_ALIGNMENT, alignment);
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
            glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
            glBindBuffer(GL_PIXEL_PACK_BUFFER, previousBuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, previousFramebuffer);
        }
    }

    public boolean canFinishDownload() {
        if (fence == 0) throw new IllegalStateException("No framebuffer transfer pending");
        int status = glClientWaitSync(fence, GL_SYNC_FLUSH_COMMANDS_BIT, 0);
        if (status == GL_WAIT_FAILED) throw new IllegalStateException("OpenGL framebuffer transfer failed");
        return status == GL_ALREADY_SIGNALED || status == GL_CONDITION_SATISFIED;
    }

    public @Nullable ImageFrame finishDownload() {
        if (!canFinishDownload()) return null;
        int previous = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo);
        try {
            ByteBuffer mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, 4L * width * height, GL_MAP_READ_BIT);
            if (mapped == null) throw new IllegalStateException("Failed to map framebuffer transfer");
            try {
                return new ImageFrame(MemoryUtil.memAddress(mapped), width, height, format);
            } finally {
                glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
                glDeleteSync(fence);
                fence = 0;
            }
        } finally {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, previous);
        }
    }

    public void close() {
        if (fence != 0) { glDeleteSync(fence); fence = 0; }
        if (pbo != 0) { glDeleteBuffers(pbo); pbo = 0; }
    }
}
