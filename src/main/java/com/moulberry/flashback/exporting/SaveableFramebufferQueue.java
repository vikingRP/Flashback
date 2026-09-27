package com.moulberry.flashback.exporting;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.visuals.ShaderManager;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Objects;
import static org.lwjgl.opengl.GL33.*;

public final class SaveableFramebufferQueue implements AutoCloseable {
    private final int width, height;
    private final ArrayDeque<SaveableFramebuffer> available = new ArrayDeque<>();
    private final ArrayDeque<SaveableFramebuffer> waiting = new ArrayDeque<>();
    private final int flipBuffer, flipDepthBuffer, flipFramebuffer, flipDepthFramebuffer;
    private final TransformDepthUniform transformDepthUniform = new TransformDepthUniform();

    public SaveableFramebufferQueue(int width, int height) {
        this.width = width;
        this.height = height;
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            flipBuffer = createTexture(GL_RGBA8, GL_RGBA, GL_UNSIGNED_BYTE);
            flipFramebuffer = createFramebuffer(flipBuffer);
            flipDepthBuffer = createTexture(GL_R32F, GL_RED, GL_FLOAT);
            flipDepthFramebuffer = createFramebuffer(flipDepthBuffer);
        } finally {
            glBindTexture(GL_TEXTURE_2D, previousTexture);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private int createTexture(int internalFormat, int format, int type) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, 0L);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    private int createFramebuffer(int texture) {
        int framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Flashback export framebuffer is incomplete");
        return framebuffer;
    }

    public SaveableFramebuffer take() {
        return available.isEmpty() ? new SaveableFramebuffer(width, height) : available.removeFirst();
    }

    public void startDownload(RenderTarget target, SaveableFramebuffer texture, boolean supersampling) {
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindTexture(GL_TEXTURE_2D, target.getColorTextureId());
        int min = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER);
        int mag = glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER);
        try {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, supersampling ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, supersampling ? GL_LINEAR : GL_NEAREST);
            ShaderManager.blit(target.getColorTextureId(), flipFramebuffer, width, height, 0, 0, 1, 1, false, true, false, 0, 0);
        } finally {
            glBindTexture(GL_TEXTURE_2D, target.getColorTextureId());
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, min);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mag);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
        }
        texture.startDownload(flipFramebuffer, false);
        waiting.add(texture);
    }

    public void startDepthDownload(RenderTarget target, SaveableFramebuffer texture) {
        var parameters = transformDepthUniform.getOrUpdate(ReplayUI.lastProjectionMatrix);
        ShaderManager.blit(target.getDepthTextureId(), flipDepthFramebuffer, width, height, 0, 0, 1, 1,
            false, true, false, parameters.near(), parameters.far());
        texture.startDownload(flipDepthFramebuffer, true);
        waiting.add(texture);
    }

    public @Nullable ImageFrame finishDownload() {
        SaveableFramebuffer first = this.waiting.peekFirst();
        if (first == null) {
            return null;
        }

        ImageFrame downloaded = first.finishDownload();

        if (downloaded == null) {
            return null;
        }

        downloaded.audioBuffer = first.audioBuffer;

        SaveableFramebuffer popped = this.waiting.removeFirst();
        popped.audioBuffer = null;
        this.available.add(popped);

        return downloaded;
    }


    public @Nullable ImageFrame[] finishDownloadMultiple(int n) {
        if (this.waiting.size() < n) {
            return null;
        }

        var iterator = this.waiting.iterator();
        for (int i = 0; i < n; i++) {
            var saveableFramebuffer = iterator.next();
            if (!saveableFramebuffer.canFinishDownload()) {
                return null;
            }
        }

        ImageFrame[] downloads = new ImageFrame[n];
        for (int i = 0; i < n; i++) {
            SaveableFramebuffer next = Objects.requireNonNull(this.waiting.removeFirst());
            ImageFrame downloaded = Objects.requireNonNull(next.finishDownload());

            downloaded.audioBuffer = next.audioBuffer;

            downloads[i] = downloaded;
            next.audioBuffer = null;
            this.available.add(next);
        }

        return downloads;
    }

    public boolean isEmpty() {
        return this.waiting.isEmpty();
    }

    public int pendingCount() {
        return this.waiting.size();
    }

    @Override
    public void close() {
        for (SaveableFramebuffer texture : this.waiting) {
            texture.close();
        }
        for (SaveableFramebuffer texture : this.available) {
            texture.close();
        }
        this.waiting.clear();
        this.available.clear();
        glDeleteTextures(flipBuffer); glDeleteTextures(flipDepthBuffer); glDeleteFramebuffers(flipFramebuffer); glDeleteFramebuffers(flipDepthFramebuffer);
        this.transformDepthUniform.close();
    }


}
