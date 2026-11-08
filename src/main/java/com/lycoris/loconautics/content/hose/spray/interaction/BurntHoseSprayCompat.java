package com.lycoris.loconautics.content.hose.spray.interaction;

import com.lycoris.loconautics.LoconauticsConstants;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

final class BurntHoseSprayCompat {
    static final boolean LOADED = ModList.get().isLoaded("burnt");
    private static final Method EXTINGUISH;

    private BurntHoseSprayCompat() {
    }

    static boolean isBurntBlock(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("burnt");
    }

    static void douse(ServerLevel level, BlockPos pos) {
        if (EXTINGUISH != null) {
            try {
                EXTINGUISH.invoke(null, level, pos.getX(), pos.getY(), pos.getZ());
            } catch (ReflectiveOperationException e) {
                LoconauticsConstants.LOGGER.debug("Burnt ExtinguishBlockProcedure invoke failed", e);
            }
        }

        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F);
    }

    static {
        Method m = null;
        if (LOADED) {
            try {
                Class<?> cls = Class.forName("net.pixelbank.burnt.procedures.ExtinguishBlockProcedure");
                m = cls.getMethod("execute", ServerLevel.class, int.class, int.class, int.class);
            } catch (ReflectiveOperationException e) {
                LoconauticsConstants.LOGGER.debug("Burnt ExtinguishBlockProcedure lookup failed", e);
            }
        }

        EXTINGUISH = m;
    }
}
