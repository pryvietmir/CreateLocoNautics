package com.lycoris.loconautics.content.hose.spray;

import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

public final class HoseSprayColors {
    private static final Map<Fluid, int[]> OVERRIDES = new HashMap<>();

    private HoseSprayColors() {
    }

    public static void setDatapack(Map<ResourceLocation, int[]> byFluidId) {
        OVERRIDES.clear();

        for (Entry<ResourceLocation, int[]> e : byFluidId.entrySet()) {
            BuiltInRegistries.FLUID.getOptional(e.getKey()).ifPresent(f -> OVERRIDES.put(HoseSprayInteractions.sourceOf(f), e.getValue()));
        }
    }

    @Nullable
    public static int[] get(Fluid fluid) {
        return OVERRIDES.get(HoseSprayInteractions.sourceOf(fluid));
    }
}
