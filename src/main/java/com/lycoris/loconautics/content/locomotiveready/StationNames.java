package com.lycoris.loconautics.content.locomotiveready;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class StationNames {
    private static final String[] SUFFIXES = new String[]{
        "Station", "Depot", "Stop", "Halt", "Terminal", "Yard", "Platform", "Siding", "Junction", "Interchange"
    };
    private static final String[] BANNED = new String[]{
        "air", "command_block", "barrier", "structure", "jigsaw", "light", "debug", "spawn_egg", "test", "portal", "end_gateway", "unknown", "bedrock"
    };
    private static final int MAX_NAME_LENGTH = 22;

    private StationNames() {
    }

    public static String random(RandomSource random) {
        String suffix = SUFFIXES[random.nextInt(SUFFIXES.length)];

        for (int attempt = 0; attempt < 32; attempt++) {
            String base = random.nextBoolean() ? blockName(random) : itemName(random);
            if (base != null) {
                return base + " " + suffix;
            }
        }

        return "Track " + suffix;
    }

    private static String blockName(RandomSource random) {
        return BuiltInRegistries.BLOCK
            .getRandom(random)
            .map(holder -> usable(holder.key().location(), ((Block)holder.value()).getName().getString()))
            .orElse(null);
    }

    private static String itemName(RandomSource random) {
        return BuiltInRegistries.ITEM
            .getRandom(random)
            .map(holder -> usable(holder.key().location(), ((Item)holder.value()).getDescription().getString()))
            .orElse(null);
    }

    private static String usable(ResourceLocation id, String name) {
        if (!name.isBlank() && name.length() <= 22 && !name.startsWith("block.") && !name.startsWith("item.")) {
            for (String banned : BANNED) {
                if (id.getPath().contains(banned)) {
                    return null;
                }
            }

            return name;
        } else {
            return null;
        }
    }
}
