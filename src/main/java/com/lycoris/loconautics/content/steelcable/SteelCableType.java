package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.strand.StrandKind;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public record SteelCableType(ResourceLocation id, int maxSpan, float maxStretchAllowed, float snapStretchPercent, StrandKind kind, boolean carriesFluid) {
    private static final Map<ResourceLocation, SteelCableType> REGISTRY = new ConcurrentHashMap<>();
    public static final SteelCableType DEFAULT = register(
        new SteelCableType(LoconauticsConstants.id("default"), 32, 25.0F, 0.0F, StrandKind.STEEL_CABLE, false)
    );
    public static final SteelCableType HOSE_LINK = register(
        new SteelCableType(LoconauticsConstants.id("hose_link"), 28, 25.0F, 50.0F, StrandKind.HOSE_LINK, true)
    );

    public static SteelCableType register(SteelCableType type) {
        REGISTRY.put(type.id(), type);
        return type;
    }

    public static SteelCableType get(ResourceLocation id) {
        return REGISTRY.getOrDefault(id, DEFAULT);
    }
}
