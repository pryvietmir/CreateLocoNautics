package com.lycoris.loconautics.content.trainlookup.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;

public final class TrainHistory extends SavedData {
    private static final String DATA_NAME = "loconautics_train_history";
    private final List<HistoryEntry> entries = new ArrayList<>();
    private final Map<UUID, List<HistoryEntry>> byTrain = new HashMap<>();
    private final Map<UUID, List<HistoryEntry>> byActor = new HashMap<>();

    public static TrainHistory get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (TrainHistory)storage.computeIfAbsent(new Factory(TrainHistory::new, TrainHistory::load, null), "loconautics_train_history");
    }

    public void record(HistoryEntry entry) {
        if (entry != null) {
            this.index(entry);
            this.setDirty();
        }
    }

    private void index(HistoryEntry entry) {
        this.entries.add(entry);
        this.byTrain.computeIfAbsent(entry.trainUUID(), k -> new ArrayList<>()).add(entry);
        this.byActor.computeIfAbsent(entry.actorUUID(), k -> new ArrayList<>()).add(entry);
    }

    public List<HistoryEntry> forTrain(UUID trainId) {
        return Collections.unmodifiableList(this.byTrain.getOrDefault(trainId, List.of()));
    }

    public List<HistoryEntry> forPlayer(UUID actorId) {
        return Collections.unmodifiableList(this.byActor.getOrDefault(actorId, List.of()));
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        ListTag list = new ListTag();

        for (HistoryEntry entry : this.entries) {
            list.add(entry.save());
        }

        tag.put("Entries", list);
        return tag;
    }

    private static TrainHistory load(CompoundTag tag, Provider registries) {
        TrainHistory history = new TrainHistory();
        ListTag list = tag.getList("Entries", 10);

        for (int i = 0; i < list.size(); i++) {
            HistoryEntry entry = HistoryEntry.load(list.getCompound(i));
            if (entry != null) {
                history.index(entry);
            }
        }

        return history;
    }
}
