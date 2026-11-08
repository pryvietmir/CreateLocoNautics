package com.lycoris.loconautics.content.boiler.steamtakeoff;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class SteamTakeoffBlock extends RotatedPillarKineticBlock implements IBE<SteamTakeoffBlockEntity> {
    public SteamTakeoffBlock(Properties properties) {
        super(properties);
    }

    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    public Axis getRotationAxis(BlockState state) {
        return (Axis)state.getValue(AXIS);
    }

    public Class<SteamTakeoffBlockEntity> getBlockEntityClass() {
        return SteamTakeoffBlockEntity.class;
    }

    public BlockEntityType<? extends SteamTakeoffBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends SteamTakeoffBlockEntity>)BoilerBlocks.STEAM_TAKEOFF_BE.get();
    }
}
