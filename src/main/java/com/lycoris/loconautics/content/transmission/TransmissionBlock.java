package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.content.frequency.FrequencyBindUtil;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TransmissionBlock extends AbstractEncasedShaftBlock implements IBE<TransmissionBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty PLUS_SIDE = BooleanProperty.create("plus_side");
    private static final VoxelShape Z_SHAPE = Shapes.or(
        Block.box(0.0, 0.0, 9.0, 16.0, 16.0, 16.0), new VoxelShape[]{Block.box(1.0, 1.0, 2.0, 15.0, 15.0, 9.0), Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0)}
    );
    private static final VoxelShape X_SHAPE = Shapes.or(
        Block.box(0.0, 0.0, 0.0, 7.0, 16.0, 16.0), new VoxelShape[]{Block.box(7.0, 1.0, 1.0, 14.0, 15.0, 15.0), Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0)}
    );
    private static final VoxelShape Y_SHAPE = Shapes.or(
        Block.box(0.0, 9.0, 0.0, 16.0, 16.0, 16.0), new VoxelShape[]{Block.box(1.0, 2.0, 1.0, 15.0, 9.0, 15.0), Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0)}
    );

    public TransmissionBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.defaultBlockState().setValue(POWERED, false)).setValue(PLUS_SIDE, false));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[]{POWERED, PLUS_SIDE});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)((BlockState)super.getStateForPlacement(context).setValue(POWERED, false)).setValue(PLUS_SIDE, false);
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && FrequencyBindUtil.isBound(stack) && level.getBlockEntity(pos) instanceof TransmissionBlockEntity be) {
            RegistryAccess var10 = level.registryAccess();
            ItemStack[] speed = FrequencyBindUtil.read(stack, var10, "Speed");
            if (speed != null) {
                be.setSpeedFrequency(speed[0], speed[1]);
            }

            ItemStack[] dir = FrequencyBindUtil.read(stack, var10, "Direction");
            if (dir != null) {
                be.setDirectionFrequency(dir[0], dir[1]);
            }
        }
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch ((Axis)state.getValue(BlockStateProperties.AXIS)) {
            case X -> X_SHAPE;
            case Y -> Y_SHAPE;
            case Z -> Z_SHAPE;
            default -> throw new MatchException(null, null);
        };
    }

    public Class<TransmissionBlockEntity> getBlockEntityClass() {
        return TransmissionBlockEntity.class;
    }

    public BlockEntityType<? extends TransmissionBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends TransmissionBlockEntity>)LoconauticsRegistries.TRANSMISSION_BE.get();
    }

    public InteractionResult useWithoutItem(BlockState state, Level worldIn, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (worldIn.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (worldIn.getBlockEntity(pos) instanceof TransmissionBlockEntity tbe) {
            player.openMenu(tbe, tbe::sendToMenu);
        }

        return InteractionResult.CONSUME;
    }

    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (!worldIn.isClientSide) {
            Axis axis = (Axis)state.getValue(BlockStateProperties.AXIS);
            boolean plusSide = (Boolean)state.getValue(PLUS_SIDE);
            Direction plusFace = getPlusFace(axis, plusSide);
            Direction minusFace = plusFace.getOpposite();
            boolean fromPlus = fromPos.equals(pos.relative(plusFace));
            boolean fromMinus = fromPos.equals(pos.relative(minusFace));
            if (fromPlus || fromMinus) {
                if (worldIn.getBlockEntity(pos) instanceof TransmissionBlockEntity tbe) {
                    if (fromPlus) {
                        int speedSignal = worldIn.getSignal(pos.relative(plusFace), plusFace.getOpposite());
                        tbe.setRedstonePower(speedSignal);
                    }

                    if (fromMinus) {
                        boolean dirSignal = worldIn.getSignal(pos.relative(minusFace), minusFace.getOpposite()) > 0;
                        tbe.setDirectionActive(dirSignal);
                    }
                }
            }
        }
    }

    public boolean isSignalSource(BlockState state) {
        return false;
    }

    public static Direction getPlusFace(Axis axis, boolean plusSide) {
        Direction base = switch (axis) {
            case X -> Direction.NORTH;
            case Y -> Direction.WEST;
            case Z -> Direction.WEST;
            default -> throw new MatchException(null, null);
        };
        return plusSide ? base.getOpposite() : base;
    }

    public static Direction getPlusFace(BlockState state) {
        return getPlusFace((Axis)state.getValue(BlockStateProperties.AXIS), (Boolean)state.getValue(PLUS_SIDE));
    }

    public static Direction getMinusFace(BlockState state) {
        return getPlusFace(state).getOpposite();
    }

    public BlockState getRotatedBlockState(BlockState state, Direction targetedFace) {
        Axis currentAxis = (Axis)state.getValue(BlockStateProperties.AXIS);
        return targetedFace.getAxis() == currentAxis
            ? (BlockState)state.cycle(PLUS_SIDE)
            : (BlockState)super.getRotatedBlockState(state, targetedFace).setValue(PLUS_SIDE, false);
    }

    public void detachKinetics(Level worldIn, BlockPos pos) {
        if (worldIn.getBlockEntity(pos) instanceof KineticBlockEntity kbe) {
            RotationPropagator.handleRemoved(worldIn, pos, kbe);
        }
    }
}
