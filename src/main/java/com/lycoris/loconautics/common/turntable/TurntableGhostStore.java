package com.lycoris.loconautics.common.turntable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class TurntableGhostStore extends SavedData {
    private static final String DATA_NAME = "loconautics_turntable_ghosts";
    private final List<CompoundTag> loaded = new ArrayList<>();

    public static TurntableGhostStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (TurntableGhostStore)storage.computeIfAbsent(
            new Factory<TurntableGhostStore>(TurntableGhostStore::new, TurntableGhostStore::load, null), "loconautics_turntable_ghosts"
        );
    }

    List<CompoundTag> loaded() {
        return this.loaded;
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        ListTag list = new ListTag();

        for (GhostTurntable ghost : TurntableGhosts.all()) {
            list.add(ghost.write());
        }

        tag.put("Ghosts", list);
        return tag;
    }

    private static TurntableGhostStore load(CompoundTag tag, Provider registries) {
        TurntableGhostStore store = new TurntableGhostStore();
        ListTag list = tag.getList("Ghosts", 10);

        for (int i = 0; i < list.size(); i++) {
            store.loaded.add(list.getCompound(i));
        }

        return store;
    }
}
