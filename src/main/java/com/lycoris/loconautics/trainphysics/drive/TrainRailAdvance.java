package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.SableRailHop;
import com.lycoris.loconautics.trainphysics.rail.TurnLog;
import com.lycoris.loconautics.trainphysics.rail.TurnMemory;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class TrainRailAdvance {
    private static final Map<UUID, TurnLog> TURN_LOGS = new HashMap<>();
    private static final Map<UUID, Integer> TURN_LEAD = new HashMap<>();
    private static final Map<UUID, TrainRailAdvance.Stress> MAX_STRESS = new HashMap<>();
    private static final int HOP_REPORT_TTL = 200;
    private static final int HOP_CAN = 65331;
    private static final int HOP_CANNOT = 16724016;
    private static final int HOP_HEADING = 52479;

    public static double peekStress(UUID subLevelId) {
        TrainRailAdvance.Stress stress = MAX_STRESS.get(subLevelId);
        return stress == null ? 0.0 : stress.amount();
    }

    @Nullable
    static TrainRailAdvance.Stress takeMaxStress(UUID subLevelId) {
        return MAX_STRESS.remove(subLevelId);
    }

    static TurnLog turnLogFor(SableTrain.Car car, int leadIdx) {
        UUID id = car.subLevelId();
        TurnLog log = TURN_LOGS.computeIfAbsent(id, key -> new TurnLog());
        Integer last = TURN_LEAD.put(id, leadIdx);
        if (last != null && last != leadIdx) {
            log.clear();
        }

        return log;
    }

    static void retainTurnLogs(Set<UUID> liveSubLevels) {
        TURN_LOGS.keySet().retainAll(liveSubLevels);
        TURN_LEAD.keySet().retainAll(liveSubLevels);
        MAX_STRESS.keySet().retainAll(liveSubLevels);
        ConsistTurns.retain(liveSubLevels);
    }

    public static void advanceRail(SableTrain.Car car, double ds) {
        advanceRail(car, ds, null);
    }

    @Nullable
    public static Vec3 loneFacing(ServerSubLevel sub, SableTrain.Car car) {
        return car.chain().size() < 2 ? SableTrainSpawner.currentFacing(sub, car) : null;
    }

    public static void advanceRail(SableTrain.Car car, double ds, @Nullable Vec3 loneFacing) {
        List<SableTrain.Bogey> bogeys = car.chain();
        if (bogeys.isEmpty()) {
            double loneSign = travelSign(car.carriage(), car.carriage().forward());
            car.carriage().unstick();
            car.carriage().setTravelIntent(ds * loneSign);
            car.carriage().setSpeed(ds * loneSign);
            car.carriage().tick();
        } else {
            int n = bogeys.size();
            Integer heldLead = TURN_LEAD.get(car.subLevelId());
            boolean forward = Math.abs(ds) < 1.0E-9 && heldLead != null ? heldLead != 0 : ds >= 0.0;
            int leadIdx = forward ? n - 1 : 0;
            int toTail = forward ? -1 : 1;
            SableTrain.Bogey leadBogey = bogeys.get(leadIdx);
            TurnLog log = turnLogFor(car, leadIdx);

            for (int i = 0; i < n; i++) {
                bogeys.get(i).rail().setTurnLog(log, i == leadIdx);
            }

            SableTrain owner = SableTrainRegistry.bySubLevel(car.subLevelId());
            if (owner != null && owner.car() == car) {
                TurnMemory memory = ConsistTurns.memoryFor(owner);

                for (SableTrain.Bogey bogey : bogeys) {
                    bogey.rail().setTurnMemory(memory, new TurnMemory.BogeyId(car.subLevelId(), bogey.localPos()));
                }

                car.carriage().setTurnMemory(memory, new TurnMemory.BogeyId(car.subLevelId(), null));
            }

            long slowest = Long.MAX_VALUE;

            for (int i = 0; i < n; i++) {
                if (i != leadIdx) {
                    slowest = Math.min(slowest, bogeys.get(i).rail().turnCursor());
                }
            }

            if (slowest != Long.MAX_VALUE) {
                log.pruneBelow(slowest);
            }

            for (int i = leadIdx + toTail; i >= 0 && i < n; i += toTail) {
                bogeys.get(i).rail().followPoint(bogeys.get(i - toTail).rail().trailing());
            }

            car.carriage().followPoint(leadBogey.rail().leading());
            Vec3 bodyFwd = bogeyChordForward(bogeys);
            if (bodyFwd == null) {
                bodyFwd = loneFacing != null ? loneFacing : leadBogey.rail().forward();
            }

            double sign = travelSign(leadBogey.rail(), bodyFwd);
            leadBogey.rail().unstick();
            leadBogey.rail().setTravelIntent(ds * sign);
            leadBogey.rail().setSpeed(ds * sign);
            leadBogey.rail().tick();
            if (n == 1) {
                car.carriage().setPortal(leadBogey.rail().portal());
                car.carriage().recenterOn(leadBogey.rail().leading(), leadBogey.rail().graph());
            } else {
                double spanSign = travelSign(car.carriage(), bodyFwd);
                car.carriage().unstick();
                car.carriage().setTravelIntent(ds * spanSign);
                car.carriage().setSpeed(ds * spanSign);
                car.carriage().tick();
            }

            boolean docked = DockGroups.grouped(car);

            for (int i = leadIdx + toTail; i >= 0 && i < n; i += toTail) {
                SableTrain.Bogey ahead = docked ? bogeys.get(leadIdx) : bogeys.get(i - toTail);
                SableTrain.Bogey bogey = bogeys.get(i);
                bogey.rail().unstick();
                double bogeySign = travelSign(bogey.rail(), bodyFwd);
                double move = ds * bogeySign;
                Vector3d cAhead = ahead.rail().center();
                Vector3d cThis = bogey.rail().center();
                if (cAhead != null && cThis != null) {
                    double chord = cAhead.distance(cThis);
                    double rigid = DockGroups.rigidGap(bogey, ahead);
                    double err = chord - rigid;
                    MAX_STRESS.merge(
                        car.subLevelId(),
                        new TrainRailAdvance.Stress(Math.abs(err), bogey.localPos()),
                        (old, now) -> (TrainRailAdvance.Stress)(now.amount() > old.amount() ? now : old)
                    );
                    Vec3 f = travelDir(bogey.rail());
                    double toward = (cAhead.x - cThis.x) * f.x + (cAhead.z - cThis.z) * f.z;
                    double cap = 0.5;
                    double corr = Math.max(-cap, Math.min(cap, err));
                    move += toward >= 0.0 ? corr : -corr;
                }

                bogey.rail().setTravelIntent(ds * bogeySign);
                bogey.rail().setSpeed(move);
                bogey.rail().tick();
            }

            for (SableTrain.Bogey other : car.bogeys()) {
                if (other.rail().isIndependent()) {
                    double otherSign = travelSign(other.rail(), bodyFwd);
                    other.rail().unstick();
                    other.rail().setTravelIntent(ds * otherSign);
                    other.rail().setSpeed(ds * otherSign);
                    other.rail().tick();
                }
            }
        }
    }

    static void animateBogeyWheels(SableTrain.Car car, ServerSubLevel sub, double ds) {
        if (ds != 0.0) {
            float distanceMoved = (float)Math.abs(ds);
            ServerLevel subLevel = sub.getLevel();

            for (SableTrain.Bogey bogey : car.bogeys()) {
                BlockPos worldPos = DockGroups.ownerOf(bogey, sub).getPlot().getCenterBlock().offset(bogey.localPos());
                if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                    be.animate(distanceMoved);
                }
            }
        }
    }

    public static void seedTravelSign(RailCarriage rail, Vec3 bodyFwd) {
        travelSign(rail, bodyFwd);
    }

    static double travelSign(RailCarriage rail, Vec3 bodyFwd) {
        double dot = travelDot(rail, bodyFwd);
        double sign = rail.lastTravelSign();
        if (sign >= 0.0) {
            if (dot < -0.09) {
                sign = -1.0;
            }
        } else if (dot > 0.09) {
            sign = 1.0;
        }

        rail.setLastTravelSign(sign);
        return sign;
    }

    static double travelDot(RailCarriage rail, Vec3 bodyFwd) {
        Vec3 f = travelDir(rail);
        return f.x * bodyFwd.x + f.z * bodyFwd.z;
    }

    @Nullable
    static Vec3 bodyForward(RailCarriage rail) {
        Vec3 dir = travelDir(rail);
        return dir == null ? null : dir.scale(rail.lastTravelSign());
    }

    static Vec3 travelDir(RailCarriage rail) {
        Vec3 tangent = rail.edgeTangent();
        return tangent != null ? tangent : rail.forward();
    }

    public static Vec3 bogeyChordForward(List<SableTrain.Bogey> bogeys) {
        if (bogeys.size() < 2) {
            return null;
        }

        Vector3d rear = bogeys.get(0).rail().center();
        Vector3d front = bogeys.get(bogeys.size() - 1).rail().center();
        if (rear != null && front != null) {
            Vector3d chord = new Vector3d(front).sub(rear);
            if (chord.lengthSquared() < 1.0E-9) {
                return null;
            }

            chord.normalize();
            return new Vec3(chord.x, chord.y, chord.z);
        } else {
            return null;
        }
    }

    static Vector3d[] bogeyReference(SableTrain.Car car) {
        return bogeyReference(null, car);
    }

    public static Vector3d[] bogeyReference(ServerSubLevel sub, SableTrain.Car car) {
        List<SableTrain.Bogey> bogeys = car.chain();
        if (bogeys.isEmpty()) {
            return null;
        }

        if (bogeys.size() >= 2) {
            Vector3d frontC = bogeys.get(bogeys.size() - 1).rail().center();
            Vector3d rearC = bogeys.get(0).rail().center();
            if (frontC != null && rearC != null) {
                Vector3d chord = new Vector3d(frontC).sub(rearC);
                if (chord.lengthSquared() >= 1.0E-9) {
                    return new Vector3d[]{new Vector3d(frontC).add(rearC).mul(0.5), chord.normalize()};
                }
            }
        }

        Vec3 frontP = bogeys.get(bogeys.size() - 1).rail().leadingPos();
        Vec3 rearP = bogeys.get(0).rail().trailingPos();
        if (frontP != null && rearP != null) {
            Vector3d front = new Vector3d(frontP.x, frontP.y, frontP.z);
            Vector3d rear = new Vector3d(rearP.x, rearP.y, rearP.z);
            Vector3d chord = new Vector3d(front).sub(rear);
            if (chord.lengthSquared() >= 1.0E-9) {
                Vector3d mid = new Vector3d(front).add(rear).mul(0.5);
                return new Vector3d[]{mid, faceLikeBody(sub, car, chord.normalize())};
            }
        }

        RailCarriage rail = bogeys.get(0).rail();
        Vector3d c = rail.center();
        Vec3 t = rail.edgeTangent();
        return c != null && t != null ? new Vector3d[]{c, faceLikeBody(sub, car, new Vector3d(t.x, t.y, t.z))} : null;
    }

    private static Vector3d faceLikeBody(ServerSubLevel sub, SableTrain.Car car, Vector3d axis) {
        if (sub == null) {
            return axis;
        }

        Vec3 facing = SableTrainSpawner.currentFacing(sub, car);
        return axis.x * facing.x + axis.z * facing.z < 0.0 ? axis.negate() : axis;
    }

    public static Vector3d bogeyAnchorLocal(ServerSubLevel sub, SableTrain.Car car) {
        Vector3d docked = DockGroups.anchorLocal(sub, car);
        if (docked != null) {
            return docked;
        }

        List<SableTrain.Bogey> bogeys = car.chain();
        if (bogeys.isEmpty()) {
            return null;
        }

        BlockPos plot = sub.getPlot().getCenterBlock();
        BlockPos first = plot.offset(bogeys.get(0).localPos());
        BlockPos last = plot.offset(bogeys.get(bogeys.size() - 1).localPos());
        return new Vector3d((first.getX() + last.getX()) * 0.5 + 0.5, (first.getY() + last.getY()) * 0.5 + 0.5, (first.getZ() + last.getZ()) * 0.5 + 0.5);
    }

    static boolean railsValid(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car.bogeys().isEmpty()) {
            return car.carriage().edgesValid();
        }

        if (car.carriage() != null) {
            car.carriage().edgesValid();
        }

        boolean valid = true;

        for (SableTrain.Bogey bogey : car.bogeys()) {
            valid &= bogey.rail().edgesValid();
        }

        return valid;
    }

    public static boolean anyPointFar(SableTrain.Car car) {
        if (car.carriage() != null && car.carriage().anyPointFar()) {
            return true;
        }

        for (SableTrain.Bogey b : car.bogeys()) {
            if (b.rail() != null && b.rail().anyPointFar()) {
                return true;
            }
        }

        return false;
    }

    static boolean anyEndedFar(SableTrain.Car car, SableTrain train) {
        for (SableTrain.Bogey b : car.chain()) {
            if (!train.isBogeyOffTrack(b.localPos()) && b.rail().reachedEnd() && (b.rail().isFar(b.rail().leading()) || b.rail().isFar(b.rail().trailing()))) {
                return true;
            }
        }

        return car.bogeys().isEmpty() && car.carriage().reachedEnd() && car.carriage().anyPointFar();
    }

    public static boolean anyPendingPortal(SableTrain.Car car) {
        if (car.carriage().pendingPortal() != null) {
            return true;
        }

        for (SableTrain.Bogey b : car.bogeys()) {
            if (b.rail().pendingPortal() != null) {
                return true;
            }
        }

        return false;
    }

    public static boolean anyReachedEnd(SableTrain.Car car) {
        if (car.bogeys().isEmpty()) {
            return car.carriage().reachedEnd();
        }

        for (SableTrain.Bogey b : car.chain()) {
            if (b.rail().reachedEnd()) {
                return true;
            }
        }

        return false;
    }

    static boolean anyRailedReachedEnd(SableTrain.Car car, SableTrain train) {
        for (SableTrain.Bogey b : car.chain()) {
            if (!train.isBogeyOffTrack(b.localPos()) && b.rail().reachedEnd()) {
                return true;
            }
        }

        return false;
    }

    static boolean weldEndedFollowers(ServerSubLevel bodySub, SableTrain.Car car, SableTrain train) {
        Vec3 up = SableTrainDriver.bodyUpWorld(bodySub);
        if (!car.bogeys().isEmpty()) {
            boolean welded = false;

            for (int i = 0; i < car.bogeys().size(); i++) {
                SableTrain.Bogey b = car.bogeys().get(i);
                if (!train.isBogeyOffTrack(b.localPos())
                    && b.rail().reachedEnd()
                    && weldFollower(bodySub, b.rail(), "b" + i, wheelProbe(bodySub, b), outwardOf(b.rail(), bodySub, car, train), up)) {
                    welded = true;
                }
            }

            return welded;
        } else {
            Vector3d c = car.carriage().center();
            return c != null
                && car.carriage().reachedEnd()
                && weldFollower(bodySub, car.carriage(), "span", new Vec3(c.x, c.y, c.z), outwardOf(car.carriage(), bodySub, car, train), up);
        }
    }

    static Vec3 outwardOf(RailCarriage rail, ServerSubLevel bodySub, SableTrain.Car car, SableTrain train) {
        Vec3 outward = rail.railheadOutward();
        if (outward != null) {
            return outward;
        }

        Vector3d t = bodyForwardWorld(bodySub, car);
        Vec3 tan = new Vec3(t.x, t.y, t.z);
        return train.speed() < 0.0 ? tan.scale(-1.0) : tan;
    }

    public static List<String> hopReport(SableTrain train) {
        List<String> out = new ArrayList<>();
        SableTrain.Car car = train.car();
        ServerLevel level = train.level();
        ServerSubLevel sub = level == null ? null : SubLevelData.resolve(level, car.subLevelId());
        if (sub == null) {
            out.add("the car's sub level is not loaded, nothing to probe");
            return out;
        }

        Vec3 up = SableTrainDriver.bodyUpWorld(sub);
        if (car.bogeys().isEmpty()) {
            Vector3d c = car.carriage().center();
            reportFollower(out, level, sub, car, train, "span", car.carriage(), c == null ? null : new Vec3(c.x, c.y, c.z), up);
            return out;
        }

        for (int i = 0; i < car.bogeys().size(); i++) {
            SableTrain.Bogey b = car.bogeys().get(i);
            reportFollower(out, level, sub, car, train, "b" + i, b.rail(), wheelProbe(sub, b), up);
        }

        return out;
    }

    private static String xyz(Vec3 v) {
        return v == null ? "null" : String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static void reportFollower(
        List<String> out, ServerLevel level, ServerSubLevel sub, SableTrain.Car car, SableTrain train, String name, RailCarriage rail, Vec3 probe, Vec3 up
    ) {
        if (probe == null) {
            out.add(name + " has no rail position");
        } else {
            Vec3 outward = outwardOf(rail, sub, car, train);
            SableRailHop.Probe found = rail.railAheadProbe(level, sub, probe, outward, up, true);
            DebugLines.arrow(level, "hopwhere_dir_" + name, probe, probe.add(outward.scale(2.0)), 52479, 0.125F, 200);
            if (found != null && found.hit() != null) {
                DebugLines.column(level, "hopwhere_" + name, found.hit().railPoint(), 65331, 200);
                out.add(
                    String.format(
                        "%s CAN hop %.2f ahead onto %s at %s",
                        name,
                        found.step(),
                        found.hit().sub() == null ? "the world" : LogFormat.shortId(found.hit().sub()),
                        xyz(found.hit().railPoint())
                    )
                );
            } else {
                DebugLines.column(level, "hopwhere_" + name, probe, 16724016, 200);
                out.add(String.format("%s CANNOT hop from %s, %s", name, xyz(probe), found == null ? "the probe had no heading" : found.detail()));
            }
        }
    }

    private static boolean weldFollower(ServerSubLevel bodySub, RailCarriage rail, String name, Vec3 probe, Vec3 sweep, Vec3 up) {
        ServerLevel level = bodySub.getLevel();
        SableRailHop.Result target = rail.railAheadTarget(level, bodySub, probe, sweep, up, true);
        return target == null ? false : rail.weldAheadTo(target);
    }

    static boolean ownGraphRailAhead(SableTrain.Car car, ServerSubLevel sub, SableTrain train) {
        ServerLevel level = sub.getLevel();
        if (!car.bogeys().isEmpty()) {
            for (SableTrain.Bogey b : car.bogeys()) {
                if (!train.isBogeyOffTrack(b.localPos()) && b.rail().reachedEnd()) {
                    Vec3 probe = wheelProbe(sub, b);
                    Vec3 outward = outwardOf(b.rail(), sub, car, train);
                    if (SableRailHop.ownGraphTrackAhead(level, b.rail().graph(), b.rail().activeSub(), probe, outward)) {
                        return true;
                    }
                }
            }

            return false;
        } else {
            Vector3d c = car.carriage().center();
            return c != null
                && SableRailHop.ownGraphTrackAhead(
                    level, car.carriage().graph(), car.carriage().activeSub(), new Vec3(c.x, c.y, c.z), outwardOf(car.carriage(), sub, car, train)
                );
        }
    }

    static Vec3 wheelProbe(ServerSubLevel sub, SableTrain.Bogey bogey) {
        Vec3 block = bogeyWorldPos(sub, bogey);
        Vec3 rail = bogey.rail().leadingRailPos();
        double drop = 1.5;
        if (rail != null) {
            double measured = block.y - rail.y;
            if (measured > 0.0 && measured <= 3.0) {
                drop = measured;
            }
        }

        return block.subtract(SableTrainDriver.bodyUpWorld(sub).scale(drop));
    }

    static Vec3 bogeyWorldPos(ServerSubLevel sub, SableTrain.Bogey bogey) {
        BlockPos w = sub.getPlot().getCenterBlock().offset(bogey.localPos());
        Vector3d local = new Vector3d(w.getX() + 0.5, w.getY() + 0.5, w.getZ() + 0.5);
        Vector3d world = sub.logicalPose().transformPosition(local, new Vector3d());
        return new Vec3(world.x, world.y, world.z);
    }

    static Vector3d bodyForwardWorld(ServerSubLevel sub, SableTrain.Car car) {
        Vec3 f = SableTrainSpawner.currentFacing(sub, car);
        return new Vector3d(f.x, f.y, f.z);
    }

    public static ServerSubLevel leadingActiveSub(SableTrain.Car car) {
        List<SableTrain.Bogey> bogeys = car.chain();
        return !bogeys.isEmpty() ? bogeys.get(bogeys.size() - 1).rail().activeSub() : car.carriage().activeSub();
    }

    public static Vector3d railTangentOf(SableTrain.Bogey bogey) {
        Vec3 f = bogey.rail().forward();
        Vector3d t = new Vector3d(f.x, f.y, f.z);
        return t.lengthSquared() < 1.0E-9 ? null : t.normalize();
    }

    private TrainRailAdvance() {
    }

    record Stress(double amount, BlockPos bogey) {
    }
}
