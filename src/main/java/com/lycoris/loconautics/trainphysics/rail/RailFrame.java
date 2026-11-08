package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import org.joml.Vector3d;

public record RailFrame(RailFrame.Mode mode, Vector3d anchor, Vector3d tangent, Vector3d yawPerBlock, Vector3d railVelocity, double speed) {
    public static RailFrame.Mode modeOf(SableTrain train) {
        if (train != null && train.car() != null && !train.isDerailed() && !train.isPhysics() && !train.hasOffTrackBogey()) {
            return SableTrainDriver.useForceGlue(train, train.car()) ? RailFrame.Mode.RIGID : RailFrame.Mode.PINNED;
        } else {
            return RailFrame.Mode.OTHER;
        }
    }

    public static RailFrame of(ServerSubLevel sub, SableTrain train) {
        RailFrame.Mode mode = modeOf(train);
        RigidBodyHandle handle = mode == RailFrame.Mode.OTHER ? null : RigidBodyHandle.of(sub);
        Vector3d[] ref = handle == null ? null : TrainRailAdvance.bogeyReference(sub, train.car());
        if (ref == null) {
            return none();
        }

        Vector3d railVelocity = SableTrainDriver.railVelocityUnder(sub, train.car());
        double speed = handle.getLinearVelocity(new Vector3d()).sub(railVelocity).dot(ref[1]);
        return new RailFrame(mode, ref[0], ref[1], yawPerBlock(train.car(), ref[1]), railVelocity, speed);
    }

    private static RailFrame none() {
        return new RailFrame(RailFrame.Mode.OTHER, new Vector3d(), new Vector3d(), new Vector3d(), new Vector3d(), 0.0);
    }

    private static Vector3d yawPerBlock(SableTrain.Car car, Vector3d forward) {
        List<SableTrain.Bogey> chain = car.chain();
        if (chain.size() < 2) {
            return new Vector3d();
        }

        SableTrain.Bogey lead = chain.get(chain.size() - 1);
        SableTrain.Bogey trail = chain.get(0);
        Vector3d leadCenter = lead.rail().center();
        Vector3d trailCenter = trail.rail().center();
        Vector3d leadTangent = TrainRailAdvance.railTangentOf(lead);
        Vector3d trailTangent = TrainRailAdvance.railTangentOf(trail);
        if (leadCenter != null && trailCenter != null && leadTangent != null && trailTangent != null) {
            double length = leadCenter.distance(trailCenter);
            if (length < 1.0E-6) {
                return new Vector3d();
            }

            if (leadTangent.dot(forward) < 0.0) {
                leadTangent.negate();
            }

            if (trailTangent.dot(forward) < 0.0) {
                trailTangent.negate();
            }

            Vector3d chordRate = leadTangent.sub(trailTangent, new Vector3d());
            Vector3d forwardRate = chordRate.sub(new Vector3d(forward).mul(forward.dot(chordRate)), new Vector3d()).div(length);
            return forward.cross(forwardRate, new Vector3d());
        } else {
            return new Vector3d();
        }
    }

    public enum Mode {
        RIGID,
        PINNED,
        OTHER;
    }
}
