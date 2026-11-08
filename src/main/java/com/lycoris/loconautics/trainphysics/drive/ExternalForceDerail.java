package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.math.VecMath;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffDragSessionAccessor;
import com.lycoris.loconautics.mixin.sable.physicsstaff.PhysicsStaffServerHandlerAccessor;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.glue.RailGlue;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.force.ForceGroup;
import dev.ryanhcode.sable.api.physics.force.QueuedForceGroup;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

public final class ExternalForceDerail {
    private static final int LIFT_TICKS = 5;
    private static final double LIFT_RATIO = 100.0;
    private static final double NOISE = 1.0E-6;
    private static final long STALE_TICKS = 1L;
    private static final int REGRIP_TICKS = 40;
    private static final long TRACE_TICKS = 10L;
    private static final double PLUNGER_RATIO = 1.0;
    private static final Map<UUID, ExternalForceDerail.Pull> PLUNGER_PULL = new HashMap<>();
    private static final Map<UUID, ExternalForceDerail.Push> PLUNGER_PEAK = new HashMap<>();
    private static final Map<UUID, ExternalForceDerail.Push> PEAK = new HashMap<>();
    private static final Map<UUID, Integer> LIFTED = new HashMap<>();
    private static final Map<UUID, Long> REGRIP = new HashMap<>();
    private static final Map<UUID, Long> TRACED = new HashMap<>();
    private static final Map<UUID, Long> RELEASED = new HashMap<>();
    private static final int RELEASE_TICKS = 10;
    private static final double RELEASE_SPEED = 4.0;
    private static final double RELEASE_SPIN = 2.0;

    private ExternalForceDerail() {
    }

    public static void notePlunger(ServerSubLevel sub, Vector3d localImpulse, double dt) {
        MassData mass = sub.getMassTracker();
        if (!(dt <= 0.0) && mass != null && !mass.isInvalid() && !(mass.getMass() <= 0.0)) {
            Vector3d accel = sub.logicalPose().transformNormal(localImpulse, new Vector3d()).mul(1.0 / (mass.getMass() * dt));
            PLUNGER_PULL.merge(
                sub.getUniqueId(),
                new ExternalForceDerail.Pull(sub.getLevel().getGameTime(), accel),
                (old, fresh) -> old.gameTime() == fresh.gameTime()
                    ? new ExternalForceDerail.Pull(fresh.gameTime(), old.accel().add(fresh.accel(), new Vector3d()))
                    : fresh
            );
        }
    }

    private static void samplePlunger(ServerSubLevel sub, SableTrain train, Vector3d tangent, long now) {
        ExternalForceDerail.Pull pull = PLUNGER_PULL.get(sub.getUniqueId());
        if (pull != null && now - pull.gameTime() <= 1L) {
            double along = pull.accel().dot(tangent);
            double across = new Vector3d(pull.accel()).fma(-along, tangent).length();
            if (throttle(sub, now)) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[plungerpull] car={} accel={} b/s2 along={} across={} limit={} derailed={}",
                        new Object[]{
                            LogFormat.shortId(train.id()),
                            SableTrainDriver.f(pull.accel().length()),
                            SableTrainDriver.f(along),
                            SableTrainDriver.f(across),
                            SableTrainDriver.f(DimensionPhysicsData.getGravity(sub.getLevel()).length() * 1.0),
                            train.isDerailed()
                        }
                    );
            }

