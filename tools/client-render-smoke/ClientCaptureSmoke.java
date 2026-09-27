import com.moulberry.flashback.exporting.ImageFrame;
import com.moulberry.flashback.exporting.SaveableFramebuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33.*;

/** Checks production PBO transfers, reuse and state restoration on a hidden OpenGL context. */
public final class ClientCaptureSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static ImageFrame await(SaveableFramebuffer capture) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        ImageFrame image;
        while ((image = capture.finishDownload()) == null) {
            if (System.nanoTime() > deadline) throw new AssertionError("Pixel transfer timed out");
            java.util.concurrent.locks.LockSupport.parkNanos(100_000L);
        }
        return image;
    }
    public static void main(String[] args) {
        require(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(32, 32, "Flashback PBO validation", 0, 0);
        require(window != 0, "Hidden OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 3, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            int fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Framebuffer completeness");
            glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 8);
            glPixelStorei(GL_PACK_ROW_LENGTH, 17);
            glPixelStorei(GL_PACK_SKIP_ROWS, 2);
            glPixelStorei(GL_PACK_SKIP_PIXELS, 3);
            int previousBuffer = glGenBuffers();
            glBindBuffer(GL_PIXEL_PACK_BUFFER, previousBuffer);
            glBufferData(GL_PIXEL_PACK_BUFFER, 8192, GL_STREAM_READ);

            try (SaveableFramebuffer capture = new SaveableFramebuffer(4, 3)) {
                require(glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) == previousBuffer, "Constructor pack buffer restoration");
                for (int cycle = 0; cycle < 20; cycle++) {
                    java.nio.ByteBuffer upload = MemoryUtil.memAlloc(4 * 3 * 4);
                    try {
                        for (int pixel = 0; pixel < 12; pixel++) upload.put((byte) cycle).put((byte) 128).put((byte) 255).put((byte) 64);
                        upload.flip();
                        glBindTexture(GL_TEXTURE_2D, texture);
                        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 4, 3, GL_RGBA, GL_UNSIGNED_BYTE, upload);
                    } finally { MemoryUtil.memFree(upload); }
                    capture.startDownload(fbo, false);
                    require(glGetInteger(GL_READ_FRAMEBUFFER_BINDING) == 0, "Read framebuffer restoration");
                    require(glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) == previousBuffer, "Pack buffer restoration");
                    require(glGetInteger(GL_PACK_ROW_LENGTH) == 17 && glGetInteger(GL_PACK_ALIGNMENT) == 8
                        && glGetInteger(GL_PACK_SKIP_ROWS) == 2 && glGetInteger(GL_PACK_SKIP_PIXELS) == 3, "Pixel pack layout restoration");
                    boolean rejected = false;
                    try { capture.startDownload(fbo, false); } catch (IllegalStateException expected) { rejected = true; }
                    require(rejected, "Reject overlapping capture");
                    try (ImageFrame frame = await(capture)) {
                        require(frame.width == 4 && frame.height == 3 && frame.format == ImageFrame.Format.RGBA_U8, "RGBA frame metadata");
                        for (int pixel = 0; pixel < 12; pixel++) {
                            long address = frame.pixels + pixel * 4L;
                            require((MemoryUtil.memGetByte(address) & 255) == cycle, "Capture reuse / red channel");
                            require((MemoryUtil.memGetByte(address + 1) & 255) == 128, "Green channel: " + (MemoryUtil.memGetByte(address + 1) & 255));
                            require((MemoryUtil.memGetByte(address + 2) & 255) == 255, "Blue channel");
                            require((MemoryUtil.memGetByte(address + 3) & 255) == 64, "Alpha channel");
                        }
                    }
                }
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, 4, 3, 0, GL_RED, GL_FLOAT, 0L);
                glClearColor(.375f, 0, 0, 1);
                glClear(GL_COLOR_BUFFER_BIT);
                capture.startDownload(fbo, true);
                try (ImageFrame frame = await(capture)) {
                    require(frame.format == ImageFrame.Format.GRAY_F32, "Depth frame format");
                    for (int pixel = 0; pixel < 12; pixel++)
                        require(Math.abs(MemoryUtil.memGetFloat(frame.pixels + pixel * 4L) - .375f) < .00001f, "Float depth transfer");
                }
            }
            require(glGetError() == GL_NO_ERROR, "OpenGL error state");
            System.out.println("PASS: PBO RGBA capture, 20 consecutive reuses, float depth, overlap rejection, pack and framebuffer restoration");
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glDeleteBuffers(previousBuffer);
            glDeleteFramebuffers(fbo);
            glDeleteTextures(texture);
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
