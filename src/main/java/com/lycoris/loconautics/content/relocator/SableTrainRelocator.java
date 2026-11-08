package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.network.packets.train.PlaceStoredTrainPacket;
import com.lycoris.loconautics.network.packets.train.RelocatePreviewPacket;
import com.lycoris.loconautics.network.packets.train.SableTrainRelocatePacket;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.rail.RailFollower;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.simibubi.create.content.trains.track.TrackBlockOutline.BezierPointSelection;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@OnlyIn(Dist.CLIENT)
public final class SableTrainRelocator {
    private static final int COLOR_VALID = 9817409;
    private static final int COLOR_INVALID = 15359019;
    private static UUID relocatingSubLevel;
    private static double relocatingSpacing;
    private static Component relocatingName;
    private static boolean storedTrain;
    private static boolean storedOffHand;
    private static BlockPos lastHoveredPos;
    private static BezierTrackPointLocation lastHoveredBezier;
    private static Boolean lastHoveredResult;
    private static List<SableTrainRelocator.Spot> toVisualise;
    private static boolean trainInTheWay;
    private static final int ARROW_KEY_SHAFT = 100000;
    private static final int ARROW_KEY_LEFT = 100001;
    private static final int ARROW_KEY_RIGHT = 100002;

    private SableTrainRelocator() {
    }

    public static void acceptPreview(RailPick pick, boolean blocked) {
        if (pick.pos().equals(lastHoveredPos) && Objects.equals(pick.bezier(), lastHoveredBezier)) {
            trainInTheWay = blocked;
            lastHoveredResult = !blocked;
        }
    }

    private static boolean trainInTheWayAt(BlockPos pos) {
        return trainInTheWay && pos.equals(lastHoveredPos);
    }

    @Nullable
    private static RailPick pickUnderCursor(Minecraft mc, Vec3 look) {
        if (mc.hitResult instanceof BlockHitResult hit) {
            BezierPointSelection curve = TrackBlockOutline.result;
            if (curve != null) {
                BlockPos curvePos = curve.blockEntity().getBlockPos();
                Vec3 lookLocal = RailSeams.hostAt(mc.level, curvePos) instanceof ClientSubLevel c ? toLocalDir(c, look) : look;
                return new RailPick(curvePos, lookLocal.dot(curve.direction()) < 0.0, curve.loc());
            } else {
                return new RailPick(hit.getBlockPos(), false, null);
            }
        } else {
            return null;
        }
    }

    public static boolean isRelocating() {
        return relocatingSubLevel != null;
    }

    private static boolean isRelocator(ItemStack stack) {
        return stack.is((Item)LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get());
    }

