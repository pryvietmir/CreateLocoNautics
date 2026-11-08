package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.schedule.LocomotiveHardware;
import com.lycoris.loconautics.content.schedule.RouteDistance;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.door.TrainDoorsAccessor;
import com.lycoris.loconautics.trainphysics.drive.AxleGearing;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainSteeringAccessor;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.Couple;

public final class CcControl {
    public static final String PARKED_EVENT = "train_parked";
    public static final String STOPPED_EVENT = "train_stopped";
    private static final Map<UUID, CcControl> ACTIVE = new HashMap<>();
    private static int lastParkId;
    private static int lastStopId;
    private static final int DRIVETRAIN_RECHECK_TICKS = 20;
    private static final int DRIVE_MIN_TICKS = 5;
    private static final float RPM_STEP = 8.0F;
    private final UUID trainId;
    private Double targetSpeed;
    private Double accel;
    private Double brakeScale;
    private Integer steer;
    private Boolean doors;
    private GlobalStation parkStation;
    private TrackGraphLocation parkLocation;
    private List<Couple<TrackNode>> parkRoute;
    private int parkDirection;
    private int terminalSense;
    private int brakeSense;
    private int parkId;
    private boolean stopping;
    private boolean emergencyStop;
    private double stopRemaining = Double.NaN;
    private int stopSense;
    private int stopId;
    private boolean drivetrainOk;
    private LocomotiveHardware.Drivetrain drivetrain;
    private long drivetrainCheckedAt = -4611686018427387904L;
    private long driveSetAt = -4611686018427387904L;

    private CcControl(UUID trainId) {
        this.trainId = trainId;
    }

    public static CcControl claim(UUID trainId) {
        return ACTIVE.computeIfAbsent(trainId, CcControl::new);
    }

    public static CcControl claimClean(UUID trainId) {
        CcControl fresh = new CcControl(trainId);
        CcControl replaced = ACTIVE.put(trainId, fresh);
        if (replaced != null) {
            replaced.abandonPark();
            replaced.clearTransmission();
        }

        return fresh;
    }

    public static CcControl get(UUID trainId) {
        return ACTIVE.get(trainId);
    }

    public static boolean held(UUID trainId) {
        return ACTIVE.containsKey(trainId);
    }

    public static void release(UUID trainId) {
        CcControl released = ACTIVE.remove(trainId);
        if (released != null) {
            LoconauticsConstants.LOGGER.debug("[ccsteer] dropped control of {} steer={}", trainId, released.steer);
            released.abandonPark();
            released.clearTransmission();
        }
    }

    public static void retain(Set<UUID> live) {
        ACTIVE.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) {
                return false;
            }

