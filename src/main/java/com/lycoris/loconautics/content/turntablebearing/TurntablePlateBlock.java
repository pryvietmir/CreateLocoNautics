package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TurntablePlateBlock extends DirectionalKineticBlock implements IBE<TurntablePlateBlockEntity>, BlockSubLevelAssemblyListener {
    public TurntablePlateBlock(Properties properties) {
        super(properties);
    }

    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    public Axis getRotationAxis(BlockState state) {
        return ((Direction)state.getValue(FACING)).getAxis();
    }

    public Class<TurntablePlateBlockEntity> getBlockEntityClass() {
        return TurntablePlateBlockEntity.class;
    }

    public BlockEntityType<? extends TurntablePlateBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends TurntablePlateBlockEntity>)LoconauticsRegistries.TURNTABLE_PLATE_BE.get();
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TurntableBearingShapes.PLATE.get((Direction)state.getValue(FACING));
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TurntableBearingShapes.PLATE_COLLISION.get((Direction)state.getValue(FACING));
    }

    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return TurntableBearingShapes.PLATE.get((Direction)state.getValue(FACING));
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (player.mayBuild() && !player.isShiftKeyDown()) {
            if (stack.isEmpty() && !level.isClientSide) {
                this.withBlockEntityDo(level, pos, TurntablePlateBlockEntity::setParentAssembleNextTick);
            }

            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        } else {
            return ItemInteractionResult.FAIL;
        }
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack((ItemLike)LoconauticsRegistries.TURNTABLE_BEARING_ITEM.get());
    }

    @Override
    public void beforeMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        this.withBlockEntityDo(originLevel, oldPos, TurntablePlateBlockEntity::beforeAssembly);
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        this.withBlockEntityDo(resultingLevel, newPos, TurntablePlateBlockEntity::fixParentLinkingWhenMoved);
    }
}
