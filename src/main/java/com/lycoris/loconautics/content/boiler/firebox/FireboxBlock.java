package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.client.connected.ConnectedBlock;
import com.lycoris.loconautics.client.connected.ConnectionBounds;
import com.lycoris.loconautics.client.copycat.CopycatMaterialSource;
import com.lycoris.loconautics.client.copycat.CopycatQuads;
import com.lycoris.loconautics.common.ShiftPlacement;
import com.lycoris.loconautics.compat.cbc.BoilerImpactCallback;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.Plane;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FireboxBlock
    extends BaseEntityBlock
    implements BlockWithSubLevelCollisionCallback,
    BlockSubLevelAssemblyListener,
    IWrenchable,
    ConnectedBlock,
    CopycatMaterialSource {
    public static final BooleanProperty EXTENSION = BooleanProperty.create("extension");

    public FireboxBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            (BlockState)((BlockState)((BlockState)((BlockState)this.getStateDefinition().any()).setValue(BlockStateProperties.LIT, false))
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH))
                .setValue(EXTENSION, false)
        );
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FireboxBlock::new);
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_FACING, EXTENSION});
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)((BlockState)this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, ShiftPlacement.facing(context)))
            .setValue(EXTENSION, absorbingController(context.getLevel(), context.getClickedPos()) != null);
    }

    protected BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState)state.setValue(
            BlockStateProperties.HORIZONTAL_FACING, rotation.rotate((Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING))
        );
    }

    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Nullable
    private static BlockPos absorbingController(BlockGetter level, BlockPos pos) {
        for (Direction dir : Plane.HORIZONTAL) {
            BlockPos controller = FireboxGroups.controllerOf(level, pos.relative(dir));
            if (controller != null && !controller.equals(pos) && FireboxGroups.canAbsorb(level, controller, List.of(pos))) {
                return controller;
            }
        }

        return null;
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos controller = absorbingController(level, pos);
            if (controller != null) {
                FireboxGroups.adopt(level, controller, List.of(pos));
                absorbNeighbors(level, controller);
                FireboxGroups.freeDoor(level, controller);
            } else {
                if ((Boolean)state.getValue(EXTENSION)) {
                    level.setBlock(pos, (BlockState)state.setValue(EXTENSION, false), 3);
                }

                absorbNeighbors(level, pos);
                FireboxGroups.freeDoor(level, pos);
                FireboxGroups.rerender(level, pos);
            }
        }
    }

    private static void absorbNeighbors(Level level, BlockPos controller) {
        for (BlockPos member : FireboxGroups.members(level, controller)) {
            for (Direction dir : Plane.HORIZONTAL) {
                BlockPos other = FireboxGroups.controllerOf(level, member.relative(dir));
                if (other != null && !other.equals(controller)) {
                    Set<BlockPos> theirs = FireboxGroups.members(level, other);
                    if (FireboxGroups.canAbsorb(level, controller, theirs)) {
                        if (level.getBlockEntity(other) instanceof FireboxBlockEntity oldController
                            && level.getBlockEntity(controller) instanceof FireboxBlockEntity kept
                            && kept.firstFuel().isEmpty()) {
                            FireboxBlockEntity.transferControllerData(oldController, level, other, controller, dir);
                        }

                        FireboxGroups.adopt(level, controller, theirs);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public boolean connectsTo(BlockGetter level, BlockState state, BlockPos anchor, BlockPos neighborPos) {
        return anchor.equals(FireboxGroups.renderAnchor(level, neighborPos));
    }

    @Override
    public ConnectionBounds connectionBounds() {
        return FireboxGroups.BOUNDS;
    }

    @Override
    public BlockPos groupAnchor(BlockGetter level, BlockState state, BlockPos pos) {
        BlockPos anchor = FireboxGroups.renderAnchor(level, pos);
        return anchor != null ? anchor : pos;
    }

    @Nullable
    @Override
    public BlockState copycatMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockPos controller = FireboxGroups.renderAnchor(level, pos);
        if (controller == null) {
            return null;
        } else if (level.getBlockEntity(controller) instanceof FireboxBlockEntity be) {
            ItemStack fuel = be.firstFuel();
            return fuel.isEmpty() ? null : CopycatQuads.materialOf(fuel);
        } else {
            return null;
        }
    }

    @Override
    public boolean isOpenFace(BlockState state, Direction face) {
        return !(Boolean)state.getValue(EXTENSION) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == face;
    }

    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return BoilerImpactCallback.INSTANCE;
    }

    @Override
    public void afterMove(ServerLevel originLevel, ServerLevel resultingLevel, BlockState newState, BlockPos oldPos, BlockPos newPos) {
        if (resultingLevel.getBlockEntity(newPos) instanceof FireboxBlockEntity be) {
            be.shiftReferences(newPos.subtract(oldPos));
        }
    }

    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 20;
    }

    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if ((Boolean)state.getValue(EXTENSION)) {
            return InteractionResult.PASS;
        } else if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return InteractionResult.PASS;
        } else if (FireboxBlockEntity.isFuel(player.getOffhandItem())) {
            return InteractionResult.PASS;
        } else if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else if (level.getBlockEntity(pos) instanceof FireboxBlockEntity controller) {
            boolean var8 = controller.toggleDoors();
            level.playSound(null, pos, var8 ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.1F);
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.PASS;
        }
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if ((Boolean)state.getValue(EXTENSION)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.getBlockEntity(pos) instanceof FireboxBlockEntity controller) {
            if (stack.getItem() instanceof ShovelItem) {
                if (level.isClientSide) {
                    return ItemInteractionResult.SUCCESS;
                }

                if (controller.isLockedOut()) {
                    controller.clearLockout();
                } else {
                    controller.dropFuel();
                }

                return ItemInteractionResult.SUCCESS;
            } else if (!FireboxBlockEntity.isFuel(stack)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            } else {
                return controller.insertFuel(player, stack) ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (face.getAxis() == Axis.Y) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean isExtension = (Boolean)state.getValue(EXTENSION);
        if (!isExtension) {
            level.setBlock(pos, (BlockState)state.setValue(BlockStateProperties.HORIZONTAL_FACING, face), 3);
            FireboxGroups.freeDoor(level, pos);
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }

        BlockPos controllerPos = FireboxGroups.controllerOf(level, pos);
        Set<BlockPos> group = controllerPos == null ? Set.of(pos.immutable()) : FireboxGroups.members(level, controllerPos);
        if (controllerPos != null && level.getBlockEntity(controllerPos) instanceof FireboxBlockEntity old) {
            FireboxBlockEntity.transferControllerData(old, level, controllerPos, pos, face);
        }

        level.setBlock(pos, (BlockState)((BlockState)state.setValue(EXTENSION, false)).setValue(BlockStateProperties.HORIZONTAL_FACING, face), 3);
        FireboxGroups.adopt(level, pos, group);
        FireboxGroups.freeDoor(level, pos);
        IWrenchable.playRotateSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !movedByPiston) {
            if ((Boolean)state.getValue(EXTENSION)) {
                BlockPos controller = level.getBlockEntity(pos) instanceof FireboxBlockEntity be ? be.getControllerPos() : null;
                if (controller != null && !controller.equals(pos)) {
                    FireboxGroups.reseat(level, controller, pos);
                }
            } else {
                this.handOverOrDrop(level, pos, state);
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private void handOverOrDrop(Level level, BlockPos removedPos, BlockState removedState) {
        Set<BlockPos> remaining = new LinkedHashSet<>(FireboxGroups.members(level, removedPos));
        remaining.remove(removedPos);
        Direction preferred = (Direction)removedState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos target = null;

        for (Direction dir : this.orderedFromFacing(preferred)) {
            BlockPos neighbor = removedPos.relative(dir);
            if (remaining.contains(neighbor)) {
                target = neighbor;
                preferred = this.findExposedFace(level, neighbor, dir);
                break;
            }
        }

        if (target == null && !remaining.isEmpty()) {
            target = remaining.iterator().next();
        }

        if (target == null) {
            if (level.getBlockEntity(removedPos) instanceof FireboxBlockEntity controller) {
                controller.dropFuel();
                controller.parkWaterInBarrels();
            }
        } else {
            if (level.getBlockEntity(removedPos) instanceof FireboxBlockEntity old) {
                FireboxBlockEntity.transferControllerData(old, level, removedPos, target, preferred);
            }

            Set<BlockPos> island = FireboxGroups.component(level, target, removedPos);
            level.setBlock(
                target,
                (BlockState)((BlockState)level.getBlockState(target).setValue(EXTENSION, false)).setValue(BlockStateProperties.HORIZONTAL_FACING, preferred),
                3
            );
            FireboxGroups.adopt(level, target, island);
            if (level.getBlockEntity(target) instanceof FireboxBlockEntity be) {
                be.onBecameController();
            }

            for (BlockPos leftover : remaining) {
                FireboxGroups.promoteIfOrphaned(level, removedPos, leftover, island);
            }
        }
    }

    private Direction findExposedFace(Level level, BlockPos pos, Direction blockedFrom) {
        Direction best = blockedFrom.getOpposite();

        for (Direction dir : Plane.HORIZONTAL) {
            if (dir != blockedFrom) {
                BlockPos neighbor = pos.relative(dir);
                if (level.getBlockState(neighbor).isAir() || !level.getBlockState(neighbor).isSolid()) {
                    return dir;
                }
            }
        }

        return best;
    }

    private Direction[] orderedFromFacing(Direction preferred) {
        Direction[] horiz = Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        Direction[] result = new Direction[4];
        int i = 0;
        result[i++] = preferred;

        for (Direction d : horiz) {
            if (d != preferred) {
                result[i++] = d;
            }
        }

        return result;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BoilerBlocks.createFireboxBE(pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BoilerBlocks.FIREBOX_BE.get()) {
            return null;
        } else if ((Boolean)state.getValue(EXTENSION)) {
            return null;
        } else {
            return level.isClientSide ? (lvl, pos, st, be) -> ((FireboxBlockEntity)be).clientTick() : (lvl, pos, st, be) -> ((FireboxBlockEntity)be).tick();
        }
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
