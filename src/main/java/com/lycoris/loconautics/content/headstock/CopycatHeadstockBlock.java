package com.lycoris.loconautics.content.headstock;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.lycoris.loconautics.foundation.math.VoxelShapes;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CopycatHeadstockBlock extends DirectionalBlock implements IBE<CopycatHeadstockBlockEntity>, IWrenchable {
    public static final MapCodec<CopycatHeadstockBlock> CODEC = simpleCodec(CopycatHeadstockBlock::new);
    private static final Map<CopycatPlacement, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(CopycatPlacement.class);

    private static VoxelShape tiltedFromNorth(VoxelShape north, boolean down) {
        VoxelShape result = Shapes.empty();

        for (AABB box : north.toAabbs()) {
            result = down
                ? Shapes.or(result, Shapes.box(box.minX, box.minZ, 1.0 - box.maxY, box.maxX, box.maxZ, 1.0 - box.minY))
                : Shapes.or(result, Shapes.box(box.minX, 1.0 - box.maxZ, box.minY, box.maxX, 1.0 - box.minZ, box.maxY));
        }

        return result.optimize();
    }

    public CopycatHeadstockBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH))
                .setValue(CopycatPlacement.PLACEMENT, CopycatPlacement.TOP)
        );
    }

    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, CopycatPlacement.PLACEMENT});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        return (BlockState)((BlockState)this.defaultBlockState().setValue(FACING, facing))
            .setValue(CopycatPlacement.PLACEMENT, facing.getAxis() == Axis.Y ? aimedAlongDepth(context, facing) : CopycatPlacement.aimedAt(context));
    }

    private static CopycatPlacement aimedAlongDepth(BlockPlaceContext context, Direction facing) {
        double depth = context.getClickLocation().z - context.getClickedPos().getZ();
        if (facing == Direction.DOWN) {
            depth = 1.0 - depth;
        }

        if (depth > 0.6666666666666666) {
            return CopycatPlacement.TOP;
        } else {
            return depth > 0.3333333333333333 ? CopycatPlacement.CENTER : CopycatPlacement.BOTTOM;
        }
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(CopycatPlacement.PLACEMENT)).get(state.getValue(FACING));
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
    ) {
        return CopycatBlockLogic.useItemOn(stack, level, pos, player, hit.getDirection());
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return CopycatBlockLogic.cyclePlacement(state, context.getLevel(), context.getClickedPos());
    }

    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        InteractionResult reclaimed = CopycatBlockLogic.reclaim(state, context.getLevel(), context.getClickedPos(), context.getPlayer());
        return reclaimed != InteractionResult.PASS ? reclaimed : IWrenchable.super.onSneakWrenched(state, context);
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CopycatBlockLogic.placedBy(level, pos, placer);
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !movedByPiston) {
            CopycatBlockLogic.popConsumed(level, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public Class<CopycatHeadstockBlockEntity> getBlockEntityClass() {
        return CopycatHeadstockBlockEntity.class;
    }

    public BlockEntityType<? extends CopycatHeadstockBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends CopycatHeadstockBlockEntity>)LoconauticsRegistries.COPYCAT_HEADSTOCK_BE.get();
    }

    static {
        for (CopycatPlacement placement : CopycatPlacement.values()) {
            VoxelShape north = Block.box(0.0, 4.0, 13.0, 16.0, 16.0, 16.0).move(0.0, placement.blocks(), 0.0);
            Map<Direction, VoxelShape> byFacing = new EnumMap<>(Direction.class);
            byFacing.putAll(VoxelShapes.byHorizontalFacing(north));
            byFacing.put(Direction.DOWN, tiltedFromNorth(north, true));
            byFacing.put(Direction.UP, tiltedFromNorth(north, false));
            SHAPES.put(placement, byFacing);
        }
    }
}
