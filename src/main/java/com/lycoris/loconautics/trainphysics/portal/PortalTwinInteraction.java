package com.lycoris.loconautics.trainphysics.portal;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;

@EventBusSubscriber(modid = "loconautics")
public final class PortalTwinInteraction {
    private static boolean forwarding;

    private PortalTwinInteraction() {
    }

    @SubscribeEvent
    public static void onUse(RightClickBlock event) {
        if (!forwarding && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            PortalTwins.MasterBlock target = PortalTwins.masterBlock(level, event.getPos());
            if (target != null) {
                event.setCanceled(true);
                if (target.level().getBlockState(target.pos()).getBlock() instanceof SeatBlock) {
                    event.setCancellationResult(InteractionResult.FAIL);
                } else {
                    BlockHitResult hit = event.getHitVec();
                    Vec3 shift = Vec3.atLowerCornerOf(target.pos().subtract(event.getPos()));
                    BlockHitResult moved = new BlockHitResult(hit.getLocation().add(shift), hit.getDirection(), target.pos(), hit.isInside());
                    forwarding = true;

                    try {
                        event.setCancellationResult(
                            player.gameMode.useItemOn(player, target.level(), player.getItemInHand(event.getHand()), event.getHand(), moved)
                        );
                    } finally {
                        forwarding = false;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PortalTwins.MasterBlock target = PortalTwins.masterBlock(level, event.getPos());
            if (target != null) {
                event.setCanceled(true);
                if (event.getPlayer() instanceof ServerPlayer player) {
                    ServerLevel var12 = target.level();
                    BlockPos masterPos = target.pos();
                    BlockState state = var12.getBlockState(masterPos);
                    if (!state.isAir()) {
                        if (player.isCreative()) {
                            var12.destroyBlock(masterPos, false, player);
                        } else {
                            ItemStack tool = player.getMainHandItem();
                            BlockEntity be = var12.getBlockEntity(masterPos);

                            for (ItemStack drop : Block.getDrops(state, var12, masterPos, be, player, tool)) {
                                Block.popResource(level, event.getPos(), drop);
                            }

                            tool.mineBlock(var12, state, masterPos, player);
                            var12.destroyBlock(masterPos, false, player);
                        }
                    }
                }
            }
        }
    }
}
