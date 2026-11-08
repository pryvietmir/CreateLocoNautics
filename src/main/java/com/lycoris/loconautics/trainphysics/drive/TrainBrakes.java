package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.brakes.HandbrakeBlock;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeBlock;
import com.lycoris.loconautics.content.brakes.pull.PullBrakeBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleRailCoupler;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.debug.force.ForceSource;
import com.lycoris.loconautics.trainphysics.debug.force.ForceTrace;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class TrainBrakes {
    public static final double BRAKE_DECEL = 60.0;
    static final double WHEEL_GRIP_DECEL = 30.0;
    static final double SKID_FACTOR = 0.7;
    static final double HANDBRAKE_STRESS_WEIGHT = 4.0;
    private static final int BRAKE_SCAN_TICKS = 10;
    private static final Map<ServerSubLevel, TrainBrakes.BrakeScan> BRAKE_SCANS = new WeakHashMap<>();
    private static final double MIN_ROLLING_WEIGHT = 0.25;
    private static final double MAX_ROLLING_WEIGHT = 4.0;
    private static final double SLOPE_RELIEF_DOWNHILL_SPEED = 0.05;

    private TrainBrakes() {
    }

    public static void holdSoft(SableTrain train) {
        train.armBrakePulse();
    }

    public static void update(SableTrain train) {
        if (!train.isDerailed() && !train.isPhysics()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container != null) {
                SableTrain.Car car = train.car();
                if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                    train.tickBrakePulse();
                    double hand = handbrakeStrength(sub);
                    train.setHandbrake(hand);
                    double handScale = Math.min(1.0, hand / Math.max(1.0, rollingWeightUnclamped(sub)));
                    boolean locked = handScale * 60.0 > 30.0;
                    if (locked) {
                        handScale *= 0.7;
                    }

                    ConsistBrake.Demand consist = ConsistBrake.demandFor(train);
                    double pipe = Math.min(1.0, consist.decel() / 60.0);
                    double soft = Math.max(pipe, train.brakePulseActive() ? 1.0 : 0.0);
                    double scale = Math.max(handScale, soft);
                    train.setBrakeScale(scale);
                    train.setBraked(scale > 0.0);
                    train.setBrakeHold(consist.hold() || train.brakePulseActive());
                    train.setBrakeHard(locked);
                }
            }
        } else {
            train.setBraked(false);
            train.setBrakeHard(false);
            train.setBrakeHold(false);
        }
    }

    public static double handbrakeLoadMass(SableTrain train) {
        return train.handbrake() * (Double)LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() * 4.0;
    }

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }

    public static double handbrakeStrength(ServerSubLevel sub) {
        ServerLevel subLevel = sub.getLevel();
        long now = subLevel.getGameTime();
        TrainBrakes.BrakeScan scan = BRAKE_SCANS.get(sub);
        if (scan == null || now - scan.tick() >= 10L || now < scan.tick()) {
            List<BlockPos> found = new ArrayList<>();
            SubLevelBlocks.forEachBlock(sub, (posx, statex) -> {
                if (statex.getBlock() instanceof PullBrakeBlock || statex.getBlock() instanceof HandbrakeBlock) {
                    found.add(posx.immutable());
                }

                return true;
            });
            scan = new TrainBrakes.BrakeScan(now, found);
            BRAKE_SCANS.put(sub, scan);
        }

        int best = 0;

        for (BlockPos pos : scan.positions()) {
            BlockState state = subLevel.getBlockState(pos);
            if (state.getBlock() instanceof PullBrakeBlock && subLevel.getBlockEntity(pos) instanceof PullBrakeBlockEntity brake) {
                best = Math.max(best, brake.power());
            } else if (state.getBlock() instanceof HandbrakeBlock && (Boolean)state.getValue(HandbrakeBlock.POWERED)) {
                best = Math.max(best, subLevel.getBestNeighborSignal(pos));
            }

            if (best >= 15) {
                break;
            }
        }

        double fraction = best / 15.0;
        return fraction * fraction;
    }

    public static double rollingScale(SableTrain train, ServerSubLevel sub) {
        return !train.isPowered() && !train.isDerailed() && !train.isFollowing()
            ? Math.min(1.0, (Double)LoconauticsConfig.DYNAMICS_ROLLING_RESISTANCE.get() * rollingWeight(sub) / 60.0)
            : 0.0;
    }

    private static double rollingWeight(ServerSubLevel sub) {
        return Math.max(0.25, Math.min(4.0, rollingWeightUnclamped(sub)));
    }

    private static double rollingWeightUnclamped(ServerSubLevel sub) {
        MassData md = sub == null ? null : sub.getMassTracker();
        double mass = md != null && !md.isInvalid() ? md.getMass() : 0.0;
        return mass <= 0.0 ? 1.0 : mass / (Double)LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get();
    }

    public static void relieveSlopeGravity(ServerSubLevel sub, Vector3d railPoint, Vector3d tangent, double dt) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle != null) {
            double along = DimensionPhysicsData.getGravity(sub.getLevel()).dot(tangent);
            if (!(Math.abs(along) < 1.0E-6)) {
                double rolling = handle.getLinearVelocity(new Vector3d()).dot(tangent);
                if (!(rolling * along > 0.05)) {
                    MassData md = sub.getMassTracker();
                    double mass = md != null && !md.isInvalid() ? md.getMass() : 1.0;
                    Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * along * dt);
                    Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(worldImpulse, new Vector3d());
                    ForceTotal forces = new ForceTotal();
                    forces.applyLinearImpulse(localImpulse);
                    handle.applyForcesAndReset(forces);
                    ForceTrace.impulse(sub, ForceSource.SLOPE_RELIEF, worldImpulse, railPoint, "along rail gravity " + fmt(along) + " mass=" + fmt(mass));
                }
            }
        }
    }

    public static void applyResistance(
        ServerSubLevel sub, ServerSubLevel trackSub, Vector3d railPoint, Vector3d tangent, double dt, double scale, ForceSource source
    ) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        if (handle != null && !(scale <= 0.0)) {
            Vector3d vel = handle.getLinearVelocity(new Vector3d());
            double vTan = TrackHostMotion.relativeAlongTrack(sub.getLevel(), trackSub, railPoint, vel, tangent);
            if (!(Math.abs(vTan) < 1.0E-6)) {
                MassData md = sub.getMassTracker();
                double mass = md != null && !md.isInvalid() ? md.getMass() : 1.0;
                double decel = Math.min(60.0 * scale, Math.abs(vTan) / dt);
                Vector3d worldImpulse = new Vector3d(tangent).mul(-mass * decel * dt * Math.signum(vTan));
                Vector3d localImpulse = new Quaterniond(sub.logicalPose().orientation()).conjugate().transform(worldImpulse, new Vector3d());
                ForceTotal forces = new ForceTotal();
                forces.applyLinearImpulse(localImpulse);
                handle.applyForcesAndReset(forces);
                KnuckleRailCoupler.noteExternal(sub.getUniqueId(), -decel * dt * Math.signum(vTan));
                ForceTrace.impulse(
                    sub,
                    source,
                    worldImpulse,
                    railPoint,
                    "scale=" + fmt(scale) + " decel=" + fmt(decel) + " of " + fmt(60.0) + " against vTan=" + fmt(vTan) + " mass=" + fmt(mass)
                );
                TrackHostMotion.pushHost(trackSub, railPoint, worldImpulse);
            }
        }
    }

    private record BrakeScan(long tick, List<BlockPos> positions) {
    }
}