    public static void onInteract(InteractionKeyMappingTriggered event) {
        if (event.isUseItem()) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && !player.isSpectator() && mc.level != null) {
                if (storedTrain) {
                    placeStored(event, mc, player);
                } else if (relocatingSubLevel != null) {
                    if (player.isShiftKeyDown()) {
                        relocatingSubLevel = null;
                        player.displayClientMessage(CreateLang.translateDirect("train.relocate.abort", new Object[0]).withStyle(ChatFormatting.RED), true);
                        event.setCanceled(true);
                        event.setSwingHand(false);
                    } else if (!player.isPassenger()) {
                        Boolean relocate = relocateClient(false);
                        if (relocate != null && relocate) {
                            relocatingSubLevel = null;
                        }

                        if (relocate != null) {
                            event.setCanceled(true);
                            event.setSwingHand(false);
                        }
                    }
                } else if ((Boolean)LoconauticsConfig.WRENCH_RELOCATION_ENABLED.get()) {
                    ItemStack heldStack = player.getItemInHand(event.getHand());
                    if (isRelocator(heldStack)) {
                        CompoundTag itemData = RelocatorItemData.read(heldStack);
                        if (itemData != null) {
                            UUID subLevelId = itemTrainSubLevel(itemData);
                            SableTrainClientRegistry.TrainMarker marker = subLevelId == null ? null : SableTrainClientRegistry.get(subLevelId);
                            if (marker != null) {
                                if (!(Boolean)LoconauticsConfig.WRENCH_RELOCATION_DERAILED_ONLY.get() || marker.derailed()) {
                                    relocatingSubLevel = subLevelId;
                                    relocatingSpacing = marker.consistLength();
                                    relocatingName = heldStack.getHoverName().copy();
                                    lastHoveredPos = null;
                                    lastHoveredResult = null;
                                    toVisualise = null;
                                    event.setCanceled(true);
                                    event.setSwingHand(false);
                                }
                            }
                        } else if (mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == Type.BLOCK) {
                            SubLevel sub = Sable.HELPER.getContaining(mc.level, blockHit.getBlockPos());
                            if (sub != null) {
                                SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
                                if (marker != null) {
                                    if (!(Boolean)LoconauticsConfig.WRENCH_RELOCATION_DERAILED_ONLY.get() || marker.derailed()) {
                                        relocatingSubLevel = sub.getUniqueId();
                                        relocatingSpacing = marker.consistLength();
                                        relocatingName = sub.getName() != null ? Component.literal(sub.getName()) : Component.literal("Train");
                                        lastHoveredPos = null;
                                        lastHoveredResult = null;
                                        toVisualise = null;
                                        event.setCanceled(true);
                                        event.setSwingHand(false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void placeStored(InteractionKeyMappingTriggered event, Minecraft mc, LocalPlayer player) {
        if (player.isShiftKeyDown()) {
            clear();
            event.setCanceled(true);
            event.setSwingHand(false);
        } else {
            RailPick pick = pickUnderCursor(mc, player.getLookAngle());
            if (pick != null && mc.level.getBlockState(pick.pos()).getBlock() instanceof ITrackBlock) {
                if (trainInTheWayAt(pick.pos())) {
                    event.setCanceled(true);
                    event.setSwingHand(false);
                } else {
                    CatnipServices.NETWORK.sendToServer(new PlaceStoredTrainPacket(pick, player.getLookAngle(), storedOffHand));
                    clear();
                    event.setCanceled(true);
                    event.setSwingHand(false);
                }
            }
        }
    }

    private static void clear() {
        relocatingSubLevel = null;
        storedTrain = false;
        trainInTheWay = false;
        lastHoveredPos = null;
        lastHoveredResult = null;
        toVisualise = null;
    }

    private static boolean beginStored(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isRelocator(stack)) {
                CompoundTag tag = RelocatorItemData.read(stack);
                if (tag != null && RelocatorItemData.isStored(tag)) {
                    UUID trainId = RelocatorItemData.trainId(tag);
                    if (trainId != null) {
                        relocatingSubLevel = trainId;
                        relocatingSpacing = Math.max(1.0, RelocatorItemData.span(tag));
                        relocatingName = stack.getHoverName().copy();
                        storedTrain = true;
                        storedOffHand = hand == InteractionHand.OFF_HAND;
                        lastHoveredPos = null;
                        lastHoveredResult = null;
                        toVisualise = null;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Nullable
    private static UUID itemTrainSubLevel(CompoundTag tag) {
        if (RelocatorItemData.isConsist(tag)) {
            List<UUID> order = RelocatorItemData.consistOrder(tag);
            return order.isEmpty() ? null : order.get(0);
        } else {
            return RelocatorItemData.carriageId(tag);
        }
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && !player.isPassenger() && mc.level != null) {
            if (relocatingSubLevel != null || beginStored(player)) {
                if (!storedTrain && SableTrainClientRegistry.get(relocatingSubLevel) == null) {
                    relocatingSubLevel = null;
                } else if (storedTrain && !isRelocator(player.getMainHandItem()) && !isRelocator(player.getOffhandItem())) {
                    clear();
                } else if (!isRelocator(player.getMainHandItem()) && !isRelocator(player.getOffhandItem())) {
                    player.displayClientMessage(CreateLang.translateDirect("train.relocate.abort", new Object[0]).withStyle(ChatFormatting.RED), true);
                    relocatingSubLevel = null;
                } else {
                    Boolean success = relocateClient(true);
                    if (success == null) {
                        player.displayClientMessage(CreateLang.translateDirect("train.relocate", new Object[]{relocatingName}), true);
                    } else if (trainInTheWay) {
                        player.displayClientMessage(
                            Component.translatable("message.loconautics.relocator.train_in_the_way").withStyle(ChatFormatting.RED), true
                        );
                    } else if (success) {
                        player.displayClientMessage(CreateLang.translateDirect("train.relocate.valid", new Object[0]).withStyle(ChatFormatting.GREEN), true);
                    } else {
                        player.displayClientMessage(CreateLang.translateDirect("train.relocate.invalid", new Object[0]).withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    @Nullable
    private static Boolean relocateClient(boolean simulate) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult blockhit)) {
            return null;
        } else {
            Vec3 lookAngle = mc.player.getLookAngle();
            RailPick pick = pickUnderCursor(mc, lookAngle);
            BlockPos blockPos = pick.pos();
            Vec3 offset = Vec3.ZERO;
            if (simulate && toVisualise != null && lastHoveredResult != null) {
                List<Vec3> drawn = new ArrayList<>(toVisualise.size());

                for (SableTrainRelocator.Spot spot : toVisualise) {
                    drawn.add(spot.world());
                }

                for (int i = 0; i < drawn.size() - 1; i++) {
                    Vec3 v1 = drawn.get(i).add(offset);
                    Vec3 v2 = drawn.get(i + 1).add(offset);
                    Outliner.getInstance()
                        .showLine(Pair.of(relocatingSubLevel, i), v1.add(0.0, -0.925F, 0.0), v2.add(0.0, -0.925F, 0.0))
                        .colored(!lastHoveredResult && i == drawn.size() - 2 ? 15359019 : 9817409)
                        .disableLineNormals()
                        .lineWidth(i % 2 == 1 ? 0.16666667F : 0.25F);
                }

                drawDirectionArrow(drawn, offset, lastHoveredResult ? 9817409 : 15359019);
            }

            if (simulate) {
                if (lastHoveredPos != null && lastHoveredPos.equals(blockPos) && Objects.equals(lastHoveredBezier, pick.bezier())) {
                    return lastHoveredResult;
                }

                lastHoveredPos = blockPos;
                lastHoveredBezier = pick.bezier();
                toVisualise = null;
            }

            BlockState blockState = mc.level.getBlockState(blockPos);
            Block block = blockState.getBlock();
            if (!(block instanceof ITrackBlock)) {
                lastHoveredResult = null;
                trainInTheWay = false;
                return null;
            }

            boolean onRail = relocate(mc.level, pick, lookAngle);
            if (!onRail) {
                trainInTheWay = false;
            }

            if (simulate && onRail) {
                CatnipServices.NETWORK.sendToServer(new RelocatePreviewPacket(storedTrain, storedOffHand, relocatingSubLevel, pick, lookAngle));
            }

            boolean result = onRail && !trainInTheWayAt(blockPos);
            if (!simulate && result) {
                CatnipServices.NETWORK.sendToServer(new SableTrainRelocatePacket(relocatingSubLevel, pick, lookAngle));
            }

            lastHoveredResult = result;
            return result;
        }
    }

    private static void drawDirectionArrow(List<Vec3> drawn, Vec3 offset, int color) {
        if (drawn.size() >= 2) {
            Vec3 yOff = new Vec3(0.0, 0.35, 0.0);
            Vec3 head = drawn.get(0).add(offset).add(yOff);
            Vec3 behind = drawn.get(1).add(offset).add(yOff);
            Vec3 railTan = head.subtract(behind);
            if (!(railTan.lengthSqr() < 1.0E-6)) {
                Vec3 fwd = railTan.normalize();
                Vec3 lateral = new Vec3(-fwd.z, 0.0, fwd.x);
                Vec3 tail = head.subtract(fwd.scale(0.3));
                Vec3 tip = head.add(fwd.scale(1.6));
                Vec3 wingL = tip.subtract(fwd.scale(0.7)).add(lateral.scale(0.6));
                Vec3 wingR = tip.subtract(fwd.scale(0.7)).subtract(lateral.scale(0.6));
                arrowSegment(100000, tail, tip, color);
                arrowSegment(100001, tip, wingL, color);
                arrowSegment(100002, tip, wingR, color);
            }
        }
    }

    private static void arrowSegment(int key, Vec3 a, Vec3 b, int color) {
        Outliner.getInstance().showLine(Pair.of(relocatingSubLevel, key), a, b).colored(color).disableLineNormals().lineWidth(0.25F);
    }

    private static boolean relocate(Level level, RailPick pick, Vec3 lookAngle) {
        BlockPos pos = pick.pos();
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof ITrackBlock track) {
            ClientSubLevel host = RailSeams.hostAt(level, pos) instanceof ClientSubLevel c ? c : null;
            Vec3 lookLocal = host == null ? lookAngle : toLocalDir(host, lookAngle);
            TrackGraphLocation graphLocation = pick.graphLocation(level, track, blockState, lookLocal);
            if (graphLocation != null && graphLocation.graph != null) {
                TrackGraph graph = graphLocation.graph;
                TravellingPoint probe = RailFollower.pointAt(graphLocation);
                if (probe == null) {
                    return false;
                }

                Vec3 upNormal = track.getUpNormal(level, pos, blockState);
                ITrackSelector steer = probe.steer(SteerDirection.NONE, upNormal);
                List<SableTrainRelocator.Spot> recorded = new ArrayList<>();
                Vec3 here = probe.getPosition(graph);
                recorded.add(spotOf(probe, graph));
                TravellingPoint tangentProbe = new TravellingPoint(probe.node1, probe.node2, probe.edge, probe.position, probe.upsideDown);
                tangentProbe.travel(graph, 0.1, steer, tangentProbe.ignoreEdgePoints(), tangentProbe.ignoreTurns(), tangentProbe.ignorePortals());
                Vec3 tangent = tangentProbe.getPosition(graph).subtract(here);
                double sign = tangent.x * lookLocal.x + tangent.z * lookLocal.z >= 0.0 ? -1.0 : 1.0;
                boolean blocked = false;
                double remaining = Math.max(1.0, relocatingSpacing);

                while (remaining > 0.001 && !blocked) {
                    double d = Math.min(1.0, remaining);
                    probe.travel(graph, sign * d, steer, probe.ignoreEdgePoints(), probe.ignoreTurns(), portal -> true);
                    recorded.add(spotOf(probe, graph));
                    if (probe.blocked) {
                        blocked = true;
                    }

                    remaining -= d;
                }

                toVisualise = recorded;
                return !blocked;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static SableTrainRelocator.Spot spotOf(TravellingPoint point, TrackGraph graph) {
        ClientSubLevel host = RailSeams.clientHostOf(point.node1.getLocation()) instanceof ClientSubLevel c ? c : null;
        return new SableTrainRelocator.Spot(point.getPosition(graph), host);
    }

    private static Vec3 toWorld(ClientSubLevel host, Vec3 local) {
        if (host != null && local != null) {
            Vector3d w = host.renderPose().transformPosition(new Vector3d(local.x, local.y, local.z), new Vector3d());
            return new Vec3(w.x, w.y, w.z);
        } else {
            return local;
        }
    }

    private static Vec3 toLocalDir(ClientSubLevel host, Vec3 world) {
        Vector3d l = host.renderPose().transformNormalInverse(new Vector3d(world.x, world.y, world.z), new Vector3d());
        return new Vec3(l.x, l.y, l.z);
    }

    private record Spot(Vec3 local, @Nullable ClientSubLevel host) {
        Vec3 world() {
            return SableTrainRelocator.toWorld(this.host, this.local);
        }
    }
}
