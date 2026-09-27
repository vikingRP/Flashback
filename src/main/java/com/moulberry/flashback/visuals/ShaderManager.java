package com.moulberry.flashback.visuals;

import static org.lwjgl.opengl.GL33.*;

/** Small OpenGL compositor used by the editor, transparent exports and depth exports. */
public final class ShaderManager {
    private static int program;
    private static int vao;

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String message = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Flashback shader compilation failed: " + message);
        }
        return shader;
    }

    private static void init() {
        if (program != 0) return;
        int vertex = compile(GL_VERTEX_SHADER, """
            #version 150
            out vec2 texCoord;
            uniform int Flip;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                texCoord = vec2(p.x, Flip != 0 ? 1.0 - p.y : p.y);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """);
        int fragment = compile(GL_FRAGMENT_SHADER, """
            #version 150
            uniform sampler2D InSampler;
            uniform int Mode;
            uniform float Near;
            uniform float Far;
            in vec2 texCoord;
            out vec4 fragColor;
            void main() {
                vec4 colour = texture(InSampler, texCoord);
                if (Mode == 1 && colour.a > 0.0) colour.a = 1.0;
                if (Mode == 2) {
                    float z = colour.r * 2.0 - 1.0;
                    float linear = 2.0 * Near * Far / (Far + Near - z * (Far - Near));
                    colour = vec4(linear / Far, 0.0, 0.0, 1.0);
                }
                fragColor = colour;
            }
            """);
        int result = glCreateProgram();
        glAttachShader(result, vertex);
        glAttachShader(result, fragment);
        glBindFragDataLocation(result, 0, "fragColor");
        glLinkProgram(result);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(result, GL_LINK_STATUS) == GL_FALSE) {
            String message = glGetProgramInfoLog(result);
            glDeleteProgram(result);
            throw new IllegalStateException("Flashback shader link failed: " + message);
        }
        program = result;
        vao = glGenVertexArrays();
    }

    public static void blit(int texture, int framebuffer, int width, int height,
                            float x1, float y1, float x2, float y2, boolean blend,
                            boolean flip, boolean roundAlpha, float near, float far) {
        init();
        int oldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, viewport);
        boolean oldDepth = glIsEnabled(GL_DEPTH_TEST);
        boolean oldCull = glIsEnabled(GL_CULL_FACE);
        boolean oldScissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean oldBlend = glIsEnabled(GL_BLEND);
        int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glActiveTexture(GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int oldSampler = glGetInteger(GL_SAMPLER_BINDING);
        try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glViewport(Math.round(x1 * width), Math.round((1 - y2) * height),
                Math.round((x2 - x1) * width), Math.round((y2 - y1) * height));
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            if (blend) {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            } else glDisable(GL_BLEND);
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "InSampler"), 0);
            glUniform1i(glGetUniformLocation(program, "Flip"), flip ? 1 : 0);
            glUniform1i(glGetUniformLocation(program, "Mode"), near > 0 ? 2 : roundAlpha ? 1 : 0);
            glUniform1f(glGetUniformLocation(program, "Near"), near);
            glUniform1f(glGetUniformLocation(program, "Far"), far);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindSampler(0, 0);
            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            glBindVertexArray(oldVao);
            glUseProgram(oldProgram);
            glBindTexture(GL_TEXTURE_2D, oldTexture);
            glBindSampler(0, oldSampler);
            glActiveTexture(oldActive);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldFramebuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            setEnabled(GL_DEPTH_TEST, oldDepth);
            setEnabled(GL_CULL_FACE, oldCull);
            setEnabled(GL_SCISSOR_TEST, oldScissor);
            setEnabled(GL_BLEND, oldBlend);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) glEnable(capability); else glDisable(capability);
    }
}
