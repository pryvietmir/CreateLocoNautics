package com.lycoris.loconautics.trainphysics.visual;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.lycoris.loconautics.trainphysics.wheels.WheelSlip;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

@OnlyIn(Dist.CLIENT)
public final class BogeyWheelAnimator {
    private static final Map<AbstractBogeyBlockEntity, Double> DERAIL_VEL = new WeakHashMap<>();
    private static final double AIRBORNE_DECAY = 0.97;
    private static final double BLOCKED_SLIP = 0.1;
    private static final double SLIP_CHECK_MIN_SPEED = 0.02;
    private static final Map<ResourceKey<Level>, Set<UUID>> STAFF_LOCKS = new ConcurrentHashMap<>();
    private static final Map<AbstractBogeyBlockEntity, UUID> RENDERED = Collections.synchronizedMap(new WeakHashMap<>());

    private BogeyWheelAnimator() {
    }

    public static Quaternionf railWorldRotation(ClientSubLevel sub, AbstractBogeyBlockEntity be, Quaternionf dest) {
        BogeyRailPoints.Chord chord = BogeyRailPoints.chordAt(be, sub);
        if (chord != null) {
            return BogeyRailPoints.worldFrame(chord, dest).normalize();
        }

        Quaterniondc q = sub.renderPose().orientation();
        return dest.set((float)q.x(), (float)q.y(), (float)q.z(), (float)q.w()).normalize();
    }

    private static double wheelSign(AbstractBogeyBlockEntity be) {
        return -1.0;
    }

    public static List<AbstractBogeyBlockEntity> bogeysOf(ClientSubLevel sub) {
        List<AbstractBogeyBlockEntity> out = new ArrayList<>(2);

        for (BlockEntity be : SubLevelBlocks.blockEntities(sub)) {
            if (be instanceof AbstractBogeyBlockEntity bogey
                && !bogey.isRemoved()
                && sub.getLevel().getBlockState(bogey.getBlockPos()).getBlock() instanceof AbstractBogeyBlock) {
                out.add(bogey);
            }
        }

        return out;
    }

