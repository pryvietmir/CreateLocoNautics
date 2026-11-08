package com.lycoris.loconautics.content.hose.spray;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntityRule;
import com.lycoris.loconautics.content.hose.spray.interaction.CompositeSprayInteraction;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteraction;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import com.lycoris.loconautics.content.hose.spray.interaction.VanillaHoseSprayInteractions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

public final class HoseSprayLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static final String DIRECTORY = "hose_spray";
    private static volatile Map<ResourceLocation, int[]> loadedColors = Map.of();

    public HoseSprayLoader() {
        super(GSON, "hose_spray");
    }

    public static Map<ResourceLocation, int[]> loadedColors() {
        return loadedColors;
    }

    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<HoseSprayLoader.Prepared> prepared = new ArrayList<>();
        Map<ResourceLocation, int[]> colors = new LinkedHashMap<>();

        for (Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                JsonObject root = (JsonObject)GSON.fromJson(e.getValue(), JsonObject.class);
                if (root.has("bindings")) {
                    for (JsonElement b : root.getAsJsonArray("bindings")) {
                        parse(b.getAsJsonObject(), prepared, colors);
                    }
                } else {
                    parse(root, prepared, colors);
                }
            } catch (Exception ex) {
                LoconauticsConstants.LOGGER.error("[HoseSpray] bad file {}", e.getKey(), ex);
            }
        }

        loadedColors = Map.copyOf(colors);
        HoseSprayInteractions.setBuiltin(VanillaHoseSprayInteractions::register);
        HoseSprayInteractions.rebuild(
            () -> {
                for (HoseSprayLoader.Prepared p : prepared) {
                    if (p.isTag) {
                        HoseSprayInteractions.register(TagKey.create(Registries.FLUID, p.id), p.interaction);
                    } else {
                        BuiltInRegistries.FLUID
                            .getOptional(p.id)
                            .ifPresentOrElse(
                                f -> HoseSprayInteractions.register(f, p.interaction),
                                () -> LoconauticsConstants.LOGGER.error("[HoseSpray] unknown fluid {}, binding skipped", p.id)
                            );
                    }
                }
            }
        );
        HoseSprayColors.setDatapack(loadedColors);
        LoconauticsConstants.LOGGER.info("[HoseSpray] loaded {} bindings, {} color overrides", prepared.size(), loadedColors.size());
    }

    private static void parse(JsonObject o, List<HoseSprayLoader.Prepared> out, Map<ResourceLocation, int[]> colors) {
        boolean isTag = o.has("fluidTag");
        ResourceLocation id = ResourceLocation.parse(o.get(isTag ? "fluidTag" : "fluid").getAsString());
        HoseSprayInteraction base = o.has("effect") ? HoseSprayInteractions.effect(o.get("effect").getAsString()) : null;
        if (o.has("effect") && base == null) {
            LoconauticsConstants.LOGGER.error("[HoseSpray] unknown effect '{}' (have {})", o.get("effect").getAsString(), HoseSprayInteractions.effectIds());
        }

        List<HoseSprayEntityRule> rules = new ArrayList<>();
        List<Predicate<EntityType<?>>> blacklist = new ArrayList<>();
        if (o.has("entities")) {
            JsonObject ents = o.getAsJsonObject("entities");
            if (ents.has("rules")) {
                for (JsonElement r : ents.getAsJsonArray("rules")) {
                    rules.add(HoseSprayEntityRule.fromJson(r.getAsJsonObject()));
                }
            }

            if (ents.has("blacklist")) {
                blacklist = HoseSprayEntityRule.matchers(ents.getAsJsonArray("blacklist"));
            }
        }

        if (o.has("mob_effect") || o.has("damage") || o.has("ignite")) {
            rules.add(HoseSprayEntityRule.fromJson(o));
        }

        if (base != null || !rules.isEmpty() || !blacklist.isEmpty()) {
            out.add(new HoseSprayLoader.Prepared(isTag, id, new CompositeSprayInteraction(base, rules, blacklist)));
        }

        if (!isTag && o.has("colors")) {
            colors.put(id, parseColors(o.getAsJsonArray("colors")));
        }
    }

    private static int[] parseColors(JsonArray arr) {
        List<Integer> parsed = new ArrayList<>();

        for (JsonElement e : arr) {
            String s = e.getAsString().replace("#", "");
            parsed.add(0xFF000000 | (int)(Long.parseLong(s, 16) & 16777215L));
        }

        int[] out = new int[]{-1, -1, -1, -1};
        if (!parsed.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                out[i] = parsed.get(i % parsed.size());
            }
        }

        return out;
    }

    private record Prepared(boolean isTag, ResourceLocation id, HoseSprayInteraction interaction) {
    }
}
