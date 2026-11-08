package com.lycoris.loconautics.trainphysics.actors;

import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TrainActorDrops {
    private TrainActorDrops() {
    }

    public static boolean depositInPlace(Level level, BlockPos machinePos, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel && machinePos != null && stack != null && !stack.isEmpty())) {
            return false;
        } else if (!(Sable.HELPER.getContaining(serverLevel, machinePos) instanceof ServerSubLevel sub)) {
            return false;
        } else {
            return SableTrainRegistry.bySubLevel(sub.getUniqueId()) == null ? false : CarriageStorage.insert(sub, machinePos, stack);
        }
    }
}
