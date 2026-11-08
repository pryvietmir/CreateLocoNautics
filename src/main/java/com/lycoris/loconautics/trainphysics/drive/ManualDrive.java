package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class ManualDrive {
    public static final double COUNTER_BRAKE = 2.0;
    private static final double REVERSE_DIVISOR = 4.0;
    private static final double IDLE_TARGET = 1.0E-4;
    private static final Map<AnalogControllerBlockEntity, Boolean> CURVE_TRACE = new WeakHashMap<>();

    private ManualDrive() {
    }

    static List<ManualDrive.Driver> driversIn(ServerLevel level, List<UUID> members) {
        List<ManualDrive.Driver> drivers = new ArrayList<>();

        for (UUID id : members) {
            ServerSubLevel sub = SubLevelData.resolve(level, id);
            if (sub != null && !sub.isRemoved()) {
                for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                    if ((controller.hasUser() || controller.isLocked())
                        && !controller.isScheduleControlled()
                        && LocomotiveHardware.hasTransmissionFor(sub, controller)) {
                        drivers.add(new ManualDrive.Driver(controller, sub));
                    }
                }
            }
        }

        return drivers;
    }

    static ManualDrive.Pull pull(
        List<ManualDrive.Driver> drivers, Map<UUID, SableTrain> cars, Map<UUID, Integer> orientation, boolean engineTurning, double gearedTop, double rpmTop
    ) {
        int ahead = 0;
        int back = 0;
        double aheadTarget = 0.0;
        double backTarget = 0.0;

        for (ManualDrive.Driver driver : drivers) {
            UUID id = driver.sub().getUniqueId();
            SableTrain car = cars.get(id);
            if (car != null) {
                double target = seedTarget(driver, car, orientation.getOrDefault(id, 1), engineTurning, gearedTop, rpmTop);
                if (target > 1.0E-4) {
                    ahead++;
                    aheadTarget = Math.max(aheadTarget, target);
                } else if (target < -1.0E-4) {
                    back++;
                    backTarget = Math.min(backTarget, target);
                }
            }
        }

        return ahead == back ? new ManualDrive.Pull(0.0, ahead, back) : new ManualDrive.Pull(ahead > back ? aheadTarget : backTarget, ahead, back);
    }

    static double accelerationScale(int pushers) {
        if (pushers >= 3) {
            return (Double)LoconauticsConfig.DYNAMICS_THREE_DRIVER_ACCELERATION.get();
        } else {
            return pushers == 2 ? (Double)LoconauticsConfig.DYNAMICS_TWO_DRIVER_ACCELERATION.get() : 1.0;
        }
    }

    static double seedTarget(ManualDrive.Driver driver, SableTrain car, int flip, boolean engineTurning, double gearedTop, double rpmTop) {
        AnalogControllerBlockEntity controller = driver.controller();
        int input = controller.heldInput();
        boolean onCurve = car.car().carriage() != null && car.car().carriage().isOnCurve();
        if (controller.isLocked()) {
            if (Double.isNaN(controller.cruiseSpeed()) || input != 0 && !onCurve) {
                controller.setCruiseSpeed(car.speed());
            }

            Boolean wasOnCurve = CURVE_TRACE.put(controller, onCurve);
            if (wasOnCurve == null || wasOnCurve != onCurve) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[cruisecurve] onCurve {} input {} speed {} cruise {}",
                        new Object[]{onCurve, input, String.format("%.4f", car.speed()), String.format("%.4f", controller.cruiseSpeed())}
                    );
            }

            if (input == 0) {
                double cruise = controller.cruiseSpeed() * flip;
                return onCurve ? Math.copySign(Math.min(Math.abs(cruise), curveTop(gearedTop)), cruise) : cruise;
            }
        }

        Vec3 railForward = ConsistPower.forwardOf(car);
        if (input != 0 && engineTurning && railForward != null) {
            Direction ahead = ((Direction)controller.getBlockState().getValue(AnalogControllerBlock.FACING)).getOpposite();
            Vector3d heading = new Vector3d(ahead.getStepX(), 0.0, ahead.getStepZ());
            driver.sub().logicalPose().transformNormal(heading, heading);
            double along = heading.x * railForward.x + heading.z * railForward.z;
            if (Math.abs(along) < 1.0E-6) {
                return 0.0;
            }

            double top = onCurve ? Math.min(rpmTop, curveTop(gearedTop)) : rpmTop;
            int direction = input * (along < 0.0 ? -1 : 1) * flip;
            return direction < 0 ? top / 4.0 * direction : top * direction;
        } else {
            return 0.0;
        }
    }

    static double curveTop(double gearedTop) {
        double baseline = TrainPropulsion.bearingAxleCapBpt();
        return AllConfigs.server().trains.trainTurningTopSpeed.getF() / 20.0 * (baseline > 0.0 ? Math.max(1.0, gearedTop / baseline) : 1.0);
    }

    record Driver(AnalogControllerBlockEntity controller, ServerSubLevel sub) {
    }

    record Pull(double target, int ahead, int back) {
        int pushers() {
            return Math.abs(this.ahead - this.back);
        }
    }
}
