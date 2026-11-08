package com.lycoris.loconautics.client;

import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ConductorHats {
    private static final Map<UUID, Set<BlockPos>> DRIVERS = new HashMap<>();

    private ConductorHats() {
    }

    public static void setDriver(UUID subLevelId, BlockPos pos, boolean driving) {
        if (driving) {
            DRIVERS.computeIfAbsent(subLevelId, id -> new HashSet<>()).add(pos.immutable());
        } else {
            Set<BlockPos> inLevel = DRIVERS.get(subLevelId);
            if (inLevel != null && inLevel.remove(pos) && inLevel.isEmpty()) {
                DRIVERS.remove(subLevelId);
            }
        }
    }

    public static boolean wearsHat(BlockEntity be) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        return sub != null && driving(sub.getUniqueId(), be.getBlockPos());
    }

    public static boolean hatOverride(BlockEntity be, boolean original) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        return sub != null && SableTrainClientRegistry.isTrain(sub.getUniqueId()) ? driving(sub.getUniqueId(), be.getBlockPos()) : original;
    }

    public static PartialModel hatFor(BlockEntity be, PartialModel original) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        return sub != null && SableTrainClientRegistry.isTrain(sub.getUniqueId())
            ? LoconauticsPartialModels.conductorsHat(be.getBlockPos().asLong())
            : original;
    }

    private static boolean driving(UUID subLevelId, BlockPos pos) {
        Set<BlockPos> inLevel = DRIVERS.get(subLevelId);
        return inLevel != null && inLevel.contains(pos);
    }
}
