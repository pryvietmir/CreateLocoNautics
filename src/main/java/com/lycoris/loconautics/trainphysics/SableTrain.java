package com.lycoris.loconautics.trainphysics;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.compat.computercraft.CcEvents;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spacing.CarExtent;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableTrain {
    private final UUID id;
    private ServerLevel level;
    private SableTrain.Car car;
    private final boolean physics;
    private UUID owner;
    private double speed;
    private double lastRollingSpeed;
    private double targetSpeed;
    private double accel = 0.01;
    private double measuredAccel;
    private Direction steerIntent;
    private double brakeScale = 1.0;
    private double handbrake;
    private boolean powered;
    private boolean derailed;
    private String derailReason = "";
    private SableTrain.Residency residency = SableTrain.Residency.EMBODIED;
    private GlobalSavedSubLevelPointer bodyPointer;
    private ChunkPos bodyChunk;
    private Vector3d lastKnownPos;
    private final Set<UUID> consistCache = new LinkedHashSet<>();
    private boolean scheduleDriven;
    private CarExtent extent;
    private double ghostCruise;
    private double axleSign = 1.0;
    private int poseSettle;
    private Vector3d pendingKnockback;
    private final Set<BlockPos> offTrackBogeys = new HashSet<>();
    private final Map<BlockPos, Direction> bogeyFront = new HashMap<>();
    private final Map<BlockPos, Long> offTrackSince = new HashMap<>();
    private double wheelDrive;
    private final PoseTweak poseTweak = new PoseTweak();
    private int mapColorIndex;
    private String iconType = "";
    private String name = "";
    private boolean braked;
    private boolean brakeHard;
    private boolean brakeHold;
    private SableTrain.StationState stationState = SableTrain.StationState.RUNNING;
    private double distanceToStation = Double.MAX_VALUE;
    private double approachTotal;
    private int dwellTicks;
    private GlobalStation currentStation;
    private GlobalStation sightedStation;
    private double sightedDistance = Double.MAX_VALUE;
    private GlobalStation atStation;
    private Vector3dc atStationPos;
    private double stationDirection = 1.0;
    private boolean doorsOpen;
    private final Map<BlockPos, Double> drivenBogeys = new LinkedHashMap<>();
    private final Map<BlockPos, Double> wheelSurface = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> wheelStall = new LinkedHashMap<>();
    private final Set<BlockPos> slippingBogeys = new LinkedHashSet<>();
    private Vector3d lastBodyPos;
    private double slipFactor = 1.0;
    private boolean servoBrake;
    private boolean following;
    public static final int BRAKE_PULSE_TICKS = 10;
    private int brakePulse;
    private boolean scheduleHold;

    public SableTrain(UUID id, ServerLevel level, SableTrain.Car car, boolean physics) {
        this.id = id;
        this.level = level;
        this.car = car;
        this.physics = physics;
    }

    public boolean isPhysics() {
        return this.physics;
    }

    public UUID id() {
        return this.id;
    }

    public ServerLevel level() {
        return this.level;
    }

    public void setLevel(ServerLevel newLevel) {
        this.level = newLevel;
    }

    public SableTrain.Car car() {
        return this.car;
    }

    public void relocate(RailCarriage newCarriage, List<SableTrain.Bogey> newBogeys) {
        this.relocate(newCarriage, newBogeys, this.car.localLead(), this.car.localTrail());
    }

    public void relocate(SableTrain.Car rebuilt) {
        this.relocate(
            rebuilt.carriage(),
            rebuilt.bogeys(),
            rebuilt.localLead() != null ? rebuilt.localLead() : this.car.localLead(),
            rebuilt.localTrail() != null ? rebuilt.localTrail() : this.car.localTrail()
        );
    }

    private void relocate(RailCarriage newCarriage, List<SableTrain.Bogey> newBogeys, Vector3dc lead, Vector3dc trail) {
        this.traceBogeyDrop("relocate", newBogeys.size());
        this.car = new SableTrain.Car(this.car.subLevelId(), newCarriage, newBogeys, lead, trail);
        this.offTrackBogeys.removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        this.offTrackSince.keySet().removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        this.speed = 0.0;
        this.targetSpeed = 0.0;
        this.stationState = SableTrain.StationState.RUNNING;
        this.distanceToStation = Double.MAX_VALUE;
        this.dwellTicks = 0;
        this.currentStation = null;
    }

    public void keepStop(SableTrain.StationState state, GlobalStation station, double distance, int dwell) {
        this.stationState = state;
        this.currentStation = station;
        this.distanceToStation = distance;
        this.dwellTicks = dwell;
    }

    private void traceBogeyDrop(String how, int newCount) {
        if (this.car != null && newCount < this.car.bogeys().size()) {
            StringBuilder callers = new StringBuilder();
            StackWalker.getInstance()
                .walk(
                    frames -> frames.skip(2L)
                        .limit(5L)
                        .map(f -> f.getClassName().substring(f.getClassName().lastIndexOf(46) + 1) + "." + f.getMethodName() + ":" + f.getLineNumber())
                        .toList()
                )
                .forEach(s -> callers.append(s).append(" < "));
        }
    }

    public void replaceCar(SableTrain.Car newCar) {
        this.traceBogeyDrop("replaceCar", newCar.bogeys().size());
        this.car = newCar;
    }

    public void setBogeys(List<SableTrain.Bogey> newBogeys) {
        this.traceBogeyDrop("setBogeys", newBogeys.size());
        this.car = new SableTrain.Car(this.car.subLevelId(), this.car.carriage(), newBogeys, this.car.localLead(), this.car.localTrail());
        this.offTrackBogeys.removeIf(pos -> !hasBogeyAt(newBogeys, pos));
        this.bogeyFront.keySet().removeIf(pos -> !hasBogeyAt(newBogeys, pos));
    }

    private static boolean hasBogeyAt(List<SableTrain.Bogey> bogeys, BlockPos localPos) {
        for (SableTrain.Bogey bogey : bogeys) {
            if (bogey.localPos().equals(localPos)) {
                return true;
            }
        }

        return false;
    }

    public RailCarriage leadingBogeyRail() {
        if (this.car != null && !this.car.bogeys().isEmpty()) {
            for (int i = this.car.bogeys().size() - 1; i >= 0; i--) {
                SableTrain.Bogey bogey = this.car.bogeys().get(i);
                if (!this.offTrackBogeys.contains(bogey.localPos())) {
                    return bogey.rail();
                }
            }

            return this.car.bogeys().get(this.car.bogeys().size() - 1).rail();
        } else {
            return null;
        }
    }

    public UUID owner() {
        return this.owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double lastRollingSpeed() {
        return this.lastRollingSpeed;
    }

    public void setLastRollingSpeed(double lastRollingSpeed) {
        this.lastRollingSpeed = lastRollingSpeed;
    }

    public void setTargetSpeed(double targetSpeed) {
        this.targetSpeed = targetSpeed;
    }

    public double targetSpeed() {
        return this.targetSpeed;
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public boolean doorsOpen() {
        return this.doorsOpen;
    }

    public void setDoorsOpen(boolean doorsOpen) {
        this.doorsOpen = doorsOpen;
    }

    public boolean isDerailed() {
        return this.derailed;
    }

    public boolean isTrackless() {
        return this.car != null && this.car.carriage() == null;
    }

    public void setDerailed(boolean derailed) {
        this.setDerailed(derailed, "track_missing");
    }

    public void setDerailed(boolean derailed, String statusKey) {
        if (this.setDerailedQuiet(derailed)) {
            this.derailReason = derailed ? statusKey : "";
            if (derailed) {
                TrainStatusChat.derailed(this, statusKey);
            } else {
                TrainStatusChat.backOnTrack(this);
            }

            CcEvents.queue(this, derailed ? "train_derailed" : "train_rerailed");
        }
    }

    public void setDerailReason(String reason) {
        this.derailReason = reason;
    }

    public boolean derailedAtTrackEnd() {
        return "end_of_track".equals(this.derailReason);
    }

    public boolean setDerailedQuiet(boolean derailed) {
        if (this.derailed == derailed) {
            return false;
        }

        this.derailed = derailed;
        if (!derailed) {
            this.offTrackBogeys.clear();
        }

        SableTrainPersistence.persist(this);
        return true;
    }

    public void queueKnockback(Vector3d worldDeltaVelocity) {
        this.pendingKnockback = worldDeltaVelocity;
    }

    public Vector3d takeKnockback() {
        Vector3d v = this.pendingKnockback;
        this.pendingKnockback = null;
        return v;
    }

    public boolean isBogeyOffTrack(BlockPos localPos) {
        return this.offTrackBogeys.contains(localPos);
    }

    public boolean markBogeyOffTrack(BlockPos localPos) {
        if (this.level != null) {
            this.offTrackSince.put(localPos, this.level.getGameTime());
        }

        return this.offTrackBogeys.add(localPos);
    }

    public void markBogeyAwaitingRail(BlockPos localPos) {
        this.offTrackSince.remove(localPos);
        this.offTrackBogeys.add(localPos);
    }

    public boolean bogeyRerailReady(BlockPos localPos, int cooldownTicks) {
        Long since = this.offTrackSince.get(localPos);
        return since == null || this.level == null || this.level.getGameTime() - since >= cooldownTicks;
    }

    public void clearOffTrackBogeys() {
        this.offTrackBogeys.clear();
    }

    public boolean clearOffTrackBogey(BlockPos localPos) {
        return this.offTrackBogeys.remove(localPos);
    }

    public Direction bogeyFrontOverride(BlockPos localPos) {
        return this.bogeyFront.get(localPos);
    }

    public void setBogeyFrontOverride(BlockPos localPos, Direction front) {
        if (front == null) {
            this.bogeyFront.remove(localPos);
        } else {
            this.bogeyFront.put(localPos, front);
        }

        SableTrainPersistence.persist(this);
    }

    public void restoreBogeyFrontOverrides(Map<BlockPos, Direction> fronts) {
        this.bogeyFront.clear();
        this.bogeyFront.putAll(fronts);
    }

    public Map<BlockPos, Direction> bogeyFrontOverrides() {
        return Collections.unmodifiableMap(this.bogeyFront);
    }

    public int offTrackBogeyCount() {
        return this.offTrackBogeys.size();
    }

    public boolean hasOffTrackBogey() {
        return !this.offTrackBogeys.isEmpty();
    }

    public Map<BlockPos, Double> wheelSurface() {
        return this.wheelSurface;
    }

    public Map<BlockPos, Integer> wheelStall() {
        return this.wheelStall;
    }

    public Set<BlockPos> slippingBogeys() {
        return this.slippingBogeys;
    }

    public Vector3d lastBodyPos() {
        return this.lastBodyPos;
    }

    public void setLastBodyPos(Vector3d pos) {
        this.lastBodyPos = pos;
    }

    public Map<BlockPos, Double> drivenBogeys() {
        return this.drivenBogeys;
    }

    public void setDrivenBogeys(Map<BlockPos, Double> driven) {
        this.drivenBogeys.clear();
        if (driven != null) {
            this.drivenBogeys.putAll(driven);
        }
    }

    public double wheelDrive() {
        return this.wheelDrive;
    }

    public void setWheelDrive(double wheelDrive) {
        this.wheelDrive = wheelDrive;
    }

    public PoseTweak poseTweak() {
        return this.poseTweak;
    }

    public String iconType() {
        return this.iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType == null ? "" : iconType;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int mapColorIndex() {
        return this.mapColorIndex;
    }

    public void setMapColorIndex(int mapColorIndex) {
        this.mapColorIndex = Mth.clamp(mapColorIndex, 0, 15);
    }

    public double slipFactor() {
        return this.slipFactor;
    }

    public void setSlipFactor(double slipFactor) {
        this.slipFactor = slipFactor;
    }

    public boolean servoBrake() {
        return this.servoBrake;
    }

    public void setServoBrake(boolean servoBrake) {
        this.servoBrake = servoBrake;
    }

    public boolean isFollowing() {
        return this.following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    public boolean isBraked() {
        return this.braked;
    }

    public void setBraked(boolean braked) {
        this.braked = braked;
    }

    public boolean brakeHold() {
        return this.brakeHold;
    }

    public void setBrakeHold(boolean hold) {
        this.brakeHold = hold;
    }

    public boolean brakeHard() {
        return this.brakeHard;
    }

    public void setBrakeHard(boolean hard) {
        this.brakeHard = hard;
    }

    public Direction steerIntent() {
        return this.steerIntent;
    }

    public void setSteerIntent(Direction intent) {
        this.steerIntent = intent;
    }

    public double brakeScale() {
        return this.brakeScale;
    }

    public double handbrake() {
        return this.handbrake;
    }

    public void setHandbrake(double handbrake) {
        this.handbrake = handbrake;
    }

    public void setBrakeScale(double scale) {
        this.brakeScale = Math.max(0.0, Math.min(1.0, scale));
    }

    public void armBrakePulse() {
        this.brakePulse = 10;
    }

    public boolean brakePulseActive() {
        return this.brakePulse > 0;
    }

    public void tickBrakePulse() {
        if (this.brakePulse > 0) {
            this.brakePulse--;
        }
    }

    public boolean scheduleHold() {
        return this.scheduleHold;
    }

    public void setScheduleHold(boolean hold) {
        if (hold != this.scheduleHold) {
            String caller = StackWalker.getInstance()
                .walk(
                    frames -> frames.skip(1L)
                        .findFirst()
                        .map(
                            frame -> frame.getClassName().substring(frame.getClassName().lastIndexOf(46) + 1)
                                + "."
                                + frame.getMethodName()
                                + ":"
                                + frame.getLineNumber()
                        )
                        .orElse("?")
                );
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] hold {} -> {} on {} speed {} by {}",
                    new Object[]{this.scheduleHold, hold, this.id().toString().substring(0, 8), String.format("%.3f", this.speed()), caller}
                );
        }

        this.scheduleHold = hold;
    }

    public SableTrain.StationState stationState() {
        return this.stationState;
    }

    public void setStationState(SableTrain.StationState state) {
        SableTrain.StationState previous = this.stationState;
        this.stationState = state;
        if (previous != state) {
            if (state != SableTrain.StationState.RUNNING) {
                this.setSighted(null, Double.MAX_VALUE);
            }

            if (state == SableTrain.StationState.STOPPED) {
                CcEvents.queue(this, "train_arrived", this.stationLabel());
            } else if (previous == SableTrain.StationState.STOPPED) {
                CcEvents.queue(this, "train_departed", this.stationLabel());
            }
        }
    }

    private String stationLabel() {
        GlobalStation station = this.stoppedAt();
        return station == null ? "" : station.name;
    }

    public boolean isAtStation() {
        return this.stationState == SableTrain.StationState.STOPPING || this.stationState == SableTrain.StationState.STOPPED;
    }

    public double distanceToStation() {
        return this.distanceToStation;
    }

    public void setDistanceToStation(double distance) {
        this.distanceToStation = distance;
    }

    public GlobalStation sightedStation() {
        return this.sightedStation;
    }

    public double sightedDistance() {
        return this.sightedDistance;
    }

    public void setSighted(GlobalStation station, double distance) {
        this.sightedStation = station;
        this.sightedDistance = station == null ? Double.MAX_VALUE : distance;
    }

    public double approachTotal() {
        return this.approachTotal;
    }

    public void setApproachTotal(double total) {
        this.approachTotal = total;
    }

    public int dwellTicks() {
        return this.dwellTicks;
    }

    public void setDwellTicks(int ticks) {
        this.dwellTicks = ticks;
    }

    public GlobalStation currentStation() {
        return this.currentStation;
    }

    public void setCurrentStation(GlobalStation station) {
        this.currentStation = station;
    }

    public GlobalStation stoppedAt() {
        return this.currentStation != null ? this.currentStation : this.atStation;
    }

    public GlobalStation atStation() {
        return this.atStation;
    }

    public void setAtStation(GlobalStation station) {
        this.atStation = station;
        if (station == null) {
            this.atStationPos = null;
        }
    }

    public Vector3dc atStationPos() {
        return this.atStationPos;
    }

    public void setAtStationPos(Vector3dc pos) {
        this.atStationPos = pos;
    }

    public double stationDirection() {
        return this.stationDirection;
    }

    public void setStationDirection(double direction) {
        this.stationDirection = direction;
    }

    public void setAccel(double accel) {
        this.accel = Math.abs(accel);
    }

    public double accel() {
        return this.accel;
    }

    public double measuredAccel() {
        return this.measuredAccel;
    }

    public SableTrain.Residency residency() {
        return this.residency;
    }

    public boolean isGhost() {
        return this.residency == SableTrain.Residency.GHOST;
    }

    public void setResidency(SableTrain.Residency residency) {
        if (this.residency != residency) {
            this.brakePulse = 0;
        }

        this.residency = residency;
    }

    public GlobalSavedSubLevelPointer bodyPointer() {
        return this.bodyPointer;
    }

    public void setBodyPointer(GlobalSavedSubLevelPointer pointer) {
        this.bodyPointer = pointer;
    }

    public ChunkPos bodyChunk() {
        return this.bodyChunk;
    }

    public void setBodyChunk(ChunkPos chunk) {
        this.bodyChunk = chunk;
    }

    public Vector3dc lastKnownPos() {
        return this.lastKnownPos;
    }

    public void setLastKnownPos(Vector3dc pos) {
        this.lastKnownPos = pos == null ? null : new Vector3d(pos);
    }

    public Set<UUID> consistCache() {
        return Collections.unmodifiableSet(this.consistCache);
    }

    public void setConsistCache(Collection<UUID> ids) {
        this.consistCache.clear();
        if (ids != null) {
            this.consistCache.addAll(ids);
        }
    }

    public CarExtent extent() {
        return this.extent;
    }

    public void setExtent(CarExtent extent) {
        this.extent = extent;
    }

    public boolean isScheduleDriven() {
        return this.scheduleDriven;
    }

    public double ghostCruise() {
        return this.ghostCruise;
    }

    public void setGhostCruise(double cruise) {
        this.ghostCruise = Math.abs(cruise);
    }

    public double axleSign() {
        return this.axleSign;
    }

    public void setAxleSign(double sign) {
        this.axleSign = sign < 0.0 ? -1.0 : 1.0;
    }

    public boolean isPoseSettling() {
        return this.poseSettle > 0;
    }

    public void beginPoseSettle(int ticks) {
        this.poseSettle = ticks;
    }

    public void tickPoseSettle() {
        if (this.poseSettle > 0) {
            this.poseSettle--;
        }
    }

    public void setScheduleDriven(boolean scheduleDriven) {
        this.scheduleDriven = scheduleDriven;
    }

    public void tickMotion() {
        double before = this.speed;
        if (this.speed < this.targetSpeed) {
            this.speed = Math.min(this.targetSpeed, this.speed + this.accel);
        } else if (this.speed > this.targetSpeed) {
            this.speed = Math.max(this.targetSpeed, this.speed - this.accel);
        }

        this.measuredAccel = this.speed - before;
    }

    public record Bogey(BlockPos localPos, RailCarriage rail) {
    }

    public record Car(UUID subLevelId, RailCarriage carriage, List<SableTrain.Bogey> bogeys, Vector3dc localLead, Vector3dc localTrail) {
        public List<SableTrain.Bogey> chain() {
            List<SableTrain.Bogey> railed = this.bogeys.stream().filter(b -> b.rail() != null).toList();
            List<SableTrain.Bogey> chain = railed.stream().filter(b -> !b.rail().isIndependent()).toList();
            return chain.isEmpty() ? railed : chain;
        }
    }

    public enum Residency {
        EMBODIED,
        GHOST;
    }

    public enum StationState {
        RUNNING,
        STOPPING,
        STOPPED,
        DEPARTING;
    }
}
