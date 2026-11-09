package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandManager;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData.Factory;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.Nullable;

public final class ScrewLinkStrandManager extends StrandManager<ScrewLinkStrand> {
    private static final String DATA_NAME = "loconautics_screw_link_strands";

    public static ScrewLinkStrandManager get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return (ScrewLinkStrandManager)storage.computeIfAbsent(
            new Factory<ScrewLinkStrandManager>(ScrewLinkStrandManager::new, ScrewLinkStrandManager::load, null), "loconautics_screw_link_strands"
        );
    }

    @Nullable
    public static ScrewLinkStrandManager get(Level level) {
        return !level.isClientSide && level.getServer() != null ? get(level.getServer()) : null;
    }

    protected UUID idOf(ScrewLinkStrand strand) {
        return strand.id;
    }

    protected CompoundTag toNbt(ScrewLinkStrand strand) {
        return strand.toNbt();
    }

    private static ScrewLinkStrandManager load(CompoundTag tag, Provider registries) {
        ScrewLinkStrandManager m = new ScrewLinkStrandManager();
        loadInto(m, tag, ScrewLinkStrand::fromNbt);
        return m;
    }
}
