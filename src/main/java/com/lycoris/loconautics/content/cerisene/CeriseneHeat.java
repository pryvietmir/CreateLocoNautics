package com.lycoris.loconautics.content.cerisene;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class CeriseneHeat {
    public static final TagKey<Block> HEAT_SOURCES = BlockTags.create(LoconauticsConstants.id("cerisene_heat_sources"));

    private CeriseneHeat() {
    }

    public static boolean isHeatSource(BlockState state) {
        if (!state.is(HEAT_SOURCES)) {
            return false;
        } else {
            return state.getBlock() instanceof BlazeBurnerBlock && !((HeatLevel)state.getValue(BlazeBurnerBlock.HEAT_LEVEL)).isAtLeast(HeatLevel.SMOULDERING)
                ? false
                : state.getOptionalValue(BlockStateProperties.LIT).orElse(true);
        }
    }

    public static boolean heatSourceNear(Level level, BlockPos pos) {
        int range = 2;

        for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-range, -range, -range), pos.offset(range, range, range))) {
            if (!probe.equals(pos) && isHeatSource(level.getBlockState(probe))) {
                return true;
            }
        }

        return false;
    }

    public static boolean waterAdjacent(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getFluidState(pos.relative(dir)).is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    public static int nextHeat(Level level, BlockPos pos, int heat) {
        if (waterAdjacent(level, pos)) {
            return Math.max(0, heat - 2);
        } else if (heatSourceNear(level, pos)) {
            return Math.min(4, heat + 1);
        } else {
            return engineNear(level, pos) ? Integer.signum(3 - heat) + heat : Math.max(0, heat - 1);
        }
    }

    public static boolean engineNear(Level level, BlockPos pos) {
        int range = 2;

        for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-range, -range, -range), pos.offset(range, range, range))) {
            if (level.getBlockState(probe).getBlock() instanceof SmallEngineBlock
                && level.getBlockEntity(probe) instanceof SmallEngineBlockEntity engine
                && engine.isRunning()) {
                return true;
            }
        }

        return false;
    }
}
