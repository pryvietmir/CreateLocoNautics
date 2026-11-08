package com.lycoris.loconautics.foundation.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public final class SmokeSpriteBounds {
    private static final int FILTER_PAD = 2;
    private static final Map<TextureAtlasSprite, SmokeSpriteBounds> CACHE = new WeakHashMap<>();
    public final float left;
    public final float right;
    public final float top;
    public final float bottom;

    private SmokeSpriteBounds(float left, float right, float top, float bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    public static SmokeSpriteBounds of(TextureAtlasSprite sprite) {
        return CACHE.computeIfAbsent(sprite, SmokeSpriteBounds::measure);
    }

    private static SmokeSpriteBounds measure(TextureAtlasSprite sprite) {
        NativeImage image = sprite.contents().getOriginalImage();
        int width = sprite.contents().width();
        int height = sprite.contents().height();
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (image.getPixelRGBA(x, y) >>> 24 != 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        return maxX < 0
            ? new SmokeSpriteBounds(0.0F, 1.0F, 0.0F, 1.0F)
            : new SmokeSpriteBounds(
                (float)Math.max(0, minX - 2) / width,
                (float)Math.min(width, maxX + 1 + 2) / width,
                (float)Math.max(0, minY - 2) / height,
                (float)Math.min(height, maxY + 1 + 2) / height
            );
    }
}
