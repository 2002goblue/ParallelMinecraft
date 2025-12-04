package com.mojang.minecraft.renderer;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class Tesselator {
   private static final int MAX_MEMORY_USE = 4194304;
   private static final int MAX_FLOATS = 524288;
   private FloatBuffer buffer = BufferUtils.createFloatBuffer(524288);
   private float[] array = new float[524288];
   private int vertices = 0;
   private float u;
   private float v;
   private float r;
   private float g;
   private float b;
   private boolean hasColor = false;
   private boolean hasTexture = false;
   private int len = 3;
   private int p = 0;
   private boolean noColor = false;
   public static Tesselator instance = new Tesselator();

   public void flush() {
      if (this.vertices > 0) {
         this.buffer.clear();
         this.buffer.put(this.array, 0, this.p);
         this.buffer.flip();
         if (this.hasTexture && this.hasColor) {
            GL11.glInterleavedArrays(10794, 0, this.buffer);
         } else if (this.hasTexture) {
            GL11.glInterleavedArrays(10791, 0, this.buffer);
         } else if (this.hasColor) {
            GL11.glInterleavedArrays(10788, 0, this.buffer);
         } else {
            GL11.glInterleavedArrays(10785, 0, this.buffer);
         }

         GL11.glEnableClientState(32884);
         if (this.hasTexture) {
            GL11.glEnableClientState(32888);
         }

         if (this.hasColor) {
            GL11.glEnableClientState(32886);
         }

         GL11.glDrawArrays(7, 0, this.vertices);
         GL11.glDisableClientState(32884);
         if (this.hasTexture) {
            GL11.glDisableClientState(32888);
         }

         if (this.hasColor) {
            GL11.glDisableClientState(32886);
         }
      }

      this.clear();
   }

   private void clear() {
      this.vertices = 0;
      this.buffer.clear();
      this.p = 0;
   }

   public void init() {
      this.clear();
      this.hasColor = false;
      this.hasTexture = false;
      this.noColor = false;
   }

   public void tex(float u, float v) {
      if (!this.hasTexture) {
         this.len += 2;
      }

      this.hasTexture = true;
      this.u = u;
      this.v = v;
   }

   public void color(float r, float g, float b) {
      if (!this.noColor) {
         if (!this.hasColor) {
            this.len += 3;
         }

         this.hasColor = true;
         this.r = r;
         this.g = g;
         this.b = b;
      }
   }

   public void vertexUV(float x, float y, float z, float u, float v) {
      this.tex(u, v);
      this.vertex(x, y, z);
   }

   public void vertex(float x, float y, float z) {
      if (this.hasTexture) {
         this.array[this.p++] = this.u;
         this.array[this.p++] = this.v;
      }

      if (this.hasColor) {
         this.array[this.p++] = this.r;
         this.array[this.p++] = this.g;
         this.array[this.p++] = this.b;
      }

      this.array[this.p++] = x;
      this.array[this.p++] = y;
      this.array[this.p++] = z;
      ++this.vertices;
      if (this.vertices % 4 == 0 && this.p >= 524288 - this.len * 4) {
         this.flush();
      }

   }

   public void color(int c) {
      float r = (float)(c >> 16 & 255) / 255.0F;
      float g = (float)(c >> 8 & 255) / 255.0F;
      float b = (float)(c & 255) / 255.0F;
      this.color(r, g, b);
   }

   public void noColor() {
      this.noColor = true;
   }

   // === BEGIN: thread-safe mesh snapshot & helpers ===
   public static final class MeshData {
      // interleaved array and metadata captured from a Tesselator
      public final float[] array;
      public final int p;           // number of floats used
      public final int vertices;    // vertex count
      public final boolean hasTex;
      public final boolean hasColor;
      public final int len;         // stride floats per vertex (3, 5, 6, or 8)

      public MeshData(float[] array, int p, int vertices, boolean hasTex, boolean hasColor, int len) {
         this.array = array;
         this.p = p;
         this.vertices = vertices;
         this.hasTex = hasTex;
         this.hasColor = hasColor;
         this.len = len;
      }

      // Render-thread only: issues GL calls to draw the captured mesh once
      public void emitToGL() {
         if (vertices <= 0) return;
         java.nio.FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(p);
         buf.put(array, 0, p).flip();

         // Choose the right interleaved layout
         if (hasTex && hasColor) {
            org.lwjgl.opengl.GL11.glInterleavedArrays(10794 /* GL_T2F_C3F_V3F */, 0, buf);
         } else if (hasTex) {
            org.lwjgl.opengl.GL11.glInterleavedArrays(10791 /* GL_T2F_V3F */, 0, buf);
         } else if (hasColor) {
            org.lwjgl.opengl.GL11.glInterleavedArrays(10788 /* GL_C3F_V3F */, 0, buf);
         } else {
            org.lwjgl.opengl.GL11.glInterleavedArrays(10785 /* GL_V3F */, 0, buf);
         }

         org.lwjgl.opengl.GL11.glEnableClientState(32884 /* GL_VERTEX_ARRAY */);
         if (hasTex)   org.lwjgl.opengl.GL11.glEnableClientState(32888 /* GL_TEXTURE_COORD_ARRAY */);
         if (hasColor) org.lwjgl.opengl.GL11.glEnableClientState(32886 /* GL_COLOR_ARRAY */);

         org.lwjgl.opengl.GL11.glDrawArrays(7 /* GL_QUADS */, 0, vertices);

         if (hasColor) org.lwjgl.opengl.GL11.glDisableClientState(32886);
         if (hasTex)   org.lwjgl.opengl.GL11.glDisableClientState(32888);
         org.lwjgl.opengl.GL11.glDisableClientState(32884);
      }
   }

   // Worker-thread safe: capture the current contents without issuing any GL
   public MeshData snapshot() {
      // copy only the used portion
      float[] copy = new float[this.p];
      System.arraycopy(this.array, 0, copy, 0, this.p);
      return new MeshData(copy, this.p, this.vertices, this.hasTexture, this.hasColor, this.len);
   }
   // === END: thread-safe mesh snapshot & helpers ===
   
   
}
