package com.lycoris.loconautics.content.steelcable;

import com.lycoris.loconautics.common.strand.StrandManager;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

public final class SteelCableStrandManager extends StrandManager<SteelCableStrand> {
    private static final String DATA_NAME = "loconautics_steel_cable_strands";

    public static SteelCableStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (SteelCableStrandManager)storage.computeIfAbsent(
            new Factory<SteelCableStrandManager>(SteelCableStrandManager::new, SteelCableStrandManager::load, null), "loconautics_steel_cable_strands"
        );
    }

    @Nullable
    public static SteelCableStrandManager get(Level level) {
        return !level.isClientSide && level.getServer() != null ? get(level.getServer()) : null;
    }

    protected UUID idOf(SteelCableStrand strand) {
        return strand.id;
    }

    protected CompoundTag toNbt(SteelCableStrand strand) {
        return strand.toNbt();
    }

    private static SteelCableStrandManager load(CompoundTag tag, Provider registries) {
        SteelCableStrandManager m = new SteelCableStrandManager();
        loadInto(m, tag, SteelCableStrand::fromNbt);
        return m;
    }
}
