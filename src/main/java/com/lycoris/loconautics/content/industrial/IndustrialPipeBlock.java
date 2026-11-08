package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class IndustrialPipeBlock extends FluidPipeBlock {
    public IndustrialPipeBlock(Properties properties) {
        super(properties);
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (this.tryRemoveBracket(context)) {
            return InteractionResult.SUCCESS;
        }

        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        Axis axis = FluidPropagator.getStraightPipeAxis(state);
        if (axis == null) {
            Vec3 clickLocation = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            double closest = Double.MAX_VALUE;
            Direction argClosest = Direction.UP;

            for (Direction direction : Iterate.directions) {
                if (clickedFace.getAxis() != direction.getAxis()) {
                    double distance = Vec3.atCenterOf(direction.getNormal()).distanceToSqr(clickLocation);
                    if (distance < closest) {
                        closest = distance;
                        argClosest = direction;
                    }
                }
            }

            axis = argClosest.getAxis();
        }

        if (clickedFace.getAxis() == axis) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide) {
            FluidTransportBehaviour.cacheFlows(world, pos);
            world.setBlockAndUpdate(
                pos,
                (BlockState)((BlockState)((IndustrialGlassPipeBlock)IndustrialBlocks.GLASS_PIPE.get())
                        .defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, axis))
                    .setValue(BlockStateProperties.WATERLOGGED, (Boolean)state.getValue(BlockStateProperties.WATERLOGGED))
            );
            FluidTransportBehaviour.loadFlows(world, pos);
        }

        return InteractionResult.SUCCESS;
    }
}
