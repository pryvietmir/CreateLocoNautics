package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.math.VecMath;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.coupling.CouplerTwist;
import com.lycoris.loconautics.trainphysics.debug.RidingBodyTrace;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelMergeWatcher;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class RailGlue {
    public static final Map<UUID, GenericConstraintHandle> CONSTRAINTS = new HashMap<>();
    static final Map<UUID, RailGlue.GlueMode> GLUE_MODE = new HashMap<>();
    static final Map<UUID, Quaterniond> LAST_GLUE_Q = new HashMap<>();
    static final Map<UUID, UUID> GLUE_BODY_A_ID = new HashMap<>();
    static final double MAX_GLUE_STEP = 0.15;

    private RailGlue() {
    }

    public static boolean glueBody(
        PhysicsPipeline pipeline,
        ServerSubLevel sub,
        Vector3dc localAnchor,
        Vector3d railPoint,
        Vector3d tangent,
        Quaterniond q,
        RailGlue.GlueMode mode,
        ServerSubLevel trackSub
    ) {
        return glueBody(pipeline, sub, localAnchor, railPoint, tangent, null, q, mode, trackSub);
    }

    public static boolean glueBody(
        PhysicsPipeline pipeline,
        ServerSubLevel sub,
        Vector3dc localAnchor,
        Vector3d railPoint,
        Vector3d tangent,
        Vector3d trackUp,
        Quaterniond q,
        RailGlue.GlueMode mode,
        ServerSubLevel trackSub
    ) {
        UUID id = sub.getUniqueId();
        if (sub.getLevel() instanceof ServerLevel pullLevel && SubLevelMergeWatcher.pulling(pullLevel, id)) {
            releaseConstraint(id);
            return true;
        } else {
            if (BodyRef.ofBody(trackSub).absent()) {
                releaseConstraint(id);
                return true;
            }

            if (trackSub == null && railInDeadPlot(sub, railPoint)) {
                releaseConstraint(id);
                return false;
            }

            UUID bodyAId = trackSub != null ? trackSub.getUniqueId() : null;
            Vector3d up = trackUp == null ? VecMath.perpendicularUp(tangent) : new Vector3d(trackUp);
            Vector3d lateral = new Vector3d(tangent).cross(up).normalize();
            Quaterniond frame1World = basisQuaternion(tangent, up, lateral);
            Vector3d pos1;
            Quaterniond frame1;
            PhysicsPipelineBody bodyA;
            if (trackSub != null) {
                pos1 = trackSub.logicalPose().transformPositionInverse(new Vector3d(railPoint), new Vector3d());
                frame1 = new Quaterniond(trackSub.logicalPose().orientation()).conjugate().mul(frame1World);
                bodyA = trackSub;
            } else {
                pos1 = new Vector3d(railPoint);
                frame1 = frame1World;
                bodyA = null;
            }

            Quaterniond frame2 = new Quaterniond(q).invert().mul(frame1World);
            GenericConstraintHandle existing = CONSTRAINTS.get(id);
            if (existing != null && existing.isValid() && GLUE_MODE.get(id) == mode && Objects.equals(GLUE_BODY_A_ID.get(id), bodyAId)) {
                existing.setFrame1(pos1, frame1);
                existing.setFrame2(new Vector3d(localAnchor), frame2);
                return true;
            }

            rebuildGuide(id, mode);
            RigidBodyHandle issue297 = RigidBodyHandle.of(sub);
            Vector3d issue295Anchor = sub.logicalPose().transformPosition(new Vector3d(localAnchor), new Vector3d());
            Quaterniond issue295Turn = new Quaterniond(sub.logicalPose().orientation()).conjugate().mul(q);
            LoconauticsConstants.LOGGER
                .debug(
                    "[issue295] new {} glue for {} anchorToRail={} ({} blocks) orientationError={} deg tangent={}",
                    new Object[]{
                        mode,
                        LogFormat.shortId(id),
                        new Vector3d(railPoint).sub(issue295Anchor),
                        LogFormat.f(railPoint.distance(issue295Anchor)),
                        LogFormat.f(Math.toDegrees(issue295Turn.angle())),
                        tangent
                    }
                );
            LoconauticsConstants.LOGGER
                .debug(
                    "[issue297] new {} glue for {} body vel={} ang={} existing={}",
                    new Object[]{
                        mode,
                        LogFormat.shortId(id),
                        issue297 == null ? "none" : issue297.getLinearVelocity(new Vector3d()),
                        issue297 == null ? "none" : issue297.getAngularVelocity(new Vector3d()),
                        existing != null
                    }
                );

            Set<ConstraintJointAxis> locked = switch (mode) {
                case RIGID -> EnumSet.of(
                    ConstraintJointAxis.LINEAR_Y,
                    ConstraintJointAxis.LINEAR_Z,
                    ConstraintJointAxis.ANGULAR_X,
                    ConstraintJointAxis.ANGULAR_Y,
                    ConstraintJointAxis.ANGULAR_Z
                );
                case PIVOT -> EnumSet.of(ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_X);
                case ROLLING -> EnumSet.of(ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z);
                case ROLLING_HELD -> EnumSet.of(
                    ConstraintJointAxis.LINEAR_Y, ConstraintJointAxis.LINEAR_Z, ConstraintJointAxis.ANGULAR_Y, ConstraintJointAxis.ANGULAR_Z
                );
            };
            GenericConstraintConfiguration config = new GenericConstraintConfiguration(pos1, new Vector3d(localAnchor), frame1, frame2, locked);
            GenericConstraintHandle handle = (GenericConstraintHandle)pipeline.addConstraint(bodyA, sub, config);
            if (handle != null) {
                CONSTRAINTS.put(id, handle);
                GLUE_MODE.put(id, mode);
                GLUE_BODY_A_ID.put(id, bodyAId);
            }

            return true;
        }
    }

    static boolean railInDeadPlot(ServerSubLevel sub, Vector3d railPoint) {
        if (!(sub.getLevel() instanceof ServerLevel level)) {
            return false;
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            return container != null && container.inBounds(railPoint);
        }
    }

    public static Quaterniond limitGlueRotation(UUID id, Quaterniond target) {
        Quaterniond last = LAST_GLUE_Q.get(id);
        Quaterniond out;
        if (last == null) {
            out = new Quaterniond(target);
        } else {
            double dot = Math.abs(last.x * target.x + last.y * target.y + last.z * target.z + last.w * target.w);
            double angle = 2.0 * Math.acos(Math.min(1.0, dot));
            if (!(angle <= 0.15) && !(angle < 1.0E-6)) {
                out = new Quaterniond(last).slerp(target, 0.15 / angle).normalize();
            } else {
                out = new Quaterniond(target);
            }
        }

        LAST_GLUE_Q.put(id, out);
        return out;
    }

    public static Quaterniond basisQuaternion(Vector3d forward, Vector3d up, Vector3d lateral) {
        Matrix3d m = new Matrix3d();
        m.setColumn(0, forward);
        m.setColumn(1, up);
        m.setColumn(2, lateral);
        return m.getNormalizedRotation(new Quaterniond());
    }

    public static String glueModeOf(ServerSubLevel sub) {
        RailGlue.GlueMode mode = sub == null ? null : GLUE_MODE.get(sub.getUniqueId());
        return mode == null ? "none" : mode.name();
    }

    private static void rebuildGuide(UUID id, RailGlue.GlueMode next) {
        RailGlue.GlueMode previous = GLUE_MODE.get(id);
        if (next.rolls() && previous != null && previous.rolls()) {
            GLUE_MODE.remove(id);
            LAST_GLUE_Q.remove(id);
            GLUE_BODY_A_ID.remove(id);
            GenericConstraintHandle handle = CONSTRAINTS.remove(id);
            if (handle != null && handle.isValid()) {
                handle.remove();
            }
        } else {
            releaseConstraint(id);
        }
    }

    public static void releaseConstraint(UUID id) {
        WheelSupports.release(id);
        CarriageRoll.release(id);
        GLUE_MODE.remove(id);
        LAST_GLUE_Q.remove(id);
        GLUE_BODY_A_ID.remove(id);
        GenericConstraintHandle handle = CONSTRAINTS.remove(id);
        if (handle != null && handle.isValid()) {
            handle.remove();
        }
    }

    public static Set<UUID> sweepStaleConstraints() {
        Set<UUID> live = new HashSet<>();
        Map<UUID, ServerLevel> levels = new HashMap<>();

        for (SableTrain train : SableTrainRegistry.all()) {
            UUID id = train.car().subLevelId();
            if (id != null) {
                live.add(id);
                levels.put(id, train.level());
            }
        }

        List<UUID> stale = new ArrayList<>();

        for (UUID id : CONSTRAINTS.keySet()) {
            if (!live.contains(id) || BodyRef.of(levels.get(id), GLUE_BODY_A_ID.get(id)).absent()) {
                stale.add(id);
            }
        }

        for (UUID id : stale) {
            releaseConstraint(id);
        }

        GLUE_MODE.keySet().retainAll(live);
        LAST_GLUE_Q.keySet().retainAll(live);
        GLUE_BODY_A_ID.keySet().retainAll(live);
        WheelSupports.retain(live);
        CarriageRoll.retain(live);
        RidingBodyTrace.retain(live);
        CouplerTwist.retain(live);
        return live;
    }

    public static void clearAll() {
        WheelSupports.clearAll();
        CarriageRoll.clearAll();
        CouplerTwist.clearAll();
        CONSTRAINTS.clear();
        GLUE_MODE.clear();
        LAST_GLUE_Q.clear();
        GLUE_BODY_A_ID.clear();
    }

    public enum GlueMode {
        RIGID,
        PIVOT,
        ROLLING,
        ROLLING_HELD;

        boolean rolls() {
            return this == ROLLING || this == ROLLING_HELD;
        }
    }
}
