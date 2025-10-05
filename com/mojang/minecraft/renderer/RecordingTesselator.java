// com/mojang/minecraft/renderer/RecordingTesselator.java
package com.mojang.minecraft.renderer;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

public final class RecordingTesselator {
    private static final int MAX_FLOATS = 524288; // match original
    private final FloatBuffer buffer = BufferUtils.createFloatBuffer(MAX_FLOATS);
    private final float[] array = new float[MAX_FLOATS];

    private int vertices = 0, len = 3, p = 0;
    private float u, v, r = 1, g = 1, b = 1;
    private boolean hasColor = false, hasTexture = false, noColor = false;

    // mirror Tesselator.init()
    public void init() {
        vertices = 0; p = 0; len = 3;
        hasColor = false; hasTexture = false; noColor = false;
        buffer.clear();
    }

    // mirror Tesselator.tex()
    public void tex(float u, float v) {
        if (!hasTexture) len += 2;
        hasTexture = true;
        this.u = u; this.v = v;
    }

    // mirror Tesselator.color()
    public void color(float r, float g, float b) {
        if (noColor) return;
        if (!hasColor) len += 3;
        hasColor = true;
        this.r = r; this.g = g; this.b = b;
    }

    public void color(int c) {
        float r = ((c >> 16) & 255) / 255f;
        float g = ((c >> 8)  & 255) / 255f;
        float b = ( c        & 255) / 255f;
        color(r, g, b);
    }

    public void noColor() { this.noColor = true; }

    public void vertexUV(float x, float y, float z, float u, float v) {
        tex(u, v);
        vertex(x, y, z);
    }

    // mirror Tesselator.vertex() but NEVER calls GL
    public void vertex(float x, float y, float z) {
        if (hasTexture) { array[p++] = u; array[p++] = v; }
        if (hasColor)   { array[p++] = r; array[p++] = g; array[p++] = b; }
        array[p++] = x; array[p++] = y; array[p++] = z;
        vertices++;
        // NOTE: in real Tesselator this might trigger flush(); here we don’t
    }

    // mirror signature, but make it a NO-OP for workers
    public void flush() {
        // do nothing on worker; just keep recording
    }

    // worker-only: produce a MeshData snapshot
    public MeshData toMeshData() {
        buffer.clear();
        buffer.put(array, 0, p);
        buffer.flip();
        return new MeshData(buffer, vertices, hasTexture, hasColor, len);
    }

    // tiny container to pass back to main thread
    public static final class MeshData {
        public final FloatBuffer interleaved; // layout matches Tesselator order: [tex?][color?]pos
        public final int vertexCount;
        public final boolean hasTexture, hasColor;
        public final int strideFloats; // per-vertex float count (len)
        public MeshData(FloatBuffer buf, int count, boolean tex, boolean color, int stride) {
            interleaved = buf; vertexCount = count; hasTexture = tex; hasColor = color; strideFloats = stride;
        }
    }
}