package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.client.StrandPreviewOutline;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.winch.HoseWinchBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableItem;
import com.lycoris.loconautics.network.packets.attachment.HoseAttachPacket;
import com.lycoris.loconautics.network.packets.attachment.HoseLinkPacket;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class HoseInteractionHandler {
    @Nullable
    private static BlockPos pendingWinchPos;
    @Nullable
    private static UUID pendingNozzleSub;
    @Nullable
    private static BlockPos pendingNozzleLocal;

    private HoseInteractionHandler() {
    }

    public static void onInteract(InteractionKeyMappingTriggered event) {
        if (event.isUseItem()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.level != null && !player.isSpectator()) {
                if (player.getItemInHand(event.getHand()).getItem() instanceof HoseItem) {
                    if (player.isShiftKeyDown() && hasPending()) {
                        clearPending();
                        player.displayClientMessage(Component.translatable("message.loconautics.hose.cleared"), true);
                        event.setCanceled(true);
                    } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
                        BlockPos hitPos = hit.getBlockPos();
                        SubLevel sub = Sable.HELPER.getContaining(mc.level, hitPos);
                        UUID hitSub = sub == null ? null : sub.getUniqueId();
                        HoseInteractionHandler.Kind kind = classify(mc.level, hitPos);
                        ItemStack stack = player.getItemInHand(event.getHand());
                        BlockPos firstConnection = (BlockPos)stack.get(SteelCableItem.firstConnection());
                        if (firstConnection != null) {
                            if (kind == HoseInteractionHandler.Kind.WINCH) {
                                SubLevel firstSub = Sable.HELPER.getContaining(mc.level, firstConnection);
                                fire(player, hitPos, firstSub == null ? null : firstSub.getUniqueId(), firstConnection);
                                stack.remove(SteelCableItem.firstConnection());
                                event.setCanceled(true);
                            }
                        } else if (pendingWinchPos == null) {
                            if (pendingNozzleLocal != null) {
                                if (kind == HoseInteractionHandler.Kind.WINCH) {
                                    fire(player, hitPos, pendingNozzleSub, pendingNozzleLocal);
                                    event.setCanceled(true);
                                } else if (kind == HoseInteractionHandler.Kind.LINK) {
                                    PacketDistributor.sendToServer(new HoseLinkPacket(pendingNozzleLocal, hitPos), new CustomPacketPayload[0]);
                                    clearPending();
                                    player.displayClientMessage(Component.translatable("message.loconautics.hose.attached"), true);
                                    event.setCanceled(true);
                                }
                            } else {
                                if (kind == HoseInteractionHandler.Kind.NOZZLE) {
                                    pendingNozzleSub = hitSub;
                                    pendingNozzleLocal = hitPos;
                                    player.displayClientMessage(Component.translatable("message.loconautics.hose.nozzle_selected"), true);
                                    event.setCanceled(true);
                                } else if (kind == HoseInteractionHandler.Kind.WINCH) {
                                    pendingWinchPos = hitPos;
                                    player.displayClientMessage(Component.translatable("message.loconautics.hose.winch_selected"), true);
                                    event.setCanceled(true);
                                }
                            }
                        } else {
                            if (kind == HoseInteractionHandler.Kind.NOZZLE || kind == HoseInteractionHandler.Kind.LINK) {
                                fire(player, pendingWinchPos, hitSub, hitPos);
                                event.setCanceled(true);
                            }
                        }
                    }
                }
            }
        }
    }

    private static HoseInteractionHandler.Kind classify(Level level, BlockPos pos) {
        SubLevel sub = Sable.HELPER.getContaining(level, pos);
        BlockEntity be = (sub != null ? sub.getLevel() : level).getBlockEntity(pos);
        if (be instanceof HoseWinchBlockEntity) {
            return HoseInteractionHandler.Kind.WINCH;
        } else if (be instanceof HoseNozzleBlockEntity && sub != null) {
            return HoseInteractionHandler.Kind.NOZZLE;
        } else {
            return be instanceof SteelCableHolder holder && holder.acceptsFluidLinks() && !(be instanceof HoseNozzleBlockEntity)
                ? HoseInteractionHandler.Kind.LINK
                : HoseInteractionHandler.Kind.NONE;
        }
    }

    private static void fire(LocalPlayer player, BlockPos winchPos, @Nullable UUID farSub, BlockPos farLocal) {
        PacketDistributor.sendToServer(new HoseAttachPacket(winchPos, farSub, farLocal), new CustomPacketPayload[0]);
        clearPending();
        player.displayClientMessage(Component.translatable("message.loconautics.hose.attached"), true);
    }

    private static boolean hasPending() {
        return pendingWinchPos != null || pendingNozzleSub != null && pendingNozzleLocal != null;
    }

    private static void clearPending() {
        pendingWinchPos = null;
        pendingNozzleSub = null;
        pendingNozzleLocal = null;
    }

    public static void tickPreview() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player != null && level != null && mc.screen == null && hasPending()) {
            boolean holding = player.getMainHandItem().getItem() instanceof HoseItem || player.getOffhandItem().getItem() instanceof HoseItem;
            if (holding) {
                Vec3 firstPoint = resolveFirstPoint(level);
                if (firstPoint != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == Type.BLOCK) {
                    StrandPreviewOutline.draw(
                        level,
                        "Hose",
                        firstPoint,
                        hit.getLocation(),
                        hit.getBlockPos(),
                        HoseType.DEFAULT.maxLength(),
                        targetComplementary(level, hit.getBlockPos())
                    );
                }
            }
        }
    }

    @Nullable
    private static Vec3 resolveFirstPoint(ClientLevel level) {
        if (pendingWinchPos != null) {
            return Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(pendingWinchPos));
        }

        if (pendingNozzleSub != null && pendingNozzleLocal != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(pendingNozzleSub) instanceof ClientSubLevel sub) {
                Pose3dc pose = sub.renderPose();
                Vector3d w = pose.transformPosition(
                    new Vector3d(pendingNozzleLocal.getX() + 0.5, pendingNozzleLocal.getY() + 0.5, pendingNozzleLocal.getZ() + 0.5), new Vector3d()
                );
                return new Vec3(w.x, w.y, w.z);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static boolean targetComplementary(ClientLevel level, BlockPos hitPos) {
        HoseInteractionHandler.Kind kind = classify(level, hitPos);
        return pendingWinchPos != null
            ? kind == HoseInteractionHandler.Kind.NOZZLE || kind == HoseInteractionHandler.Kind.LINK
            : kind == HoseInteractionHandler.Kind.WINCH || kind == HoseInteractionHandler.Kind.LINK;
    }

    private enum Kind {
        WINCH,
        NOZZLE,
        LINK,
        NONE;
    }
}
