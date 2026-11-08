package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.common.strand.StrandRemap;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class KnuckleBlock
    extends HorizontalDirectionalBlock
    implements IBE<KnuckleBlockEntity>,
    IWrenchable,
    BlockSubLevelCollisionShape,
    BlockSubLevelAssemblyListener {
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    public static final MapCodec<KnuckleBlock> CODEC = simpleCodec(KnuckleBlock::new);
    private static final Map<Direction, VoxelShape> DOCKED_SHAPES = VoxelShapes.byHorizontalFacing(Block.box(0.0, 4.0, 13.0, 16.0, 16.0, 16.0));
    private static final double OPEN_COUPLING_YAW = -22.5;
    private static final double OPEN_PIN_LIFT = 0.0625;
    private static final Vec3 CENTER = new Vec3(0.5, 0.0, 0.5);
    private static final Vec3 COUPLING_PIVOT_NORTH = new Vec3(0.84375, 0.0, 0.21875);
    private static final AABB COUPLING_UPPER_NORTH = aabb(7.0, 7.0, 7.1, 12.0, 13.0, 9.1);
    private static final AABB COUPLING_LOWER_NORTH = aabb(4.5, 6.0, 0.0, 13.5, 14.0, 3.0);
    private static final AABB PIN_UPPER_NORTH = aabb(7.2, 10.2, 7.2, 8.8, 15.8, 8.8);
    private static final AABB PIN_LOWER_NORTH = aabb(7.2, 3.7, 7.2, 8.8, 9.3, 8.8);
    private static final double COUPLER_LIFT = CopycatPlacement.CENTER.blocks();
    private static final Map<Direction, VoxelShape> CLOSED_COUPLER;
    private static final Map<Direction, VoxelShape> OPEN_COUPLER;
    private static final Map<Direction, List<AABB>> OPEN_COUPLING;
    private static final Map<Direction, Vec3> COUPLING_PIVOT;
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> CLOSED_COLLISION;
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> OPEN_COLLISION;

    private static Map<CopycatPlacement, Map<Direction, VoxelShape>> liftedByPlacement(Map<Direction, VoxelShape> coupler) {
        EnumMap<CopycatPlacement, Map<Direction, VoxelShape>> byPlacement = new EnumMap<>(CopycatPlacement.class);

        for (CopycatPlacement placement : CopycatPlacement.values()) {
            EnumMap<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);

            for (Direction facing : Plane.HORIZONTAL) {
                VoxelShape whole = Shapes.or(DOCKED_SHAPES.get(facing).move(0.0, placement.blocks(), 0.0), coupler.get(facing).move(0.0, COUPLER_LIFT, 0.0));
                byFacing.put(facing, Shapes.join(whole, Shapes.block(), BooleanOp.AND).optimize());
            }

            byPlacement.put(placement, byFacing);
        }

        return byPlacement;
    }

    private static AABB swungBounds(AABB box, Vec3 pivot, double degrees) {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (Vec3 corner : new Vec3[]{
            new Vec3(box.minX, box.minY, box.minZ),
            new Vec3(box.maxX, box.minY, box.minZ),
            new Vec3(box.minX, box.minY, box.maxZ),
            new Vec3(box.maxX, box.minY, box.maxZ)
        }) {
            Vec3 turned = spin(corner, pivot, degrees);
            minX = Math.min(minX, turned.x);
            maxX = Math.max(maxX, turned.x);
            minZ = Math.min(minZ, turned.z);
            maxZ = Math.max(maxZ, turned.z);
        }

        return new AABB(minX, box.minY, minZ, maxX, box.maxY, maxZ);
    }

    private static AABB aabb(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
    }

    private static double shapeYaw(Direction facing) {
        return switch (facing) {
            case EAST -> 270.0;
            case SOUTH -> 180.0;
            case WEST -> 90.0;
            default -> 0.0;
        };
    }

    private static Vec3 spin(Vec3 point, Vec3 pivot, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double dx = point.x - pivot.x;
        double dz = point.z - pivot.z;
        return new Vec3(pivot.x + dx * cos + dz * sin, point.y, pivot.z - dx * sin + dz * cos);
    }

    private static AABB spin(AABB box, Vec3 pivot, double degrees) {
        Vec3 min = spin(new Vec3(box.minX, box.minY, box.minZ), pivot, degrees);
        Vec3 max = spin(new Vec3(box.maxX, box.maxY, box.maxZ), pivot, degrees);
        return new AABB(Math.min(min.x, max.x), min.y, Math.min(min.z, max.z), Math.max(min.x, max.x), max.y, Math.max(min.z, max.z));
    }

    public static boolean redstoneLocked(BlockGetter level, BlockPos pos, Direction facing) {
        return level instanceof Level actual && actual.hasNeighborSignal(pos.relative(facing.getOpposite()));
    }

    public KnuckleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH)).setValue(CONNECTED, false))
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP)
        );
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = (Direction)state.getValue(FACING);
        VoxelShape plate = DOCKED_SHAPES.get(facing).move(0.0, ((CopycatPlacement)state.getValue(CopycatPlacement.PLACEMENT)).blocks(), 0.0);
        if ((Boolean)state.getValue(CONNECTED)) {
            return plate;
        }

        if (redstoneLocked(level, pos, facing)) {
            return Shapes.or(plate, CLOSED_COUPLER.get(facing).move(0.0, COUPLER_LIFT, 0.0));
        }

        Vec3 pivot = COUPLING_PIVOT.get(facing);
        List<AABB> swung = new ArrayList<>(2);

        for (AABB box : OPEN_COUPLING.get(facing)) {
            swung.add(box.move(0.0, COUPLER_LIFT, 0.0));
        }

        return TiltedShape.of(
            Shapes.or(plate, OPEN_COUPLER.get(facing).move(0.0, COUPLER_LIFT, 0.0)),
            swung,
            point -> spin(point, pivot, -22.5),
            point -> spin(point, pivot, 22.5)
        );
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = (Direction)state.getValue(FACING);
        CopycatPlacement placement = (CopycatPlacement)state.getValue(CopycatPlacement.PLACEMENT);
        return !state.getValue(CONNECTED) && !redstoneLocked(level, pos, facing)
            ? OPEN_COLLISION.get(placement).get(facing)
            : CLOSED_COLLISION.get(placement).get(facing);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return Shapes.block();
    }

    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, CONNECTED, CopycatPlacement.PLACEMENT});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)((BlockState)this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(context)))
            .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.aimedAt(context));
    }

    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return CopycatBlockLogic.hasSupportBehind(level, pos, (Direction)state.getValue(FACING));
    }

    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == ((Direction)state.getValue(FACING)).getOpposite() && !state.canSurvive(level, pos)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        return CopycatBlockLogic.useItemOn(stack, level, pos, player, hit.getDirection());
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CopycatBlockLogic.placedBy(level, pos, placer);
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if ((Boolean)state.getValue(CONNECTED)) {
            if (!level.isClientSide()) {
                KnuckleConnectionManager.disconnect(level, context.getClickedPos());
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        } else {
            return !level.isClientSide() && level.getBlockEntity(context.getClickedPos()) instanceof KnuckleBlockEntity be && be.isRotateLocked()
                ? InteractionResult.SUCCESS
                : CopycatBlockLogic.cyclePlacement(state, level, context.getClickedPos());
        }
    }

    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, level, pos, context.getPlayer());
        if (reclaimed != InteractionResult.PASS) {
            return reclaimed;
        }

        if ((Boolean)state.getValue(CONNECTED)) {
            KnuckleConnectionManager.disconnect(level, pos);
        }

        return super.onSneakWrenched(state, context);
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            KnuckleConnectionManager.sever(level, pos);
            if (!movedByPiston) {
                CopycatBlockLogic.popConsumed(level, pos);
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        StrandRemap.afterMove(originLevel, resultingLevel, oldPos, newPos);
    }

    public Class<KnuckleBlockEntity> getBlockEntityClass() {
        return KnuckleBlockEntity.class;
    }

    public BlockEntityType<? extends KnuckleBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends KnuckleBlockEntity>)LoconauticsRegistries.KNUCKLE_BE.get();
    }

    static {
        VoxelShape body = Shapes.empty();
        body = Shapes.or(body, Block.box(6.0, 7.5, 10.0, 10.0, 12.5, 16.0));
        body = Shapes.or(body, Block.box(4.0, 5.5, 10.0, 12.0, 7.5, 16.0));
        body = Shapes.or(body, Block.box(4.0, 12.5, 10.0, 12.0, 14.5, 16.0));
        body = Shapes.or(body, Block.box(0.75736, 5.05, 4.09314, 5.70711, 14.95, 9.04289));
        body = Shapes.or(body, Block.box(0.757359, 6.025, 5.12468, 3.21197, 13.975, 9.20288));
        body = Shapes.or(body, Block.box(4.0, 5.0, 6.0, 12.0, 15.0, 10.0));
        body = Shapes.or(body, Block.box(2.0, 6.0, 7.0, 14.0, 14.0, 9.975));
        body = Shapes.or(body, Block.box(12.0, 5.0, 2.0, 15.0, 15.0, 9.0));
        VoxelShape closed = Shapes.or(
            body,
            new VoxelShape[]{
                Shapes.create(COUPLING_UPPER_NORTH), Shapes.create(COUPLING_LOWER_NORTH), Shapes.create(PIN_UPPER_NORTH), Shapes.create(PIN_LOWER_NORTH)
            }
        );
        VoxelShape open = Shapes.or(
            body, new VoxelShape[]{Shapes.create(PIN_UPPER_NORTH.move(0.0, 0.0625, 0.0)), Shapes.create(PIN_LOWER_NORTH.move(0.0, 0.0625, 0.0))}
        );
        CLOSED_COUPLER = VoxelShapes.byHorizontalFacing(closed);
        OPEN_COUPLER = VoxelShapes.byHorizontalFacing(open);
        EnumMap<Direction, List<AABB>> coupling = new EnumMap<>(Direction.class);
        EnumMap<Direction, Vec3> pivots = new EnumMap<>(Direction.class);

        for (Direction facing : Plane.HORIZONTAL) {
            double yaw = shapeYaw(facing);
            coupling.put(facing, List.of(spin(COUPLING_UPPER_NORTH, CENTER, yaw), spin(COUPLING_LOWER_NORTH, CENTER, yaw)));
            pivots.put(facing, spin(COUPLING_PIVOT_NORTH, CENTER, yaw));
        }

        OPEN_COUPLING = Map.copyOf(coupling);
        COUPLING_PIVOT = Map.copyOf(pivots);
        Map<Direction, VoxelShape> openSolid = new EnumMap<>(Direction.class);

        for (Direction facing : Plane.HORIZONTAL) {
            VoxelShape solid = OPEN_COUPLER.get(facing);

            for (AABB box : OPEN_COUPLING.get(facing)) {
                solid = Shapes.or(solid, Shapes.create(swungBounds(box, COUPLING_PIVOT.get(facing), -22.5)));
            }

            openSolid.put(facing, solid);
        }

        CLOSED_COLLISION = liftedByPlacement(CLOSED_COUPLER);
        OPEN_COLLISION = liftedByPlacement(openSolid);
    }
}
