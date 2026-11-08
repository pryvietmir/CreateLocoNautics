package com.lycoris.loconautics.content.conductorshat;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ConductorsHats {
    public static final DyeColor TAB_COLOR = DyeColor.RED;
    public static final Map<DyeColor, DeferredHolder<Item, ConductorsHatItem>> HATS = registerHats();

    private ConductorsHats() {
    }

    public static String hatName(DyeColor color) {
        return color.getSerializedName() + "_conductors_hat";
    }

    public static DyeColor colorFor(long seed) {
        return DyeColor.byId(RandomSource.create(seed).nextInt(DyeColor.values().length));
    }

    public static void init() {
    }

    private static Map<DyeColor, DeferredHolder<Item, ConductorsHatItem>> registerHats() {
        Map<DyeColor, DeferredHolder<Item, ConductorsHatItem>> hats = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            hats.put(color, LoconauticsRegistries.ITEMS.register(hatName(color), () -> new ConductorsHatItem(new Properties().stacksTo(1), color)));
        }

        return Collections.unmodifiableMap(hats);
    }
}
