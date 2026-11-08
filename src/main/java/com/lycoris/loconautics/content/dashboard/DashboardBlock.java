package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.client.connected.ConnectedBlock;
import com.lycoris.loconautics.client.connected.ConnectionBounds;
import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelHost;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DashboardBlock extends HorizontalDirectionalBlock implements ConnectedBlock, DyedBlock, IWrenchable, PanelHost, IBE<DashboardBlockEntity> {
    public static final int MAX_RUN = 5;
    public static final BooleanProperty JOIN_CW = BooleanProperty.create("join_cw");
    public static final BooleanProperty JOIN_CCW = BooleanProperty.create("join_ccw");
    private static final ConnectionBounds BOUNDS = new ConnectionBounds(5, 1, 5);
    public static final float BOARD_TILT = -22.5F;
    public static final float BOARD_TOP = 0.875F;
    public static final float BOARD_DEPTH = 1.0625F;
    private static final Map<Direction, VoxelShape> COLLISION = VoxelShapes.byHorizontalFacing(
        Shapes.or(box(0.0, 0.0, 4.0, 16.0, 2.0, 16.0), box(0.0, 2.0, 6.0, 16.0, 15.0, 16.0))
    );
    private static final Map<Direction, VoxelShape> BODY = VoxelShapes.byHorizontalFacing(
        Shapes.or(box(0.0, 0.0, 4.0, 16.0, 2.0, 16.0), box(0.0, 2.0, 8.0, 16.0, 8.0, 16.0))
    );
    private static final AABB BOARD_BOX = new AABB(0.0, -0.1875, 0.4375, 1.0, 0.0, 0.984375);
    private final DyeColor color;

    public DashboardBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)this.getStateDefinition().any()).setValue(FACING, Direction.NORTH)).setValue(JOIN_CW, false))
                .setValue(JOIN_CCW, false)
        );
    }

    @Override
    public DyeColor getColor() {
        return this.color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return (BlockState)((BlockState)((BlockState)((DashboardBlock)DashboardBlocks.DASHBOARDS.get(dyed).get())
                    .defaultBlockState()
                    .setValue(FACING, (Direction)state.getValue(FACING)))
                .setValue(JOIN_CW, (Boolean)state.getValue(JOIN_CW)))
            .setValue(JOIN_CCW, (Boolean)state.getValue(JOIN_CCW));
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new DashboardBlock(properties, this.color));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, JOIN_CW, JOIN_CCW});
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = ShiftPlacement.facing(context);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        int leftLength = this.freeEndLength(level, pos.relative(facing.getCounterClockWise()), facing, JOIN_CW);
        int rightLength = this.freeEndLength(level, pos.relative(facing.getClockWise()), facing, JOIN_CCW);
        boolean joinLeft = leftLength > 0 && leftLength < 5;
        boolean joinRight = rightLength > 0 && (joinLeft ? leftLength + 1 : 1) + rightLength <= 5;
        return (BlockState)((BlockState)((BlockState)this.defaultBlockState().setValue(FACING, facing)).setValue(JOIN_CCW, joinLeft))
            .setValue(JOIN_CW, joinRight);
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = (Direction)state.getValue(FACING);
        List<AABB> tilted = new ArrayList<>();
        tilted.add(BOARD_BOX);
        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel != null) {
            for (PanelSlot slot : panel.slots()) {
                PanelPart part = panel.getPart(slot);
                if (part != null) {
                    tilted.add(part.type().localBox(slot.modelX, slot.modelZ));
                }
            }
        }

        return TiltedShape.of(BODY.get(facing), tilted, point -> boardToBlock(facing, point), point -> blockToBoard(facing, point), outline(facing, tilted));
    }

    private static List<Vec3[]> outline(Direction facing, List<AABB> tilted) {
        AABB board = BOARD_BOX;
        Vec3[] profile = new Vec3[]{
            profilePoint(facing, 0.0, 0.25),
            profilePoint(facing, 0.125, 0.25),
            profilePoint(facing, 0.125, 0.5),
            board(facing, board.minY, board.minZ),
            board(facing, board.maxY, board.minZ),
            board(facing, board.maxY, board.maxZ),
            board(facing, board.minY, board.maxZ),
            profilePoint(facing, 0.0, 1.0)
        };
        List<Vec3[]> lines = new ArrayList<>();

        for (int i = 0; i < profile.length; i++) {
            Vec3 a = profile[i];
            Vec3 b = profile[(i + 1) % profile.length];
            Vec3 shift = acrossStep(facing);
            lines.add(new Vec3[]{a, b});
            lines.add(new Vec3[]{a.add(shift), b.add(shift)});
            lines.add(new Vec3[]{a, a.add(shift)});
        }

        for (int i = 1; i < tilted.size(); i++) {
            Vec3[] corners = boxCorners(facing, tilted.get(i));
            int[][] pairs = new int[][]{{0, 1}, {0, 2}, {1, 3}, {2, 3}, {4, 5}, {4, 6}, {5, 7}, {6, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

            for (int[] pair : pairs) {
                lines.add(new Vec3[]{corners[pair[0]], corners[pair[1]]});
            }
        }

        return lines;
    }

    private static Vec3 acrossStep(Direction facing) {
        Direction across = facing.getClockWise();
        return new Vec3(across.getStepX(), 0.0, across.getStepZ());
    }

    private static Vec3 profilePoint(Direction facing, double height, double depth) {
        double[] local = new double[]{0.0, height, 0.0};
        place(local, facing.getOpposite(), depth);
        place(local, facing.getClockWise(), 0.0);
        return new Vec3(local[0], local[1], local[2]);
    }

    private static Vec3 board(Direction facing, double y, double z) {
        return boardToBlock(facing, new Vec3(0.0, y, z));
    }

    private static Vec3[] boxCorners(Direction facing, AABB box) {
        Vec3[] corners = new Vec3[8];

        for (int i = 0; i < 8; i++) {
            corners[i] = boardToBlock(
                facing, new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ)
            );
        }

        return corners;
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION.get(state.getValue(FACING));
    }

    private static Vec3 boardToBlock(Direction facing, Vec3 board) {
        double sin = Math.sin(Math.toRadians(-22.5));
        double cos = Math.cos(Math.toRadians(-22.5));
        double depth = board.z - 1.0625;
        double modelY = board.y * cos - depth * sin + 0.875;
        double modelZ = board.y * sin + depth * cos + 1.0;
        Direction across = facing.getClockWise();
        double[] local = new double[]{0.0, modelY, 0.0};
        place(local, across, board.x);
        place(local, facing.getOpposite(), modelZ);
        return new Vec3(local[0], local[1], local[2]);
    }

    private static Vec3 blockToBoard(Direction facing, Vec3 local) {
        double modelX = modelCoord(local, facing.getClockWise());
        double modelZ = modelCoord(local, facing.getOpposite());
        double sin = Math.sin(Math.toRadians(22.5));
        double cos = Math.cos(Math.toRadians(22.5));
        double height = local.y - 0.875;
        double depth = modelZ - 1.0;
        return new Vec3(modelX, height * cos - depth * sin, height * sin + depth * cos + 1.0625);
    }

    private static void place(double[] local, Direction axis, double model) {
        int index = axis.getAxis() == Axis.X ? 0 : 2;
        local[index] = axis.getAxisDirection() == AxisDirection.POSITIVE ? model : 1.0 - model;
    }

    @Override
    public ConnectionBounds connectionBounds() {
        return BOUNDS;
    }

    @Override
    public BlockPos groupAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        return this.runAnchor(level, state, pos);
    }

    @Override
    public boolean connectsTo(BlockGetter level, BlockState state, BlockPos anchor, BlockPos neighborPos) {
        BlockState neighbor = level.getBlockState(neighborPos);
        return neighbor.is(this) && neighbor.getValue(FACING) == state.getValue(FACING) ? anchor.equals(this.runAnchor(level, neighbor, neighborPos)) : false;
    }

    private BlockPos runAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        Direction facing = (Direction)state.getValue(FACING);
        Direction back = facing.getCounterClockWise();
        BlockPos anchor = pos;
        BlockState current = state;

        for (int i = 0; i < 5 && current.getValue(JOIN_CCW); i++) {
            BlockPos previous = anchor.relative(back);
            BlockState previousState = level.getBlockState(previous);
            if (!this.matches(previousState, facing) || !(Boolean)previousState.getValue(JOIN_CW)) {
                break;
            }

            anchor = previous;
            current = previousState;
        }

        return anchor.immutable();
    }

    private int runLength(BlockGetter level, BlockPos pos, BlockState state) {
        Direction facing = (Direction)state.getValue(FACING);
        BlockPos at = this.runAnchor(level, state, pos);
        BlockState current = level.getBlockState(at);

        int length;
        for (length = 1; length < 5 && current.getValue(JOIN_CW); length++) {
            at = at.relative(facing.getClockWise());
            current = level.getBlockState(at);
            if (!this.matches(current, facing) || !(Boolean)current.getValue(JOIN_CCW)) {
                break;
            }
        }

        return length;
    }

    private int freeEndLength(BlockGetter level, BlockPos pos, Direction facing, BooleanProperty towardUs) {
        BlockState state = level.getBlockState(pos);
        return this.matches(state, facing) && !state.getValue(towardUs) ? this.runLength(level, pos, state) : 0;
    }

    private boolean matches(BlockState state, Direction facing) {
        return state.is(this) && state.getValue(FACING) == facing;
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        ItemInteractionResult painted = this.paint(stack, state, level, pos, player);
        if (painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return painted;
        }

        ItemInteractionResult result = this.configureTargetedPart(stack, state, level, pos, player, hit);
        if (result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            return result;
        }

        result = this.labelTargetedPart(stack, state, level, pos, player, hit);
        return result != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION ? result : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return this.useTargetedPart(state, level, pos, player, hit);
    }

    @Override
    public PanelSlot targetedSlot(BlockPos pos, BlockState state, Vec3 clickLocation) {
        Vec3 local = clickLocation.subtract(Vec3.atLowerCornerOf(pos));
        return modelCoord(local, ((Direction)state.getValue(FACING)).getClockWise()) < 0.5 ? PanelSlot.TOP_RIGHT : PanelSlot.TOP_LEFT;
    }

    private static double modelCoord(Vec3 local, Direction axis) {
        double along = axis.getAxis() == Axis.X ? local.x : local.z;
        return axis.getAxisDirection() == AxisDirection.NEGATIVE ? 1.0 - along : along;
    }

    @Nullable
    private static PanelSlot aimedPart(BlockState state, BlockPos pos, PanelBlockEntity panel, Vec3 hit) {
        Vec3 board = blockToBoard((Direction)state.getValue(FACING), hit.subtract(Vec3.atLowerCornerOf(pos)));

        for (PanelSlot slot : panel.slots()) {
            PanelPart part = panel.getPart(slot);
            if (part != null && part.type().localBox(slot.modelX, slot.modelZ).inflate(0.001953125).contains(board)) {
                return slot;
            }
        }

        return null;
    }

    @Override
    public boolean removedWhenEmpty() {
        return false;
    }

    @Override
    public boolean supportsCenteredPart() {
        return false;
    }

    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel != null && target instanceof BlockHitResult hit) {
            PanelSlot aimed = aimedPart(state, pos, panel, hit.getLocation());
            ItemStack stack = aimed == null ? ItemStack.EMPTY : panel.stackForSlot(aimed);
            if (!stack.isEmpty()) {
                return player != null && player.isShiftKeyDown() ? stack : new ItemStack(stack.getItem());
            }
        }

        return super.getCloneItemStack(state, target, level, pos, player);
    }

    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        PanelBlockEntity panel = PanelHost.panelAt(level, pos);
        if (panel != null && panel.partCount() != 0) {
            PanelSlot aimed = player.pick(10.0, 0.0F, false) instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)
                ? aimedPart(state, pos, panel, hit.getLocation())
                : null;
            if (aimed == null) {
                dropAllParts(level, pos, panel, player);
                return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
            }

            if (!level.isClientSide) {
                ItemStack stack = panel.stackForSlot(aimed);
                panel.removePart(aimed);
                if (!player.isCreative()) {
                    Block.popResource(level, pos, stack);
                }
            }

            return false;
        } else {
            return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        }
    }

    private static void dropAllParts(Level level, BlockPos pos, PanelBlockEntity panel, Player player) {
        if (!level.isClientSide) {
            for (PanelSlot slot : panel.slots()) {
                if (panel.hasPart(slot)) {
                    ItemStack stack = panel.stackForSlot(slot);
                    panel.removePart(slot);
                    if (!player.isCreative()) {
                        Block.popResource(level, pos, stack);
                    }
                }
            }
        }
    }

    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        PanelBlockEntity panel = PanelHost.panelAt(context.getLevel(), context.getClickedPos());
        return panel != null && panel.partCount() != 0 ? this.takeTargetedPartWithWrench(state, context) : super.onSneakWrenched(state, context);
    }

    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        List<ItemStack> parts = PanelHost.partDrops(params);
        if (parts == null) {
            return super.getDrops(state, params);
        }

        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        drops.addAll(parts);
        return drops;
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof DashboardBlock)) {
            PanelBlockEntity panel = PanelHost.panelAt(level, pos);
            if (panel != null) {
                panel.releaseParts();
            }
        }

        if (!level.isClientSide && !(newState.getBlock() instanceof DashboardBlock)) {
            this.setNeighborJoin(level, pos, state, false);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
        this.refreshRun(level, state, pos);
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            this.setNeighborJoin(level, pos, state, true);
        }

        this.refreshRun(level, state, pos);
    }

    private void setNeighborJoin(Level level, BlockPos pos, BlockState state, boolean joined) {
        Direction facing = (Direction)state.getValue(FACING);
        if ((Boolean)state.getValue(JOIN_CCW)) {
            this.setJoin(level, pos.relative(facing.getCounterClockWise()), facing, JOIN_CW, joined);
        }

        if ((Boolean)state.getValue(JOIN_CW)) {
            this.setJoin(level, pos.relative(facing.getClockWise()), facing, JOIN_CCW, joined);
        }
    }

    private void setJoin(Level level, BlockPos pos, Direction facing, BooleanProperty property, boolean joined) {
        BlockState neighbor = level.getBlockState(pos);
        if (this.matches(neighbor, facing) && (Boolean)neighbor.getValue(property) != joined) {
            level.setBlock(pos, (BlockState)neighbor.setValue(property, joined), 3);
        }
    }

    private void refreshRun(Level level, BlockState state, BlockPos pos) {
        if (level.isClientSide) {
            Direction facing = (Direction)state.getValue(FACING);

            for (int offset = -5; offset <= 5; offset++) {
                BlockPos at = pos.relative(facing.getClockWise(), offset);
                BlockState atState = level.getBlockState(at);
                if (this.matches(atState, facing)) {
                    level.sendBlockUpdated(at, atState, atState, 8);
                }
            }
        }
    }

    public Class<DashboardBlockEntity> getBlockEntityClass() {
        return DashboardBlockEntity.class;
    }

    public BlockEntityType<? extends DashboardBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends DashboardBlockEntity>)DashboardBlocks.DASHBOARD_BE.get();
    }
}
