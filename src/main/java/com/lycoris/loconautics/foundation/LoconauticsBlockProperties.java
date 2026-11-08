package com.lycoris.loconautics.foundation;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;

public final class LoconauticsBlockProperties {
    private LoconauticsBlockProperties() {
    }

    public static Properties metallic() {
        return Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }
}