            ExternalForceDerail.Push seen = PLUNGER_PEAK.get(sub.getUniqueId());
            PLUNGER_PEAK.put(sub.getUniqueId(), seen == null ? new ExternalForceDerail.Push(now, across) : seen.worse(now, across));
        }
    }

    static void sample(ServerSubLevel sub, SableTrain.Car car, SableTrain train, double dt) {
        if ((Boolean)LoconauticsConfig.DERAIL_ON_EXTERNAL_FORCE.get() && !(dt <= 0.0) && !TrainPropulsion.isMonorail(train)) {
            long now = sub.getLevel().getGameTime();
            boolean dragged = staffDrags(sub);
            if (!CarriageRoll.isRolling(sub.getUniqueId()) || dragged && !(liftRatio(sub) <= 0.0)) {
                if (SubLevelMergeWatcher.merging(sub.getLevel(), sub.getUniqueId())) {
                    PEAK.remove(sub.getUniqueId());
                    traceSkip(sub, train, "merging glue pull", now);
                } else if (staffHolds(sub) && !dragged) {
                    forget(train.id());
                    PEAK.remove(sub.getUniqueId());
                    traceSkip(sub, train, "held by the physics staff", now);
                } else {
                    MassData mass = sub.getMassTracker();
                    if (mass != null && !mass.isInvalid() && !(mass.getMass() <= 0.0)) {
                        Vector3d[] ref = TrainRailAdvance.bogeyReference(car);
                        Vector3d tangent = ref != null ? ref[1] : SableTrainDriver.railTangent(car);
                        if (tangent == null) {
                            traceSkip(sub, train, "no rail tangent", now);
                        } else {
                            Vector3d up = VecMath.perpendicularUp(tangent);
                            Vector3d where = ref != null ? ref[0] : SubLevelData.position(sub);
                            samplePlunger(sub, train, tangent, now);
                            double lift = 0.0;
                            Vector3d push = queuedPush(sub);
                            if (push != null) {
                                Vector3d accel = new Vector3d(push).mul(1.0 / (mass.getMass() * dt));
                                lift = accel.dot(up);
                                ForceTrace.impulse(sub, ForceSource.EXTERNAL, push, where, "queued force groups, lift " + SableTrainDriver.f(lift) + " b/s2");
                            }

                            Vector3d held = train.hasOffTrackBogey() ? null : jointAccel(sub, tangent, up, mass.getMass(), dt);
                            double pull = held == null ? 0.0 : held.dot(up);
                            lift = Math.max(lift, pull);
                            trace(sub, train, up, mass.getMass(), pull, lift, now, dragged, push != null);
                            if (lift != 0.0) {
                                double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
                                if (lift > gravity * liftRatio(sub)) {
                                    REGRIP.put(train.id(), now + 40L);
                                }

                                ExternalForceDerail.Push seen = PEAK.get(sub.getUniqueId());
                                PEAK.put(sub.getUniqueId(), seen == null ? new ExternalForceDerail.Push(now, lift) : seen.worse(now, lift));
                            }
                        }
                    } else {
                        traceSkip(sub, train, "no mass", now);
                    }
                }
            } else {
                PEAK.remove(sub.getUniqueId());
                traceSkip(sub, train, "rolling car, the wheel hinge owns the lift", now);
            }
        }
    }

    private static Vector3d queuedPush(ServerSubLevel sub) {
        Map<ForceGroup, QueuedForceGroup> groups = sub.getQueuedForceGroups();
        if (groups != null && !groups.isEmpty()) {
            Vector3d local = new Vector3d();

            for (QueuedForceGroup group : groups.values()) {
                local.add(group.getForceTotal().getLocalForce());
            }

            return local.lengthSquared() < 1.0E-6 ? null : sub.logicalPose().transformNormal(local, new Vector3d());
        } else {
            return null;
        }
    }

    private static Vector3d jointAccel(ServerSubLevel sub, Vector3d tangent, Vector3d up, double mass, double dt) {
        GenericConstraintHandle glue = RailGlue.CONSTRAINTS.get(sub.getUniqueId());
        if (glue != null && glue.isValid()) {
            Vector3d linear = new Vector3d();
            glue.getJointImpulses(linear, new Vector3d());
            Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
            return RailGlue.basisQuaternion(tangent, up, lateral).transform(linear, new Vector3d()).mul(1.0 / (mass * dt));
        } else {
            return null;
        }
    }

    public static void holdAfterRoll(SableTrain train) {
        if (train.level() != null) {
            long now = train.level().getGameTime();
            REGRIP.put(train.id(), now + 40L);
        }
    }

    public static void dampRelease(ServerSubLevel sub, SableTrain train) {
        Long until = RELEASED.get(train.id());
        if (until != null) {
            if (sub.getLevel().getGameTime() > until) {
                RELEASED.remove(train.id());
            } else {
                RigidBodyHandle handle = RigidBodyHandle.of(sub);
                if (handle != null) {
                    Vector3d slowLinear = overspill(handle.getLinearVelocity(new Vector3d()), 4.0);
                    Vector3d slowAngular = overspill(handle.getAngularVelocity(new Vector3d()), 2.0);
                    if (slowLinear.lengthSquared() > 1.0E-6 || slowAngular.lengthSquared() > 1.0E-6) {
                        handle.addLinearAndAngularVelocity(slowLinear, slowAngular);
                    }
                }
            }
        }
    }

    private static Vector3d overspill(Vector3d velocity, double limit) {
        double speed = velocity.length();
        return speed <= limit ? new Vector3d() : velocity.mul(limit / speed - 1.0);
    }

    static boolean holdDownForStaff(ServerSubLevel sub) {
        return (Boolean)LoconauticsConfig.DERAIL_ON_EXTERNAL_FORCE.get() && (Double)LoconauticsConfig.DERAIL_EXTERNAL_LIFT_RATIO.get() > 0.0 && staffDrags(sub);
    }

    static boolean staffDrags(ServerSubLevel sub) {
        if (sub.getLevel() instanceof ServerLevel level) {
            try {
                PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(level);

                for (Object session : ((PhysicsStaffServerHandlerAccessor)handler).loconautics$getDraggingSessions().values()) {
                    if (((PhysicsStaffDragSessionAccessor)session).loconautics$getSubLevel() == sub) {
                        return true;
                    }
                }

                return false;
            } catch (NoClassDefFoundError ignored) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean staffLocked(ServerSubLevel sub) {
        if (sub.getLevel() instanceof ServerLevel level) {
            try {
                return PhysicsStaffServerHandler.get(level).isLocked(sub);
            } catch (NoClassDefFoundError ignored) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean staffHolds(ServerSubLevel sub) {
        if (sub.getLevel() instanceof ServerLevel level) {
            try {
                return PhysicsStaffServerHandler.get(level).isLocked(sub) || staffDrags(sub);
            } catch (NoClassDefFoundError ignored) {
                return false;
            }
        } else {
            return false;
        }
    }

    public static boolean staffLockedById(ServerLevel level, Collection<UUID> ids) {
        try {
            Map<UUID, Object> locks = ((PhysicsStaffServerHandlerAccessor)PhysicsStaffServerHandler.get(level)).loconautics$getLocks();

            for (UUID id : ids) {
                if (locks.containsKey(id)) {
                    return true;
                }
            }

            return false;
        } catch (NoClassDefFoundError ignored) {
            return false;
        }
    }

    static void tracePinned(ServerSubLevel sub, SableTrain train) {
        traceSkip(sub, train, "pinned, not glued", sub.getLevel().getGameTime());
    }

    private static void traceSkip(ServerSubLevel sub, SableTrain train, String why, long now) {
        if (ForceTrace.enabled() && throttle(sub, now)) {
            LoconauticsConstants.LOGGER.debug("[extforce] car={} not sampled, {}", LogFormat.shortId(train.id()), why);
        }
    }

    private static boolean throttle(ServerSubLevel sub, long now) {
        Long last = TRACED.get(sub.getUniqueId());
        if (last != null && now - last < 10L) {
            return false;
        }

        TRACED.put(sub.getUniqueId(), now);
        return true;
    }

    private static void trace(
        ServerSubLevel sub, SableTrain train, Vector3d up, double mass, double pull, double lift, long now, boolean dragged, boolean queued
    ) {
        if (ForceTrace.enabled() && throttle(sub, now)) {
            RigidBodyHandle handle = RigidBodyHandle.of(sub);
            double vUp = handle == null ? Double.NaN : handle.getLinearVelocity(new Vector3d()).dot(up);
            LoconauticsConstants.LOGGER
                .debug(
                    "[extforce] car={} glue={} offTrack={} mass={} vUp={} lift={} railJoint={} b/s2 staffDrag={} queuedForce={}",
                    new Object[]{
                        LogFormat.shortId(train.id()),
                        RailGlue.glueModeOf(sub),
                        train.hasOffTrackBogey(),
                        SableTrainDriver.f(mass),
                        SableTrainDriver.f(vUp),
                        SableTrainDriver.f(lift),
                        SableTrainDriver.f(pull),
                        dragged,
                        queued
                    }
                );
        }
    }

    public static boolean holdsRerail(SableTrain train) {
        Long until = REGRIP.get(train.id());
        return until != null && train.level() != null && train.level().getGameTime() < until;
    }

    static boolean check(SableTrain train, ServerSubLevel sub) {
        ExternalForceDerail.Push plunger = PLUNGER_PEAK.remove(sub.getUniqueId());
        if (plunger != null
            && !TrainPropulsion.isMonorail(train)
            && sub.getLevel().getGameTime() - plunger.gameTime() <= 1L
            && checkLift(train, plunger, DimensionPhysicsData.getGravity(sub.getLevel()).length() * 1.0)) {
            PEAK.remove(sub.getUniqueId());
            return true;
        } else {
            ExternalForceDerail.Push push = PEAK.remove(sub.getUniqueId());
            if (push != null && !TrainPropulsion.isMonorail(train) && sub.getLevel().getGameTime() - push.gameTime() <= 1L) {
                double gravity = DimensionPhysicsData.getGravity(sub.getLevel()).length();
                return checkLift(train, push, gravity * liftRatio(sub));
            } else {
                forget(train.id());
                return false;
            }
        }
    }

    private static double liftRatio(ServerSubLevel sub) {
        return CarriageRoll.isRolling(sub.getUniqueId()) ? (Double)LoconauticsConfig.DERAIL_EXTERNAL_LIFT_RATIO.get() : 100.0;
    }

    private static boolean checkLift(SableTrain train, ExternalForceDerail.Push push, double limit) {
        if (push.lift() <= limit) {
            LIFTED.remove(train.id());
            return false;
        }

        if (LIFTED.merge(train.id(), 1, Integer::sum) < 5) {
            return false;
        }

        forget(train.id());
        train.setDerailed(true, "lifted_off");
        if (train.level() != null) {
            RELEASED.put(train.id(), train.level().getGameTime() + 10L);
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[sabletrain] car {} was lifted off the rail, {} b/s2 up against {}",
                new Object[]{train.id(), SableTrainDriver.f(push.lift()), SableTrainDriver.f(limit)}
            );
        return true;
    }

    private static void forget(UUID trainId) {
        LIFTED.remove(trainId);
    }

    static void retain(Set<UUID> liveTrains, Set<UUID> liveSubLevels) {
        PEAK.keySet().retainAll(liveSubLevels);
        PLUNGER_PEAK.keySet().retainAll(liveSubLevels);
        PLUNGER_PULL.keySet().retainAll(liveSubLevels);
        LIFTED.keySet().retainAll(liveTrains);
        REGRIP.keySet().retainAll(liveTrains);
        TRACED.keySet().retainAll(liveSubLevels);
        RELEASED.keySet().retainAll(liveTrains);
    }

    private record Pull(long gameTime, Vector3d accel) {
    }

    private record Push(long gameTime, double lift) {
        ExternalForceDerail.Push worse(long now, double otherLift) {
            return this.gameTime == now ? new ExternalForceDerail.Push(now, Math.max(this.lift, otherLift)) : new ExternalForceDerail.Push(now, otherLift);
        }
    }
}
