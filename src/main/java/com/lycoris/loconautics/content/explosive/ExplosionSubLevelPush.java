package com.lycoris.loconautics.content.explosive;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class ExplosionSubLevelPush {
    private ExplosionSubLevelPush() {
    }

    public static void apply(ServerLevel level, Vec3 center, float power) {
        if (LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_ENABLED != null && (Boolean)LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_ENABLED.get()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                double radius = Math.max(0.0, power) * (Double)LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_RADIUS_MULTIPLIER.get();
                if (!(radius <= 0.0)) {
                    double strength = power * (Double)LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_STRENGTH.get();
                    double minDistance = Math.max(0.1, (Double)LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_MIN_DISTANCE.get());
                    double upward = (Double)LoconauticsConfig.EXPLOSION_SUBLEVEL_PUSH_UPWARD.get();

                    for (ServerSubLevel sub : container.getAllSubLevels()) {
                        if (sub != null && !sub.isRemoved()) {
                            RigidBodyHandle handle = RigidBodyHandle.of(sub);
                            if (handle != null) {
                                Vector3d localCenter = new Vector3d(
                                    sub.getPlot().getCenterBlock().getX() + 0.5,
                                    sub.getPlot().getCenterBlock().getY() + 0.5,
                                    sub.getPlot().getCenterBlock().getZ() + 0.5
                                );
                                Vector3d worldCenter = sub.logicalPose().transformPosition(localCenter, new Vector3d());
                                double dx = worldCenter.x - center.x;
                                double dy = worldCenter.y - center.y;
                                double dz = worldCenter.z - center.z;
                                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                if (!(dist > radius)) {
                                    double clampedDist = Math.max(dist, minDistance);
                                    double inverseSquare = minDistance * minDistance / (clampedDist * clampedDist);
                                    double edgeTaper = 1.0 - dist / radius;
                                    double magnitude = strength * inverseSquare * edgeTaper;
                                    if (!(magnitude <= 1.0E-4)) {
                                        Vector3d dir = dist > 1.0E-4 ? new Vector3d(dx, dy, dz).div(dist) : new Vector3d(0.0, 1.0, 0.0);
                                        Vector3d dv = new Vector3d(dir).mul(magnitude);
                                        dv.y += magnitude * upward;
                                        container.physicsSystem().getPipeline().wakeUp(sub);
                                        handle.addLinearAndAngularVelocity(dv, new Vector3d());
                                        ForceTrace.deltaV(sub, ForceSource.EXPLOSION, dv, new Vector3d(sub.logicalPose().position()), "blast wave");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
