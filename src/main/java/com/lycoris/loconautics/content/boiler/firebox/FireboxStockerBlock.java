package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;

public class FireboxStockerBlock extends HorizontalDirectionalBlock implements IWrenchable, IBE<FireboxStockerBlockEntity> {
    public static final MapCodec<FireboxStockerBlock> CODEC = simpleCodec(FireboxStockerBlock::new);

    public FireboxStockerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(context));
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !moving && level.getBlockEntity(pos) instanceof FireboxStockerBlockEntity be) {
            Containers.dropContents(level, pos, be.dropContents());
        }

        super.onRemove(state, level, pos, newState, moving);
    }

    public Class<FireboxStockerBlockEntity> getBlockEntityClass() {
        return FireboxStockerBlockEntity.class;
    }

    public BlockEntityType<? extends FireboxStockerBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends FireboxStockerBlockEntity>)BoilerBlocks.FIREBOX_STOCKER_BE.get();
    }
}
