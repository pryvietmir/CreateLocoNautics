package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.LoconauticsConstants;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public record HoseType(
    ResourceLocation id,
    int maxLength,
    int bufferSize,
    float spillParticleRate,
    ResourceLocation strandTexture,
    float reelTierSpeedMultiplier,
    float maxRopeStretchAllowed,
    float snapStretchPercent
) {
    private static final Map<ResourceLocation, HoseType> REGISTRY = new ConcurrentHashMap<>();
    public static final HoseType DEFAULT = register(
        new HoseType(LoconauticsConstants.id("default"), 28, 1000, 2.0F, LoconauticsConstants.id("block/hose/hose"), 1.0F, 25.0F, 50.0F)
    );

    public static HoseType register(HoseType type) {
        REGISTRY.put(type.id(), type);
        return type;
    }

    public static HoseType get(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, DEFAULT);
    }
}
