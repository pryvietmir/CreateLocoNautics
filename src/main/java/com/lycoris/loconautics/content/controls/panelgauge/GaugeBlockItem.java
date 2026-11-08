package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockItem;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public class GaugeBlockItem extends PanelBlockItem {
    public GaugeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    public static BlockPos boundFirebox(ItemStack stack) {
        return (BlockPos)stack.get((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
    }

    public static GaugeMetric boundMetric(ItemStack stack) {
        GaugeMetric metric = (GaugeMetric)stack.get((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get());
        return metric == null ? GaugeMetric.TEMPERATURE : metric;
    }

    public static ItemStack stackFor(GaugeBlockEntity.Gauge gauge) {
        ItemStack stack = new ItemStack((ItemLike)BoilerBlocks.LOCOMOTIVE_GAUGE_ITEM.get());
        if (gauge != null && gauge.bound()) {
            stack.set((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), gauge.firebox());
            stack.set((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(), gauge.metric());
        }

        return stack;
    }

    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && boundFirebox(stack) != null) {
            if (!level.isClientSide) {
                stack.remove((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
                player.displayClientMessage(Component.translatable("gui.loconautics.locomotive_gauge.cleared"), true);
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
        Player shifter = context.getPlayer();
        if (shifter != null && shifter.isShiftKeyDown() && boundFirebox(context.getItemInHand()) != null) {
            if (!level.isClientSide) {
                context.getItemInHand().remove((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get());
                shifter.displayClientMessage(Component.translatable("gui.loconautics.locomotive_gauge.cleared"), true);
            }

            return InteractionResult.SUCCESS;
        } else {
            GaugeMetric.Group group = GaugeMetric.Group.of(level.getBlockEntity(pos));
            if (group != null) {
                if (!level.isClientSide) {
                    ItemStack stack = context.getItemInHand();
                    stack.set((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_TARGET.get(), pos);
                    stack.set((DataComponentType)LoconauticsRegistries.LOCOMOTIVE_GAUGE_METRIC.get(), GaugeMetric.defaultFor(group));
                }

                return InteractionResult.SUCCESS;
            } else {
                return super.useOn(context);
            }
        }
    }

    @Nullable
    @Override
    protected PanelPart partFor(ItemStack stack) {
        BlockPos firebox = boundFirebox(stack);
        GaugeMetric metric = boundMetric(stack);
        int background = firebox == null ? 9 : metric.defaultBackground();
        return new GaugeBlockEntity.Gauge(
            firebox == null ? GaugeBlockEntity.Gauge.UNBOUND : firebox, metric, background, 3, 4, GaugeBlockEntity.DEFAULT_OPTIONS
        );
    }

    @Override
    protected void onPartPlaced(Player player, BlockPos pos, PanelSlot slot, ItemStack stack) {
        PanelFrequencyMenu.open(player, pos, slot);
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockPos firebox = boundFirebox(stack);
        if (firebox != null) {
            tooltip.add(
                Component.translatable("gui.loconautics.locomotive_gauge.bound_to", new Object[]{firebox.getX(), firebox.getY(), firebox.getZ()})
                    .withStyle(ChatFormatting.GREEN)
            );
        }
    }
}
