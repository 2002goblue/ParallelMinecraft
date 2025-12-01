package com.mojang.minecraft.level;

import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Tesselator;
import org.lwjgl.opengl.GL11;

// Reuse one direct-buffer Tesselator per worker thread to avoid DirectBuffer OOM


public class Chunk {
   public AABB aabb;
   public final Level level;
   public final int x0;
   public final int y0;
   public final int z0;
   public final int x1;
   public final int y1;
   public final int z1;
   public final float x;
   public final float y;
   public final float z;
   private boolean dirty = true;
   private int lists = -1;
   public long dirtiedTime = 0L;
   private static Tesselator t;
   public static int updates;
   public static long meshTimeNanos = 0L;
   public static int  meshCount     = 0;

   static {
      t = Tesselator.instance;
      updates = 0;
   }

   private static final ThreadLocal<Tesselator> TL_TESS = new ThreadLocal<Tesselator>() {
      @Override protected Tesselator initialValue() { return new Tesselator(); }
   };

   public Chunk(Level level, int x0, int y0, int z0, int x1, int y1, int z1) {
      this.level = level;
      this.x0 = x0;
      this.y0 = y0;
      this.z0 = z0;
      this.x1 = x1;
      this.y1 = y1;
      this.z1 = z1;
      this.x = (float)(x0 + x1) / 2.0F;
      this.y = (float)(y0 + y1) / 2.0F;
      this.z = (float)(z0 + z1) / 2.0F;
      this.aabb = new AABB((float)x0, (float)y0, (float)z0, (float)x1, (float)y1, (float)z1);
      this.lists = GL11.glGenLists(2);
   }


   public Tesselator.MeshData buildMeshData(int layer) {
      Tesselator workerTess = TL_TESS.get();
      workerTess.init();
      ++updates;
      for (int x = this.x0; x < this.x1; ++x) {
         for (int y = this.y0; y < this.y1; ++y) {
            for (int z = this.z0; z < this.z1; ++z) {
               int tileId = this.level.getTile(x, y, z);
               if (tileId > 0) {
                  Tile.tiles[tileId].render(workerTess, this.level, layer, x, y, z);
               }
            }
         }
      } 
      //System.out.println(Float.toString((this.x0 >> 4)) + " " + Float.toString(this.y0 >> 4) + " " + Float.toString((this.z0 >> 4)) + " " + Long.toString(System.currentTimeMillis()));
      try {
         // Append to chunk_log.txt
         java.io.FileWriter fw = new java.io.FileWriter("chunk_log.txt", true);
         java.io.PrintWriter pw = new java.io.PrintWriter(fw);
         
         // Format: X Y Z TIME
         pw.println(
            Float.toString((this.x0 >> 4)) + " " + 
            Float.toString((this.y0 >> 4)) + " " + 
            Float.toString((this.z0 >> 4)) + " " + 
            Long.toString(System.currentTimeMillis())
         );
         
         pw.close();
      } catch (Exception e) {
         e.printStackTrace();
      }
            
      return workerTess.snapshot();
   }

   public void uploadMeshDataToDisplayList(Tesselator.MeshData md, int layer) {
      org.lwjgl.opengl.GL11.glNewList(this.lists + layer, 4864 /* GL_COMPILE */);
      md.emitToGL();
      org.lwjgl.opengl.GL11.glEndList();
   }

   private void rebuild(int layer) {
      this.dirty = false;
      GL11.glNewList(this.lists + layer, 4864);
      t.init();

      for(int x = this.x0; x < this.x1; ++x) {
         for(int y = this.y0; y < this.y1; ++y) {
            for(int z = this.z0; z < this.z1; ++z) {
               int tileId = this.level.getTile(x, y, z);
               if (tileId > 0) {
                  Tile.tiles[tileId].render(t, this.level, layer, x, y, z);
               }
            }
         }
      }

      t.flush();
      GL11.glEndList();
   }

   public void rebuild() {
      this.rebuild(0);
      this.rebuild(1);
   }

   public void render(int layer) {
      GL11.glCallList(this.lists + layer);
   }

   public void setDirty() {
      if (!this.dirty) {
         this.dirtiedTime = System.currentTimeMillis();
      }

      this.dirty = true;
   }

   public void markClean() {
      this.dirty = false;
   }

   public boolean isDirty() {
      return this.dirty;
   }

   public float distanceToSqr(Player player) {
      float xd = player.x - this.x;
      float yd = player.y - this.y;
      float zd = player.z - this.z;
      return xd * xd + yd * yd + zd * zd;
   }
}
