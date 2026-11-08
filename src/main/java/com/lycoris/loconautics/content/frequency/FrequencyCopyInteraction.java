package com.lycoris.loconautics.content.frequency;

import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

@EventBusSubscriber(modid = "loconautics")
public final class FrequencyCopyInteraction {
    private FrequencyCopyInteraction() {
    }

    private static boolean isAnalogController(Item item) {
        return item instanceof BlockItem placed && placed.getBlock() instanceof AnalogControllerBlock;
    }

    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        Player player = event.getEntity();
        if (player != null && player.isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND) {
            ItemStack held = event.getItemStack();
            Item item = held.getItem();
            boolean heldAnalog = isAnalogController(item);
            boolean heldAxle = item == LoconauticsRegistries.BEARING_AXLE_ITEM.get();
            boolean heldTrans = item == LoconauticsRegistries.TRANSMISSION_ITEM.get();
            if (heldAnalog || heldAxle || heldTrans) {
                Level level = event.getLevel();
                BlockEntity be = resolveBlockEntity(level, event.getPos());
                if (be != null) {
                    List<FrequencyBindUtil.Pair> pairs = null;
                    if (be instanceof AnalogControllerBlockEntity analog) {
                        if (heldAxle) {
                            pairs = List.of(
                                new FrequencyBindUtil.Pair("Left", analog.getFrequencyLeftFirst(), analog.getFrequencyLeftSecond()),
                                new FrequencyBindUtil.Pair("Right", analog.getFrequencyRightFirst(), analog.getFrequencyRightSecond())
                            );
                        } else if (heldTrans) {
                            pairs = List.of(
                                new FrequencyBindUtil.Pair("Speed", analog.getFrequencyFirst(), analog.getFrequencySecond()),
                                new FrequencyBindUtil.Pair("Direction", analog.getFrequencyBackFirst(), analog.getFrequencyBackSecond())
                            );
                        }
                    } else if (be instanceof BearingAxleBlockEntity axle && heldAnalog) {
                        pairs = List.of(
                            new FrequencyBindUtil.Pair("Left", axle.getLeftFreqFirst(), axle.getLeftFreqSecond()),
                            new FrequencyBindUtil.Pair("Right", axle.getRightFreqFirst(), axle.getRightFreqSecond())
                        );
                    } else if (be instanceof TransmissionBlockEntity trans && heldAnalog) {
                        pairs = List.of(
                            new FrequencyBindUtil.Pair("Speed", trans.getSpeedFreqFirst(), trans.getSpeedFreqSecond()),
                            new FrequencyBindUtil.Pair("Direction", trans.getDirFreqFirst(), trans.getDirFreqSecond())
                        );
                    }

                    if (pairs != null) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        if (!level.isClientSide && player instanceof ServerPlayer sp) {
                            if (FrequencyBindUtil.hasGlint(held) && FrequencyBindUtil.conflicts(held, sp.registryAccess(), pairs)) {
                                sp.displayClientMessage(Component.translatable("loconautics.bind.locked").withStyle(ChatFormatting.RED), true);
                            } else {
                                FrequencyBindUtil.Result result = FrequencyBindUtil.bind(held, sp.registryAccess(), pairs);
                                sp.setItemInHand(event.getHand(), held);
                                switch (result) {
                                    case EMPTY:
                                        sp.displayClientMessage(Component.translatable("loconautics.bind.empty").withStyle(ChatFormatting.RED), true);
                                        break;
                                    case PARTIAL:
                                        sp.displayClientMessage(Component.translatable("loconautics.bind.partial").withStyle(ChatFormatting.YELLOW), true);
                                        break;
                                    case FULL:
                                        sp.displayClientMessage(Component.translatable("loconautics.bind.copied").withStyle(ChatFormatting.GREEN), true);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(RightClickItem event) {
        Player player = event.getEntity();
        if (player != null && player.isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND) {
            ItemStack held = event.getItemStack();
            Item item = held.getItem();
            if (isAnalogController(item) || item == LoconauticsRegistries.BEARING_AXLE_ITEM.get() || item == LoconauticsRegistries.TRANSMISSION_ITEM.get()) {
                if (FrequencyBindUtil.isBound(held) || FrequencyBindUtil.hasGlint(held)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    if (!event.getLevel().isClientSide && player instanceof ServerPlayer sp) {
                        FrequencyBindUtil.unbind(held);
                        sp.setItemInHand(event.getHand(), held);
                        sp.displayClientMessage(Component.translatable("loconautics.bind.unbound").withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
        }
    }

    private static BlockEntity resolveBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            return be;
        }

        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        return sub != null ? sub.getLevel().getBlockEntity(pos) : null;
    }
}
