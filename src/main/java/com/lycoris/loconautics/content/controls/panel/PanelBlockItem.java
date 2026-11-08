package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class PanelBlockItem extends BlockItem {
    protected PanelBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Nullable
    protected abstract PanelPart partFor(ItemStack var1);

    protected void onPartPlaced(Player player, BlockPos pos, PanelSlot slot, ItemStack stack) {
    }

    protected void notifyCannotPlace(Player player) {
    }

    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = this.putPartOnHost(context);
        return result == InteractionResult.PASS ? super.useOn(context) : result;
    }

    private InteractionResult putPartOnHost(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState clicked = level.getBlockState(pos);
        if (!(clicked.getBlock() instanceof PanelHost)) {
            Direction face = context.getClickedFace();
            BlockPos front = pos.relative(face);
            BlockState mounted = level.getBlockState(front);
            if (mounted.getBlock() instanceof PanelBlock && PanelBlock.mountedOn(mounted, face)) {
                pos = front;
                clicked = mounted;
            }
        }

        if (clicked.getBlock() instanceof PanelHost host && level.getBlockEntity(pos) instanceof PanelBlockEntity panel) {
            ItemStack var18 = context.getItemInHand();
            Player player = context.getPlayer();
            if (this.partFor(var18) == null) {
                if (player != null && !level.isClientSide) {
                    this.notifyCannotPlace(player);
                }

                return InteractionResult.FAIL;
            } else {
                if (panel.isFull()) {
                    return InteractionResult.FAIL;
                }

                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }

                PanelSlot targeted = host.targetedSlot(pos, clicked, context.getClickLocation());
                PanelSlot free = panel.firstFreeSlot(targeted);
                boolean center = host.supportsCenteredPart() && panel.partCount() == 0 && PanelBlock.aimedAtCenter(pos, clicked, context.getClickLocation());
                if (free != null && panel.putPart(free, this.partFor(var18), center)) {
                    panel.setLabel(free, (Component)var18.get(DataComponents.CUSTOM_NAME));
                    CompoundTag picked = pickedConfig(var18);
                    LoconauticsConstants.LOGGER.debug("[panelpick] putPartOnHost picked={}", picked);
                    if (picked != null) {
                        panel.applyPicked(free, picked, level.registryAccess());
                    }

                    SoundType sound = clicked.getSoundType();
                    level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                    if (player != null) {
                        if (!player.isCreative()) {
                            var18.shrink(1);
                        }

                        this.onPartPlaced(player, pos, free, var18);
                        checkCockpit(player, panel);
                    }

                    return InteractionResult.CONSUME;
                } else {
                    return InteractionResult.FAIL;
                }
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    private static void checkCockpit(Player player, PanelBlockEntity panel) {
        if (player instanceof ServerPlayer serverPlayer) {
            TrainAdvancements.panelChanged(serverPlayer, panel);
        }
    }

    public InteractionResult place(BlockPlaceContext context) {
        if (this.partFor(context.getItemInHand()) == null) {
            Player player = context.getPlayer();
            if (player != null && !context.getLevel().isClientSide) {
                this.notifyCannotPlace(player);
            }

            return InteractionResult.FAIL;
        } else {
            ItemStack held = context.getItemInHand();
            CustomData picked = (CustomData)held.get(DataComponents.BLOCK_ENTITY_DATA);

            try {
                return super.place(context);
            } finally {
                if (picked != null) {
                    held.set(DataComponents.BLOCK_ENTITY_DATA, picked);
                }
            }
        }
    }

    @Nullable
    private static CompoundTag pickedConfig(ItemStack stack) {
        CustomData data = (CustomData)stack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data == null ? null : data.copyTag();
    }

    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (placed && !context.getLevel().isClientSide) {
            ItemStack stack = context.getItemInHand();
            PanelPart part = this.partFor(stack);
            BlockPos pos = context.getClickedPos();
            if (part != null && context.getLevel().getBlockEntity(pos) instanceof PanelBlockEntity panel) {
                PanelSlot targeted = PanelBlock.getTargetedSlot(pos, context.getLevel().getBlockState(pos), context.getClickLocation());
                boolean center = PanelBlock.aimedAtCenter(pos, context.getLevel().getBlockState(pos), context.getClickLocation());
                CompoundTag picked = pickedConfig(stack);
                LoconauticsConstants.LOGGER.debug("[panelpick] placeBlock picked={}", picked);
                stack.remove(DataComponents.BLOCK_ENTITY_DATA);
                if (panel.putPart(targeted, part, center)) {
                    panel.setLabel(targeted, (Component)stack.get(DataComponents.CUSTOM_NAME));
                    if (picked != null) {
                        panel.applyPicked(targeted, picked, context.getLevel().registryAccess());
                    }

                    if (context.getPlayer() != null) {
                        this.onPartPlaced(context.getPlayer(), pos, targeted, stack);
                        checkCockpit(context.getPlayer(), panel);
                    }
                }
            }

            return placed;
        } else {
            return placed;
        }
    }
}