    public static void frame(AbstractBogeyBlockEntity be) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
        if (sub != null) {
            if (SableTrainClientRegistry.get(sub.getUniqueId()) != null) {
                RENDERED.put(be, sub.getUniqueId());
            }
        }
    }

    public static void tickAll() {
        List<Entry<AbstractBogeyBlockEntity, UUID>> live;
        synchronized (RENDERED) {
            live = new ArrayList<>(RENDERED.entrySet());
        }

        for (Entry<AbstractBogeyBlockEntity, UUID> entry : live) {
            AbstractBogeyBlockEntity be = entry.getKey();
            SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(entry.getValue());
            if (marker != null) {
                ClientSubLevel sub = Sable.HELPER.getContainingClient(be);
                if (sub != null) {
                    BogeyRailPoints.tick(be);
                    if (marker.brakeHard()) {
                        DERAIL_VEL.remove(be);
                        be.animate(0.0F);
                    } else {
                        BlockPos local = be.getBlockPos().subtract(sub.getPlot().getCenterBlock());
                        if (!marker.workingBogeys().isEmpty() && !marker.workingBogeys().contains(local)) {
                            be.animate(0.0F);
                        } else {
                            Float driven = marker.drivenBogeys().get(local);
                            if (marker.derailed()) {
                                BlockState state = be.getBlockState();
                                Axis axis = state.hasProperty(AbstractBogeyBlock.AXIS) ? (Axis)state.getValue(AbstractBogeyBlock.AXIS) : Axis.X;
                                animateDerailed(be, sub, axis, driven != null ? marker.wheelDrive() : 0.0);
                            } else if (staffLocked(sub)) {
                                be.animate(0.0F);
                            } else {
                                double rolled = marker.speed();
                                double fraction = movedFraction(sub, be, rolled);
                                double raw = rolled;
                                rolled *= fraction;
                                double turn = driven != null ? driven.floatValue() : rolled;
                                if (driven != null && marker.slippingBogeys().contains(local)) {
                                    turn *= WheelSlip.bite(sub.getLevel().getGameTime(), local);
                                }

                                if (Math.abs(raw) < 0.05 && sub.getLevel().getGameTime() % 10L == 0L) {
                                    LoconauticsConstants.LOGGER
                                        .debug(
                                            "[wheelslow] bogey={} speed={} fraction={} rolled={} driven={} turn={}",
                                            new Object[]{local, raw, fraction, rolled, driven, turn}
                                        );
                                }

                                if (driven == null && !marker.slippingBogeys().isEmpty()) {
                                    be.animate(0.0F);
                                } else {
                                    be.animate((float)(turn * wheelSign(be)));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void setStaffLocks(ResourceKey<Level> dimension, List<UUID> locks) {
        STAFF_LOCKS.put(dimension, Set.copyOf(locks));
    }

    private static boolean staffLocked(ClientSubLevel sub) {
        Set<UUID> locks = STAFF_LOCKS.get(sub.getLevel().dimension());
        return locks != null && locks.contains(sub.getUniqueId());
    }

    private static double movedFraction(ClientSubLevel sub, AbstractBogeyBlockEntity be, double commanded) {
        if (Math.abs(commanded) < 0.02) {
            return 1.0;
        }

        BlockPos bp = be.getBlockPos();
        Vector3d center = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        Vector3d cur = sub.logicalPose().transformPosition(center, new Vector3d());
        Vector3d prev = sub.lastPose().transformPosition(center, new Vector3d());
        ClientSubLevel host = BogeyRailPoints.railHost(be);
        if (host != null) {
            host.logicalPose().transformPositionInverse(cur, cur);
            host.lastPose().transformPositionInverse(prev, prev);
        }

        double moved = cur.distance(prev);
        return Math.min(1.0, Math.max(0.1, moved / Math.abs(commanded)));
    }

    private static void animateDerailed(AbstractBogeyBlockEntity be, ClientSubLevel sub, Axis axis, double wheelDrive) {
        BlockPos bp = be.getBlockPos();
        Vector3d center = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        Vector3d cur = sub.logicalPose().transformPosition(center, new Vector3d());
        if (Math.abs(wheelDrive) > 1.0E-6) {
            DERAIL_VEL.put(be, wheelDrive);
            be.animate((float)(wheelDrive * wheelSign(be)));
        } else {
            Vector3d prev = sub.lastPose().transformPosition(center, new Vector3d());
            Vector3d vel = cur.sub(prev, new Vector3d());
            Vector3d a = axis == Axis.X ? new Vector3d(1.0, 0.0, 0.0) : new Vector3d(0.0, 0.0, 1.0);
            sub.logicalPose().transformNormal(a, a);
            double along = vel.dot(a) * (BogeyRenderFrame.facesBackwards(be) ? -1.0 : 1.0);
            double velPerTick;
            if (isGrounded(sub.getLevel(), cur, railSide(be))) {
                velPerTick = along;
                DERAIL_VEL.put(be, along);
            } else {
                Double v = DERAIL_VEL.get(be);
                velPerTick = (v == null ? 0.0 : v) * 0.97;
                DERAIL_VEL.put(be, velPerTick);
            }

            be.animate((float)(velPerTick * wheelSign(be)));
        }
    }

    private static boolean isGrounded(ClientLevel level, Vector3d worldPos, double side) {
        return isGroundedAt(level, worldPos.x, worldPos.y, worldPos.z, side);
    }

    public static double railSide(AbstractBogeyBlockEntity be) {
        BlockState state = be.getBlockState();
        return state.getBlock() instanceof AbstractBogeyBlock<?> bogey && bogey.isUpsideDown(state) ? -1.0 : 1.0;
    }

    public static boolean isGroundedAt(ClientLevel level, double x, double y, double z) {
        return isGroundedAt(level, x, y, z, 1.0);
    }

    public static boolean isGroundedAt(ClientLevel level, double x, double y, double z, double side) {
        if (level == null) {
            return false;
        }

        BlockPos foot = BlockPos.containing(x, y - 0.9 * side, z);
        return hasCollision(level, foot) || hasCollision(level, side < 0.0 ? foot.above() : foot.below());
    }

    private static boolean hasCollision(ClientLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
