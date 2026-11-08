package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.DyedBlock;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.foundation.math.TiltedShape;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelCollisionShape;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BufferBlock extends HorizontalDirectionalBlock implements IBE<BufferBlockEntity>, DyedBlock, IWrenchable, BlockSubLevelCollisionShape {
    private static final double HEAD_ROLL = 45.0;
    private static final double HEAD_CENTER_Y = 0.625;
    private static final Map<BufferSize, Map<CopycatPlacement, Map<Direction, VoxelShape>>> SHAPES = new EnumMap<>(BufferSize.class);
    private static final Map<BufferSize, Map<CopycatPlacement, Map<Direction, VoxelShape>>> COLLISION = new EnumMap<>(BufferSize.class);
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> BODY_COLLIDER = new EnumMap<>(CopycatPlacement.class);
    private final DyeColor color;

    private static VoxelShape plate(CopycatPlacement placement) {
        return Block.box(0.0, 4.0, 13.0, 16.0, 16.0, 16.0).move(0.0, placement.blocks(), 0.0);
    }

    private static VoxelShape body(BufferSize size, CopycatPlacement placement) {
        int out = size.extensionPixels();
        int sleeveOut = size.sleeveOutPixels();
        return Shapes.or(
            plate(placement),
            new VoxelShape[]{
                Block.box(5.0, 7.0, 10 - sleeveOut, 11.0, 13.0, 16.0).move(0.0, placement.blocks(), 0.0),
                Block.box(6.0, 8.0, 10 - out, 10.0, 12.0, 16.0).move(0.0, placement.blocks(), 0.0)
            }
        );
    }

    private static VoxelShape head(BufferSize size, CopycatPlacement placement) {
        int out = size.extensionPixels();
        return Block.box(4.0, 6.0, 8 - out, 12.0, 14.0, 10 - out).move(0.0, placement.blocks(), 0.0);
    }

    private static VoxelShape tiltedHead(VoxelShape body, VoxelShape head, Direction facing, CopycatPlacement placement) {
        double centerY = 0.625 + placement.blocks();
        return TiltedShape.of(body, head.toAabbs(), point -> roll(point, facing, centerY, 45.0), point -> roll(point, facing, centerY, -45.0));
    }

    private static VoxelShape diamondBounds(VoxelShape head, Direction facing, CopycatPlacement placement) {
        double centerY = 0.625 + placement.blocks();
        VoxelShape bounds = Shapes.empty();

        for (AABB box : head.toAabbs()) {
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double minZ = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            double maxZ = -Double.MAX_VALUE;

            for (Vec3 corner : new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
            }) {
                Vec3 turned = roll(corner, facing, centerY, 45.0);
                minX = Math.min(minX, turned.x);
                minY = Math.min(minY, turned.y);
                minZ = Math.min(minZ, turned.z);
                maxX = Math.max(maxX, turned.x);
                maxY = Math.max(maxY, turned.y);
                maxZ = Math.max(maxZ, turned.z);
            }

            bounds = Shapes.or(bounds, Shapes.box(minX, minY, minZ, maxX, maxY, maxZ));
        }

        return bounds;
    }

    private static Vec3 roll(Vec3 point, Direction facing, double centerY, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double dy = point.y - centerY;
        if (facing.getAxis() == Axis.Z) {
            double dx = point.x - 0.5;
            return new Vec3(0.5 + dx * cos - dy * sin, centerY + dx * sin + dy * cos, point.z);
        } else {
            double dz = point.z - 0.5;
            return new Vec3(point.x, centerY + dz * sin + dy * cos, 0.5 + dz * cos - dy * sin);
        }
    }

    public BufferBlock(Properties properties, DyeColor color) {
        super(properties);
        this.color = color;
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH))
                    .setValue(BufferSize.SIZE, BufferSize.HALF))
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP)
        );
    }

    @Override
    public DyeColor getColor() {
        return this.color;
    }

    @Override
    public BlockState paintedState(BlockState state, DyeColor dyed) {
        return (BlockState)((BlockState)((BlockState)((BufferBlock)BufferBlocks.BUFFERS.get(dyed).get())
                    .defaultBlockState()
                    .setValue(FACING, (Direction)state.getValue(FACING)))
                .setValue(BufferSize.SIZE, (BufferSize)state.getValue(BufferSize.SIZE)))
            .setValue(CopycatPlacement.PLACEMENT, (CopycatPlacement)state.getValue(CopycatPlacement.PLACEMENT));
    }

    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new BufferBlock(properties, this.color));
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, BufferSize.SIZE, CopycatPlacement.PLACEMENT});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)((BlockState)this.defaultBlockState().setValue(FACING, ShiftPlacement.facing(context)))
            .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.aimedAt(context));
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(BufferSize.SIZE)).get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION.get(state.getValue(BufferSize.SIZE)).get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getSubLevelCollisionShape(BlockGetter level, BlockState state) {
        return BODY_COLLIDER.get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
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
        ItemInteractionResult painted = this.paint(stack, state, level, pos, player);
        return painted != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            ? painted
            : CopycatBlockLogic.useItemOn(stack, level, pos, player, hit.getDirection());
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CopycatBlockLogic.placedBy(level, pos, placer);
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        level.setBlock(context.getClickedPos(), (BlockState)state.setValue(BufferSize.SIZE, ((BufferSize)state.getValue(BufferSize.SIZE)).next()), 3);
        level.playSound(null, context.getClickedPos(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, context.getLevel(), context.getClickedPos(), context.getPlayer());
        return reclaimed != InteractionResult.PASS ? reclaimed : super.onSneakWrenched(state, context);
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !(newState.getBlock() instanceof BufferBlock) && !movedByPiston) {
            CopycatBlockLogic.popConsumed(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public Class<BufferBlockEntity> getBlockEntityClass() {
        return BufferBlockEntity.class;
    }

    public BlockEntityType<? extends BufferBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends BufferBlockEntity>)BufferBlocks.BUFFER_BE.get();
    }

    static {
        for (CopycatPlacement placement : CopycatPlacement.values()) {
            Map<Direction, VoxelShape> plates = VoxelShapes.byHorizontalFacing(plate(placement));
            Map<Direction, VoxelShape> clipped = new EnumMap<>(Direction.class);

            for (Direction facing : Plane.HORIZONTAL) {
                clipped.put(facing, Shapes.join(plates.get(facing), Shapes.block(), BooleanOp.AND).optimize());
            }

            BODY_COLLIDER.put(placement, clipped);
        }

        for (BufferSize size : BufferSize.values()) {
            Map<CopycatPlacement, Map<Direction, VoxelShape>> shapes = new EnumMap<>(CopycatPlacement.class);
            Map<CopycatPlacement, Map<Direction, VoxelShape>> collision = new EnumMap<>(CopycatPlacement.class);

            for (CopycatPlacement placement : CopycatPlacement.values()) {
                Map<Direction, VoxelShape> body = VoxelShapes.byHorizontalFacing(body(size, placement));
                Map<Direction, VoxelShape> head = VoxelShapes.byHorizontalFacing(head(size, placement));
                Map<Direction, VoxelShape> tilted = new EnumMap<>(Direction.class);
                Map<Direction, VoxelShape> clipped = new EnumMap<>(Direction.class);

                for (Direction facing : Plane.HORIZONTAL) {
                    tilted.put(facing, tiltedHead(body.get(facing), head.get(facing), facing, placement));
                    clipped.put(
                        facing,
                        Shapes.join(Shapes.or(body.get(facing), diamondBounds(head.get(facing), facing, placement)), Shapes.block(), BooleanOp.AND).optimize()
                    );
                }

                shapes.put(placement, tilted);
                collision.put(placement, clipped);
            }

            SHAPES.put(size, shapes);
            COLLISION.put(size, collision);
        }
    }
}
