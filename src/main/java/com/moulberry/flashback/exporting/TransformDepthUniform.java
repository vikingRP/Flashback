package com.moulberry.flashback.exporting;

import org.joml.Matrix4fc;

/** Perspective depth parameters for Minecraft 1.20.1's OpenGL [-1, 1] depth convention. */
public final class TransformDepthUniform implements AutoCloseable {
    public record Parameters(float near, float far) {}
    private Parameters parameters;

    public Parameters getOrUpdate(Matrix4fc projection) {
        float near = projection.perspectiveNear();
        float far = projection.perspectiveFar();
        if (parameters == null || parameters.near() != near || parameters.far() != far)
            parameters = new Parameters(near, far);
        return parameters;
    }
    public void close() { parameters = null; }
}
