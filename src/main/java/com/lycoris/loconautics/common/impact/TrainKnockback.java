package com.lycoris.loconautics.common.impact;

import com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class TrainKnockback {
    public static void applyPendingKnockback(PhysicsPipeline pipeline, ServerSubLevel sub, SableTrain train) {
        Vector3d dv = train.takeKnockback();
        if (dv != null) {
            RigidBodyHandle handle = RigidBodyHandle.of(sub);
            if (handle != null) {
                MassData md = sub.getMassTracker();
                double mass = md != null && !md.isInvalid() ? md.getMass() : 1.0;
                Vector3d worldImpulse = new Vector3d(dv).mul(mass);
                Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(worldImpulse, new Vector3d());
                pipeline.wakeUp(sub);
                ForceTotal forces = new ForceTotal();
                forces.applyLinearImpulse(localImpulse);
                handle.applyForcesAndReset(forces);
                ForceTrace.impulse(sub, ForceSource.KNOCKBACK, worldImpulse, new Vector3d(sub.logicalPose().position()), "queued knockback for a derailed car");
            }
        }
    }

    public static void applyKnockbackNow(SableTrain train, Vector3d worldDeltaVelocity) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null) {
            UUID id = train.car().subLevelId();
            if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                RigidBodyHandle handle = RigidBodyHandle.of(sub);
                if (handle != null) {
                    container.physicsSystem().getPipeline().wakeUp(sub);
                    handle.addLinearAndAngularVelocity(worldDeltaVelocity, new Vector3d());

                    for (UUID plateId : GhostWheelBodies.wheelBodiesOf(id)) {
                        if (container.getSubLevel(plateId) instanceof ServerSubLevel plateSub && !plateSub.isRemoved()) {
                            RigidBodyHandle plate = RigidBodyHandle.of(plateSub);
                            if (plate != null) {
                                container.physicsSystem().getPipeline().wakeUp(plateSub);
                                plate.addLinearAndAngularVelocity(worldDeltaVelocity, new Vector3d());
                            }
                        }
                    }

                    ForceTrace.deltaV(sub, ForceSource.KNOCKBACK, worldDeltaVelocity, new Vector3d(sub.logicalPose().position()), "immediate knockback");
                }
            }
        }
    }

    public static void launchOnDerail(SableTrain train, double lift) {
        launchOnDerail(train, lift, null, 0.0, null, 0.0);
    }

    public static void launchOnDerail(SableTrain train, double lift, Vec3 outward, double kick, Vec3 rollAxis, double roll) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null && train.car() != null) {
            UUID id = train.car().subLevelId();
            if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                RigidBodyHandle handle = RigidBodyHandle.of(sub);
                if (handle != null) {
                    double speed = handle.getLinearVelocity(new Vector3d()).length();
                    if (!(speed < 0.001)) {
                        container.physicsSystem().getPipeline().wakeUp(sub);
                        Vector3d launch = new Vector3d(0.0, speed * lift, 0.0);
                        if (outward != null) {
                            launch.add(outward.x * speed * kick, 0.0, outward.z * speed * kick);
                        }

                        Vector3d spin = rollAxis == null ? new Vector3d() : new Vector3d(rollAxis.x, rollAxis.y, rollAxis.z).mul(roll);
                        handle.addLinearAndAngularVelocity(launch, spin);
                        ForceTrace.deltaV(
                            sub,
                            ForceSource.DERAIL_LAUNCH,
                            launch,
                            new Vector3d(sub.logicalPose().position()),
                            "thrown clear of the railhead at " + String.format(Locale.ROOT, "%.2f", speed) + " b/s with lift " + lift
                        );
                    }
                }
            }
        }
    }

    public static void applyKnockbackNowAtPoint(SableTrain train, Vector3d worldDeltaVelocity, Vector3d worldPoint) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null) {
            UUID id = train.car().subLevelId();
            if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                RigidBodyHandle handle = RigidBodyHandle.of(sub);
                if (handle != null) {
                    container.physicsSystem().getPipeline().wakeUp(sub);
                    MassData md = sub.getMassTracker();
                    Vector3dc comLocal = md != null && !md.isInvalid() ? md.getCenterOfMass() : null;
                    if (md != null && !md.isInvalid() && comLocal != null) {
                        double mass = md.getMass();
                        Quaterniond orientation = new Quaterniond(sub.logicalPose().orientation());
                        Quaterniond inverse = new Quaterniond(orientation).conjugate();
                        Vector3d worldImpulse = new Vector3d(worldDeltaVelocity).mul(mass);
                        Vector3d localImpulse = inverse.transform(worldImpulse, new Vector3d());
                        Vector3d comWorld = sub.logicalPose().transformPosition(new Vector3d(comLocal), new Vector3d());
                        Vector3d offsetWorld = new Vector3d(worldPoint).sub(comWorld);
                        Vector3d offsetLocal = inverse.transform(offsetWorld, new Vector3d());
                        Vector3d angularImpulseLocal = offsetLocal.cross(localImpulse, new Vector3d());
                        Vector3d deltaOmegaLocal = md.getInverseInertiaTensor().transform(angularImpulseLocal, new Vector3d());
                        Vector3d deltaOmegaWorld = orientation.transform(deltaOmegaLocal, new Vector3d());
                        handle.addLinearAndAngularVelocity(worldDeltaVelocity, deltaOmegaWorld);
                        ForceTrace.deltaV(
                            sub,
                            ForceSource.KNOCKBACK,
                            worldDeltaVelocity,
                            worldPoint,
                            "off center knockback, spin " + String.format(Locale.ROOT, "%.3f", deltaOmegaWorld.length()) + " rad/s"
                        );
                    } else {
                        handle.addLinearAndAngularVelocity(worldDeltaVelocity, new Vector3d());
                    }
                }
            }
        }
    }

    public static void applyRailRecoil(SableTrain train, Vector3d worldRecoilVelocity) {
        if (train != null && !train.isDerailed() && train.car() != null) {
            Vector3d tangent = null;

            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                tangent = TrainRailAdvance.railTangentOf(bogey);
                if (tangent != null) {
                    break;
                }
            }

            if (tangent != null) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
                if (container != null) {
                    UUID id = train.car().subLevelId();
                    if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                        RigidBodyHandle handle = RigidBodyHandle.of(sub);
                        if (handle != null) {
                            double alongMps = worldRecoilVelocity.dot(tangent);
                            Vector3d dvWorld = new Vector3d(tangent).mul(alongMps);
                            container.physicsSystem().getPipeline().wakeUp(sub);
                            handle.addLinearAndAngularVelocity(dvWorld, new Vector3d());
                            ForceTrace.deltaV(
                                sub,
                                ForceSource.RAIL_RECOIL,
                                dvWorld,
                                new Vector3d(sub.logicalPose().position()),
                                "recoil resolved onto the rail, " + String.format(Locale.ROOT, "%.3f", alongMps) + " b/s along tangent"
                            );
                            train.setSpeed(train.speed() + alongMps / 20.0);
                            KnuckleRailCoupler.noteExternal(id, alongMps);
                        }
                    }
                }
            }
        }
    }

    public static int applyKnockbackToAllSubLevels(ServerLevel level, Vector3d worldDeltaVelocity) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return 0;
        }

        int nudged = 0;

        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (sub != null && !sub.isRemoved() && SableTrainRegistry.bySubLevel(sub.getUniqueId()) == null) {
                RigidBodyHandle handle = RigidBodyHandle.of(sub);
                if (handle != null) {
                    container.physicsSystem().getPipeline().wakeUp(sub);
                    handle.addLinearAndAngularVelocity(worldDeltaVelocity, new Vector3d());
                    nudged++;
                }
            }
        }

        return nudged;
    }

    private TrainKnockback() {
    }
}
