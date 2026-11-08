package com.lycoris.loconautics.content.turntablebearing;

import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.IntegratedLargeCogHelper;
import com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem.IntegratedSmallCogHelper;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

@EventBusSubscriber(modid = "loconautics")
public final class TurntableCogPlacement {
    private static IPlacementHelper smallHelper;
    private static IPlacementHelper largeHelper;

    private TurntableCogPlacement() {
    }

    public static void register() {
        smallHelper = new TurntableCogPlacement.TurntableSmallCogHelper();
        largeHelper = new TurntableCogPlacement.TurntableLargeCogHelper();
        PlacementHelpers.register(smallHelper);
        PlacementHelpers.register(largeHelper);
    }

    private static boolean isTurntable(BlockState state) {
        return state.getBlock() instanceof TurntableBearingBlock;
    }

    @SubscribeEvent
    public static void onRightClickBlock(RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (smallHelper != null && !player.isShiftKeyDown() && stack.getItem() instanceof CogwheelBlockItem item) {
            Level var10 = event.getLevel();
            BlockPos pos = event.getPos();
            BlockState state = var10.getBlockState(pos);
            if (isTurntable(state) && ICogWheel.isDedicatedCogItem(stack)) {
                IPlacementHelper helper = ICogWheel.isLargeCogItem(stack) ? largeHelper : smallHelper;
                BlockHitResult ray = event.getHitVec();
                PlacementOffset offset = helper.getOffset(player, var10, state, pos, ray);
                if (offset.isSuccessful()) {
                    event.setCancellationResult(offset.placeInWorld(var10, item, player, event.getHand(), ray).result());
                    event.setCanceled(true);
                }
            }
        }
    }

    private static final class TurntableLargeCogHelper extends IntegratedLargeCogHelper {
        public Predicate<BlockState> getStatePredicate() {
            return TurntableCogPlacement::isTurntable;
        }
    }

    private static final class TurntableSmallCogHelper extends IntegratedSmallCogHelper {
        public Predicate<BlockState> getStatePredicate() {
            return TurntableCogPlacement::isTurntable;
        }
    }
}
