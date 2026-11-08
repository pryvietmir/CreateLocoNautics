package com.lycoris.loconautics.content.boiler.barrel;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.placement.PoleHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BoilerBarrelBlock extends DirectionalBlock implements IWrenchable, EntityBlock, BlockSubLevelCollisionShape {
    public static final BooleanProperty CONNECTED_POS = BooleanProperty.create("connected_pos");
    public static final BooleanProperty CONNECTED_NEG = BooleanProperty.create("connected_neg");
    public static final BooleanProperty WRENCHED_POS = BooleanProperty.create("wrenched_pos");
    public static final BooleanProperty WRENCHED_NEG = BooleanProperty.create("wrenched_neg");
    public static final BooleanProperty SMOKE_POS = BooleanProperty.create("smoke_pos");
    public static final BooleanProperty SMOKE_NEG = BooleanProperty.create("smoke_neg");
    private static final int SMALL_HELPER_ID = PlacementHelpers.register(new BoilerBarrelBlock.BarrelPlacementHelper(false));
    private static final int BIG_HELPER_ID = PlacementHelpers.register(new BoilerBarrelBlock.BarrelPlacementHelper(true));
    private static final double[] SMALL = new double[]{-8.0, 24.0, 1.373, 14.629};
    private static final double[] BIG = new double[]{-16.0, 32.0, -0.532, 16.532};
    private static final int CORNER_STEPS = 3;
    private static final double SQRT_HALF = Math.sqrt(0.5);
    private static final VoxelShape[][] VISUAL_SHAPES = buildAll(BoilerBarrelBlock::buildVisualBarrel);
    private static final VoxelShape[][] COLLISION_SHAPES = buildAll(BoilerBarrelBlock::buildSteppedBarrel);
    public static final MapCodec<BoilerBarrelBlock> CODEC = RecordCodecBuilder.mapCodec(
        inst -> inst.group(propertiesCodec(), Codec.STRING.fieldOf("color").forGetter(b -> b.color), Codec.BOOL.fieldOf("big").forGetter(b -> b.big))
            .apply(inst, BoilerBarrelBlock::new)
    );
    public final String color;
    public final boolean big;

    private static VoxelShape[][] buildAll(BiFunction<Axis, double[], VoxelShape> build) {
        VoxelShape[][] out = new VoxelShape[2][3];

        for (int b = 0; b < 2; b++) {
            double[] oct = b == 1 ? BIG : SMALL;

            for (Axis axis : Axis.values()) {
                out[b][axis.ordinal()] = build.apply(axis, oct);
            }
        }

        return out;
    }

    private static VoxelShape buildVisualBarrel(Axis lengthAxis, double[] oct) {
        double outLo = oct[0];
        double outHi = oct[1];
        double flatLo = oct[2];
        double flatHi = oct[3];
        VoxelShape straight = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi), boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi)).optimize();
        double half = (outHi - outLo) / 32.0;
        double flat = (flatHi - flatLo) / 32.0;
        double along = (half + flat) * SQRT_HALF;
        double across = (half - flat) * SQRT_HALF;
        List<AABB> turned = List.of(turnedBox(lengthAxis, -along, along, -across, across), turnedBox(lengthAxis, -across, across, -along, along));
        return TiltedShape.of(
            straight, turned, point -> turnedToBlock(lengthAxis, point), point -> blockToTurned(lengthAxis, point), octagonOutline(lengthAxis, half, flat)
        );
    }

    private static List<Vec3[]> octagonOutline(Axis lengthAxis, double half, double flat) {
        double[][] rim = new double[][]{{half, flat}, {flat, half}, {-flat, half}, {-half, flat}, {-half, -flat}, {-flat, -half}, {flat, -half}, {half, -flat}};
        List<Vec3[]> edges = new ArrayList<>();

        for (int i = 0; i < rim.length; i++) {
            double[] here = rim[i];
            double[] next = rim[(i + 1) % rim.length];
            Vec3 lo = rimPoint(lengthAxis, here, 0.0);
            Vec3 hi = rimPoint(lengthAxis, here, 1.0);
            edges.add(new Vec3[]{lo, hi});
            edges.add(new Vec3[]{lo, rimPoint(lengthAxis, next, 0.0)});
            edges.add(new Vec3[]{hi, rimPoint(lengthAxis, next, 1.0)});
        }

        return edges;
    }

    private static Vec3 rimPoint(Axis lengthAxis, double[] cross, double along) {
        double u = cross[0] + 0.5;
        double v = cross[1] + 0.5;

        return switch (lengthAxis) {
            case X -> new Vec3(along, u, v);
            case Y -> new Vec3(u, along, v);
            case Z -> new Vec3(u, v, along);
            default -> throw new MatchException(null, null);
        };
    }

    private static AABB turnedBox(Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> new AABB(0.0, u0, v0, 1.0, u1, v1);
            case Y -> new AABB(u0, 0.0, v0, u1, 1.0, v1);
            case Z -> new AABB(u0, v0, 0.0, u1, v1, 1.0);
            default -> throw new MatchException(null, null);
        };
    }

    private static Vec3 turnedToBlock(Axis lengthAxis, Vec3 turned) {
        double s = crossU(lengthAxis, turned);
        double t = crossV(lengthAxis, turned);
        return withCross(lengthAxis, turned, (s - t) * SQRT_HALF + 0.5, (s + t) * SQRT_HALF + 0.5);
    }

    private static Vec3 blockToTurned(Axis lengthAxis, Vec3 local) {
        double u = crossU(lengthAxis, local) - 0.5;
        double v = crossV(lengthAxis, local) - 0.5;
        return withCross(lengthAxis, local, (u + v) * SQRT_HALF, (v - u) * SQRT_HALF);
    }

    private static double crossU(Axis lengthAxis, Vec3 point) {
        return lengthAxis == Axis.X ? point.y : point.x;
    }

    private static double crossV(Axis lengthAxis, Vec3 point) {
        return lengthAxis == Axis.Z ? point.y : point.z;
    }

    private static Vec3 withCross(Axis lengthAxis, Vec3 point, double u, double v) {
        return switch (lengthAxis) {
            case X -> new Vec3(point.x, u, v);
            case Y -> new Vec3(u, point.y, v);
            case Z -> new Vec3(u, v, point.z);
            default -> throw new MatchException(null, null);
        };
    }

    private static VoxelShape buildSteppedBarrel(Axis lengthAxis, double[] oct) {
        double outLo = oct[0];
        double outHi = oct[1];
        double flatLo = oct[2];
        double flatHi = oct[3];
        double step = (flatLo - outLo) / 3.0;
        VoxelShape shape = Shapes.or(boxForAxis(lengthAxis, outLo, outHi, flatLo, flatHi), boxForAxis(lengthAxis, flatLo, flatHi, outLo, outHi));

        for (int k = 0; k < 3; k++) {
            double inset = (k + 1) * step;
            double u0 = outLo + inset;
            double u1 = outHi - inset;
            shape = Shapes.or(shape, boxForAxis(lengthAxis, u0, u1, flatHi + k * step, flatHi + (k + 1) * step));
            shape = Shapes.or(shape, boxForAxis(lengthAxis, u0, u1, flatLo - (k + 1) * step, flatLo - k * step));
        }

        return shape.optimize();
    }

    private static VoxelShape boxForAxis(Axis lengthAxis, double u0, double u1, double v0, double v1) {
        return switch (lengthAxis) {
            case X -> Block.box(0.0, u0, v0, 16.0, u1, v1);
            case Y -> Block.box(u0, 0.0, v0, u1, 16.0, v1);
            case Z -> Block.box(u0, v0, 0.0, u1, v1, 16.0);
            default -> throw new MatchException(null, null);
        };
    }

    private VoxelShape shapeFor(BlockState state, VoxelShape[][] shapes) {
        return shapes[this.big ? 1 : 0][((Direction)state.getValue(FACING)).getAxis().ordinal()];
    }

    static VoxelShape bigOctagon(Axis axis) {
        return VISUAL_SHAPES[1][axis.ordinal()];
    }

    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    public BoilerBarrelBlock(Properties properties, String color, boolean big) {
        super(properties);
        this.color = color;
        this.big = big;
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any())
                                        .setValue(FACING, Direction.NORTH))
                                    .setValue(CONNECTED_POS, false))
                                .setValue(CONNECTED_NEG, false))
                            .setValue(WRENCHED_POS, false))
                        .setValue(WRENCHED_NEG, false))
                    .setValue(SMOKE_POS, false))
                .setValue(SMOKE_NEG, false)
        );
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, CONNECTED_POS, CONNECTED_NEG, WRENCHED_POS, WRENCHED_NEG, SMOKE_POS, SMOKE_NEG});
    }

    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this.shapeFor(state, VISUAL_SHAPES);
    }

    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return this.shapeFor(state, COLLISION_SHAPES);
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return this.shapeFor(state, COLLISION_SHAPES);
    }

    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    private boolean sameFamily(LevelAccessor level, BlockPos pos, Axis axis) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BoilerBarrelBlock other && other.big == this.big && ((Direction)s.getValue(FACING)).getAxis() == axis;
    }

    private boolean autoJoins(LevelAccessor level, BlockPos pos, Axis axis) {
        return this.sameFamily(level, pos, axis) && level.getBlockState(pos).getBlock() == this;
    }

    static Direction positiveOf(Axis axis) {
        return Direction.fromAxisAndDirection(axis, AxisDirection.POSITIVE);
    }

    static BooleanProperty connectedFor(Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? CONNECTED_POS : CONNECTED_NEG;
    }

    static BooleanProperty wrenchedFor(Axis axis, Direction toNeighbor) {
        return toNeighbor == positiveOf(axis) ? WRENCHED_POS : WRENCHED_NEG;
    }

    static BooleanProperty smokeEndFor(Axis axis, Direction toEnd) {
        return toEnd == positiveOf(axis) ? SMOKE_POS : SMOKE_NEG;
    }

    protected BlockState rotate(BlockState state, Rotation rotation) {
        return turnEnds(state, rotation::rotate);
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        Rotation turn = mirror.getRotation((Direction)state.getValue(FACING));
        return turnEnds(state, turn::rotate);
    }

    private static BlockState turnEnds(BlockState state, UnaryOperator<Direction> turn) {
        Direction facing = (Direction)state.getValue(FACING);
        boolean swapped = turn.apply(positiveOf(facing.getAxis())).getAxisDirection() != AxisDirection.POSITIVE;
        return (BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)((BlockState)state.setValue(FACING, turn.apply(facing)))
                                .setValue(CONNECTED_POS, (Boolean)state.getValue(swapped ? CONNECTED_NEG : CONNECTED_POS)))
                            .setValue(CONNECTED_NEG, (Boolean)state.getValue(swapped ? CONNECTED_POS : CONNECTED_NEG)))
                        .setValue(WRENCHED_POS, (Boolean)state.getValue(swapped ? WRENCHED_NEG : WRENCHED_POS)))
                    .setValue(WRENCHED_NEG, (Boolean)state.getValue(swapped ? WRENCHED_POS : WRENCHED_NEG)))
                .setValue(SMOKE_POS, (Boolean)state.getValue(swapped ? SMOKE_NEG : SMOKE_POS)))
            .setValue(SMOKE_NEG, (Boolean)state.getValue(swapped ? SMOKE_POS : SMOKE_NEG));
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getNearestLookingDirection();
        Direction facing = look.getOpposite();
        Axis axis = facing.getAxis();
        BlockState state = (BlockState)((BlockState)this.defaultBlockState().setValue(FACING, facing)).setValue(smokeEndFor(axis, look), true);

        for (Direction end : new Direction[]{positiveOf(axis), positiveOf(axis).getOpposite()}) {
            boolean join = this.autoJoins(context.getLevel(), context.getClickedPos().relative(end), axis);
            state = (BlockState)state.setValue(connectedFor(axis, end), join);
        }

        return state;
    }

    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Axis axis = ((Direction)state.getValue(FACING)).getAxis();
        if (direction.getAxis() != axis) {
            return state;
        }

        BooleanProperty connected = connectedFor(axis, direction);
        BooleanProperty wrenched = wrenchedFor(axis, direction);
        return !this.sameFamily(level, neighborPos, axis)
            ? (BlockState)((BlockState)state.setValue(connected, false)).setValue(wrenched, false)
            : (BlockState)state.setValue(connected, this.autoJoins(level, neighborPos, axis) != (Boolean)state.getValue(wrenched));
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoilerBarrelBlockEntity((BlockEntityType<?>)BoilerBlocks.BOILER_BARREL_BE.get(), pos, state);
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        IPlacementHelper helper = PlacementHelpers.get(this.big ? BIG_HELPER_ID : SMALL_HELPER_ID);
        return !helper.matchesItem(stack)
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : helper.getOffset(player, level, state, pos, hit).placeInWorld(level, (BlockItem)stack.getItem(), player, hand, hit);
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Axis axis = ((Direction)state.getValue(FACING)).getAxis();
        Direction clicked = context.getClickedFace();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (clicked.getAxis() == axis) {
            if (!level.isClientSide()) {
                BooleanProperty smoke = smokeEndFor(axis, clicked);
                level.setBlock(pos, (BlockState)state.cycle(smoke), 2);
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        } else {
            Direction end = this.aimedEnd(state, context);
            BlockPos neighborPos = pos.relative(end);
            if (!this.sameFamily(level, neighborPos, axis)) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide()) {
                boolean connect = !(Boolean)state.getValue(connectedFor(axis, end));
                this.toggleSeam(level, pos, neighborPos, axis, end, connect);
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }
    }

    private Direction aimedEnd(BlockState state, UseOnContext context) {
        Axis axis = ((Direction)state.getValue(FACING)).getAxis();
        Vec3 rel = context.getClickLocation().subtract(Vec3.atCenterOf(context.getClickedPos()));
        double comp = axis.choose(rel.x, rel.y, rel.z);
        Direction pos = positiveOf(axis);
        return comp >= 0.0 ? pos : pos.getOpposite();
    }

    private void toggleSeam(Level level, BlockPos pos, BlockPos neighborPos, Axis axis, Direction end, boolean connect) {
        boolean wrenched = connect != this.autoJoins(level, neighborPos, axis);
        BlockState here = level.getBlockState(pos);
        BlockState there = level.getBlockState(neighborPos);
        Direction back = end.getOpposite();
        level.setBlock(pos, (BlockState)((BlockState)here.setValue(connectedFor(axis, end), connect)).setValue(wrenchedFor(axis, end), wrenched), 2);
        level.setBlock(neighborPos, (BlockState)((BlockState)there.setValue(connectedFor(axis, back), connect)).setValue(wrenchedFor(axis, back), wrenched), 2);
    }

    private static class BarrelPlacementHelper extends PoleHelper<Direction> {
        private final boolean big;

        BarrelPlacementHelper(boolean big) {
            super(sameSize(big), s -> ((Direction)s.getValue(DirectionalBlock.FACING)).getAxis(), DirectionalBlock.FACING);
            this.big = big;
        }

        private static Predicate<BlockState> sameSize(boolean big) {
            return s -> s.getBlock() instanceof BoilerBarrelBlock b && b.big == big;
        }

        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem item && item.getBlock() instanceof BoilerBarrelBlock b && b.big == this.big;
        }
    }
}
