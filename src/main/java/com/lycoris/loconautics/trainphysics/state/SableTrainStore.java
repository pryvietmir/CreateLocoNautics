package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.common.SavedDataStores;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class SableTrainStore extends SavedData {
    private static final String DATA_NAME = "loconautics_sable_trains";
    private final Map<UUID, CompoundTag> records = new HashMap<>();

    public static SableTrainStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (SableTrainStore)storage.computeIfAbsent(new Factory<SableTrainStore>(SableTrainStore::new, SableTrainStore::load, null), "loconautics_sable_trains");
    }

    public void put(UUID id, CompoundTag record) {
        this.records.put(id, record);
        this.setDirty();
    }

    public void remove(UUID id) {
        SavedDataStores.remove(this, this.records, id);
    }

    public Map<UUID, CompoundTag> records() {
        return this.records;
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        ListTag list = new ListTag();

        for (CompoundTag record : this.records.values()) {
            list.add(record.copy());
        }

        tag.put("Trains", list);
        return tag;
    }

    private static SableTrainStore load(CompoundTag tag, Provider registries) {
        SableTrainStore store = new SableTrainStore();
        ListTag list = tag.getList("Trains", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag record = list.getCompound(i);
            if (record.hasUUID("Id")) {
                store.records.put(record.getUUID("Id"), record);
            }
        }

        return store;
    }
}
