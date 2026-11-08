package com.lycoris.loconautics.content.boiler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BoilerContributions extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "boiler_blocks";
    private static volatile Map<Block, Float> fireboxHeat = Map.of();
    private static volatile Map<Block, Float> boilerCapacity = Map.of();
    private static volatile Map<Block, Integer> waterCapacity = Map.of();

    public BoilerContributions() {
        super(GSON, "boiler_blocks");
    }

    public static float fireboxHeatOf(BlockState state) {
        return fireboxHeat.getOrDefault(state.getBlock(), 1.0F);
    }

    public static float boilerCapacityOf(BlockState state) {
        return boilerCapacity.getOrDefault(state.getBlock(), 1.0F);
    }

    public static int waterCapacityOf(BlockState state) {
        return waterCapacity.getOrDefault(state.getBlock(), (Integer)LoconauticsConfig.BODY_WATER_CAPACITY.get());
    }

    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, Float> heat = new LinkedHashMap<>();
        Map<Block, Float> capacity = new LinkedHashMap<>();
        Map<Block, Integer> water = new LinkedHashMap<>();

        for (Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                JsonObject root = (JsonObject)GSON.fromJson(entry.getValue(), JsonObject.class);
                if (root.has("entries")) {
                    for (JsonElement e : root.getAsJsonArray("entries")) {
                        parse(e.getAsJsonObject(), heat, capacity, water);
                    }
                } else {
                    parse(root, heat, capacity, water);
                }
            } catch (Exception ex) {
                LoconauticsConstants.LOGGER.error("[Boiler] bad contribution file {}", entry.getKey(), ex);
            }
        }

        fireboxHeat = Map.copyOf(heat);
        boilerCapacity = Map.copyOf(capacity);
        waterCapacity = Map.copyOf(water);
        LoconauticsConstants.LOGGER
            .info("[Boiler] loaded {} firebox, {} body and {} water contribution overrides", new Object[]{heat.size(), capacity.size(), water.size()});
    }

    private static void parse(JsonObject o, Map<Block, Float> heat, Map<Block, Float> capacity, Map<Block, Integer> water) {
        ResourceLocation id = ResourceLocation.parse(o.get("block").getAsString());
        Block block = (Block)BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            LoconauticsConstants.LOGGER.debug("[Boiler] contribution for absent block {}", id);
        } else {
            if (o.has("firebox_heat")) {
                heat.put(block, o.get("firebox_heat").getAsFloat());
            }

            if (o.has("boiler_capacity")) {
                capacity.put(block, o.get("boiler_capacity").getAsFloat());
            }

            if (o.has("water_capacity")) {
                water.put(block, o.get("water_capacity").getAsInt());
            }
        }
    }
}
