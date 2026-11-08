package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoseWinchBlock extends DirectionalAxisKineticBlock implements IBE<HoseWinchBlockEntity>, BlockSubLevelAssemblyListener, BlockSubLevelCollisionShape {
    public static final BooleanProperty HAS_COIL = BooleanProperty.create("has_coil");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape BASE_SHAPE = Shapes.or(
        Block.box(0.0, 0.0, 4.0, 16.0, 3.0, 12.0),
        new VoxelShape[]{
            Block.box(0.0, 2.0, 2.0, 3.0, 14.0, 14.0),
            Block.box(13.0, 2.0, 2.0, 16.0, 14.0, 14.0),
            Block.box(3.0, 4.0, 4.0, 13.0, 12.0, 12.0),
            Block.box(3.0, 13.0, 2.0, 13.0, 14.0, 14.0),
            Block.box(3.0, 14.0, 3.0, 13.0, 16.0, 13.0)
        }
    );
    private static final VoxelShape PHYSICS_BASE = Block.box(1.0, 0.0, 1.0, 15.0, 0.25, 15.0);
    private static final Map<Direction, VoxelShape> SHAPES_ALONG = shapes(BASE_SHAPE, true);
    private static final Map<Direction, VoxelShape> SHAPES_ACROSS = shapes(BASE_SHAPE, false);
    private static final Map<Direction, VoxelShape> PHYSICS_ALONG = shapes(PHYSICS_BASE, true);
    private static final Map<Direction, VoxelShape> PHYSICS_ACROSS = shapes(PHYSICS_BASE, false);

    public HoseWinchBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.defaultBlockState().setValue(HAS_COIL, false)).setValue(POWERED, false));
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : (BlockState)state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(new Property[]{HAS_COIL, POWERED});
    }

    protected Direction getFacingForPlacement(BlockPlaceContext context) {
        return context.getClickedFace().getOpposite();
    }

    protected boolean getAxisAlignmentForPlacement(BlockPlaceContext context) {
        return context.getHorizontalDirection().getAxis() != Axis.X;
    }

    public Axis getRotationAxis(BlockState state) {
        return switch (((Direction)state.getValue(FACING)).getAxis()) {
            case Y -> state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z;
            case X -> Axis.Z;
            case Z -> Axis.X;
            default -> throw new MatchException(null, null);
        };
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? SHAPES_ALONG : SHAPES_ACROSS;
        return shapes.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        Map<Direction, VoxelShape> shapes = state.getValue(AXIS_ALONG_FIRST_COORDINATE) ? PHYSICS_ALONG : PHYSICS_ACROSS;
        return shapes.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> shapes(VoxelShape base, boolean along) {
        int flat = along ? 0 : 1;
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.DOWN, turn(base, 0, flat));
        shapes.put(Direction.UP, turn(base, 2, flat));
        shapes.put(Direction.NORTH, turn(base, 3, 2));
        shapes.put(Direction.SOUTH, turn(base, 3, 0));
        shapes.put(Direction.EAST, turn(base, 3, 3));
        shapes.put(Direction.WEST, turn(base, 3, 1));
        return shapes;
    }

    private static VoxelShape turn(VoxelShape shape, int quarterTurnsX, int quarterTurnsY) {
        for (int i = 0; i < quarterTurnsX; i++) {
            shape = turnClockwiseX(shape);
        }

        for (int i = 0; i < quarterTurnsY; i++) {
            shape = turnClockwiseY(shape);
        }

        return shape;
    }

    private static VoxelShape turnClockwiseX(VoxelShape shape) {
        List<VoxelShape> turned = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> turned.add(Shapes.box(minX, 1.0 - maxZ, minY, maxX, 1.0 - minZ, maxY)));
        return turned.stream().reduce(Shapes.empty(), Shapes::or);
    }

    private static VoxelShape turnClockwiseY(VoxelShape shape) {
        List<VoxelShape> turned = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> turned.add(Shapes.box(1.0 - maxZ, minY, minX, 1.0 - minZ, maxY, maxX)));
        return turned.stream().reduce(Shapes.empty(), Shapes::or);
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HoseWinchBlockEntity be) {
            be.onWinchBroken();
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(pos);
            if ((Boolean)state.getValue(POWERED) != powered) {
                level.setBlock(pos, (BlockState)state.setValue(POWERED, powered), 2);
            }
        }
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    public Class<HoseWinchBlockEntity> getBlockEntityClass() {
        return HoseWinchBlockEntity.class;
    }

    public BlockEntityType<? extends HoseWinchBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends HoseWinchBlockEntity>)LoconauticsRegistries.HOSE_WINCH_BE.get();
    }
}
