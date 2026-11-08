package com.lycoris.loconautics.client.sprite;

import net.minecraft.client.renderer.texture.SpriteTicker;
import org.jetbrains.annotations.Nullable;

public interface SpriteTickerHolder {
    @Nullable
    SpriteTicker loconautics$getTicker();

    void loconautics$setTicker(SpriteTicker var1);
}
