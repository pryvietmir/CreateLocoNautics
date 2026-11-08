package com.lycoris.loconautics.content.combustion;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class SmallEngineBlockItem extends BlockItem {
    private static final int HELPER_ID = PlacementHelpers.register(new SmallEngineBlockItem.EngineRowHelper());

    public SmallEngineBlockItem(SmallEngineBlock block, Properties properties) {
        super(block, properties);
    }

    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        IPlacementHelper helper = PlacementHelpers.get(HELPER_ID);
        if (helper.matchesState(state) && player != null && !player.isShiftKeyDown()) {
            BlockHitResult ray = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), pos, true);
            return helper.getOffset(player, level, state, pos, ray).placeInWorld(level, this, player, context.getHand(), ray).result();
        } else {
            return super.onItemUseFirst(stack, context);
        }
    }

    private static class EngineRowHelper implements IPlacementHelper {
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof SmallEngineBlockItem;
        }

        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof SmallEngineBlock;
        }

        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult ray) {
            Direction facing = (Direction)state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            if (EngineRow.lengthAround(level, pos, facing) >= 3) {
                return PlacementOffset.fail();
            }

            BlockPos front = EngineRow.headOf(level, pos, facing).relative(facing);
            BlockPos rear = EngineRow.tailOf(level, pos, facing).relative(facing.getOpposite());
            Direction view = player.getDirection();
            Direction preferred = view.getAxis() == facing.getAxis() ? view : view.getClockWise();
            boolean frontNearer = preferred == facing;
            BlockPos first = frontNearer ? front : rear;
            BlockPos second = frontNearer ? rear : front;

            for (BlockPos target : new BlockPos[]{first, second}) {
                if (level.getBlockState(target).canBeReplaced()) {
                    return PlacementOffset.success(
                        target,
                        placed -> (BlockState)((BlockState)placed.setValue(HorizontalKineticBlock.HORIZONTAL_FACING, facing))
                            .setValue(SmallEngineBlock.PART, SmallEngineBlock.placedPart(level, target, facing))
                    );
                }
            }

            return PlacementOffset.fail();
        }
    }
}