            entry.getValue().clearTransmission();
            return true;
        });
    }

    public static boolean isDriving(UUID trainId) {
        CcControl control = ACTIVE.get(trainId);
        return control == null ? false : control.isParking() || control.targetSpeed != null && Math.abs(control.targetSpeed) > 1.0E-6;
    }

    public static boolean holdsDoors(UUID trainId) {
        CcControl control = ACTIVE.get(trainId);
        return control != null && control.doors != null;
    }

    public void setTargetSpeed(double speed) {
        this.targetSpeed = speed;
        this.abandonPark();
        this.abandonStop();
    }

    private void abandonPark() {
        if (this.isParking()) {
            this.parkStation = null;
            this.parkLocation = null;
            CcEvents.queue(this.trainId, "train_parked", false, this.parkId);
        }
    }

    public int stopAt(SableTrain train, boolean emergency, double distance) {
        this.abandonPark();
        this.abandonStop();
        this.stopId = ++lastStopId;
        this.stopping = true;
        this.emergencyStop = emergency;
        this.stopRemaining = distance;
        double speed = train.speed();
        this.stopSense = Math.abs(speed) > 1.0E-4 ? (int)Math.signum(speed) : (int)Math.signum(train.targetSpeed());
        this.brakeScale = emergency ? 1.0 : null;
        return this.stopId;
    }

    public boolean isStopping() {
        return this.stopping;
    }

    private void abandonStop() {
        if (this.stopping) {
            this.stopping = false;
            CcEvents.queue(this.trainId, "train_stopped", this.stopId, false);
        }
    }

    private void driveStop(SableTrain train) {
        double speed = Math.abs(train.speed());
        boolean measured = !Double.isNaN(this.stopRemaining);
        if (measured) {
            this.stopRemaining -= speed;
        }

        boolean overrun = measured && this.stopRemaining <= 0.0;
        boolean settled = speed <= 0.03 && (!measured || overrun || this.stopRemaining <= 0.5);
        if (this.stopSense == 0 || settled) {
            this.stopping = false;
            this.targetSpeed = 0.0;
            this.brakeScale = 1.0;
            StationLanding.applySnap(train, 0.0);
            CcEvents.queue(train, "train_stopped", this.stopId, true);
        } else if (!this.emergencyStop && !overrun) {
            this.brakeScale = null;
            this.targetSpeed = measured ? this.stopSense * StationLanding.approachSpeed(train, this.stopRemaining) : 0.0;
            train.setServoBrake(true);
        } else {
            this.targetSpeed = 0.0;
            this.brakeScale = 1.0;
        }
    }

    public void setAccel(Double accel) {
        this.accel = accel;
    }

    public void setBrakeScale(Double scale) {
        this.brakeScale = scale;
    }

    public void setSteer(Integer steer) {
        this.steer = steer;
    }

    public Integer steer() {
        return this.steer;
    }

    public void setDoors(Boolean doors) {
        this.doors = doors;
    }

    public int parkAt(SchedulePath path, RailCarriage nose) {
        this.parkId = ++lastParkId;
        this.parkStation = path.station();
        this.parkLocation = path.target();
        this.parkRoute = path.route();
        this.parkDirection = path.direction();
        this.terminalSense = 0;
        this.brakeSense = 0;
        this.brakeScale = null;
        if (nose != null && path.followsRoute()) {
            nose.setPath(path.route());
        }

        return this.parkId;
    }

    public boolean isParking() {
        return this.parkLocation != null;
    }

    public static void apply(SableTrain train) {
        CcControl control = ACTIVE.get(train.id());
        if (control != null) {
            if (control.stopping) {
                control.driveStop(train);
            }

            boolean held = train.brakeHold();
            boolean driven = control.drivetrainWorks(train);
            if (control.isParking() && !held) {
                if (driven) {
                    control.drivePark(train);
                } else {
                    control.stop(train);
                }
            }

            if (control.targetSpeed != null && !held) {
                double desired = driven ? control.targetSpeed : 0.0;
                if (driven) {
                    control.driveTransmission(train, desired);
                }

                train.setTargetSpeed(capped(train.targetSpeed(), desired));
            }

            if (control.accel != null && !held && driven) {
                train.setAccel(control.accel);
            }

            if (control.brakeScale != null) {
                train.setBrakeScale(control.brakeScale);
                train.setBraked(control.brakeScale > 0.0);
            }

            if (control.doors != null) {
                TrainDoorsAccessor.setDoors(train, control.doors);
            }

            if (control.steer != null) {
                TrainSteeringAccessor.steer(train, control.steer);
            }
        }
    }

    private boolean drivetrainWorks(SableTrain train) {
        long now = train.level().getGameTime();
        if (now - this.drivetrainCheckedAt >= 20L) {
            this.drivetrainCheckedAt = now;
            ServerSubLevel sub = SubLevelData.resolve(train.level(), train.car().subLevelId());
            this.drivetrain = sub == null ? null : LocomotiveHardware.drivetrain(sub);
            this.drivetrainOk = this.drivetrain != null && this.drivetrain.ok();
            if (!this.drivetrainOk) {
                this.clearTransmission();
                if (this.targetSpeed != null) {
                    this.targetSpeed = 0.0;
                }
            }
        }

        return this.drivetrainOk;
    }

    private void clearTransmission() {
        if (this.drivetrain != null && this.drivetrain.transmission() != null) {
            this.drivetrain.transmission().clearComputerDrive();
        }
    }

    private void driveTransmission(SableTrain train, double desired) {
        TransmissionBlockEntity transmission = this.drivetrain.transmission();
        double input = transmission.getSpeed();
        boolean running = transmission.isComputerDriven();
        float rpm = 0.0F;
        boolean reversed = running && transmission.isComputerReversed();
        if (desired != 0.0) {
            double perRpm = AxleGearing.gearedSpeed(TrainPropulsion.bearingAxleCapBpt(), this.drivetrain.axle().gearRatio(), 1.0);
            if (Math.abs(input) < 0.001 || perRpm <= 1.0E-9) {
                return;
            }

            rpm = (float)Math.min(256.0, Math.abs(desired) / perRpm);
            double outSign = Math.signum(desired) * TrainPropulsion.axleSign(train);
            reversed = outSign * Math.signum(input) < 0.0;
        }

        if (running) {
            boolean sameStep = Math.abs(rpm - transmission.getComputerRpm()) < 8.0F && rpm == 0.0F == (transmission.getComputerRpm() == 0.0F);
            if (sameStep && reversed == transmission.isComputerReversed() || train.level().getGameTime() - this.driveSetAt < 5L) {
                return;
            }
        }

        this.driveSetAt = train.level().getGameTime();
        transmission.setComputerDrive(rpm, reversed);
    }

    private static double capped(double physics, double desired) {
        return desired != 0.0 && Math.signum(physics) == Math.signum(desired) ? Math.signum(desired) * Math.min(Math.abs(physics), Math.abs(desired)) : 0.0;
    }

    private void drivePark(SableTrain train) {
        RailCarriage carriage = TrainStations.noseRail(train);
        TrackGraph graph = carriage == null ? null : carriage.graph();
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (graph != null && nose != null && nose.edge != null) {
            double onEdge = RouteDistance.signedOnEdge(nose, this.parkLocation);
            double remaining = RouteDistance.remaining(nose, graph, this.parkRoute, this.parkLocation);
            if (Double.isNaN(onEdge) && remaining == -1.0) {
                this.stop(train);
            } else {
                double error = remaining + StationLanding.parkOffset;
                if (!Double.isNaN(onEdge)) {
                    if (this.terminalSense == 0) {
                        this.terminalSense = onEdge >= 0.0 ? 1 : -1;
                    }

                    error = this.terminalSense * onEdge + StationLanding.parkOffset;
                }

                double gap = Math.abs(error);
                if (this.brakeSense == 0) {
                    this.brakeSense = error >= 0.0 ? 1 : -1;
                }

                if (StationLanding.canSnap(train, gap) || StationLanding.wentPast(error, this.brakeSense)) {
                    this.arrive(train, this.parkDirection * error, true);
                } else if (this.brakeSense < 0) {
                    this.arrive(train, 0.0, false);
                } else {
                    this.targetSpeed = this.parkDirection * ConsistNose.frameFlip(train) * StationLanding.approachSpeed(train, gap);
                    train.setServoBrake(true);
                }
            }
        } else {
            this.stop(train);
        }
    }

    private void arrive(SableTrain train, double signedGap, boolean onTarget) {
        GlobalStation station = this.parkStation;
        double direction = this.parkDirection;
        this.land(train, signedGap, onTarget);
        if (station != null) {
            train.setCurrentStation(station);
            train.setStationDirection(direction);
            train.setDistanceToStation(0.0);
            train.setStationState(SableTrain.StationState.STOPPED);
            train.setAtStation(station);
        }
    }

    private void land(SableTrain train, double signedGap, boolean onTarget) {
        this.parkStation = null;
        this.parkLocation = null;
        RailCarriage nose = TrainStations.noseRail(train);
        if (this.parkRoute != null && nose != null) {
            nose.clearPath();
        }

        this.parkRoute = null;
        this.targetSpeed = 0.0;
        this.brakeScale = 1.0;
        if (Double.isNaN(signedGap)) {
            StationLanding.applySnap(train, 0.0);
        } else {
            StationLanding.snapConsist(train, signedGap);
        }

        CcEvents.queue(train, "train_parked", onTarget, this.parkId);
    }

    private void stop(SableTrain train) {
        this.land(train, Double.NaN, false);
    }
}
