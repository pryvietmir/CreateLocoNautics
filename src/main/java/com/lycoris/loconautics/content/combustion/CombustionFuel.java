package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.content.cerisene.CeriseneBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public enum CombustionFuel {
    CERISENE(null, 256.0F, 0.05F),
    DIESEL("diesel", 240.0F, 0.060000002F),
    GASOLINE("gasoline", 224.0F, 0.075F),
    REFINED_FUEL("refined_fuel", 208.0F, 0.089999996F),
    BIODIESEL("biodiesel", 192.0F, 0.105F),
    HEAVY_OIL("heavy_oil", 176.0F, 0.120000005F),
    ETHANOL("ethanol", 160.0F, 0.15F),
    LAVA(null, 128.0F, 0.3F);

    @Nullable
    private final TagKey<Fluid> tag;
    private final float topRpm;
    private final float burnPerTick;

    CombustionFuel(@Nullable String tagPath, float topRpm, float burnPerTick) {
        this.tag = tagPath == null ? null : TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", tagPath));
        this.topRpm = topRpm;
        this.burnPerTick = burnPerTick;
    }

    public float topRpm() {
        return this.topRpm;
    }

    public float burnPerTick() {
        return this.burnPerTick;
    }

    @Nullable
    public static CombustionFuel of(FluidStack stack) {
        return stack.isEmpty() ? null : of(stack.getFluid());
    }

    @Nullable
    public static CombustionFuel of(Fluid fluid) {
        if (fluid.isSame((Fluid)CeriseneBlocks.CERISENE.get())) {
            return CERISENE;
        }

        if (fluid.isSame(Fluids.LAVA)) {
            return LAVA;
        }

        for (CombustionFuel fuel : values()) {
            if (fuel.tag != null && fluid.defaultFluidState().is(fuel.tag)) {
                return fuel;
            }
        }

        return null;
    }

    public static boolean accepts(FluidStack stack) {
        return of(stack) != null;
    }
}
