package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlock;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlock;
import com.lycoris.loconautics.content.combustion.SmallEngineBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
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

public class ValveBlockItem extends PanelBlockItem {
    public ValveBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundValve(ItemStack stack) {
        return (BlockPos)stack.get((DataComponentType)LoconauticsRegistries.VALVE_TARGET.get());
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && boundValve(stack) != null) {
            if (!level.isClientSide) {
                stack.remove((DataComponentType)LoconauticsRegistries.VALVE_TARGET.get());
                player.displayClientMessage(Component.translatable("gui.loconautics.valve.cleared"), true);
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
        Player shifter = context.getPlayer();
        if (shifter != null && shifter.isShiftKeyDown() && boundValve(context.getItemInHand()) != null) {
            if (!level.isClientSide) {
                context.getItemInHand().remove((DataComponentType)LoconauticsRegistries.VALVE_TARGET.get());
                shifter.displayClientMessage(Component.translatable("gui.loconautics.valve.cleared"), true);
            }

            return InteractionResult.SUCCESS;
        } else {
            if (!(state.getBlock() instanceof SafetyValveBlock)
                && !(state.getBlock() instanceof SteamTakeoffBlock)
                && !(state.getBlock() instanceof SmallEngineBlock)) {
                return super.useOn(context);
            }

            if (!level.isClientSide) {
                context.getItemInHand().set((DataComponentType)LoconauticsRegistries.VALVE_TARGET.get(), pos);
                Player player = context.getPlayer();
                if (player != null) {
                    player.displayClientMessage(Component.translatable("gui.loconautics.valve.bound"), true);
                }
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Nullable
    @Override
    protected PanelPart partFor(ItemStack stack) {
        BlockPos valve = boundValve(stack);
        return valve == null ? null : new ValvePart(0.0F, valve, 1);
    }

    @Override
    protected void notifyCannotPlace(Player player) {
        player.displayClientMessage(Component.translatable("gui.loconautics.valve.unbound"), true);
    }

    public boolean isFoil(ItemStack stack) {
        return boundValve(stack) != null;
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos valve = boundValve(stack);
        if (valve != null) {
            tooltip.add(
                Component.translatable("gui.loconautics.valve.bound_to", new Object[]{valve.getX(), valve.getY(), valve.getZ()})
                    .withStyle(ChatFormatting.GREEN)
            );
        }
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
    }
}
