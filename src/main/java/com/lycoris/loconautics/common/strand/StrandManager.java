package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.common.SavedDataStores;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

public abstract class StrandManager<T> extends SavedData {
    protected final Map<UUID, T> strands = new HashMap<>();

    protected abstract UUID idOf(T var1);

    protected abstract CompoundTag toNbt(T var1);

    public T create(T strand) {
        this.strands.put(this.idOf(strand), strand);
        this.setDirty();
        return strand;
    }

    @Nullable
    public T get(UUID id) {
        return id == null ? null : this.strands.get(id);
    }

    public Collection<T> all() {
        return this.strands.values();
    }

    public void remove(UUID id) {
        SavedDataStores.remove(this, this.strands, id);
    }

    public void touch() {
        this.setDirty();
    }

    public CompoundTag save(CompoundTag tag, Provider registries) {
        ListTag list = new ListTag();

        for (T s : this.strands.values()) {
            list.add(this.toNbt(s));
        }

        tag.put("Strands", list);
        return tag;
    }

    protected static <M extends StrandManager<T>, T> void loadInto(M manager, CompoundTag tag, Function<CompoundTag, T> fromNbt) {
        ListTag list = tag.getList("Strands", 10);

        for (int i = 0; i < list.size(); i++) {
            T s = fromNbt.apply(list.getCompound(i));
            manager.strands.put(manager.idOf(s), s);
        }
    }
}
