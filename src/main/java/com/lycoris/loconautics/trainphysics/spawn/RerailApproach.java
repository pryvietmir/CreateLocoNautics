package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RerailApproach {
    private static final double SETTLE_SINK = 0.1875;
    private static final double ACQUIRE_LATERAL = 0.75;
    private static final double ACQUIRE_RISE = 0.5;
    private static final double ACQUIRE_DROP = 0.5;
    private static final double ACQUIRE_YAW_DEGREES = 60.0;
    private static final double COMMIT_LATERAL = 0.2;
    private static final double COMMIT_VERTICAL = 0.2;
    private static final double COMMIT_YAW_DEGREES = 6.0;
    private static final double STEP_GAIN = 0.35;
    private static final double MIN_STEP = 0.05;
    private static final double MAX_STEP = 0.4;
    private static final double TURN_GAIN = 0.3;
    private static final int MAX_TICKS = 60;
    private static final int RETRY_COOLDOWN_TICKS = 200;
    private static final int IDLE_SCAN_INTERVAL_TICKS = 10;
    private static final Map<UUID, Integer> WALKING = new HashMap<>();
    private static final Map<UUID, Integer> EASING = new HashMap<>();
    private static final Map<UUID, Long> GAVE_UP = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> SWINGING = new HashMap<>();
    private static final double EASE_MAX_GAP = 1.25;
    private static final double EASE_DONE_GAP = 0.05;
    private static final double BOGEY_RERAIL_UPRIGHT = 0.9;
    private static final double SWING_ACQUIRE_DEGREES = 35.0;
    private static final double SWING_DONE_DEGREES = 0.75;
    private static final double SWING_GAIN = 0.3;
    private static final double SWING_MIN_STEP_DEGREES = 0.5;
    private static final double SWING_MAX_STEP_DEGREES = 4.0;

    private RerailApproach() {
    }

    public static boolean holdApproach(PhysicsPipeline pipeline, ServerSubLevel sub, SableTrain train) {
        if (!WALKING.containsKey(train.id()) && !EASING.containsKey(train.id())) {
            return false;
        }

        Pose3d pose = sub.logicalPose();
        pipeline.teleport(sub, pose.position(), pose.orientation());
        pipeline.resetVelocity(sub);
        GhostWheelBodies.settleWheels(train);
        return true;
    }

    public static void forget(UUID trainId) {
        SWINGING.remove(trainId);
        WALKING.remove(trainId);
        EASING.remove(trainId);
        GAVE_UP.remove(trainId);
        BogeyEditTrace.forget(trainId);
        RerailBalance.forget(trainId);
    }

    public static void clearAll() {
        SWINGING.clear();
        WALKING.clear();
        EASING.clear();
        GAVE_UP.clear();
        RerailBalance.clearAll();
    }

    static void giveUp(SableTrain train, long gameTime) {
        GAVE_UP.put(train.id(), gameTime);
    }

    private static boolean abort(SableTrain train) {
        WALKING.remove(train.id());
        EASING.remove(train.id());
        GhostWheelBodies.clearAlign(train);
        return false;
    }

    private static boolean abort(SableTrain train, String why) {
        BogeyEditTrace.refuse(train, why);
        BogeyEditTrace.phase(train, "blocked", why);
        return abort(train);
    }

    private static boolean heldFromOutside(ServerLevel level, ServerSubLevel sub) {
        if (ExternalForceDerail.staffHolds(sub)) {
            return true;
        }

        ServerLevelRopeManager manager = ServerLevelRopeManager.getOrCreate(level);
        if (manager == null) {
            return false;
        }

        UUID carId = sub.getUniqueId();

        for (ServerRopeStrand strand : manager.getAllStrands()) {
            boolean onCar = false;
            boolean outside = false;

            for (RopeAttachment attachment : strand.getAttachments()) {
                UUID end = attachment.subLevelID();
                if (carId.equals(end)) {
                    onCar = true;
                } else if (SableTrainRegistry.bySubLevel(end) == null) {
                    outside = true;
                }
            }

            if (onCar && outside) {
                return true;
            }
        }

        return false;
    }

    public static boolean tryAutoRerail(SableTrain train) {
        if ((Boolean)LoconauticsConfig.AUTO_RERAIL_ENABLED.get() && train.isDerailed()) {
            ServerLevel level = train.level();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return abort(train);
            }

            UUID subLevelId = train.car().subLevelId();
            if (subLevelId == null || !(container.getSubLevel(subLevelId) instanceof ServerSubLevel sub && !sub.isRemoved())) {
                return abort(train);
            }

            if (heldFromOutside(level, sub)) {
                BogeyEditTrace.refuse(train, "held from outside");
                BogeyEditTrace.phase(train, "held", ExternalForceDerail.staffHolds(sub) ? "physics staff" : "rope");
                return abort(train);
            }

            Long gaveUp = GAVE_UP.get(train.id());
            if (!WALKING.containsKey(train.id()) && !EASING.containsKey(train.id()) && gaveUp != null && level.getGameTime() - gaveUp < 200L) {
                BogeyEditTrace.refuse(train, "gave up cooldown");
                BogeyEditTrace.phase(train, "cooldown", "left " + (200L - (level.getGameTime() - gaveUp)) + " ticks");
                return false;
            }

            if (!WALKING.containsKey(train.id()) && !EASING.containsKey(train.id()) && (level.getGameTime() + train.id().hashCode()) % 10L != 0L) {
                return false;
            }

            if (!WALKING.containsKey(train.id()) && !EASING.containsKey(train.id()) && RerailBalance.refuses(level, sub, train)) {
                return false;
            }

            switch (tryBogeyRerail(level, sub, train)) {
                case NONE:
                    EASING.remove(train.id());
                    break;
                case EASING:
                    return false;
                case SEATED:
                    return true;
            }

            boolean walking = WALKING.containsKey(train.id());
            if (!walking && !SubLevelData.atRest(sub, (Double)LoconauticsConfig.AUTO_RERAIL_MAX_SPEED.get())) {
                BogeyEditTrace.refuse(train, "not at rest");
                BogeyEditTrace.phase(
                    train,
                    "settle",
                    String.format(
                        "at rest no, lin %.2f ang %.2f limit %.2f upright %s",
                        lengthOf(SubLevelData.linearVelocity(sub)),
                        lengthOf(SubLevelData.angularVelocity(sub)),
                        LoconauticsConfig.AUTO_RERAIL_MAX_SPEED.get(),
                        SubLevelData.isUpright(sub, 0.5)
                    )
                );
                return false;
            }

            if (!walking && !TrainPropulsion.isMonorail(train) && !SubLevelData.isUpright(sub, 0.5)) {
                BogeyEditTrace.refuse(train, "not upright");
                BogeyEditTrace.phase(train, "settle", String.format("at rest yes, upright no, up y %.2f", SubLevelData.up(sub).y));
                return false;
            }

            List<SableTrainSpawner.BogeySeat> seats = SableTrainSpawner.bogeySeats(level, sub, train);
            if (seats == null) {
                return abort(train, "bogeySeats null, a bogey has no rail under it");
            }

            Vec3 plate = GhostWheelBodies.plateFacing(train);
            Vec3 fwd = plate != null ? plate : SableTrainSpawner.currentFacing(sub, train.car());
            Vector3d anchor = SableTrainSpawner.bogeyAnchorWorld(sub, train.car());
            SableTrainSpawner.TrackHit bodyHit = SableTrainSpawner.findRailBelow(level, BlockPos.containing(anchor.x, anchor.y, anchor.z), fwd);
            if (bodyHit == null) {
                bodyHit = seats.get(0).hit();
            }

            Vec3 facing = SableTrainSpawner.currentFacing(sub, train.car());
            SableTrainSpawner.SeatPose landing = SableTrainSpawner.seatPoseAt(level, train, bodyHit.location(), bodyHit.upNormal(), facing, true);
            if (landing == null || landing.origin() == null) {
                return abort(train, "no landing pose");
            }

            if (landing.carriage() != null && landing.carriage().railheadWithin(1.0)) {
                return abort(train, "railhead within probe at landing");
            }

            RerailApproach.Error worst = worstError(level, sub, train, seats, landing);
            if (level.getGameTime() % 5L == 0L) {
                StringBuilder trace = new StringBuilder("[issue294] car ")
                    .append(train.id())
                    .append(" walking=")
                    .append(walking)
                    .append(" landing center=")
                    .append(landing.carriage() == null ? null : landing.carriage().center())
                    .append(" fwd=")
                    .append(landing.carriage() == null ? null : landing.carriage().forward())
                    .append(" worst lateral=")
                    .append(String.format("%.2f", worst.lateral()))
                    .append(" vertical=")
                    .append(String.format("%.2f", worst.vertical()))
                    .append(" yaw=")
                    .append(String.format("%.1f", worst.yawDegrees()));

                for (SableTrainSpawner.BogeySeat seat : seats) {
                    trace.append(" [")
                        .append(seat.bogey().localPos().toShortString())
                        .append(" rail=")
                        .append(seat.rail().center())
                        .append(" railFwd=")
                        .append(seat.rail().forward())
                        .append("]");
                }

                LoconauticsConstants.LOGGER.info(trace.toString());
            }

            if (!walking && !canAcquire(level, sub, train, seats, landing, worst)) {
                return abort(
                    train, String.format("cannot acquire, worst lateral %.2f vertical %.2f yaw %.1f", worst.lateral(), worst.vertical(), worst.yawDegrees())
                );
            }

            boolean aligned = steerPlates(train, seats);
            int walked = WALKING.getOrDefault(train.id(), 0) + 1;
            if (aligned && worst.within(0.2, 0.2, 6.0)) {
                WALKING.remove(train.id());
                boolean ok = SableTrainSpawner.seatCarAt(level, train, bodyHit.location(), bodyHit.upNormal(), facing, true, false);
                if (ok) {
                    RerailBalance.noteAutoRerail(train, level.getGameTime());
                    BogeyEditTrace.seated(train);
                }

                return ok;
            } else if (walked >= 60) {
                GAVE_UP.put(train.id(), level.getGameTime());
                return abort(
                    train,
                    String.format(
                        "walk ran out, aligned=%s worst lateral %.2f vertical %.2f yaw %.1f", aligned, worst.lateral(), worst.vertical(), worst.yawDegrees()
                    )
                );
            } else {
                WALKING.put(train.id(), walked);
                BogeyEditTrace.phase(
                    train,
                    "walking",
                    String.format(
                        "step %d, aligned %s, worst lateral %.2f vertical %.2f yaw %.1f",
                        walked,
                        aligned,
                        worst.lateral(),
                        worst.vertical(),
                        worst.yawDegrees()
                    )
                );
                stepBody(level, train, sub, landing);
                return false;
            }
        } else {
            return abort(train);
        }
    }

    private static double lengthOf(Vector3d vector) {
        return vector == null ? 0.0 : vector.length();
    }

    private static RerailApproach.BogeyRerail tryBogeyRerail(ServerLevel level, ServerSubLevel sub, SableTrain train) {
        if (train.isTrackless()) {
            return RerailApproach.BogeyRerail.NONE;
        }

        if (WALKING.containsKey(train.id())) {
            return RerailApproach.BogeyRerail.NONE;
        }

        boolean easing = EASING.containsKey(train.id());
        if (easing
            || SubLevelData.atRest(sub, (Double)LoconauticsConfig.AUTO_RERAIL_MAX_SPEED.get())
                && (TrainPropulsion.isMonorail(train) || SubLevelData.isUpright(sub, 0.9))) {
            Vec3 plate = GhostWheelBodies.plateFacing(train);
            Vec3 fwd = plate != null ? plate : SableTrainSpawner.currentFacing(sub, train.car());
            List<SableTrain.Bogey> updated = new ArrayList<>(train.car().bogeys().size());
            List<BlockPos> stillOff = new ArrayList<>();
            SableTrain.Bogey pivot = null;

            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                RailCarriage seat = SableTrainSpawner.acceptableSeatFor(level, sub, bogey, fwd, null, 1.25);
                if (seat == null) {
                    stillOff.add(bogey.localPos());
                    updated.add(bogey);
                } else {
                    TrainRailAdvance.seedTravelSign(seat, SableTrainSpawner.currentFacing(sub, train.car()));
                    SableTrain.Bogey seated = new SableTrain.Bogey(bogey.localPos(), seat);
                    updated.add(seated);
                    if (pivot == null) {
                        pivot = seated;
                    }
                }
            }

            if (stillOff.size() == updated.size()) {
                BogeyEditTrace.refuse(train, "bogey rerail, no bogey has an acceptable seat");
                return RerailApproach.BogeyRerail.NONE;
            }

            Vector3d gap = seatGap(sub, train, pivot);
            if (gap != null && !(gap.length() > 1.25)) {
                boolean aligned = steerSeatedPlates(train, updated, stillOff);
                if (!(gap.length() > 0.05) && aligned) {
                    easeBody(level, train, sub, gap);
                    train.setBogeys(updated);
                    train.setDerailed(false);

                    for (BlockPos pos : stillOff) {
                        train.markBogeyAwaitingRail(pos);
                    }

                    EASING.remove(train.id());
                    GhostWheelBodies.clearAlign(train);
                    RerailBalance.noteAutoRerail(train, level.getGameTime());
                    BogeyEditTrace.seated(train);
                    return RerailApproach.BogeyRerail.SEATED;
                } else {
                    int walked = EASING.getOrDefault(train.id(), 0) + 1;
                    if (walked > 60) {
                        GAVE_UP.put(train.id(), level.getGameTime());
                        BogeyEditTrace.phase(train, "gaveup", "easing ran out, gap " + gap.length());
                        abort(train);
                        return RerailApproach.BogeyRerail.NONE;
                    } else {
                        EASING.put(train.id(), walked);
                        BogeyEditTrace.phase(
                            train,
                            "easing",
                            String.format(
                                    "step %d, gap %.3f (x %.3f y %.3f z %.3f), plates aligned %s, body y %.3f",
                                    walked,
                                    gap.length(),
                                    gap.x,
                                    gap.y,
                                    gap.z,
                                    aligned,
                                    sub.logicalPose().position().y()
                                )
                                + easeProbe(sub, train, pivot)
                        );
                        easeBody(level, train, sub, gap);
                        return RerailApproach.BogeyRerail.EASING;
                    }
                }
            } else {
                BogeyEditTrace.refuse(train, "bogey rerail, pivot gap " + (gap == null ? "null" : gap.length()));
                return RerailApproach.BogeyRerail.NONE;
            }
        } else {
            return RerailApproach.BogeyRerail.NONE;
        }
    }

    public static boolean swingOntoRail(ServerLevel level, ServerSubLevel sub, SableTrain train) {
        SableTrain.Bogey pivot = null;

        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos())) {
                if (pivot != null) {
                    return stopSwing(train);
                }

                pivot = bogey;
            }
        }

        if (pivot != null && pivot.rail() != null && !heldFromOutside(level, sub)) {
            Long gaveUp = GAVE_UP.get(train.id());
            if (gaveUp != null && level.getGameTime() - gaveUp < 200L) {
                return false;
            }

            if (!SubLevelData.atRest(sub, (Double)LoconauticsConfig.AUTO_RERAIL_MAX_SPEED.get())) {
                return stopSwing(train);
            }

            Vector3d anchor = sub.logicalPose().transformPosition(new Vector3d(SableTrainDriver.pivotLocalAnchor(sub, train.car(), pivot)), new Vector3d());
            SableTrain.Bogey loose = null;
            Vector3d arm = null;

            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                if (train.isBogeyOffTrack(bogey.localPos()) && train.bogeyRerailReady(bogey.localPos(), 60)) {
                    Vector3d world = SubLevelData.worldOfOffset(sub, bogey.localPos()).sub(anchor);
                    world.y = 0.0;
                    if (arm == null || world.lengthSquared() > arm.lengthSquared()) {
                        loose = bogey;
                        arm = world;
                    }
                }
            }

            if (loose == null || arm.length() < 1.0) {
                return stopSwing(train);
            } else if (SableTrainSpawner.acceptableSeatFor(level, sub, loose, train.car().carriage().forward(), null, 1.25) != null) {
                return stopSwing(train);
            } else {
                RailCarriage target = railAlongPivot(pivot.rail(), anchor, arm);
                Vector3d seat = target == null ? null : target.center();
                if (seat == null) {
                    return stopSwing(train);
                } else {
                    Vector3d toSeat = new Vector3d(seat).sub(anchor);
                    toSeat.y = 0.0;
                    if (toSeat.lengthSquared() < 1.0E-6) {
                        return stopSwing(train);
                    } else {
                        Quaterniond full = new Quaterniond().rotateTo(new Vector3d(arm).normalize(), toSeat.normalize());
                        double angle = Math.toDegrees(full.angle());
                        if (angle > 35.0 && !SWINGING.containsKey(train.id())) {
                            return false;
                        } else {
                            boolean plateAligned = GhostWheelBodies.alignToRail(train, loose.localPos(), target.chordFrameQuaternion());
                            if (angle <= 0.75) {
                                SWINGING.remove(train.id());
                                return !plateAligned;
                            } else {
                                int swung = SWINGING.getOrDefault(train.id(), 0) + 1;
                                if (swung > 60) {
                                    GAVE_UP.put(train.id(), level.getGameTime());
                                    GhostWheelBodies.clearAlign(train, loose.localPos());
                                    return stopSwing(train);
                                } else {
                                    SWINGING.put(train.id(), swung);
                                    double step = Math.min(4.0, Math.max(0.5, angle * 0.3));
                                    Quaterniond turn = new Quaterniond().slerp(full, Math.min(1.0, step / angle)).normalize();
                                    Vector3d from = new Vector3d(sub.logicalPose().position());
                                    Quaterniond fromQ = new Quaterniond(sub.logicalPose().orientation());
                                    Vector3d to = turn.transform(new Vector3d(from).sub(anchor)).add(anchor);
                                    Quaterniond toQ = new Quaterniond(turn).mul(fromQ).normalize();
                                    if (isFinite(to) && isFinite(toQ)) {
                                        SableTrainSpawner.writePose(level, sub, to, toQ);
                                        GhostWheelBodies.carryWheels(train, from, fromQ, to, toQ);
                                        return true;
                                    } else {
                                        return stopSwing(train);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            return stopSwing(train);
        }
    }

    private static boolean stopSwing(SableTrain train) {
        SWINGING.remove(train.id());
        return false;
    }

    @Nullable
    private static RailCarriage railAlongPivot(RailCarriage pivotRail, Vector3d anchor, Vector3d arm) {
        RailCarriage walker = RailCarriage.seededAt(pivotRail.seedPoint(), pivotRail.graph(), pivotRail.upNormal(), 1.0, 0.0);
        if (walker == null) {
            return null;
        }

        Vec3 fwd = walker.forward();
        double sign = fwd.x * arm.x + fwd.z * arm.z >= 0.0 ? 1.0 : -1.0;
        walker.setSpeed(sign * arm.length());
        walker.tick();
        walker.setSpeed(0.0);
        return walker.reachedEnd() ? null : walker;
    }

    @Nullable
    private static Vector3d seatGap(ServerSubLevel sub, SableTrain train, SableTrain.Bogey pivot) {
        Vector3d railPoint = pivot.rail().center();
        if (railPoint == null) {
            return null;
        }

        Vector3d anchor = sub.logicalPose().transformPosition(new Vector3d(SableTrainDriver.pivotLocalAnchor(sub, train.car(), pivot)), new Vector3d());
        Vector3d gap = new Vector3d(railPoint).sub(anchor);
        Vec3 tangent = pivot.rail().forward();
        double along = gap.x * tangent.x + gap.y * tangent.y + gap.z * tangent.z;
        gap.sub(tangent.x * along, tangent.y * along, tangent.z * along);
        return isFinite(gap) ? gap : null;
    }

    private static String easeProbe(ServerSubLevel sub, SableTrain train, SableTrain.Bogey pivot) {
        Vector3d anchor = SubLevelData.worldOf(sub, new Vector3d(SableTrainDriver.pivotLocalAnchor(sub, train.car(), pivot)));
        Vector3d bogey = SubLevelData.worldOfOffset(sub, pivot.localPos());
        Vector3d rail = pivot.rail().center();
        Vec3 fwd = pivot.rail().forward();
        Vector3d gap = rail == null ? new Vector3d() : new Vector3d(rail).sub(anchor);
        return String.format(
            " | body %.4f %.4f %.4f anchor %.4f %.4f %.4f bogeyWorld %.4f %.4f %.4f rail %.4f %.4f %.4f tangent %.3f %.3f %.3f gapAlongTangent %.4f reachedEnd %s",
            sub.logicalPose().position().x(),
            sub.logicalPose().position().y(),
            sub.logicalPose().position().z(),
            anchor.x,
            anchor.y,
            anchor.z,
            bogey.x,
            bogey.y,
            bogey.z,
            rail == null ? 0.0 : rail.x,
            rail == null ? 0.0 : rail.y,
            rail == null ? 0.0 : rail.z,
            fwd.x,
            fwd.y,
            fwd.z,
            gap.x * fwd.x + gap.y * fwd.y + gap.z * fwd.z,
            pivot.rail().reachedEnd()
        );
    }

    private static void easeBody(ServerLevel level, SableTrain train, ServerSubLevel sub, Vector3d gap) {
        double length = gap.length();
        double step = Math.min(1.0, Math.max(0.05 / Math.max(length, 1.0E-9), 0.35));
        Vector3d move = new Vector3d(gap).mul(step);
        if (move.length() > 0.4) {
            move.mul(0.4 / move.length());
        }

        Vector3d from = new Vector3d(sub.logicalPose().position());
        Quaterniond q = new Quaterniond(sub.logicalPose().orientation());
        Vector3d next = new Vector3d(from).add(move);
        if (isFinite(next)) {
            SableTrainSpawner.writePose(level, sub, next, q);
            GhostWheelBodies.carryWheels(train, from, q, next, q);
        }
    }

    private static RerailApproach.Error worstError(
        ServerLevel level, ServerSubLevel sub, SableTrain train, List<SableTrainSpawner.BogeySeat> seats, SableTrainSpawner.SeatPose landing
    ) {
        Vec3 carFacing = SableTrainSpawner.currentFacing(sub, train.car());
        RerailApproach.Error worst = new RerailApproach.Error(0.0, 0.0, 0.0);

        for (SableTrainSpawner.BogeySeat seat : seats) {
            worst = worst.worse(errorOf(level, sub, seat, landing, carFacing));
        }

        return worst;
    }

    private static RerailApproach.Error errorOf(
        ServerLevel level, ServerSubLevel sub, SableTrainSpawner.BogeySeat seat, SableTrainSpawner.SeatPose landing, Vec3 carFacing
    ) {
        Vector3d target = seatedBogeyWorld(sub, landing, seat.bogey());
        Vector3d world = seat.world();
        double vertical = Math.abs(world.y + 0.1875 - target.y);
        double lateral = Math.hypot(world.x - target.x, world.z - target.z);
        double yaw = SubLevelData.axisDiffDeg(
            SubLevelData.of(SableTrainSpawner.bogeyFacing(level, sub, seat.bogey(), carFacing)), SubLevelData.of(seat.rail().forward())
        );
        return new RerailApproach.Error(lateral, vertical, yaw);
    }

    private static boolean canAcquire(
        ServerLevel level,
        ServerSubLevel sub,
        SableTrain train,
        List<SableTrainSpawner.BogeySeat> seats,
        SableTrainSpawner.SeatPose landing,
        RerailApproach.Error worst
    ) {
        return worst.lateral() <= 0.75 && worst.vertical() <= Math.max(0.5, 0.5) && worst.yawDegrees() <= 60.0 && !anyViewBlocked(level, sub, seats, landing);
    }

    private static boolean anyViewBlocked(ServerLevel level, ServerSubLevel sub, List<SableTrainSpawner.BogeySeat> seats, SableTrainSpawner.SeatPose landing) {
        for (SableTrainSpawner.BogeySeat seat : seats) {
            Vector3d target = seatedBogeyWorld(sub, landing, seat.bogey());
            Vector3d world = seat.world();
            if (viewBlocked(level, new Vec3(world.x, world.y, world.z), new Vec3(target.x, target.y, target.z))) {
                return true;
            }
        }

        return false;
    }

    private static Vector3d seatedBogeyWorld(ServerSubLevel sub, SableTrainSpawner.SeatPose seat, SableTrain.Bogey bogey) {
        BlockPos plotPos = sub.getPlot().getCenterBlock().offset(bogey.localPos());
        Vector3d local = new Vector3d(plotPos.getX() + 0.5, plotPos.getY() + 0.5, plotPos.getZ() + 0.5);
        Pose3d pose = sub.logicalPose();
        Quaterniond keepOrient = new Quaterniond(pose.orientation());
        pose.orientation().set(seat.orient());
        Vector3d rotated = pose.transformPosition(local, new Vector3d());
        pose.orientation().set(keepOrient);
        return rotated.sub(pose.position()).add(seat.origin());
    }

    private static boolean steerSeatedPlates(SableTrain train, List<SableTrain.Bogey> updated, List<BlockPos> stillOff) {
        boolean aligned = true;

        for (SableTrain.Bogey bogey : updated) {
            if (!stillOff.contains(bogey.localPos())
                && bogey.rail() != null
                && !GhostWheelBodies.alignToRail(train, bogey.localPos(), bogey.rail().chordFrameQuaternion())) {
                aligned = false;
            }
        }

        return aligned;
    }

    private static boolean steerPlates(SableTrain train, List<SableTrainSpawner.BogeySeat> seats) {
        boolean aligned = true;

        for (SableTrainSpawner.BogeySeat seat : seats) {
            if (!GhostWheelBodies.alignToRail(train, seat.bogey().localPos(), seat.rail().chordFrameQuaternion())) {
                aligned = false;
            }
        }

        return aligned;
    }

    private static boolean viewBlocked(ServerLevel level, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 1.0) {
            return false;
        }

        BlockHitResult hit = level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == Type.BLOCK && hit.getLocation().distanceToSqr(to) > 2.25;
    }

    private static void stepBody(ServerLevel level, SableTrain train, ServerSubLevel sub, SableTrainSpawner.SeatPose seat) {
        Vector3d pos = new Vector3d(sub.logicalPose().position());
        Quaterniond q = new Quaterniond(sub.logicalPose().orientation());
        double gap = pos.distance(seat.origin());
        Vector3d next = gap <= 1.0E-9 ? new Vector3d(seat.origin()) : pos.lerp(seat.origin(), Math.min(1.0, Math.max(0.05 / gap, 0.35)), new Vector3d());
        Quaterniond nextQ = q.slerp(new Quaterniond(seat.orient()), 0.3, new Quaterniond()).normalize();
        if (gap > 0.4) {
            Vector3d capped = new Vector3d(next).sub(pos);
            double moved = capped.length();
            if (moved > 0.4) {
                next = new Vector3d(pos).add(capped.mul(0.4 / moved));
            }
        }

        if (isFinite(next) && isFinite(nextQ)) {
            SableTrainSpawner.writePose(level, sub, next, nextQ);
            GhostWheelBodies.carryWheels(train, pos, q, next, nextQ);
        }
    }

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static boolean isFinite(Quaterniond q) {
        return Double.isFinite(q.x) && Double.isFinite(q.y) && Double.isFinite(q.z) && Double.isFinite(q.w);
    }

    private enum BogeyRerail {
        NONE,
        EASING,
        SEATED;
    }

    private record Error(double lateral, double vertical, double yawDegrees) {
        RerailApproach.Error worse(RerailApproach.Error other) {
            return new RerailApproach.Error(
                Math.max(this.lateral, other.lateral), Math.max(this.vertical, other.vertical), Math.max(this.yawDegrees, other.yawDegrees)
            );
        }

        boolean within(double maxLateral, double maxVertical, double maxYaw) {
            return this.lateral <= maxLateral && this.vertical <= maxVertical && this.yawDegrees <= maxYaw;
        }
    }
}
