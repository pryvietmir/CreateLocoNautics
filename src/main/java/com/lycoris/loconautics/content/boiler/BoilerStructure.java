package com.lycoris.loconautics.content.boiler;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public record BoilerStructure(
    BlockPos controllerPos,
    List<BlockPos> fireboxPositions,
    List<BlockPos> boilerPositions,
    @Nullable BlockPos stackPos,
    @Nullable BlockPos injectorPos,
    @Nullable BlockPos takeoffPos,
    @Nullable BlockPos safetyValvePos,
    List<BlockPos> connectorPositions,
    float fireboxHeat,
    float boilerCapacity,
    int waterCapacity
) {
    public static final float OPTIMAL_BODY_PER_FIREBOX = 3.0F;
    public static final int MIN_BODY_BLOCKS = 1;

    public float optimalHeat() {
        return this.boilerCapacity / 3.0F;
    }

    public float heatRatio() {
        float optimal = this.optimalHeat();
        return optimal <= 0.0F ? 0.0F : this.fireboxHeat / optimal;
    }

    public boolean isUnderfired() {
        return this.heatRatio() < 1.0F;
    }

    public boolean isOverfired() {
        return this.heatRatio() > 1.0F;
    }

    public float temperatureCap() {
        return Math.min(1.0F, this.heatRatio());
    }

    public float burnMultiplier() {
        return Math.max(1.0F, this.heatRatio());
    }

    public boolean hasStack() {
        return this.stackPos != null;
    }

    public boolean hasWaterInlet() {
        return this.injectorPos != null || !this.connectorPositions.isEmpty();
    }

    public boolean hasTakeoff() {
        return this.takeoffPos != null;
    }

    public boolean hasSafetyValve() {
        return this.safetyValvePos != null;
    }
}
