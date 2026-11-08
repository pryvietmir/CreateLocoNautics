package com.lycoris.loconautics.content.schedule.map.client;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

public class MapRasterTexture {
    public static final ResourceLocation TEXTURE_LOC = LoconauticsConstants.id("advanced_schedule_map");
    private static final int PX_PER_CHUNK = 16;
    private final int gridChunks;
    private final int viewOriginX;
    private final int viewOriginZ;
    private final int texSize;
    private DynamicTexture texture;
    private boolean textureValid;
    private volatile boolean rebuilding;
    private final AtomicReference<NativeImage> pending = new AtomicReference<>(null);

    public MapRasterTexture(int gridChunks, int viewOriginX, int viewOriginZ) {
        this.gridChunks = gridChunks;
        this.viewOriginX = viewOriginX;
        this.viewOriginZ = viewOriginZ;
        this.texSize = gridChunks * 16;
    }

    public void rebuildAsync(Map<Long, byte[]> colors) {
        if (!this.rebuilding) {
            this.rebuilding = true;
            int gc = this.gridChunks;
            int vx = this.viewOriginX;
            int vz = this.viewOriginZ;
            int ts = this.texSize;
            Map<Long, byte[]> snapshot = Map.copyOf(colors);
            CompletableFuture.runAsync(() -> {
                try {
                    NativeImage img = new NativeImage(Format.RGBA, ts, ts, false);
                    fill(img, gc, vx, vz, snapshot);
                    NativeImage old = this.pending.getAndSet(img);
                    if (old != null) {
                        old.close();
                    }
                } catch (Exception e) {
                    LoconauticsConstants.LOGGER.error("map raster rebuild failed", e);
                } finally {
                    this.rebuilding = false;
                }
            });
        }
    }

    public void tick() {
        NativeImage img = this.pending.getAndSet(null);
        if (img != null) {
            if (this.texture == null) {
                this.texture = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(TEXTURE_LOC, this.texture);
            } else {
                NativeImage px = this.texture.getPixels();
                if (px != null) {
                    px.copyFrom(img);
                    this.texture.upload();
                }

                img.close();
            }

            this.textureValid = true;
        }
    }

    public void free() {
        if (this.texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_LOC);
            this.texture.close();
            this.texture = null;
        }

        NativeImage p = this.pending.getAndSet(null);
        if (p != null) {
            p.close();
        }

        this.textureValid = false;
    }

    private static void fill(NativeImage img, int gc, int vox, int voz, Map<Long, byte[]> colors) {
        for (int rz = 0; rz < gc; rz++) {
            int cz = voz + rz;

            for (int rx = 0; rx < gc; rx++) {
                int cx = vox + rx;
                byte[] cell = colors.get(key(cx, cz));
                int pxBase = rx * 16;
                int pyBase = rz * 16;

                for (int bz = 0; bz < 16; bz++) {
                    for (int bx = 0; bx < 16; bx++) {
                        int abgr;
                        if (cell == null) {
                            abgr = 0;
                        } else {
                            int packed = cell[bz * 16 + bx] & 255;
                            abgr = packed == 0 ? 0 : MapColor.getColorFromPackedId(packed);
                        }

                        img.setPixelRGBA(pxBase + bx, pyBase + bz, abgr);
                    }
                }
            }
        }
    }

    private static long key(int cx, int cz) {
        return (long)cx << 32 | cz & 4294967295L;
    }

    public boolean isTextureValid() {
        return this.textureValid;
    }

    public int pxPerChunk() {
        return 16;
    }

    public int viewOriginX() {
        return this.viewOriginX;
    }

    public int viewOriginZ() {
        return this.viewOriginZ;
    }

    public int gridChunks() {
        return this.gridChunks;
    }
}
