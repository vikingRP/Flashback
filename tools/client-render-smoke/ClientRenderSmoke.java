import com.moulberry.flashback.visuals.ShaderManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33.*;

/** Executes the production compositor on a hidden real OpenGL context. */
public final class ClientRenderSmoke {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.0001f, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        require(GLFW.glfwInit(), "GLFW initialization");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(32, 32, "Flashback renderer validation", 0, 0);
        require(window != 0, "Hidden OpenGL context");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int source = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, source);
            float[] pixels = {1,0,0,.25f, 0,1,0,0, 0,0,1,1, 1,1,1,.5f};
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 2, 2, 0, GL_RGBA, GL_FLOAT, pixels);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            int target = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA32F, 2, 2, 0, GL_RGBA, GL_FLOAT, 0L);
            int fbo = glGenFramebuffers();
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
            require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Framebuffer completeness");
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(3, 4, 11, 12);
            glEnable(GL_SCISSOR_TEST);
            glEnable(GL_DEPTH_TEST);
            glScissor(0, 0, 1, 1);
            float[] actual = new float[16];

            ShaderManager.blit(source, fbo, 2, 2, 0, 0, 1, 1, false, false, false, 0, 0);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
            glReadPixels(0, 0, 2, 2, GL_RGBA, GL_FLOAT, actual);
            for (int i = 0; i < 16; i++) near(actual[i], pixels[i], "RGBA copy channel " + i);
            require(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) == 0, "Draw framebuffer restoration");
            require(glIsEnabled(GL_DEPTH_TEST) && glIsEnabled(GL_SCISSOR_TEST), "Depth and scissor restoration");
            int[] viewport = new int[4]; glGetIntegerv(GL_VIEWPORT, viewport);
            require(java.util.Arrays.equals(viewport, new int[]{3,4,11,12}), "Viewport restoration");

            ShaderManager.blit(source, fbo, 2, 2, 0, 0, 1, 1, false, true, false, 0, 0);
            glReadPixels(0, 0, 2, 2, GL_RGBA, GL_FLOAT, actual);
            for (int i = 0; i < 16; i++) near(actual[i], pixels[(i + 8) % 16], "Vertical flip channel " + i);

            ShaderManager.blit(source, fbo, 2, 2, 0, 0, 1, 1, false, false, true, 0, 0);
            glReadPixels(0, 0, 2, 2, GL_RGBA, GL_FLOAT, actual);
            near(actual[3], 1, "Fractional alpha normalization");
            near(actual[7], 0, "Transparent alpha preservation");
            near(actual[15], 1, "Half alpha normalization");

            java.util.Arrays.fill(pixels, .5f);
            glBindTexture(GL_TEXTURE_2D, source);
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, 2, 2, GL_RGBA, GL_FLOAT, pixels);
            ShaderManager.blit(source, fbo, 2, 2, 0, 0, 1, 1, false, false, false, .1f, 100f);
            glReadPixels(0, 0, 2, 2, GL_RGBA, GL_FLOAT, actual);
            near(actual[0], .2f / 100.1f, "Perspective depth normalization");
            require(glGetError() == GL_NO_ERROR, "OpenGL error state");
            System.out.println("PASS: RGBA copy, vertical flip, alpha normalization, perspective depth, GL state restoration");
            System.out.println("GPU: " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
            glDeleteFramebuffers(fbo);
            glDeleteTextures(source);
            glDeleteTextures(target);
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
