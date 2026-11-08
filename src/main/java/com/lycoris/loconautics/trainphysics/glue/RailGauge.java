package com.lycoris.loconautics.trainphysics.glue;

import com.simibubi.create.content.trains.track.TrackMaterial;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class RailGauge {
    public static final double STANDARD_HALF = 0.965;
    private static final Map<ResourceLocation, Double> HALF_BY_TYPE = Map.of(
        ResourceLocation.fromNamespaceAndPath("railways", "wide_gauge"),
        1.4649999999999999,
        ResourceLocation.fromNamespaceAndPath("railways", "narrow_gauge"),
        0.5275,
        ResourceLocation.fromNamespaceAndPath("railways", "universal"),
        0.5275
    );
    private static final ResourceLocation PHANTOM = ResourceLocation.fromNamespaceAndPath("railways", "phantom");

    private RailGauge() {
    }

    public static double halfGauge(TrackMaterial material) {
        return material != null && material.trackType != null && !PHANTOM.equals(material.id) ? HALF_BY_TYPE.getOrDefault(material.trackType.id, 0.965) : 0.965;
    }
}
