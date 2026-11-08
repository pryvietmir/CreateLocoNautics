package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BearingAxleBlock extends HorizontalKineticBlock implements IBE<BearingAxleBlockEntity> {
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
        Block.box(1.0, 1.0, 1.0, 15.0, 15.0, 16.0),
        new VoxelShape[]{Block.box(6.0, 0.0, 1.0, 10.0, 16.0, 16.0), Block.box(0.0, 6.0, 1.0, 16.0, 10.0, 16.0), Block.box(0.0, 2.0, 8.0, 16.0, 14.0, 12.0)}
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
        Block.box(0.0, 1.0, 1.0, 15.0, 15.0, 15.0),
        new VoxelShape[]{Block.box(0.0, 0.0, 6.0, 15.0, 16.0, 10.0), Block.box(0.0, 6.0, 0.0, 15.0, 10.0, 16.0), Block.box(4.0, 2.0, 0.0, 8.0, 14.0, 16.0)}
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
        Block.box(1.0, 1.0, 0.0, 15.0, 15.0, 15.0),
        new VoxelShape[]{Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 15.0), Block.box(0.0, 6.0, 0.0, 16.0, 10.0, 15.0), Block.box(0.0, 2.0, 4.0, 16.0, 14.0, 8.0)}
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
        Block.box(1.0, 1.0, 1.0, 16.0, 15.0, 15.0),
        new VoxelShape[]{Block.box(1.0, 0.0, 6.0, 16.0, 16.0, 10.0), Block.box(1.0, 6.0, 0.0, 16.0, 10.0, 16.0), Block.box(8.0, 2.0, 0.0, 12.0, 14.0, 16.0)}
    );

    public BearingAxleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)this.defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(HORIZONTAL_FACING, ShiftPlacement.facing(context));
    }

    public static Direction front(BlockState state) {
        return ((Direction)state.getValue(HORIZONTAL_FACING)).getOpposite();
    }

    public static Direction shaftFace(BlockState state) {
        return (Direction)state.getValue(HORIZONTAL_FACING);
    }

    public static Direction getPlusFace(BlockState state) {
        return front(state).getClockWise();
    }

    public static int gearSignal(Level level, BlockPos pos, BlockState state) {
        BlockPos above = pos.above();
        return level.getBlockState(above).getBlock() instanceof AnalogControllerBlock ? 0 : level.getSignal(above, Direction.UP);
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BearingAxleBlockEntity be) {
            be.setPlacedTime(level.getGameTime());
            be.setGearSignal(gearSignal(level, pos, state));
            if (FrequencyBindUtil.isBound(stack)) {
                RegistryAccess reg = level.registryAccess();
                ItemStack[] left = FrequencyBindUtil.read(stack, reg, "Left");
                if (left != null) {
                    be.setLeftFrequency(left[0], left[1]);
                }

                ItemStack[] right = FrequencyBindUtil.read(stack, reg, "Right");
                if (right != null) {
                    be.setRightFrequency(right[0], right[1]);
                }
            }
        }
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        if (level.isClientSide && !newState.is(this)) {
            BlockPos bogey = BearingAxleValidity.driverBogey(level, pos);
            if (bogey != null) {
                BlockState bogeyState = level.getBlockState(bogey);
                level.setBlocksDirty(bogey, bogeyState, bogeyState);
            }
        }
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch ((Direction)state.getValue(HORIZONTAL_FACING)) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == shaftFace(state) || face == front(state) && BogeyShaft.passes(world, pos.relative(face), face);
    }

    public Axis getRotationAxis(BlockState state) {
        return front(state).getAxis();
    }

    public InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (worldIn.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (worldIn.getBlockEntity(pos) instanceof BearingAxleBlockEntity axle) {
            player.openMenu(axle, axle::sendToMenu);
        }

        return InteractionResult.CONSUME;
    }

    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (!worldIn.isClientSide) {
            Direction plusFace = getPlusFace(state);
            Direction minusFace = plusFace.getOpposite();
            boolean fromPlus = fromPos.equals(pos.relative(plusFace));
            boolean fromMinus = fromPos.equals(pos.relative(minusFace));
            if (worldIn.getBlockEntity(pos) instanceof BearingAxleBlockEntity axle) {
                axle.setGearSignal(gearSignal(worldIn, pos, state));
                if (fromPlus) {
                    axle.setRightRedstone(worldIn.getSignal(pos.relative(plusFace), plusFace) > 0);
                }

                if (fromMinus) {
                    axle.setLeftRedstone(worldIn.getSignal(pos.relative(minusFace), minusFace) > 0);
                }
            }
        }
    }

    public Class<BearingAxleBlockEntity> getBlockEntityClass() {
        return BearingAxleBlockEntity.class;
    }

    public BlockEntityType<? extends BearingAxleBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends BearingAxleBlockEntity>)LoconauticsRegistries.BEARING_AXLE_BE.get();
    }
}
