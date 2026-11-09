package com.lycoris.loconautics.content.schedule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class ScheduleProgressStore extends SavedData {
    private static final String DATA_NAME = "loconautics_schedules";
    private final Map<UUID, CompoundTag> progress = new HashMap<>();
    private final Map<UUID, CompoundTag> schedules = new HashMap<>();

    public static ScheduleProgressStore get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (ScheduleProgressStore)storage.computeIfAbsent(
            new Factory<ScheduleProgressStore>(ScheduleProgressStore::new, ScheduleProgressStore::load, null), "loconautics_schedules"
        );
    }

    public void putProgress(UUID trainId, CompoundTag state) {
        this.progress.put(trainId, state.copy());
        this.setDirty();
    }

    public CompoundTag getProgress(UUID trainId) {
        CompoundTag record = this.progress.get(trainId);
        return record == null ? null : record.copy();
    }

    public void putSchedule(UUID trainId, CompoundTag schedule) {
        this.schedules.put(trainId, schedule.copy());
        this.setDirty();
    }

    public CompoundTag getSchedule(UUID trainId) {
        CompoundTag record = this.schedules.get(trainId);
        return record == null ? null : record.copy();
    }

    public boolean hasSchedule(UUID trainId) {
        return this.schedules.containsKey(trainId);
    }

    public void move(UUID from, UUID to) {
        CompoundTag schedule = this.schedules.remove(from);
        CompoundTag state = this.progress.remove(from);
        if (schedule != null) {
            this.schedules.put(to, schedule);
        }

        if (state != null) {
            this.progress.put(to, state);
        }

        this.setDirty();
    }

    public void remove(UUID trainId) {
        boolean removed = this.progress.remove(trainId) != null;
        removed |= this.schedules.remove(trainId) != null;
        if (removed) {
            this.setDirty();
        }
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        CompoundTag entries = new CompoundTag();

        for (Entry<UUID, CompoundTag> e : this.progress.entrySet()) {
            entries.put(e.getKey().toString(), e.getValue().copy());
        }

        tag.put("Progress", entries);
        CompoundTag held = new CompoundTag();

        for (Entry<UUID, CompoundTag> e : this.schedules.entrySet()) {
            held.put(e.getKey().toString(), e.getValue().copy());
        }

        tag.put("Schedules", held);
        return tag;
    }

    private static ScheduleProgressStore load(CompoundTag tag, Provider registries) {
        ScheduleProgressStore store = new ScheduleProgressStore();
        CompoundTag entries = tag.getCompound("Progress");

        for (String key : entries.getAllKeys()) {
            store.progress.put(UUID.fromString(key), entries.getCompound(key));
        }

        CompoundTag held = tag.getCompound("Schedules");

        for (String key : held.getAllKeys()) {
            store.schedules.put(UUID.fromString(key), held.getCompound(key));
        }

        return store;
    }
}
