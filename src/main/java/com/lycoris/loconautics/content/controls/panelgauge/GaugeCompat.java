package com.lycoris.loconautics.content.controls.panelgauge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.ModList;

public final class GaugeCompat {
    private GaugeCompat() {
    }

    public static List<GaugeCompat.Entry> sorted() {
        Map<String, GaugeCompat.Entry> byMod = new LinkedHashMap<>();

        for (GaugeMetric.Group group : GaugeMetric.Group.values()) {
            byMod.computeIfAbsent(group.modId(), id -> new GaugeCompat.Entry(id, group.modName(), new ArrayList<>())).blocks().add(group.blockName());
        }

        List<GaugeCompat.Entry> list = new ArrayList<>(byMod.values());
        list.sort(Comparator.comparing(entry -> !entry.installed()));
        return list;
    }

    public record Entry(String modId, String name, List<String> blocks) {
        public boolean installed() {
            return ModList.get().isLoaded(this.modId);
        }
    }
}
