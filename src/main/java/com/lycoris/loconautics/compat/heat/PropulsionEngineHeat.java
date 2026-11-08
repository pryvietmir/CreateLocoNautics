package com.lycoris.loconautics.compat.heat;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.propulsionteam.propulsionsimulated.content.heat.HeatSourceBehavior;
import dev.propulsionteam.propulsionsimulated.content.heat.IHeatConsumer;
import dev.propulsionteam.propulsionsimulated.content.heat.IHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

public final class PropulsionEngineHeat {
    private static final String MOD_ID = "createpropulsion";
    private static final float CAPACITY = 600.0F;
    private static final float PER_TICK = 2.0F;
    private static final float PASSIVE_LOSS = 0.05F;

    private PropulsionEngineHeat() {
    }

    public static boolean loaded() {
        return ModList.get().isLoaded("createpropulsion");
    }

    public static HeatSourceBehavior create(SmartBlockEntity engine) {
        return new HeatSourceBehavior(engine, 600.0F, 2.0F);
    }

    public static float perTick() {
        return 2.0F;
    }

    public static void tick(BlockEntityBehaviour behavior, Level level, BlockPos pos, float generation) {
        if (behavior instanceof HeatSourceBehavior source) {
            IHeatSource buffer = source.getHeatSource();
            float consumed = offer(buffer, level, pos);
            float loss = consumed == 0.0F && buffer.getHeatStored() > 0.0F ? 0.05F : 0.0F;
            float net = generation - loss - consumed;
            if (net > 0.0F) {
                buffer.generateHeat(net);
            } else if (net < 0.0F) {
                buffer.extractHeat(-net, false);
            }
        }
    }

    private static float offer(IHeatSource buffer, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos.above()) instanceof IHeatConsumer consumer && consumer.isActive()) {
            float stored = buffer.getHeatStored();
            return !(stored <= 0.0F) && !(stored < 600.0F * consumer.getOperatingThreshold())
                ? consumer.consumeHeat(stored, buffer.getExpectedHeatProduction(), false)
                : 0.0F;
        } else {
            return 0.0F;
        }
    }
}
