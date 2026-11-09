package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.StrandManager;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

public final class HoseStrandManager extends StrandManager<HoseStrand> {
    private static final String DATA_NAME = "loconautics_hose_strands";

    public static HoseStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (HoseStrandManager)storage.computeIfAbsent(new Factory<HoseStrandManager>(HoseStrandManager::new, HoseStrandManager::load, null), "loconautics_hose_strands");
    }

    @Nullable
    public static HoseStrandManager get(Level level) {
        return !level.isClientSide && level.getServer() != null ? get(level.getServer()) : null;
    }

    protected UUID idOf(HoseStrand strand) {
        return strand.id;
    }

    protected CompoundTag toNbt(HoseStrand strand) {
        return strand.toNbt();
    }

    private static HoseStrandManager load(CompoundTag tag, Provider registries) {
        HoseStrandManager m = new HoseStrandManager();
        loadInto(m, tag, HoseStrand::fromNbt);
        return m;
    }
}
