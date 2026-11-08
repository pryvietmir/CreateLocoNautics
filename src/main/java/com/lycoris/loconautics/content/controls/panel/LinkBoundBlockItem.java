package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class LinkBoundBlockItem extends PanelBlockItem {
    protected LinkBoundBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    protected abstract DataComponentType<BlockPos> targetComponent();

    protected abstract String langPrefix();

    @Nullable
    public BlockPos boundLink(ItemStack stack) {
        return (BlockPos)stack.get(this.targetComponent());
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && this.boundLink(stack) != null) {
            if (!level.isClientSide) {
                stack.remove(this.targetComponent());
                player.displayClientMessage(Component.translatable(this.langPrefix() + ".cleared"), true);
            }

            return InteractionResultHolder.success(stack);
        } else {
            return super.use(level, player, hand);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (state.getBlock() instanceof SmallEngineBlock) {
            if (!level.isClientSide) {
                context.getItemInHand().set(this.targetComponent(), pos);
                if (player != null) {
                    player.displayClientMessage(Component.translatable(this.langPrefix() + ".bound"), true);
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            if (!AllBlocks.REDSTONE_LINK.has(state)) {
                return super.useOn(context);
            }

            boolean receiver = (Boolean)state.getValue(RedstoneLinkBlock.RECEIVER);
            if (!level.isClientSide) {
                if (receiver) {
                    context.getItemInHand().set(this.targetComponent(), pos);
                }

                if (player != null) {
                    player.displayClientMessage(Component.translatable(this.langPrefix() + (receiver ? ".bound" : ".not_receiver")), true);
                }
            }

            return InteractionResult.SUCCESS;
        }
    }

    public boolean isFoil(ItemStack stack) {
        return this.boundLink(stack) != null;
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos link = this.boundLink(stack);
        if (link != null) {
            tooltip.add(
                Component.translatable(this.langPrefix() + ".bound_to", new Object[]{link.getX(), link.getY(), link.getZ()}).withStyle(ChatFormatting.GREEN)
            );
        }
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }
}
