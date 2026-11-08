package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface PanelHost {
    PanelSlot targetedSlot(BlockPos var1, BlockState var2, Vec3 var3);

    default boolean supportsCenteredPart() {
        return true;
    }

    default boolean removedWhenEmpty() {
        return true;
    }

    @Nullable
    static PanelBlockEntity panelAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PanelBlockEntity panel ? panel : null;
    }

    default PanelSlot slotFor(@Nullable PanelBlockEntity panel, BlockPos pos, BlockState state, Vec3 click) {
        PanelSlot sole = panel == null ? null : panel.soleSlot();
        return sole != null ? sole : this.targetedSlot(pos, state, click);
    }

    default ItemInteractionResult configureTargetedPart(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(stack.getItem() instanceof WrenchItem) || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        PanelBlockEntity panel = panelAt(level, pos);
        if (panel == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        PanelSlot slot = this.slotFor(panel, pos, state, hit.getLocation());
        PanelPart part = panel.getPart(slot);
        if (part == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        InteractionResult result = part.configure(panel, slot, player);
        return result == InteractionResult.PASS ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SUCCESS;
    }

    default ItemInteractionResult labelTargetedPart(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Component name = (Component)stack.get(DataComponents.CUSTOM_NAME);
        if (stack.is(Items.NAME_TAG) && name != null) {
            PanelBlockEntity panel = panelAt(level, pos);
            if (panel == null) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            PanelSlot slot = this.slotFor(panel, pos, state, hit.getLocation());
            if (!panel.hasPart(slot)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            panel.setLabel(slot, name);
            player.displayClientMessage(Component.translatable("gui.loconautics.panel.labeled", new Object[]{panel.label(slot)}), true);
            return ItemInteractionResult.SUCCESS;
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    default InteractionResult useTargetedPart(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        PanelBlockEntity panel = panelAt(level, pos);
        if (panel == null) {
            return InteractionResult.PASS;
        }

        PanelSlot slot = this.slotFor(panel, pos, state, hit.getLocation());
        PanelPart part = panel.getPart(slot);
        if (part != null && part.glass() != null) {
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    panel.toggleGlassFromUse(slot);
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (panel.isGlassClosed(slot)) {
                return InteractionResult.CONSUME;
            }
        }

        if (part != null && part.bindFromHeld(panel, slot, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else if (part == null || !part.consumesUse()) {
            return InteractionResult.PASS;
        } else if (level.isClientSide) {
            part.clientUse(panel, slot, player);
            return InteractionResult.SUCCESS;
        } else {
            return part.use(panel, slot, player);
        }
    }

    default ItemStack targetedPartStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, @Nullable Player player) {
        PanelBlockEntity panel = level.getBlockEntity(pos) instanceof PanelBlockEntity p ? p : null;
        if (panel != null && target instanceof BlockHitResult hit) {
            ItemStack stack = panel.stackForSlot(this.slotFor(panel, pos, state, hit.getLocation()));
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            } else {
                return player != null && player.isShiftKeyDown() ? stack : new ItemStack(stack.getItem());
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    default boolean takeTargetedPartOnBreak(BlockState state, Level level, BlockPos pos, Player player) {
        PanelBlockEntity panel = panelAt(level, pos);
        if (panel == null || panel.partCount() <= 1) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        PanelSlot slot = null;
        if (player.pick(10.0, 0.0F, false) instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
            slot = this.slotFor(panel, pos, state, hit.getLocation());
        }

        if (slot == null || !panel.hasPart(slot)) {
            slot = firstOccupied(panel);
        }

        if (slot != null) {
            ItemStack stack = panel.stackForSlot(slot);
            panel.removePart(slot);
            if (!player.isCreative()) {
                Block.popResource(level, pos, stack);
            }
        }

        return true;
    }

    default InteractionResult takeTargetedPartWithWrench(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        PanelBlockEntity panel = panelAt(level, pos);
        PanelSlot slot = this.slotFor(panel, pos, state, context.getClickLocation());
        if (panel != null && panel.hasPart(slot)) {
            takePart(panel, context.getPlayer(), slot);
            IWrenchable.playRemoveSound(level, pos);
            if (panel.partCount() == 0 && this.removedWhenEmpty()) {
                level.destroyBlock(pos, false);
            }
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    static List<ItemStack> partDrops(Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PanelBlockEntity panel && panel.partCount() != 0) {
            List<ItemStack> drops = new ArrayList<>();

            for (PanelSlot slot : panel.slots()) {
                if (panel.hasPart(slot)) {
                    drops.add(panel.stackForSlot(slot));
                }
            }

            return drops;
        } else {
            return null;
        }
    }

    @Nullable
    private static PanelSlot firstOccupied(PanelBlockEntity panel) {
        for (PanelSlot slot : panel.slots()) {
            if (panel.hasPart(slot)) {
                return slot;
            }
        }

        return null;
    }

    private static void takePart(PanelBlockEntity panel, @Nullable Player player, PanelSlot slot) {
        ItemStack stack = panel.stackForSlot(slot);
        panel.removePart(slot);
        if (player != null && !player.isCreative()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
