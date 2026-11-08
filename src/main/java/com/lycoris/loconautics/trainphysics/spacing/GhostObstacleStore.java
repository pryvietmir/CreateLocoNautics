package com.lycoris.loconautics.trainphysics.spacing;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedData.Factory;

final class GhostObstacleStore extends SavedData {
    private static final int VERSION = 2;
    private static final String DATA_NAME = "loconautics_ghost_obstacles";
    private static final Factory<GhostObstacleStore> FACTORY = new Factory(GhostObstacleStore::new, GhostObstacleStore::load, null);
    private final Long2ObjectMap<LongSet> cells = new Long2ObjectOpenHashMap();

    static GhostObstacleStore get(ServerLevel level) {
        return (GhostObstacleStore)level.getDataStorage().computeIfAbsent(FACTORY, "loconautics_ghost_obstacles");
    }

    boolean isEmpty() {
        return this.cells.isEmpty();
    }

    boolean has(long chunk, long cell) {
        LongSet set = (LongSet)this.cells.get(chunk);
        return set != null && set.contains(cell);
    }

    void replace(long chunk, LongSet found) {
        LongSet old = found != null && !found.isEmpty() ? (LongSet)this.cells.put(chunk, found) : (LongSet)this.cells.remove(chunk);
        if (old != null || found != null && !found.isEmpty()) {
            this.setDirty();
        }
    }

    void add(long chunk, LongSet found) {
        if (((LongSet)this.cells.computeIfAbsent(chunk, key -> new LongOpenHashSet())).addAll(found)) {
            this.setDirty();
        }
    }

    int size(long chunk) {
        LongSet set = (LongSet)this.cells.get(chunk);
        return set == null ? 0 : set.size();
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        ListTag list = new ListTag();
        ObjectIterator var4 = this.cells.long2ObjectEntrySet().iterator();

        while (var4.hasNext()) {
            Entry<LongSet> entry = (Entry<LongSet>)var4.next();
            CompoundTag record = new CompoundTag();
            record.putLong("Chunk", entry.getLongKey());
            record.putLongArray("Cells", ((LongSet)entry.getValue()).toLongArray());
            list.add(record);
        }

        tag.putInt("Version", 2);
        tag.put("Chunks", list);
        return tag;
    }

    private static GhostObstacleStore load(CompoundTag tag, Provider registries) {
        GhostObstacleStore store = new GhostObstacleStore();
        if (tag.getInt("Version") != 2) {
            return store;
        }

        for (Tag raw : tag.getList("Chunks", 10)) {
            CompoundTag record = (CompoundTag)raw;
            store.cells.put(record.getLong("Chunk"), new LongOpenHashSet(record.getLongArray("Cells")));
        }

        return store;
    }
}
