package com.lycoris.loconautics.content.grab;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.common.strand.StrandKnotPicker;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlock;
import com.lycoris.loconautics.content.screwlink.ScrewLinkGrabClient;
import com.lycoris.loconautics.network.packets.attachment.GrabDragPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabStopPacket;
import com.lycoris.loconautics.network.packets.attachment.GrabUndockPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandHookGrabPacket;
import com.lycoris.loconautics.network.packets.attachment.StrandKnotGrabPacket;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class GrabClientHandler {
    private static final float MIN_GRAB_DISTANCE = 1.0F;
    private static final float MAX_GRAB_DISTANCE = 5.0F;
    public static final KeyMapping ROTATE_MODE = new KeyMapping(
        "key.loconautics.grab_rotate", KeyConflictContext.IN_GAME, Type.KEYSYM, 258, "key.categories.loconautics"
    );
    private static final double ROTATE_SENSITIVITY = 0.35;
    private static final double SENSITIVITY_THAT_ZEROES_THE_TURN = -0.3333333333333333;
    @Nullable
    private static UUID grabSub;
    @Nullable
    private static UUID grabStrand;
    @Nullable
    private static UUID grabKnotStrand;
    private static int grabKnotIndex;
    @Nullable
    private static BlockPos grabLocalPos;
    private static float range;
    private static boolean rotating;
    private static boolean sawFree;
    private static final Quaterniond grabOrientation = new Quaterniond();

    private GrabClientHandler() {
    }

    public static boolean grabbing() {
        return grabSub != null || grabStrand != null || grabKnotStrand != null;
    }

    @Nullable
    public static UUID grabbedStrand() {
        return grabStrand;
    }

    @Nullable
    public static Vec3 grabAnchorWorld(float partialTick) {
        if (grabKnotStrand != null) {
            return StrandKnotPicker.knotPosition(grabKnotStrand, grabKnotIndex, partialTick);
        }

        if (grabStrand != null) {
            return ScrewLinkGrabClient.hookPosition(grabStrand, partialTick);
        }

        Minecraft mc = Minecraft.getInstance();
        if (grabSub != null && grabLocalPos != null && mc.level != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
            if (container != null && container.getSubLevel(grabSub) instanceof ClientSubLevel sub) {
                Vector3d var5 = sub.renderPose(partialTick)
                    .transformPosition(new Vector3d(grabLocalPos.getX() + 0.5, grabLocalPos.getY() + 0.5, grabLocalPos.getZ() + 0.5));
                return new Vec3(var5.x, var5.y, var5.z);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static void onInteract(InteractionKeyMappingTriggered event) {
        if (event.isUseItem()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.level != null && !player.isSpectator()) {
                if (grabbing()) {
                    stopGrabbing();
                    event.setCanceled(true);
                } else if (player.getItemInHand(event.getHand()).isEmpty()) {
                    if (player.isSecondaryUseActive()) {
                        StrandKnotPicker.Knot knot = StrandKnotPicker.pick();
                        if (knot != null) {
                            startKnotGrab(knot);
                            event.setCanceled(true);
                            return;
                        }
                    }

                    UUID hookStrand = ScrewLinkGrabClient.pickHook();
                    if (hookStrand != null) {
                        startHookGrab(player, hookStrand);
                        event.setCanceled(true);
                    } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                        BlockPos hitPos = hit.getBlockPos();
                        SubLevel sub = Sable.HELPER.getContaining(mc.level, hitPos);
                        if (sub != null && sub.getLevel().getBlockEntity(hitPos) instanceof GrabbableActor) {
                            Vector3d grabCenterWorld = sub.logicalPose()
                                .transformPosition(new Vector3d(hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5), new Vector3d());
                            double reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
                            double dist = player.getEyePosition().distanceTo(new Vec3(grabCenterWorld.x, grabCenterWorld.y, grabCenterWorld.z));
                            grabSub = sub.getUniqueId();
                            grabLocalPos = hitPos;
                            range = clampRange((float)Math.min(dist, Math.min(5.0, reach)));
                            rotating = false;
                            sawFree = false;
                            grabOrientation.set(sub.logicalPose().orientation());
                            LoconauticsConstants.LOGGER.debug("[undockdbg] client grab pos={} powered={}", hitPos, isPowered(sub, hitPos));
                            if (isPowered(sub, hitPos)) {
                                PacketDistributor.sendToServer(new GrabUndockPacket(grabSub, grabLocalPos, range), new CustomPacketPayload[0]);
                            } else {
                                sendUpdate();
                            }

                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    private static boolean isPowered(SubLevel sub, BlockPos pos) {
        BlockState state = sub.getLevel().getBlockState(pos);
        return state.hasProperty(HoseNozzleBlock.POWERED) && (Boolean)state.getValue(HoseNozzleBlock.POWERED);
    }

    public static void tick() {
        if (grabbing()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                reset();
            } else if (grabKnotStrand != null) {
                if (StrandKnotPicker.knotCount(grabKnotStrand) <= grabKnotIndex + 1) {
                    stopGrabbing();
                }
            } else if (grabStrand != null) {
                if (!ScrewLinkGrabClient.stillLoaded(grabStrand)) {
                    reset();
                }
            } else {
                SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
                if (container != null && container.getSubLevel(grabSub) != null) {
                    SubLevel sub = container.getSubLevel(grabSub);
                    boolean captured = grabLocalPos != null && isPowered(sub, grabLocalPos);
                    if (!captured) {
                        sawFree = true;
                    } else if (sawFree) {
                        reset();
                    }
                } else {
                    stopGrabbing();
                }
            }
        }
    }

    public static boolean onScroll(double deltaY) {
        if (!grabbing()) {
            return false;
        }

        range = clampRange((float)(range + deltaY));
        sendUpdate();
        return true;
    }

    public static void onCalculateTurn(CalculatePlayerTurnEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            double sensitivity = event.getMouseSensitivity() * 0.6 + 0.2;
            double cubed = sensitivity * sensitivity * sensitivity;
            boolean scoped = !event.getCinematicCameraEnabled() && mc.options.getCameraType().isFirstPerson() && player.isScoping();
            double scale = scoped ? cubed : cubed * 8.0;
            int invertY = mc.options.invertYMouse().get() ? -1 : 1;
            double yaw = mc.mouseHandler.getXVelocity() * scale;
            double pitch = mc.mouseHandler.getYVelocity() * scale * invertY;
            if (onMouseTurn(yaw, pitch)) {
                event.setMouseSensitivity(-0.3333333333333333);
                event.setCinematicCameraEnabled(false);
            }
        }
    }

    public static boolean onMouseTurn(double yaw, double pitch) {
        if (grabbing() && grabKnotStrand == null) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return false;
            }

            if (!ROTATE_MODE.isDown()) {
                if (rotating) {
                    rotating = false;
                    sendUpdate();
                }

                return false;
            } else {
                Vec3 axis = player.calculateViewVector(0.0F, player.getYRot() - 90.0F);
                grabOrientation.rotateLocalY(Math.toRadians(yaw) * 0.35);
                grabOrientation.premul(new Quaterniond(new AxisAngle4d(Math.toRadians(-pitch) * 0.35, axis.x, axis.y, axis.z)));
                rotating = true;
                sendUpdate();
                return true;
            }
        } else {
            return false;
        }
    }

    private static void startKnotGrab(StrandKnotPicker.Knot knot) {
        grabKnotStrand = knot.strand();
        grabKnotIndex = knot.index();
        grabStrand = null;
        grabSub = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
        range = clampRange((float)knot.distance());
        sendUpdate();
    }

    private static void startHookGrab(LocalPlayer player, UUID strandId) {
        grabStrand = strandId;
        grabSub = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
        range = clampRange((float)Math.min(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue(), 5.0));
        grabOrientation.identity();
        sendUpdate();
    }

    private static void sendUpdate() {
        if (grabKnotStrand != null) {
            PacketDistributor.sendToServer(new StrandKnotGrabPacket(grabKnotStrand, grabKnotIndex, false, range), new CustomPacketPayload[0]);
        } else if (grabStrand != null) {
            PacketDistributor.sendToServer(
                new StrandHookGrabPacket(grabStrand, false, range, rotating, new Quaterniond(grabOrientation)), new CustomPacketPayload[0]
            );
        } else {
            if (grabSub != null && grabLocalPos != null) {
                PacketDistributor.sendToServer(
                    new GrabDragPacket(grabSub, grabLocalPos, range, rotating, new Quaterniond(grabOrientation)), new CustomPacketPayload[0]
                );
            }
        }
    }

    private static void stopGrabbing() {
        if (grabKnotStrand != null) {
            PacketDistributor.sendToServer(StrandKnotGrabPacket.stopping(grabKnotStrand), new CustomPacketPayload[0]);
        }

        if (grabStrand != null) {
            PacketDistributor.sendToServer(StrandHookGrabPacket.stopping(grabStrand), new CustomPacketPayload[0]);
        }

        if (grabSub != null && grabLocalPos != null) {
            PacketDistributor.sendToServer(new GrabStopPacket(grabSub, grabLocalPos), new CustomPacketPayload[0]);
        }

        reset();
    }

    private static void reset() {
        grabSub = null;
        grabStrand = null;
        grabKnotStrand = null;
        grabLocalPos = null;
        rotating = false;
        sawFree = false;
    }

    private static float clampRange(float r) {
        return Mth.clamp(r, 1.0F, 5.0F);
    }
}
