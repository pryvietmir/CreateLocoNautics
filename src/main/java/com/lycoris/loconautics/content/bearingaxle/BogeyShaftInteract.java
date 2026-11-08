package com.lycoris.loconautics.content.bearingaxle;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

@EventBusSubscriber(modid = "loconautics")
public final class BogeyShaftInteract {
    private BogeyShaftInteract() {
    }

    @SubscribeEvent
    public static void onUseBlock(RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BogeyShaft.SHAFTED)) {
            ItemStack held = event.getItemStack();
            boolean sneaking = event.getEntity().isShiftKeyDown();
            boolean adding = !sneaking && held.is(AllBlocks.SHAFT.asItem());
            boolean removing = sneaking && held.is((Item)AllItems.WRENCH.get()) && BogeyShaft.isShafted(state);
            if (adding || removing) {
                if (adding && BogeyShaft.isShafted(state)) {
                    IPlacementHelper helper = PlacementHelpers.get(BogeyShaftPlacement.ID);
                    PlacementOffset offset = helper.getOffset(event.getEntity(), level, state, pos, event.getHitVec());
                    if (offset.isSuccessful()) {
                        offset.placeInWorld(level, (BlockItem)held.getItem(), event.getEntity(), event.getHand(), event.getHitVec());
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        event.setCanceled(true);
                    }
                } else {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    if (!level.isClientSide && BogeyShaft.isShafted(state) != adding) {
                        level.setBlock(pos, (BlockState)state.setValue(BogeyShaft.SHAFTED, adding), 3);
                        BogeyShaft.relink(level, pos, (Axis)state.getValue(BlockStateProperties.HORIZONTAL_AXIS));
                        if (removing) {
                            IWrenchable.playRemoveSound(level, pos);
                        } else {
                            BlockState shaft = AllBlocks.SHAFT.getDefaultState();
                            SoundType sound = shaft.getSoundType(level, pos, event.getEntity());
                            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
                        }
                    }
                }
            }
        }
    }
}
