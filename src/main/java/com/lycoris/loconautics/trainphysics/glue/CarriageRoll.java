package com.lycoris.loconautics.trainphysics.glue;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.coupling.CouplerTwist;
import com.lycoris.loconautics.trainphysics.spawn.WheelColliders;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class CarriageRoll {
    public static final double RAIL_TOP = 0.2836;
    public static final double SEAT_DROP = 0.7163999999999999;
    public static final double FLANGE = 0.125;
    public static final double LIFT_RANGE = 4.0;
    public static final double LIFT_EPS = 0.02;
    public static final double PHI_EPS = Math.toRadians(0.2);
    public static final long SETTLE_TICKS = 20L;
    public static final double SUSPENSION_HZ = 1.2;
    public static final int UNGLUE_TICKS = 3;
    public static final double FALL_MARGIN = Math.toRadians(5.0);
    public static final String LOCK_ARTICULATED = "articulated";
    private static final double MIN_GRAVITY = 0.5;
    public static final TagKey<Block> NO_WHEEL_LIFT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("loconautics", "no_wheel_lift"));
    private static final ConcurrentHashMap<UUID, CarriageRoll.Roll> STATE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Boolean> ROLLING_THIS_STEP = new ConcurrentHashMap<>();
    public static final boolean TRACE = true;
    private static final ConcurrentHashMap<UUID, Long> LAST_TRACE = new ConcurrentHashMap<>();

    private CarriageRoll() {
    }

    public static CarriageRoll.Roll of(UUID id) {
        return STATE.computeIfAbsent(id, key -> new CarriageRoll.Roll());
    }

    public static CarriageRoll.Roll rollOf(ServerSubLevel sub) {
        return sub == null ? null : STATE.get(sub.getUniqueId());
    }

    public static CarriageRoll.Roll rollById(UUID id) {
        return id == null ? null : STATE.get(id);
    }

    public static boolean traceDue(UUID id, long gameTime) {
        Long last = LAST_TRACE.get(id);
        if (last != null && gameTime - last < 10L) {
            return false;
        }

        LAST_TRACE.put(id, gameTime);
        return true;
    }

    public static String primaryLockOf(UUID id) {
        CarriageRoll.Roll roll = STATE.get(id);
        return roll == null ? null : roll.primaryLock;
    }

    public static boolean isRolling(UUID id) {
        CarriageRoll.Roll roll = STATE.get(id);
        return roll != null && roll.rolling;
    }

    public static boolean wheelsLifted(UUID id) {
        CarriageRoll.Roll roll = STATE.get(id);
        return roll != null && roll.rolling && roll.allLifted();
    }

    public static void markRolling(UUID id) {
        ROLLING_THIS_STEP.put(id, Boolean.TRUE);
    }

    public static boolean consumeRolling(UUID id) {
        return ROLLING_THIS_STEP.remove(id) != null;
    }

    public static String lockReason(ServerSubLevel sub, SableTrain.Car group, boolean docked, SableTrain train) {
        if (train.hasOffTrackBogey()) {
            return "pivot";
        }

        if (group.carriage() == null) {
            return "trackless";
        }

        if (docked) {
            return "docked";
        }

        List<SableTrain.Bogey> chain = group.chain();
        if (chain.size() < 2) {
            return "single_bogey";
        }

        if (group.carriage().isOnMonorail()) {
            return "monorail";
        }

        for (SableTrain.Bogey bogey : chain) {
            if (bogey.rail().isOnMonorail()) {
                return "monorail";
            }
        }

        for (SableTrain.Bogey bogey : group.bogeys()) {
            BlockState state = sub.getLevel().getBlockState(sub.getPlot().getCenterBlock().offset(bogey.localPos()));
            if (state.is(NO_WHEEL_LIFT)) {
                return "no_wheel_lift";
            }

            if (WheelColliders.isUpsideDown(state)) {
                return "upside_down";
            }
        }

        return CouplerTwist.articulatedLock(sub.getUniqueId());
    }

    public static boolean hasGravity(double alongUp) {
        return alongUp >= 0.5;
    }

    public static void noteLock(UUID id, String reason) {
        CarriageRoll.Roll roll = of(id);
        String next = reason == null ? "none" : reason;
        if (!next.equals(roll.lock)) {
            LoconauticsConstants.LOGGER.debug("[wheellift] car={} lock {} -> {}", new Object[]{id.toString().substring(0, 8), roll.lock, next});
            roll.lock = next;
        }

        roll.rolling = reason == null;
        roll.primaryLock = reason != null && !reason.equals("articulated") && !reason.equals("released") ? reason : null;
        if (reason != null) {
            roll.rolledOff.reset();
            roll.liftedOff.reset();
        }
    }

    public static void beginStep(CarriageRoll.Roll roll, long gameTime) {
        boolean fresh = !roll.rolling || gameTime - roll.lastDriveTick > 1L;
        roll.lastDriveTick = gameTime;
        if (fresh) {
            roll.settleUntil = gameTime + 20L;
            roll.side = 0;
            roll.rolledOff.reset();
            roll.liftedOff.reset();
            LoconauticsConstants.LOGGER.debug("[wheellift] settle window opened until tick {}", roll.settleUntil);
        }
    }

    public static int updateSide(CarriageRoll.Roll roll, double phi) {
        int side = roll.side;
        if (side == 0) {
            if (Math.abs(phi) > PHI_EPS) {
                side = phi > 0.0 ? 1 : -1;
            }
        } else if (phi * side < -PHI_EPS) {
            side = -side;
        }

        roll.side = side;
        return side;
    }

    public static CarriageRoll.Ready rollReady(ServerSubLevel sub, SableTrain.Car car, RollFrame frame, PoseTweak.Applied seated) {
        MassData md = sub.getMassTracker();
        if (frame != null && md != null && !md.isInvalid() && RigidBodyHandle.of(sub) != null && car.chain().size() >= 2) {
            WheelSupports.Prepared prepared = WheelSupports.prepare(sub, car.chain(), frame, seated);
            if (prepared == null) {
                return new CarriageRoll.Ready("no_geometry", null);
            }

            double gN = -DimensionPhysicsData.getGravity(sub.getLevel()).dot(frame.up);
            return !hasGravity(gN) ? new CarriageRoll.Ready("no_gravity", null) : new CarriageRoll.Ready(null, prepared);
        } else {
            return new CarriageRoll.Ready("no_geometry", null);
        }
    }

    public static double twist(Quaterniond seat, Quaterniond body, Vector3d forward0) {
        Quaterniond r = new Quaterniond(seat).invert().mul(body);
        double p = r.x * forward0.x + r.y * forward0.y + r.z * forward0.z;
        double phi = 2.0 * Math.atan2(p, r.w);
        if (phi > Math.PI) {
            phi -= Math.PI * 2;
        } else if (phi < -Math.PI) {
            phi += Math.PI * 2;
        }

        return phi;
    }

    public static double tippingAngle(double halfGauge, double offset, double height, double lean, double cant) {
        return Math.atan2(halfGauge - lean * offset, Math.max(height, 0.001)) - cant;
    }

    public static double holdAngle() {
        return Math.toRadians((Double)LoconauticsConfig.DERAIL_WHEEL_LIFT_MAX_DEGREES.get());
    }

    public static double safeCurveSpeed(CarriageRoll.Roll roll, double gN, double curvature, double share) {
        double k = Math.abs(curvature);
        if (!(k < 1.0E-6) && !(roll.height < 0.05) && !(roll.half <= 0.0)) {
            double phiRef = Math.toRadians((Double)LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
            double phiSafe = share * Math.min(Math.toRadians((Double)LoconauticsConfig.DERAIL_ROLL_UNGLUE_DEGREES.get()), holdAngle());
            double hold = Math.max(0.0, gN * (roll.half - Math.abs(roll.offset)));
            double hinge = gN * roll.half * phiSafe / phiRef;
            return Math.sqrt((hold + hinge) / (k * roll.height));
        } else {
            return Double.POSITIVE_INFINITY;
        }
    }

    public static double speedBlend(double speed) {
        double slow = (Double)LoconauticsConfig.DERAIL_ROLL_SLOW_SPEED.get();
        double fast = Math.max(slow + 0.001, (Double)LoconauticsConfig.DERAIL_ROLL_FAST_SPEED.get());
        double t = Math.max(0.0, Math.min(1.0, (Math.abs(speed) - slow) / (fast - slow)));
        return t * t * (3.0 - 2.0 * t);
    }

    public static double unglueAngle(double speed) {
        double blend = speedBlend(speed);
        double slowLimit = holdAngle() + FALL_MARGIN;
        double fastLimit = Math.toRadians((Double)LoconauticsConfig.DERAIL_ROLL_UNGLUE_DEGREES.get());
        return slowLimit + blend * (fastLimit - slowLimit);
    }

    public static double predictedLift(double gN, double gSide, double halfGauge, double offset, double height, double phiRef, int side) {
        double moment = gN * (side * offset - halfGauge) + gSide * height;
        double den = gN * (halfGauge - phiRef * height);
        return den <= 0.0 ? Double.POSITIVE_INFINITY : phiRef * Math.max(0.0, moment) / den;
    }

    public static double predictedLift(Vector3d gravity, Vector3d up, Vector3d lateral, double halfGauge, double offset, double height) {
        double gN = -gravity.dot(up);
        double phiRef = Math.toRadians((Double)LoconauticsConfig.DERAIL_WHEEL_LIFT_DEGREES.get());
        double worst = 0.0;

        for (int side = -1; side <= 1; side += 2) {
            worst = Math.max(worst, predictedLift(gN, gravity.dot(lateral) * side, halfGauge, offset, height, phiRef, side));
        }

        return worst;
    }

    public static void stepSuspension(CarriageRoll.Roll roll, double halfGauge, double dt) {
        double max = Math.toRadians((Double)LoconauticsConfig.SUSPENSION_LEAN_DEGREES.get());
        double target = max * Math.max(-1.0, Math.min(1.0, roll.offset / Math.max(halfGauge, 0.001)));
        double omega = Math.PI * 12.0 / 5.0;
        double zeta = (Double)LoconauticsConfig.DERAIL_WHEEL_LIFT_DAMPING.get();
        roll.suspensionRate = roll.suspensionRate + (omega * omega * (target - roll.suspension) - 2.0 * zeta * omega * roll.suspensionRate) * dt;
        roll.suspension = roll.suspension + roll.suspensionRate * dt;
    }

    public static void release(UUID id) {
        STATE.remove(id);
        ROLLING_THIS_STEP.remove(id);
    }

    public static void retain(Set<UUID> live) {
        STATE.keySet().retainAll(live);
        ROLLING_THIS_STEP.keySet().retainAll(live);
        LAST_TRACE.keySet().retainAll(live);
    }

    public static void clearAll() {
        STATE.clear();
        ROLLING_THIS_STEP.clear();
        LAST_TRACE.clear();
    }

    public static final class Hold {
        private int count;
        private long tick = Long.MIN_VALUE;

        public boolean update(boolean condition, long gameTime) {
            if (!condition) {
                this.count = 0;
                this.tick = Long.MIN_VALUE;
                return false;
            }

            if (this.tick != gameTime) {
                this.count++;
                this.tick = gameTime;
            }

            return this.count >= 3;
        }

        public void reset() {
            this.count = 0;
            this.tick = Long.MIN_VALUE;
        }
    }

    public record Ready(String reason, WheelSupports.Prepared prepared) {
    }

    public static final class Roll {
        public volatile boolean rolling;
        public volatile String lock = "none";
        public volatile double phi;
        public volatile double omegaR;
        public volatile double heave;
        public volatile double rise;
        public volatile double preUp;
        public volatile boolean preValid;
        public volatile long curveUntil = -4611686018427387904L;
        public volatile double curveBase;
        public volatile boolean curveClamped;
        public volatile int side;
        public volatile double lean;
        public volatile double offset;
        public volatile double height;
        public volatile double tip;
        public volatile double unglue;
        public volatile double predicted;
        public volatile double speed;
        public volatile double torque;
        public volatile double relief;
        public volatile double curvature;
        public volatile Vector3d axis = new Vector3d(0.0, 0.0, 1.0);
        public volatile Vector3d up = new Vector3d(0.0, 1.0, 0.0);
        public volatile Quaterniond seat = new Quaterniond();
        public volatile double hingeK;
        public volatile double hingeI;
        public volatile double half;
        public volatile double mass;
        public volatile String primaryLock;
        public volatile double couplerTorque;
        public volatile double couplerTwist;
        public volatile double suspension;
        public volatile double suspensionRate;
        public volatile double suspensionSent;
        public volatile long suspensionSentTick = -1000L;
        public volatile double lastMass = -1.0;
        public final Vector3d lastCenter = new Vector3d();
        public volatile long settleUntil;
        public volatile long lastDriveTick = Long.MIN_VALUE;
        public volatile double[] y = new double[0];
        public volatile int lifted;
        public final CarriageRoll.Hold rolledOff = new CarriageRoll.Hold();
        public final CarriageRoll.Hold liftedOff = new CarriageRoll.Hold();

        public boolean settling(long gameTime) {
            return gameTime < this.settleUntil;
        }

        public boolean curveHeld(long gameTime) {
            return gameTime < this.curveUntil;
        }

        public boolean allLifted() {
            double[] wheels = this.y;
            if (wheels.length == 0) {
                return false;
            }

            for (double wheel : wheels) {
                if (wheel <= 0.02) {
                    return false;
                }
            }

            return true;
        }

        public boolean allBeyondFlange() {
            double[] wheels = this.y;
            if (wheels.length == 0) {
                return false;
            }

            for (double wheel : wheels) {
                if (wheel <= 0.125) {
                    return false;
                }
            }

            return true;
        }
    }
}
