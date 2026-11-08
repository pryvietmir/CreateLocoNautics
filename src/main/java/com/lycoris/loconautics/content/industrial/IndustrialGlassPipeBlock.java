package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class IndustrialGlassPipeBlock extends GlassFluidPipeBlock {
    public IndustrialGlassPipeBlock(Properties properties) {
        super(properties);
    }

    public BlockState toRegularPipe(LevelAccessor world, BlockPos pos, BlockState state) {
        Direction side = Direction.get(AxisDirection.POSITIVE, this.getAxis(state));
        Map<Direction, BooleanProperty> facingToPropertyMap = FluidPipeBlock.PROPERTY_BY_DIRECTION;
        FluidPipeBlock pipe = (FluidPipeBlock)IndustrialBlocks.PIPE.get();
        return pipe.updateBlockState(
            (BlockState)((BlockState)pipe.defaultBlockState().setValue((Property)facingToPropertyMap.get(side), true))
                .setValue((Property)facingToPropertyMap.get(side.getOpposite()), true),
            side,
            null,
            world,
            pos
        );
    }
}
