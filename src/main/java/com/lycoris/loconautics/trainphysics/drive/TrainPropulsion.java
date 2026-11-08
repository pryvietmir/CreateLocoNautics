package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlock;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class TrainPropulsion {
    public static final double MPS2_TO_BPT2 = 0.0025000000000000005;
    static final double MONORAIL_TRACTION_BOOST = 3.0;
    static final double SAFETY_MAX_SPEED_MPS = 200.0;
    static final double STANDSTILL_SPEED = 0.001;

    static double tractionAccelLimit(SableTrain train, double consistMass) {
        double massRatio = (Double)LoconauticsConfig.DYNAMICS_REFERENCE_MASS.get() / Math.max(1.0, consistMass);
        double scaled = (Double)LoconauticsConfig.DYNAMICS_BASE_ACCELERATION.get()
            * Math.pow(massRatio, (Double)LoconauticsConfig.DYNAMICS_MASS_EXPONENT.get());
        double limit = Math.max(
            (Double)LoconauticsConfig.DYNAMICS_MIN_ACCELERATION.get(), Math.min(scaled, (Double)LoconauticsConfig.DYNAMICS_MAX_ACCELERATION.get())
        );
        double boosted = limit * ConsistPower.driverBoost(train);
        return isMonorail(train) ? boosted * 3.0 : boosted;
    }

    public static boolean isMonorail(SableTrain train) {
        return train.car().carriage() != null && train.car().carriage().isOnMonorail();
    }

    public static double brakeDecelLimit() {
        return (Double)LoconauticsConfig.DYNAMICS_BASE_DECELERATION.get();
    }

    static boolean isBraking(double target, double current) {
        return Math.abs(target) < Math.abs(current) || target * current < 0.0;
    }

    static double brakeLimit(SableTrain train, double target, double current) {
        boolean countering = ConsistPower.isManual(train) && target * current < 0.0;
        return brakeDecelLimit() * (countering ? 2.0 : 1.0);
    }

    static double inclineDegrees(SableTrain train) {
        Vec3 forward = train.car().carriage().forward();
        double length = forward.length();
        return length < 1.0E-9 ? 0.0 : Math.toDegrees(Math.asin(Math.min(1.0, Math.abs(forward.y) / length)));
    }

    static boolean isClimbing(SableTrain train, double rpm) {
        Vec3 forward = train.car().carriage().forward();
        double travelDir = Math.abs(train.speed()) > 1.0E-4 ? Math.signum(train.speed()) : (rpm >= 0.0 ? 1.0 : -1.0);
        return travelDir * forward.y > 1.0E-4;
    }

    public static double speedCeilingMps() {
        return AxleGearing.speedCeiling(
            (Double)LoconauticsConfig.TRAIN_MAX_SPEED.get(), 200.0, (Boolean)LoconauticsConfig.GEARING_CAP_AT_CREATE_TOP_SPEED.get(), createTopSpeedMps()
        );
    }

    public static double gearingCeilingRatio() {
        return AxleGearing.ceilingRatio(bearingAxleCapMps(), speedCeilingMps());
    }

    public static double bodySpeedCapMps(SableTrain train) {
        return Math.min(speedCeilingMps(), Math.max(bearingAxleCapMps(), ConsistPower.peakTopBpt(train) * 20.0));
    }

    public static double gearedTopBpt(SableTrain train) {
        ConsistPower.Result power = ConsistPower.resultFor(train);
        return power == null ? bearingAxleCapBpt() : power.gearedTopBpt();
    }

    static void updateAxleRedstone(SableTrain train) {
        if (!train.isDerailed() && !train.isPhysics()) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container != null) {
                SableTrain.Car car = train.car();
                if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                    ServerLevel subLevel = sub.getLevel();

                    for (BearingAxleBlockEntity axle : SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class)) {
                        BlockPos pos = axle.getBlockPos();
                        BlockState state = subLevel.getBlockState(pos);
                        if (state.getBlock() instanceof BearingAxleBlock) {
                            Direction plusFace = BearingAxleBlock.getPlusFace(state);
                            Direction minusFace = plusFace.getOpposite();
                            axle.setRightRedstone(subLevel.getSignal(pos.relative(plusFace), plusFace) > 0);
                            axle.setLeftRedstone(subLevel.getSignal(pos.relative(minusFace), minusFace) > 0);
                            axle.setGearSignal(BearingAxleBlock.gearSignal(subLevel, pos, state));
                        }
                    }
                }
            }
        }
    }

    static void applyDynamics(SableTrain train) {
        if (!train.isDerailed() && !train.isPhysics()) {
            boolean braking = isBraking(train.targetSpeed(), train.speed());
            double limitMps2 = train.brakeHold()
                ? 60.0
                : (braking ? brakeLimit(train, train.targetSpeed(), train.speed()) : tractionAccelLimit(train, ConsistPower.tractionMass(train, 1.0)));
            train.setAccel(limitMps2 * 0.0025000000000000005);
        }
    }

    @Nullable
    public static Vec3 axleDriveHeading(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null && train.car() != null && train.car().subLevelId() != null) {
            if (container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                BearingAxleBlockEntity axle = bearingAxleIn(sub);
                Direction front = TrainSteering.axleFront(axle, sub);
                if (axle != null && front != null) {
                    double rpm = axle.isOverStressed() ? 0.0 : axle.getSpeed();
                    return Math.abs(rpm) < 0.001 ? null : new Vec3(front.getStepX(), 0.0, front.getStepZ()).scale(Math.signum(rpm));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static double axleSign(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null && train.car() != null && train.car().subLevelId() != null) {
            return container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()
                ? axleSign(train, sub, bearingAxleIn(sub))
                : train.axleSign();
        } else {
            return train.axleSign();
        }
    }

    public static double axleRpm(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null && train.car() != null && train.car().subLevelId() != null) {
            if (container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                BearingAxleBlockEntity axle = bearingAxleIn(sub);
                return axle != null && !axle.isOverStressed() ? axle.getSpeed() : 0.0;
            } else {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

    public static double axleSign(SableTrain train, ServerSubLevel sub, BearingAxleBlockEntity axle) {
        Direction front = TrainSteering.axleFront(axle, sub);
        RailCarriage rail = train.leadingBogeyRail();
        if (front == null || rail == null) {
            return train.axleSign();
        }

        if (train.isPoseSettling()) {
            return train.axleSign();
        }

        Vec3 forward = TrainRailAdvance.bodyForward(rail);
        if (forward == null) {
            return train.axleSign();
        }

        double sign = front.getStepX() * forward.x + front.getStepZ() * forward.z < 0.0 ? -1.0 : 1.0;
        train.setAxleSign(sign);
        return sign;
    }

    static void applyPropulsion(SableTrain train) {
        if (train.isDerailed()) {
            train.setPowered(false);
            train.setWheelDrive(derailedWheelDrive(train));
        } else {
            train.setWheelDrive(0.0);
            if (train.brakeHold()) {
                train.setPowered(false);
                train.setTargetSpeed(0.0);
                if (Math.abs(train.speed()) <= 0.001) {
                    train.setSpeed(0.0);
                }
            } else if (consistHeldByStaff(train)) {
                train.setPowered(false);
                train.setTargetSpeed(0.0);
            } else {
                ConsistPower.Result power = ConsistPower.resultFor(train);
                boolean driven = power != null && ConsistPower.isManual(train) && !power.axles().isEmpty();
                if (power != null && (power.anyPowered() || driven)) {
                    train.setPowered(true);
                    train.setTargetSpeed(ConsistPower.targetFor(train));
                    traceReload301(train, power, driven);
                } else {
                    train.setPowered(false);
                    train.setTargetSpeed(0.0);
                }
            }
        }
    }

    private static void traceReload301(SableTrain train, ConsistPower.Result power, boolean driven) {
        if (train.level().getGameTime() % 20L == 0L && !(Math.abs(train.targetSpeed()) < 1.0E-6)) {
            AnalogControllerBlockEntity controller = SableTrainDriver.controllerIn(train);
            LoconauticsConstants.LOGGER
                .debug(
                    "[reload301] train {} target {} speed {} scheduleDriven {} station {} stationState {} manual {} driven {} anyPowered {} controller {} power {} scheduleControlled {} ghostCruise {}",
                    new Object[]{
                        train.id().toString().substring(0, 8),
                        train.targetSpeed(),
                        train.speed(),
                        train.isScheduleDriven(),
                        train.currentStation() == null ? "none" : "set",
                        train.stationState(),
                        ConsistPower.isManual(train),
                        driven,
                        power.anyPowered(),
                        controller == null ? "none" : "present",
                        controller == null ? "n/a" : controller.getCurrentPower(),
                        controller != null && controller.isScheduleControlled(),
                        train.ghostCruise()
                    }
                );
        }
    }

    private static boolean consistHeldByStaff(SableTrain train) {
        ServerLevel level = train.level();
        if (level != null && train.car() != null) {
            for (UUID id : TrainConsist.connectedSubLevels(level, train.car().subLevelId())) {
                ServerSubLevel sub = SubLevelData.resolve(level, id);
                if (sub != null && ExternalForceDerail.staffHolds(sub)) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    public static double createTopSpeedMps() {
        return (Double)AllConfigs.server().trains.trainTopSpeed.get();
    }

    static double bearingAxleCapMps() {
        double cfg = (Double)LoconauticsConfig.BEARING_AXLE_MAX_SPEED.get();
        return cfg < 0.0 ? createTopSpeedMps() : cfg;
    }

    public static double bearingAxleCapBpt() {
        return bearingAxleCapMps() / 20.0;
    }

    static double derailedWheelDrive(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return 0.0;
        }

        SableTrain.Car car = train.car();
        if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
            BearingAxleBlockEntity axle = bearingAxleIn(sub);
            if (axle == null) {
                return 0.0;
            }

            if (!SableTrainDriver.anyBogeyAlive(sub, car)) {
                return 0.0;
            }

            double rpm = axle.isOverStressed() ? 0.0 : axle.getSpeed();
            double cap = speedCeilingMps() / 20.0;
            return Math.max(-cap, Math.min(cap, AxleGearing.gearedSpeed(bearingAxleCapBpt(), axle.gearRatio(), rpm)));
        } else {
            return 0.0;
        }
    }

    static BearingAxleBlockEntity findBearingAxle(SableTrain train, ServerSubLevelContainer container) {
        SableTrain.Car car = train.car();
        return car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()
            ? bearingAxleIn(sub)
            : null;
    }

    static double liveMass(ServerSubLevel sub) {
        return TrainConsist.subLevelMass(sub);
    }

    static BearingAxleBlockEntity bearingAxleIn(ServerSubLevel sub) {
        return BearingAxleValidity.resolve(sub);
    }

    private TrainPropulsion() {
    }
}
