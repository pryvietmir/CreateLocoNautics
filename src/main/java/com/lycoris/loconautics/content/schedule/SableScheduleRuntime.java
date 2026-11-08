package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.condition.ScheduleWaitCondition;
import com.lycoris.loconautics.content.schedule.destination.CoupleInstruction;
import com.lycoris.loconautics.content.schedule.destination.DestinationInstruction;
import com.lycoris.loconautics.content.schedule.destination.ScheduleInstruction;
import com.lycoris.loconautics.content.schedule.destination.WaypointInstruction;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.lycoris.loconautics.content.trainmail.PostalPlan;
import com.lycoris.loconautics.content.trainmail.PostalState;
import com.lycoris.loconautics.content.trainmail.SablePostalWorld;
import com.lycoris.loconautics.content.trainmail.TrainMailTransfer;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.signal.SignalSnap;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class SableScheduleRuntime implements PostalState {
    private static final double ARRIVE_RADIUS = 3.0;
    private static final double ENTRY_CLEARANCE = 3.0;
    private static final double BRAKE_MIN_RANGE = 4.0;
    private static final double CHIME_ARM_RANGE = 100.0;
    private static final double CHIME_RANGE = 60.0;
    private static final int MAX_IDLE_TURNS = 3;
    private static final int MAX_LOST_MEASURES = 3;
    private static final int REPLAN_TICKS = 20;
    private static final int POSTAL_CHECK_TICKS = 10;
    private static final int COOLDOWN = 40;
    private static final int WAIT_POLL = 10;
    private static final int CONDITION_SAVE_TICKS = 20;
    private static final int LEVER_PROBE_SETTLE = 3;
    private static final int LEVER_PROBE_GIVE_UP = 60;
    private static final double LEVER_PROBE_MAX_SPEED = 0.01;
    private static final Map<UUID, SableScheduleRuntime> RUNTIMES = new ConcurrentHashMap<>();
    public Schedule schedule;
    public int currentEntry;
    public SableScheduleRuntime.State state = SableScheduleRuntime.State.PRE_TRANSIT;
    public boolean paused;
    public boolean completed;
    private final List<Integer> conditionProgress = new ArrayList<>();
    private final List<CompoundTag> conditionContext = new ArrayList<>();
    private final ScheduleAnnouncer announcer = new ScheduleAnnouncer(this);
    private SableTrain train;
    private AnalogControllerBlockEntity controller;
    private int powerLimit = -1;
    private SchedulePath path;
    private boolean parked;
    private int terminalSense;
    private int brakeSense;
    private boolean leftStartEdge;
    private boolean tailLanding;
    private boolean signalHeld;
    private boolean signalParked;
    private String lastJunction;
    private String lastStatus;
    private double lastRemaining = -1.0;
    private int replanCounter;
    private int postalCheckCounter;
    private boolean quietReplan;
    private int leverSign = 1;
    private boolean leverKnown;
    private int leverProbeTicks;
    private int maxNotches;
    private int cooldown;
    private boolean needsReplan;
    private boolean turntableAligning;
    private int idleTurns;
    private int lostMeasures;
    private boolean released;
    private String heldReason;
    private static final int NO_ROTATION_GRACE_TICKS = 40;
    private static final int NO_DRIVER_GRACE_TICKS = 300;
    private static final String NO_DRIVER = "no_driver";
    private int transientTicks;
    private String transientReason;
    private UUID departedFrom;
    private boolean chimeArmed;
    private boolean postalTripped;
    private boolean postalRepeat;
    private boolean postalDeposit;
    private final Set<UUID> postalWarned = new HashSet<>();
    private boolean waitingHere;
    private int conditionSaveCounter;
    private boolean traceNow;
    private boolean maneuver;
    private int maneuverPercent = 10;
    private final Set<UUID> former = new HashSet<>();
    private final Set<UUID> berthed = new HashSet<>();
    @Nullable
    private ManeuverPlan maneuverPlan;
    private boolean deadEndNotified;
    @Nullable
    private Component maneuverWhy;
    private boolean creeping;
    private boolean reversalUsed;
    @Nullable
    private ArrivalChime chime;
    private static final Map<UUID, Long> ISSUE301_LAST = new HashMap<>();

    private static int graceTicks(String reason) {
        return switch (reason) {
            case "no_rotation" -> 40;
            case "no_driver" -> 300;
            default -> 0;
        };
    }

    public static SableScheduleRuntime assign(UUID trainId, Schedule schedule) {
        if (trainId != null && schedule != null && !schedule.entries.isEmpty()) {
            SableScheduleRuntime runtime = new SableScheduleRuntime();
            runtime.schedule = schedule;
            runtime.currentEntry = Mth.clamp(schedule.savedProgress, 0, schedule.entries.size() - 1);
            runtime.state = SableScheduleRuntime.State.PRE_TRANSIT;
            RUNTIMES.put(trainId, runtime);
            SableTrain train = SableTrainRegistry.get(trainId);
            if (train != null) {
                TrainStatusChat.newSchedule(train);
            }

            return runtime;
        } else {
            return null;
        }
    }

    @Nullable
    public static SableScheduleRuntime get(UUID trainId) {
        return RUNTIMES.get(trainId);
    }

    public static void clear(UUID trainId) {
        RUNTIMES.remove(trainId);
        ConsistNose.forget(SableTrainRegistry.get(trainId));
    }

    public static List<SableTrain> consistCars(SableTrain car) {
        List<SableTrain> mates = new ArrayList<>();

        for (UUID subLevelId : car.consistCache()) {
            SableTrain mate = SableTrainRegistry.bySubLevel(subLevelId);
            if (mate != null && mate != car) {
                mates.add(mate);
            }
        }

        mates.sort(Comparator.comparing(SableTrain::id));
        mates.add(0, car);
        return mates;
    }

    @Nullable
    public static UUID consistKey(SableTrain car) {
        List<SableTrain> cars = consistCars(car);

        for (SableTrain mate : cars) {
            if (RUNTIMES.containsKey(mate.id())) {
                return mate.id();
            }
        }

        ScheduleProgressStore store = storeOf(car);
        if (store == null) {
            return null;
        }

        for (SableTrain mate : cars) {
            if (store.hasSchedule(mate.id())) {
                return mate.id();
            }
        }

        return null;
    }

    public static UUID consistKey(UUID trainId) {
        SableTrain car = SableTrainRegistry.get(trainId);
        UUID key = car == null ? null : consistKey(car);
        return key == null ? trainId : key;
    }

    @Nullable
    public static SableScheduleRuntime forConsist(SableTrain car) {
        for (SableTrain mate : consistCars(car)) {
            SableScheduleRuntime runtime = RUNTIMES.get(mate.id());
            if (runtime != null) {
                return runtime;
            }
        }

        return null;
    }

    @Nullable
    private static ScheduleProgressStore storeOf(SableTrain car) {
        return car.level() != null && car.level().getServer() != null ? ScheduleProgressStore.get(car.level().getServer()) : null;
    }

    public boolean heldAtSignal() {
        return this.signalHeld && this.drivesTrain();
    }

    public boolean drivesTrain() {
        return !this.paused && !this.completed && this.schedule != null;
    }

    public boolean maneuver() {
        return this.maneuver;
    }

    public Set<UUID> former() {
        return Collections.unmodifiableSet(this.former);
    }

    public Set<UUID> maneuverFriends() {
        Set<UUID> friends = new HashSet<>(this.former);
        friends.addAll(this.berthed);
        return friends;
    }

    void recordBerth(@Nullable SableTrain parked) {
        this.berthed.clear();
        if (parked != null) {
            this.berthed.add(parked.id());
        }
    }

    public void startManeuver(Set<UUID> former, int percent) {
        this.maneuver = true;
        this.maneuverPercent = Math.clamp(percent, 1, 75);
        Set<UUID> kept = former == null ? Set.of() : Set.copyOf(former);
        this.former.clear();
        this.former.addAll(kept);
        this.maneuverPlan = null;
        this.deadEndNotified = false;
        this.reversalUsed = false;
        LoconauticsConstants.LOGGER.debug("[maneuver] {} start, {} cars left behind", this.trainTag(), this.former.size());
        this.announcer.line(Component.literal("Maneuver started, " + this.former.size() + " cars left behind"));
        if (this.train != null) {
            TrainStatusChat.maneuverStarted(this.train);
        }

        this.saveManeuver();
    }

    public void endManeuver() {
        if (this.maneuver) {
            LoconauticsConstants.LOGGER.debug("[maneuver] {} end, {} cars still left behind", this.trainTag(), this.former.size());
            this.announcer.line(Component.literal("Maneuver done"));
            if (this.train != null) {
                TrainStatusChat.maneuverEnded(this.train);
            }
        }

        this.maneuver = false;
        this.maneuverPlan = null;
        this.maneuverWhy = null;
        this.berthed.clear();
        this.deadEndNotified = false;
        this.reversalUsed = false;
        this.saveManeuver();
    }

    public void clearFormer() {
        this.former.clear();
        this.saveManeuver();
    }

    public void rememberFormer(Set<UUID> ids) {
        if (ids != null) {
            this.former.addAll(ids);
        }

        this.saveManeuver();
    }

    private void saveManeuver() {
        if (this.train != null && this.train.level() != null && this.schedule != null) {
            this.sync();
        }
    }

    private String trainTag() {
        return this.train == null ? "none" : LogFormat.shortId(this.train.id());
    }

    private void traceTurn(String stage, TurntableRegistry.Exit hop, Turntable turntable) {
        if (this.train != null && this.train.level() != null && TurntableTrace.due(this.trainTag() + stage, this.train.level().getGameTime(), 20)) {
            UUID plate = hop == null ? null : hop.plate();
            TrackHostFrames.Frame frame = TrackHostFrames.frameOf(plate);
            double error = turntable != null && hop != null ? TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead()) : Double.NaN;
            TurntableTrace.log(
                this.trainTag()
                    + " "
                    + stage
                    + " ghost="
                    + this.train.isGhost()
                    + " speed="
                    + String.format("%.3f", this.train.speed())
                    + " impl="
                    + (turntable == null ? "none" : turntable.getClass().getSimpleName())
                    + " powered="
                    + (turntable != null && turntable.turntablePowered())
                    + " sched="
                    + (turntable != null && turntable.turntableScheduleDriven())
                    + " aligning="
                    + this.turntableAligning
                    + " aligned="
                    + (turntable != null && turntable.turntableAligned())
                    + " settled="
                    + (turntable != null && turntable.turntableSettled())
                    + " error="
                    + String.format("%.2f", error)
                    + " frameAngle="
                    + (frame == null ? "none" : String.format("%.2f", frame.angle()))
                    + " frameLoaded="
                    + (frame != null && frame.loaded())
                    + " hostMissing="
                    + (this.train.car() != null && this.train.car().carriage().railHostMissing())
                    + " reserved="
                    + TurntableRegistry.reserved(plate)
                    + " occupied="
                    + TurntableRegistry.occupied(plate)
                    + " entry="
                    + (hop != null && hop.entry())
                    + " plate="
                    + (plate == null ? "none" : LogFormat.shortId(plate))
            );
        }
    }

    int plannedRel() {
        return this.maneuverPlan == null ? 0 : this.maneuverPlan.currentRel();
    }

    void maneuverPlanFailed(String why) {
        LoconauticsConstants.LOGGER
            .debug("[maneuver] {} stored plan {} found no route, planning again, {}", new Object[]{this.trainTag(), this.maneuverPlan, why});
        this.maneuverPlan = null;
    }

    void recordManeuverLeg(SchedulePath best, int rel) {
        if (this.maneuverPlan == null) {
            this.maneuverPlan = new ManeuverPlan();
        }

        this.maneuverPlan.record(best.hop(), rel);
        TurntableRegistry.Exit hop = best.hop();
        String exit = hop == null
            ? "no plate"
            : (
                hop.entry()
                    ? "plate entry, leg ends at the approach end"
                    : (
                        ManeuverPlan.backsOut(hop)
                            ? "plate exit, backs out the way it came in"
                            : "plate exit, spins and leaves " + (rel > 0 ? "cab" : "tail") + " end first"
                    )
            );
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} plan legs {} leg sense {} dir {} {}{}",
                new Object[]{
                    this.trainTag(),
                    this.maneuverPlan,
                    rel > 0 ? "cab first" : "backing",
                    best.direction(),
                    exit,
                    hop == null ? "" : ", heads entry " + hop.entryHead() + " moving " + hop.movingHead() + " world " + hop.worldHead()
                }
            );
    }

    boolean reversalAllowed() {
        return this.maneuver && !this.reversalUsed;
    }

    void recordReversalLeg(int rel) {
        if (this.maneuverPlan == null) {
            this.maneuverPlan = new ManeuverPlan();
        }

        this.maneuverPlan.recordReversal(rel);
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} plan legs {} leg one {} then stop and reverse",
                new Object[]{this.trainTag(), this.maneuverPlan, rel > 0 ? "cab first" : "backing"}
            );
    }

    private boolean reversalStop() {
        return this.maneuver && this.path != null && this.path.station() == null && this.path.hop() == null;
    }

    private void turnAround(AnalogControllerBlockEntity controller) {
        this.command(controller, 0);
        this.train.setTargetSpeed(0.0);
        this.train.setScheduleHold(true);
        this.clearRoute();
        this.parked = false;
        this.reversalUsed = true;
        LoconauticsConstants.LOGGER.debug("[maneuver] {} stopped at the reversal point, going back", this.trainTag());
        this.traceCycle("turnAround");
        this.announcer.line(Component.literal("Stopped clear of the switch, reversing"));
        this.advanceManeuver();
        this.terminalSense = 0;
        this.brakeSense = 0;
        this.lastRemaining = -1.0;
        this.quietReplan = true;
        this.needsReplan = true;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        this.startCooldown();
    }

    private void advanceManeuver() {
        if (this.maneuver && this.maneuverPlan != null) {
            this.maneuverPlan.advance();
            LoconauticsConstants.LOGGER.debug("[maneuver] {} plate leg done, plan now {}", this.trainTag(), this.maneuverPlan);
        }
    }

    private double maneuverScale() {
        return this.maneuver ? this.maneuverPercent / 100.0 : 1.0;
    }

    private double cappedSpeed(double mag) {
        return this.maneuver ? Math.min(mag, TrainPropulsion.gearedTopBpt(this.train) * this.maneuverScale()) : mag;
    }

    void explainManeuver(@Nullable Component why) {
        this.maneuverWhy = why;
    }

    private void maneuverDeadEnd(AnalogControllerBlockEntity controller) {
        this.train.setTargetSpeed(0.0);
        this.train.setScheduleHold(false);
        this.command(controller, 0);
        this.maneuverPlan = null;
        this.cooldown = 100;
        String name = this.currentInstruction() instanceof DestinationInstruction destination ? destination.getFilter() : "";
        LoconauticsConstants.LOGGER
            .debug("[maneuver] {} dead end to \"{}\", retry in {} ticks, told={}", new Object[]{this.trainTag(), name, this.cooldown, this.deadEndNotified});
        if (!this.deadEndNotified) {
            this.deadEndNotified = true;
            TrainStatusChat.maneuverDeadEnd(this.train, name, this.maneuverWhy);
        }
    }

    @Nullable
    public static SableTrain headingTo(GlobalStation station, SableTrain asking) {
        for (SableScheduleRuntime runtime : RUNTIMES.values()) {
            if (runtime.train != null
                && runtime.train != asking
                && !runtime.paused
                && runtime.state == SableScheduleRuntime.State.IN_TRANSIT
                && runtime.path != null
                && TrainStations.sameStation(station, runtime.path.station())) {
                return runtime.train;
            }
        }

        return null;
    }

    public static boolean resume(UUID trainId) {
        SableScheduleRuntime runtime = RUNTIMES.get(consistKey(trainId));
        if (runtime != null && runtime.paused && !runtime.completed) {
            runtime.paused = false;
            runtime.departedFrom = null;
            runtime.state = SableScheduleRuntime.State.PRE_TRANSIT;
            return true;
        } else {
            return false;
        }
    }

    public SableTrain train() {
        return this.train;
    }

    public RailCarriage leadRail() {
        List<SableTrain.Bogey> bogeys = this.train.car().bogeys();
        return bogeys.isEmpty() ? this.train.car().carriage() : bogeys.get(bogeys.size() - 1).rail();
    }

    private void setRoute(List<Couple<TrackNode>> route) {
        List<Couple<TrackNode>> shared = route == null ? null : new ArrayList<>(route);
        List<SableTrain.Bogey> bogeys = this.train.car().bogeys();
        if (!this.train.isTrackless()) {
            if (bogeys.isEmpty()) {
                this.train.car().carriage().setPath(shared);
            } else {
                for (SableTrain.Bogey bogey : bogeys) {
                    bogey.rail().setPath(shared);
                }

                this.routeCarsAhead(route);
            }
        }
    }

    private void routeCarsAhead(List<Couple<TrackNode>> route) {
        if (this.maneuver) {
            for (UUID id : TrainConsist.orderedChain(this.train.level(), this.train.car().subLevelId())) {
                SableTrain other = SableTrainRegistry.bySubLevel(id);
                if (other != null && other != this.train && other.car() != null && !other.isTrackless()) {
                    List<Couple<TrackNode>> copy = route == null ? null : new ArrayList<>(route);
                    if (other.car().bogeys().isEmpty()) {
                        other.car().carriage().setPath(copy);
                    } else {
                        for (SableTrain.Bogey bogey : other.car().bogeys()) {
                            bogey.rail().setPath(copy);
                        }
                    }
                }
            }
        }
    }

    private void clearRoute() {
        this.setRoute(null);
    }

    private ConsistNose.Nose stopEnd() {
        return this.tailLanding ? ConsistNose.tail(this.train) : ConsistNose.of(this.train);
    }

    private TravellingPoint referencePoint() {
        return this.pointOf(this.stopEnd());
    }

    private TravellingPoint pointOf(ConsistNose.Nose end) {
        TravellingPoint nose = end.point();
        return nose != null && nose.edge != null ? nose : this.leadRail().leading();
    }

    private RouteDistance.Measure measure(TrackGraphLocation loc) {
        return this.measureFrom(this.stopEnd(), loc);
    }

    private RouteDistance.Measure measureFrom(ConsistNose.Nose end, TrackGraphLocation loc) {
        if (this.path == null) {
            return RouteDistance.Measure.unknown("no path");
        }

        TravellingPoint point = this.pointOf(end);
        RailCarriage rail = end.rail();
        TrackGraph graph = RailSeams.liveGraphOf(point, rail == null ? this.leadRail().graph() : rail.graph());
        return RouteDistance.measure(point, graph, this.path.route(), loc);
    }

    private double measureRemaining(TrackGraphLocation loc) {
        return this.measure(loc).distance();
    }

    private boolean onTargetEdge(TrackGraphLocation loc) {
        return RouteDistance.onTargetEdge(this.referencePoint(), loc);
    }

    private double signedTerminalOffset(TrackGraphLocation loc) {
        return RouteDistance.signedOnEdge(this.referencePoint(), loc);
    }

    public void startCooldown() {
        this.cooldown = 40;
    }

    public void waitHere() {
        this.waitingHere = true;
        this.cooldown = 10;
    }

    private int wagonDirection() {
        Vec3 tangent = ConsistNose.noseTangent(this.train);
        Vector3d here = TrainStatusChat.bodyPosition(this.train);
        if (tangent != null && here != null) {
            double best = Double.MAX_VALUE;
            int sense = 0;

            for (UUID id : this.former) {
                SableTrain wagon = SableTrainRegistry.get(id);
                Vector3d there = wagon == null ? null : TrainStatusChat.bodyPosition(wagon);
                if (there != null && !(here.distanceSquared(there) >= best)) {
                    best = here.distanceSquared(there);
                    sense = (there.x - here.x) * tangent.x + (there.z - here.z) * tangent.z >= 0.0 ? 1 : -1;
                }
            }

            return sense * ConsistNose.frameFlip(this.train);
        } else {
            return 0;
        }
    }

    private void holdWhileWaiting(AnalogControllerBlockEntity controller) {
        int toWagons = this.maneuver && this.currentInstruction() instanceof CoupleInstruction ? this.wagonDirection() : 0;
        if (toWagons != 0) {
            this.creeping = true;
            this.released = false;
            this.train.setScheduleHold(false);
            LoconauticsConstants.LOGGER.debug("[maneuver] {} creeping toward the wagons, body direction {}", this.trainTag(), toWagons);
            this.drive(controller, toWagons);
        } else {
            this.released = false;
            this.train.setScheduleHold(true);
            this.train.setTargetSpeed(0.0);
            this.command(controller, 0);
        }
    }

    @Override
    public boolean loconautics$tripped() {
        return this.postalTripped;
    }

    @Override
    public void loconautics$setTripped(boolean tripped) {
        this.postalTripped = tripped;
        if (!tripped) {
            this.postalWarned.clear();
        }
    }

    @Override
    public void loconautics$setRepeat(boolean repeat) {
        this.postalRepeat = repeat;
    }

    @Override
    public Set<UUID> loconautics$warned() {
        return this.postalWarned;
    }

    @Override
    public boolean loconautics$repeat() {
        return this.postalRepeat;
    }

    @Override
    public boolean loconautics$deposit() {
        return this.postalDeposit;
    }

    @Override
    public void loconautics$setDeposit(boolean deposit) {
        this.postalDeposit = deposit;
    }

    @Nullable
    public ScheduleInstruction currentInstruction() {
        return this.schedule != null && this.currentEntry >= 0 && this.currentEntry < this.schedule.entries.size()
            ? this.schedule.entries.get(this.currentEntry).instruction
            : null;
    }

    public void setPowerLimit(int power) {
        this.powerLimit = power;
    }

    public int maxPower() {
        return this.controller == null ? this.maxNotches : this.controller.getMaxPower();
    }

    private int currentPower() {
        return this.controller == null ? 0 : this.controller.getCurrentPower();
    }

    public void steer(int dir) {
        if (this.controller != null) {
            this.controller.setScheduleSteer(dir);
        }
    }

    @Nullable
    public String heldReason() {
        return this.heldReason;
    }

    @Nullable
    public GlobalStation targetStation() {
        return this.path == null ? null : this.path.station();
    }

    public double remainingToTarget() {
        return this.lastRemaining;
    }

    public boolean justDeparted(GlobalStation station) {
        return this.departedFrom != null && station != null && this.departedFrom.equals(station.id);
    }

    public CompoundTag saveProgress() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", this.currentEntry);
        tag.putString("Phase", this.state.name());
        tag.putBoolean("Running", !this.paused);
        tag.putBoolean("Completed", this.completed);
        tag.putInt("Limit", this.powerLimit);
        tag.putInt("LeverSign", this.leverKnown ? this.leverSign : 0);
        if (this.departedFrom != null) {
            tag.putUUID("DepartedFrom", this.departedFrom);
        }

        tag.putBoolean("Maneuver", this.maneuver);
        tag.putInt("ManeuverPercent", this.maneuverPercent);
        if (!this.former.isEmpty()) {
            ListTag ids = new ListTag();

            for (UUID id : this.former) {
                ids.add(NbtUtils.createUUID(id));
            }

            tag.put("Former", ids);
        }

        if (this.state == SableScheduleRuntime.State.POST_TRANSIT && !this.conditionProgress.isEmpty()) {
            tag.putIntArray("ConditionProgress", this.conditionProgress);
            ListTag contexts = new ListTag();

            for (CompoundTag context : this.conditionContext) {
                contexts.add(context.copy());
            }

            tag.put("ConditionContext", contexts);
        }

        return tag;
    }

    public void restoreProgress(CompoundTag tag) {
        if (tag != null && this.schedule != null && !this.schedule.entries.isEmpty()) {
            this.currentEntry = Math.max(0, Math.min(this.schedule.entries.size() - 1, tag.getInt("Index")));
            this.state = phaseOf(tag.getString("Phase"));
            this.paused = tag.contains("Running") && !tag.getBoolean("Running");
            this.completed = tag.getBoolean("Completed");
            this.powerLimit = tag.contains("Limit") ? tag.getInt("Limit") : -1;
            int savedLever = tag.getInt("LeverSign");
            if (savedLever != 0) {
                this.leverSign = savedLever;
                this.leverKnown = true;
            }

            this.departedFrom = tag.hasUUID("DepartedFrom") ? tag.getUUID("DepartedFrom") : null;
            this.maneuver = tag.getBoolean("Maneuver");
            this.maneuverPercent = tag.contains("ManeuverPercent") ? tag.getInt("ManeuverPercent") : 10;
            this.former.clear();

            for (Tag id : tag.getList("Former", 11)) {
                this.former.add(NbtUtils.loadUUID(id));
            }

            if (this.state == SableScheduleRuntime.State.IN_TRANSIT || this.state == SableScheduleRuntime.State.TURNTABLE) {
                this.state = SableScheduleRuntime.State.PRE_TRANSIT;
            }

            if (this.state == SableScheduleRuntime.State.POST_TRANSIT) {
                boolean loaded = this.loadConditions(tag);
                if (!loaded) {
                    this.resetConditions();
                }

                ScheduleGhostTrace.event(
                    null, "restored phase POST_TRANSIT entry=" + this.currentEntry + " conditionsLoaded=" + loaded + " " + this.conditionText()
                );
            }
        }
    }

    private boolean loadConditions(CompoundTag tag) {
        List<List<ScheduleWaitCondition>> columns = this.schedule.entries.get(this.currentEntry).conditions;
        int[] saved = tag.getIntArray("ConditionProgress");
        ListTag contexts = tag.getList("ConditionContext", 10);
        if (saved.length == columns.size() && contexts.size() == columns.size()) {
            this.conditionProgress.clear();
            this.conditionContext.clear();

            for (int i = 0; i < saved.length; i++) {
                this.conditionProgress.add(Mth.clamp(saved[i], 0, columns.get(i).size()));
                this.conditionContext.add(contexts.getCompound(i));
            }

            return true;
        } else {
            return false;
        }
    }

    private void resetConditions() {
        this.conditionProgress.clear();
        this.conditionContext.clear();

        for (List<ScheduleWaitCondition> ignored : this.schedule.entries.get(this.currentEntry).conditions) {
            this.conditionProgress.add(0);
            this.conditionContext.add(new CompoundTag());
        }
    }

    private static SableScheduleRuntime.State phaseOf(String name) {
        for (SableScheduleRuntime.State phase : SableScheduleRuntime.State.values()) {
            if (phase.name().equals(name)) {
                return phase;
            }
        }

        return SableScheduleRuntime.State.PRE_TRANSIT;
    }

    public static void tick(SableTrain train, AnalogControllerBlockEntity controller) {
        if (!train.isTrackless()) {
            SableScheduleRuntime runtime = RUNTIMES.get(train.id());
            if (runtime == null) {
                runtime = rehydrate(train);
            }

            boolean keeps = runtime != null && keepsConsist(train);
            issue301(train, controller, runtime != null, keeps);
            if (keeps) {
                runtime.tickSelf(train, controller);
            } else {
                if (controller != null && controller.isScheduleControlled()) {
                    LoconauticsConstants.LOGGER.debug("[schedule] {} no runtime, releasing controller", LogFormat.shortId(train.id()));
                    issue301(train, controller, runtime != null, false);
                    controller.clearScheduleControl();
                }
            }
        }
    }

    private static void issue301(SableTrain train, AnalogControllerBlockEntity controller, boolean hasRuntime, boolean keeps) {
        boolean live = Math.abs(train.targetSpeed()) > 1.0E-6
            || Math.abs(train.speed()) > 0.001
            || controller != null && (controller.getCurrentPower() != 0 || controller.getSteerInput() != 0);
        if (train.level() != null) {
            long now = train.level().getGameTime();
            Long last = ISSUE301_LAST.get(train.id());
            if (live && (last == null || now - last >= 40L)) {
                ISSUE301_LAST.put(train.id(), now);
                LoconauticsConstants.LOGGER
                    .debug(
                        "[issue301] {} runtime={} keeps={} residency={} ctrl={} power={} locked={} user={} schedCtrl={} steer={} speed={} target={} powered={} schedDriven={} schedHold={} station={}",
                        new Object[]{
                            LogFormat.shortId(train.id()),
                            hasRuntime,
                            keeps,
                            train.residency(),
                            controller != null,
                            controller == null ? "none" : controller.getCurrentPower(),
                            controller != null && controller.isLocked(),
                            controller != null && controller.hasUser(),
                            controller != null && controller.isScheduleControlled(),
                            controller == null ? "none" : controller.getSteerInput(),
                            String.format("%.3f", train.speed()),
                            String.format("%.3f", train.targetSpeed()),
                            train.isPowered(),
                            train.isScheduleDriven(),
                            train.scheduleHold(),
                            train.stationState()
                        }
                    );
            }
        }
    }

    @Nullable
    private static SableScheduleRuntime rehydrate(SableTrain train) {
        if (train.level() != null && train.level().getServer() != null) {
            ScheduleProgressStore store = ScheduleProgressStore.get(train.level().getServer());
            CompoundTag tag = store.getSchedule(train.id());
            if (tag == null) {
                return null;
            }

            SableScheduleRuntime runtime = assign(train.id(), Schedule.fromTag(train.level().registryAccess(), tag));
            if (runtime != null) {
                runtime.restoreProgress(store.getProgress(train.id()));
            }

            return runtime;
        } else {
            return null;
        }
    }

    private static boolean keepsConsist(SableTrain train) {
        List<SableTrain> holders = new ArrayList<>();
        boolean allLoaded = true;

        for (SableTrain mate : consistCars(train)) {
            if (RUNTIMES.containsKey(mate.id())) {
                holders.add(mate);
                allLoaded &= subLevelOf(mate) != null;
            }
        }

        if (holders.size() < 2) {
            return true;
        }

        SableTrain owner = allLoaded ? TrainStations.stopOwner(train) : null;
        SableTrain keep = holders.contains(owner) ? owner : holders.stream().min(Comparator.comparing(SableTrain::id)).orElse(train);

        for (SableTrain holder : holders) {
            if (holder != keep && subLevelOf(holder) != null) {
                dropDuplicate(holder, keep);
            }
        }

        return keep == train;
    }

    private static void dropDuplicate(SableTrain holder, SableTrain keep) {
        SableScheduleRuntime dropped = RUNTIMES.remove(holder.id());
        ScheduleProgressStore store = storeOf(holder);
        CompoundTag tag = store == null ? null : store.getSchedule(holder.id());
        if (tag == null && dropped.schedule != null) {
            tag = dropped.schedule.write(holder.level().registryAccess());
        }

        if (tag != null) {
            tag.putInt("Progress", dropped.currentEntry);
        }

        if (store != null) {
            store.remove(holder.id());
        }

        if (dropped.train != null) {
            dropped.release();
        }

        ServerSubLevel sub = subLevelOf(holder);
        BlockPos seat = ScheduleReadiness.driverSeat(sub, holder.id());
        ScheduleConductor.dropSchedule(sub.getLevel(), seat != null ? seat : sub.getPlot().getCenterBlock(), tag);
        LoconauticsConstants.LOGGER
            .debug(
                "[schedshare] consist had two schedules, kept {} dropped {} as an item at {}",
                new Object[]{LogFormat.shortId(keep.id()), LogFormat.shortId(holder.id()), seat}
            );
    }

    private void tickSelf(SableTrain train, AnalogControllerBlockEntity controller) {
        this.train = train;
        this.controller = controller;
        this.traceNow = this.state == SableScheduleRuntime.State.POST_TRANSIT && ScheduleGhostTrace.due(train);
        if (this.traceNow) {
            ScheduleGhostTrace.log(train, this.stateText());
        }

        if (this.chime != null && !this.chime.tick()) {
            this.chime = null;
        }

        if (!this.paused && !this.completed && this.schedule != null && !this.schedule.entries.isEmpty()) {
            if (controller != null) {
                this.maxNotches = controller.getMaxPower();
            }

            if (playerAtControls(train)) {
                this.paused = true;
                if (controller != null) {
                    controller.clearScheduleControl();
                }

                train.setScheduleHold(false);
                this.clearRoute();
                ConsistNose.forget(train);
                TrainStatusChat.manualControls(train);
                this.sync();
            } else if (!train.isDerailed() && !train.isPhysics()) {
                if (!train.isGhost()) {
                    ScheduleReadiness.Result ready = ScheduleReadiness.check(subLevelOf(train), controller, train.id());
                    if (!ready.ok() && "no_driver".equals(ready.reasonKey()) && this.handOff()) {
                        return;
                    }

                    int grace = ready.ok() ? 0 : graceTicks(ready.reasonKey());
                    this.transientTicks = grace > 0 && ready.reasonKey().equals(this.transientReason) ? this.transientTicks + 1 : 0;
                    this.transientReason = grace > 0 ? ready.reasonKey() : null;
                    if (!ready.ok() && (grace == 0 || this.transientTicks >= grace)) {
                        this.hold(controller, ready.reasonKey());
                        return;
                    }

                    if (grace > 0) {
                        train.setTargetSpeed(0.0);
                        return;
                    }
                }

                if (this.heldReason != null) {
                    this.heldReason = null;
                    train.setScheduleHold(false);
                    TrainStatusChat.successfulNavigation(train);
                }

                if (this.cooldown > 0) {
                    this.cooldown--;
                    if (!this.released && controller != null && !this.creeping) {
                        controller.setScheduleTargetPower(0);
                    }
                } else {
                    switch (this.state) {
                        case PRE_TRANSIT:
                            this.tickPreTransit(controller);
                            break;
                        case IN_TRANSIT:
                            this.tickInTransit(controller);
                            break;
                        case TURNTABLE:
                            this.tickTurntable(controller);
                            break;
                        case POST_TRANSIT:
                            this.tickPostTransit(controller);
                    }
                }
            } else {
                this.command(controller, 0);
                if (this.state == SableScheduleRuntime.State.IN_TRANSIT) {
                    this.quietReplan = true;
                    this.state = SableScheduleRuntime.State.PRE_TRANSIT;
                }
            }
        } else {
            if (controller != null && controller.isScheduleControlled()) {
                controller.clearScheduleControl();
            }

            this.clearRoute();
        }
    }

    private static boolean playerAtControls(SableTrain host) {
        for (SableTrain mate : consistCars(host)) {
            ServerSubLevel sub = subLevelOf(mate);
            if (sub != null) {
                for (AnalogControllerBlockEntity cab : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                    if (cab.hasUser()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean handOff() {
        SableTrain next = drivableMate(this.train);
        if (next != null && RUNTIMES.remove(this.train.id(), this)) {
            RUNTIMES.put(next.id(), this);
            ScheduleProgressStore store = storeOf(this.train);
            if (store != null) {
                store.move(this.train.id(), next.id());
            }

            LoconauticsConstants.LOGGER
                .debug(
                    "[schedshare] schedule moved from {} to {} in state {}",
                    new Object[]{LogFormat.shortId(this.train.id()), LogFormat.shortId(next.id()), this.state}
                );
            if (this.controller != null) {
                this.controller.clearScheduleControl();
            }

            this.clearRoute();
            ConsistNose.forget(this.train);
            next.setScheduleHold(this.train.scheduleHold());
            this.train.setScheduleHold(false);
            this.leverSign = 1;
            this.leverKnown = false;
            this.leverProbeTicks = 0;
            this.transientTicks = 0;
            this.transientReason = null;
            if (this.state == SableScheduleRuntime.State.IN_TRANSIT || this.state == SableScheduleRuntime.State.TURNTABLE) {
                this.resumeAfterTurn();
            }

            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static SableTrain drivableMate(SableTrain host) {
        SableTrain fallback = null;

        for (SableTrain mate : consistCars(host)) {
            ServerSubLevel sub = mate == host ? null : subLevelOf(mate);
            AnalogControllerBlockEntity cab = ScheduleReadiness.drivenController(sub, mate.id());
            if (cab != null) {
                if (ScheduleReadiness.check(sub, cab, mate.id()).ok()) {
                    return mate;
                }

                if (fallback == null) {
                    fallback = mate;
                }
            }
        }

        return fallback;
    }

    private void hold(AnalogControllerBlockEntity controller, String reason) {
        if (controller != null) {
            controller.setScheduleTargetPower(0);
        }

        this.train.setTargetSpeed(0.0);
        this.train.setScheduleHold(true);
        this.needsReplan = true;
        if (!reason.equals(this.heldReason)) {
            this.heldReason = reason;
            LoconauticsConstants.LOGGER.debug("[schedule] {} holding, {}", LogFormat.shortId(this.train.id()), reason);
            TrainStatusChat.cannotContinue(this.train, LoconauticsLang.translate("schedule.hold." + reason).component());
        }
    }

    private void release() {
        this.released = true;
        this.signalHeld = false;
        this.signalParked = false;
        if (this.controller != null) {
            this.controller.clearScheduleControl();
        }

        this.train.setScheduleHold(false);
        this.clearRoute();
        ConsistNose.forget(this.train);
        this.terminalSense = 0;
        this.lastRemaining = -1.0;
        this.lastJunction = null;
        this.parked = false;
    }

    private void tickPreTransit(AnalogControllerBlockEntity controller) {
        ScheduleEntry entry = this.schedule.entries.get(this.currentEntry);
        ScheduleInstruction instruction = entry.instruction;
        boolean quiet = this.quietReplan;
        this.quietReplan = false;
        if (!this.probeLeverSign(controller)) {
            if (this.creeping) {
                this.creeping = false;
                this.command(controller, 0);
                this.train.setTargetSpeed(0.0);
            }

            this.announcer.reset();
            this.parked = false;
            int startedOn = this.currentEntry;
            ConsistNose.pinCab(this.train);
            this.path = instruction.start(this, this.train.level());
            if (this.path != null && this.noseAgainstLanding(instruction)) {
                this.path = null;
            }

            this.needsReplan = false;
            if (this.path != null || !this.waitingHere) {
                this.train.setScheduleHold(false);
            }

            if (this.path == null) {
                if (this.currentEntry != startedOn) {
                    this.wrapOrFinish(controller);
                } else if (this.waitingHere) {
                    this.waitingHere = false;
                    this.holdWhileWaiting(controller);
                } else if (this.maneuver && instruction instanceof DestinationInstruction) {
                    this.maneuverDeadEnd(controller);
                } else {
                    this.release();
                }
            } else {
                if (this.deadEndNotified) {
                    LoconauticsConstants.LOGGER.debug("[maneuver] {} dead end cleared, a path appeared", this.trainTag());
                    this.deadEndNotified = false;
                }

                this.released = false;
                if (this.path.followsRoute()) {
                    this.setRoute(this.path.route());
                }

                this.leftStartEdge = this.path.route() != null && this.path.route().isEmpty();
                this.terminalSense = 0;
                this.brakeSense = 0;
                this.signalHeld = false;
                this.signalParked = false;
                this.lastRemaining = -1.0;
                this.lastJunction = null;
                this.orientNose();
                this.startHopLeg();
                this.state = SableScheduleRuntime.State.IN_TRANSIT;
                if (!quiet) {
                    this.chimeArmed = false;
                    this.announcer.departing(instruction);
                }
            }
        }
    }

    private boolean noseAgainstLanding(ScheduleInstruction instruction) {
        if (this.maneuver
            && this.path.hop() == null
            && this.path.station() != null
            && instruction instanceof DestinationInstruction destination
            && destination.landsFront()
            && !StationRouting.twoCabs(this.train)
            && (this.path.route() == null || !this.path.route().isEmpty() || !this.justDeparted(this.path.station()))) {
            int sense = SableTrainDriver.noseSense(this.train, TrainStations.nosePoint(this.train));
            int rel = sense == this.path.direction() ? 1 : -1;
            Boolean along = StationRouting.travelAlongFacing(this.train, this.path);
            boolean against = along != null && along != rel > 0;
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} landing check at {}: leg direction {} cab end looks sense {} so the cab end {}, travel along the facing {}, nose ends against it {}",
                    new Object[]{
                        this.trainTag(),
                        this.path.station().name,
                        this.path.direction(),
                        sense,
                        rel > 0 ? "leads" : "trails",
                        along == null ? "unknown" : along,
                        against
                    }
                );
            if (!against) {
                return false;
            }

            this.explainManeuver(new StationRouting.Cause(StationRouting.Why.NOSE_AGAINST, this.path.station().name, "").component());
            this.noPath(destination.getFilter(), "the nose would end against the station facing", true);
            this.recordBerth(null);
            return true;
        } else {
            return false;
        }
    }

    private void orientNose() {
        this.tailLanding = this.maneuver && this.plannedRel() < 0 && this.path.hop() == null && this.path.station() != null && this.berthed.isEmpty();
        Vec3 forward = this.train.isTrackless() ? null : ConsistNose.noseTangent(this.train);
        if (forward != null) {
            ConsistNose.pinTravel(this.train, forward.scale(this.path.direction()));
            LoconauticsConstants.LOGGER
                .debug(
                    "[nose2c] schedule {} leg direction {} nose car {} sense now {} flip {}",
                    new Object[]{
                        LogFormat.shortId(this.train.id()),
                        this.path.direction(),
                        LogFormat.shortId(ConsistNose.of(this.train).car().id()),
                        ConsistNose.senseOf(this.train),
                        ConsistNose.frameFlip(this.train)
                    }
                );
            this.traceFacing("leg start");
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} leg lands on the {} end, nose car {} tail car {}",
                    new Object[]{
                        this.trainTag(),
                        this.tailLanding ? "trailing" : "leading",
                        LogFormat.shortId(ConsistNose.of(this.train).car().id()),
                        LogFormat.shortId(ConsistNose.tail(this.train).car().id())
                    }
                );
        }
    }

    private static String dirText(@Nullable Vec3 dir) {
        return dir == null ? "null" : String.format("(%.2f,%.2f,%.2f)", dir.x, dir.y, dir.z);
    }

    private void traceFacing(String when) {
        if (this.maneuver && !this.train.isTrackless()) {
            ConsistNose.Nose lead = ConsistNose.of(this.train);
            ConsistNose.Nose trail = ConsistNose.tail(this.train);
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} facing at {}: cab {} look {} axle {} lead end car {} tangent {} trail end car {} tangent {} rel {} direction {} sense {} flip {} speed {}",
                    new Object[]{
                        this.trainTag(),
                        when,
                        dirText(SableTrainDriver.headingNose(this.train)),
                        dirText(SableTrainDriver.driverLook(this.train)),
                        dirText(SableTrainDriver.axleNose(this.train)),
                        LogFormat.shortId(lead.car().id()),
                        dirText(ConsistNose.noseTangent(lead)),
                        LogFormat.shortId(trail.car().id()),
                        dirText(ConsistNose.noseTangent(trail)),
                        this.plannedRel(),
                        this.path == null ? 0 : this.path.direction(),
                        ConsistNose.senseOf(this.train),
                        ConsistNose.frameFlip(this.train),
                        String.format("%.3f", this.train.speed())
                    }
                );
        }
    }

    private void startHopLeg() {
        TurntableRegistry.Exit hop = this.path.hop();
        if (hop == null) {
            this.idleTurns = 0;
        } else {
            TurntableRegistry.reserve(hop.plate());
        }
    }

    private int noseDirection() {
        int sense = this.train.isTrackless() ? 0 : ConsistNose.senseOf(this.train);
        return sense != 0 ? sense : this.path.direction();
    }

    private int bodyDirection() {
        return this.noseDirection() * ConsistNose.frameFlip(this.train);
    }

    private void tickInTransit(AnalogControllerBlockEntity controller) {
        if (this.path == null || this.needsReplan) {
            this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        } else if (this.postalStopGone()) {
            this.quietReplan = true;
            this.needsReplan = true;
            this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        } else if (this.berthCoupled()) {
            LoconauticsConstants.LOGGER.debug("[maneuver] {} coupled to the train it berthed behind, arriving", this.trainTag());
            this.train.setTargetSpeed(0.0);
            this.train.setScheduleHold(true);
            this.command(controller, 0);
            this.arrive(controller, true);
        } else {
            TrackGraphLocation loc = this.path.target();
            RailCarriage rail = this.leadRail();
            if (!this.leftStartEdge) {
                if (this.path.hop() == null && this.onTargetEdge(loc)) {
                    this.drive(controller);
                    return;
                }

                this.leftStartEdge = true;
            }

            RouteDistance.Measure measured = this.measure(loc);
            if (!measured.known()) {
                this.lostMeasure(controller, measured.reason());
            } else {
                double remaining = measured.distance();
                this.lostMeasures = 0;
                if (this.path.hop() != null) {
                    TurntableRegistry.reserve(this.path.hop().plate());
                }

                boolean centered = this.centersOnPlate();
                double ahead = centered ? this.aheadOfTarget(loc, remaining) : remaining;
                double toStop = centered ? Math.abs(ahead + this.landingOffset()) : remaining;
                if (!this.recheckRoute(Math.max(0.0, ahead))) {
                    this.tickChime(rail, remaining);
                    double red = TrainSignals.gapToRed(this.train, this.bodyDirection());
                    boolean redWins = !Double.isNaN(red) && red < remaining;
                    if (!redWins || !this.brakeForRed(controller, red)) {
                        this.clearSignalHold();
                        if (this.waypointLeg()) {
                            if (this.aheadOfTarget(loc, remaining) <= 3.0) {
                                this.passWaypoint(controller);
                            } else {
                                this.driveUnderCurveCap(controller);
                                this.reportNextJunction(rail);
                            }
                        } else {
                            double v = Math.abs(this.train.speed());
                            double brakingDist = v * v / (2.0 * StationLanding.decelerationFor(this.train));
                            if (toStop <= Math.max(4.0, 1.2 * brakingDist)) {
                                this.brakeToward(controller, loc, remaining);
                            } else {
                                this.driveUnderCurveCap(controller);
                                this.reportNextJunction(rail);
                                if (remaining <= 3.0 && this.path.hop() == null) {
                                    this.arrive(controller, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean berthCoupled() {
        if (this.maneuver && !this.berthed.isEmpty() && this.path.hop() == null && this.train.level() != null) {
            Set<UUID> consist = TrainConsist.connectedSubLevels(this.train.level(), this.train.car().subLevelId());

            for (UUID id : this.berthed) {
                SableTrain wagon = SableTrainRegistry.get(id);
                if (wagon != null && wagon.car() != null && consist.contains(wagon.car().subLevelId())) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean waypointLeg() {
        return this.currentInstruction() instanceof WaypointInstruction && this.path != null && this.path.hop() == null && this.path.station() != null;
    }

    private void passWaypoint(AnalogControllerBlockEntity controller) {
        LoconauticsConstants.LOGGER
            .debug("[maneuver] {} passed waypoint {} at speed {}", new Object[]{this.trainTag(), this.stationName(), String.format("%.2f", this.train.speed())});
        int next = this.currentEntry + 1;
        if (next >= this.schedule.entries.size()) {
            if (!this.schedule.cyclic) {
                this.finish(controller);
                return;
            }

            this.traceCycle("waypoint wrap");
            next = 0;
        }

        this.currentEntry = next;
        this.quietReplan = true;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        this.sync();
    }

    private void lostMeasure(AnalogControllerBlockEntity controller, String reason) {
        this.train.setTargetSpeed(0.0);
        this.command(controller, 0);
        this.quietReplan = true;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        if (++this.lostMeasures >= 3) {
            this.lostMeasures = 0;
            this.startCooldown();
            LoconauticsConstants.LOGGER.debug("[schedule] {} lost its route {} times in a row, {}", new Object[]{LogFormat.shortId(this.train.id()), 3, reason});
        }
    }

    private void tickChime(RailCarriage rail, double remaining) {
        if (remaining > 100.0) {
            this.chimeArmed = true;
        }

        if (this.chimeArmed && !(remaining >= 60.0) && !this.train.isGhost()) {
            this.chimeArmed = false;
            this.chime = ArrivalChime.start(this.train, rail.forward().scale(this.path.direction()));
        }
    }

    private boolean postalStopGone() {
        if (this.postalRepeat && this.path.station() != null && ++this.postalCheckCounter >= 10) {
            this.postalCheckCounter = 0;
            return this.currentInstruction() instanceof PostalEntry entry && PostalPlan.canSkip(new SablePostalWorld(this), entry, this.path.station());
        } else {
            return false;
        }
    }

    private boolean recheckRoute(double remaining) {
        if (++this.replanCounter < 20) {
            return false;
        }

        this.replanCounter = 0;
        boolean lostRoute = this.path.followsRoute() && !this.path.route().isEmpty() && this.noHopsLeft() && remaining > 4.0;
        if (!lostRoute && !this.signalParked) {
            return false;
        }

        this.quietReplan = true;
        this.needsReplan = true;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        return true;
    }

    private boolean noHopsLeft() {
        List<Couple<TrackNode>> live = this.leadRail().remainingPath();
        return live == null || live.isEmpty();
    }

    private void reportNextJunction(RailCarriage rail) {
        List<Couple<TrackNode>> route = rail.remainingPath();
        TrackGraph graph = rail.graph();
        if (route != null && !route.isEmpty() && graph != null) {
            for (int i = 0; i < route.size(); i++) {
                Couple<TrackNode> hop = route.get(i);
                Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom((TrackNode)hop.getFirst());
                if (connections != null && connections.size() > 2) {
                    Vec3 out = nodePos((TrackNode)hop.getSecond()).subtract(nodePos((TrackNode)hop.getFirst()));
                    Vec3 in = i > 0 ? nodePos((TrackNode)hop.getFirst()).subtract(nodePos((TrackNode)route.get(i - 1).getFirst())) : this.travelDirection(rail);
                    String text = junctionText(in, out);
                    if (!text.equals(this.lastJunction)) {
                        this.lastJunction = text;
                        this.announce(text);
                    }

                    return;
                }
            }

            this.lastJunction = null;
        }
    }

    private Vec3 travelDirection(RailCarriage rail) {
        Vec3 tangent = ConsistNose.noseTangent(this.train);
        return tangent == null ? rail.forward().scale(this.path.direction()) : tangent.scale(this.path.direction());
    }

    private static Vec3 nodePos(TrackNode node) {
        return node.getLocation().getLocation();
    }

    private static String junctionText(Vec3 in, Vec3 out) {
        Vec3 from = flatten(in);
        Vec3 to = flatten(out);
        String cardinal = Direction.getNearest(to.x, 0.0, to.z).getName();
        if (!(from.lengthSqr() < 1.0E-6) && !(to.lengthSqr() < 1.0E-6)) {
            double cross = from.z * to.x - from.x * to.z;
            return Math.abs(cross) < 0.2
                ? "next junction continue straight (" + cardinal + ")"
                : "next junction turn " + cardinal + " (" + (cross > 0.0 ? "left" : "right") + ")";
        } else {
            return "next junction continue straight (" + cardinal + ")";
        }
    }

    private static Vec3 flatten(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        return flat.lengthSqr() < 1.0E-6 ? flat : flat.normalize();
    }

    private void drive(AnalogControllerBlockEntity controller) {
        this.drive(controller, this.bodyDirection());
    }

    private double curveCap() {
        ConsistNose.Nose nose = ConsistNose.of(this.train);
        TravellingPoint point = this.pointOf(nose);
        RailCarriage rail = nose.rail();
        TrackGraph graph = RailSeams.liveGraphOf(point, rail == null ? this.leadRail().graph() : rail.graph());
        return ScheduleCurveSpeed.allowedBpt(this.train, point, graph, this.path.route(), StationLanding.decelerationFor(this.train));
    }

    private void driveUnderCurveCap(AnalogControllerBlockEntity controller) {
        double cap = this.curveCap();
        if (!(cap < TrainPropulsion.gearedTopBpt(this.train) * this.maneuverScale())) {
            this.drive(controller);
        } else {
            double mag = this.cappedSpeed(cap);
            this.train.setTargetSpeed(this.bodyDirection() * mag);
            this.train.setServoBrake(Math.abs(this.train.speed()) > mag);
            if (controller != null) {
                this.command(controller, StationLanding.powerNotches(this.train, mag) * this.leverFor(this.bodyDirection()));
            }
        }
    }

    private void drive(AnalogControllerBlockEntity controller, int direction) {
        if (controller == null) {
            this.train.setTargetSpeed(direction * this.ghostCruiseSpeed());
        } else {
            int magnitude = this.powerLimit < 0 ? controller.getMaxPower() : this.powerLimit;
            if (this.maneuver) {
                magnitude = Math.max(1, (int)(magnitude * this.maneuverScale()));
            }

            this.command(controller, magnitude * this.leverFor(direction));
        }
    }

    private double ghostCruiseSpeed() {
        double top = this.train.ghostCruise();
        if (top <= 0.0) {
            top = TrainPropulsion.bearingAxleCapBpt();
        }

        if (this.maxNotches <= 0) {
            return top * this.maneuverScale();
        }

        int magnitude = this.powerLimit < 0 ? this.maxNotches : this.powerLimit;
        int notches = this.maneuver ? Math.max(1, (int)(magnitude * this.maneuverScale())) : magnitude;
        return top * notches / this.maxNotches;
    }

    private int leverFor(int railDirection) {
        double rpm = TrainPropulsion.axleRpm(this.train);
        int power = this.currentPower();
        if (power != 0 && Math.abs(rpm) > 0.001) {
            this.leverSign = rpm < 0.0 == power < 0 ? 1 : -1;
            this.leverKnown = true;
        }

        return railDirection * (TrainPropulsion.axleSign(this.train) < 0.0 ? -1 : 1) * this.leverSign;
    }

    private boolean probeLeverSign(AnalogControllerBlockEntity controller) {
        if (controller != null && !this.train.isGhost() && !this.leverKnown && this.leverProbeTicks < 60 && !(Math.abs(this.train.speed()) > 0.01)) {
            double rpm = TrainPropulsion.axleRpm(this.train);
            if (this.leverProbeTicks >= 3 && this.currentPower() > 0 && Math.abs(rpm) > 0.001) {
                this.leverSign = rpm < 0.0 ? -1 : 1;
                this.leverKnown = true;
                this.command(controller, 0);
                return false;
            } else if (++this.leverProbeTicks >= 60) {
                this.command(controller, 0);
                return false;
            } else {
                TrainBrakes.holdSoft(this.train);
                this.train.setTargetSpeed(0.0);
                this.command(controller, 1);
                return true;
            }
        } else {
            return false;
        }
    }

    private void command(AnalogControllerBlockEntity controller, int power) {
        if (controller == null) {
            if (power == 0) {
                this.train.setTargetSpeed(0.0);
            }
        } else {
            controller.setScheduleTargetPower(power);
            if (power != 0 && controller.getCurrentPower() == 0) {
                controller.setCurrentPower(Integer.signum(power));
            }
        }
    }

    private void tickPostTransit(AnalogControllerBlockEntity controller) {
        if (Math.abs(this.train.speed()) <= 0.001 && this.train.currentStation() != null) {
            this.train.setStationState(SableTrain.StationState.STOPPED);
        }

        if (this.parked && controller != null && !controller.hasUser() && controller.getCurrentPower() != 0) {
            this.command(controller, 0);
        }

        if (this.parked && (controller == null || !controller.hasUser())) {
            TrainBrakes.holdSoft(this.train);
            if (this.traceNow && Math.abs(this.train.speed()) > 0.03) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[parkdrift] {} moving while parked speed={} target={} lever={} axleRpm={} braked={} hold={} powered={}",
                        new Object[]{
                            LogFormat.shortId(this.train.id()),
                            String.format("%.3f", this.train.speed()),
                            String.format("%.3f", this.train.targetSpeed()),
                            controller == null ? "none" : controller.getCurrentPower(),
                            String.format("%.1f", TrainPropulsion.axleRpm(this.train)),
                            this.train.isBraked(),
                            this.train.brakeHold(),
                            this.train.isPowered()
                        }
                    );
            }
        }

        if (this.driftedOffStop()) {
            ScheduleGhostTrace.event(this.train, "drifted off the stop, back to IN_TRANSIT, conditions restart on arrival " + this.conditionText());
            this.parked = false;
            this.train.setScheduleHold(false);
            this.state = SableScheduleRuntime.State.IN_TRANSIT;
        } else {
            ScheduleEntry entry = this.schedule.entries.get(this.currentEntry);
            List<List<ScheduleWaitCondition>> columns = entry.conditions;
            if (++this.conditionSaveCounter >= 20) {
                this.conditionSaveCounter = 0;
                this.sync();
            }

            if (columns.isEmpty()) {
                this.depart(controller);
            } else {
                for (int i = 0; i < columns.size() && i < this.conditionProgress.size(); i++) {
                    List<ScheduleWaitCondition> column = columns.get(i);
                    int progress = this.conditionProgress.get(i);
                    if (progress >= column.size()) {
                        ScheduleGhostTrace.event(this.train, "column " + i + " finished, departing " + this.conditionText());
                        this.depart(controller);
                        return;
                    }

                    ScheduleWaitCondition condition = column.get(progress);
                    CompoundTag context = this.conditionContext.get(i);
                    if (condition.needsBody() && consistHasGhost(this.train)) {
                        this.announcer.waitingForBody(i, columns.size(), condition);
                        if (this.traceNow) {
                            ScheduleGhostTrace.log(
                                this.train, "column " + i + " " + condition.getId().getPath() + " needs the body, cars are unloaded, ctx=" + context
                            );
                        }
                    } else {
                        this.announcer.waiting(i, columns.size(), condition, this.stationName(), context);
                        boolean met = condition.tickCompletion(this.train.level(), this.train, context);
                        if (this.traceNow) {
                            ScheduleGhostTrace.log(
                                this.train,
                                "column "
                                    + i
                                    + " "
                                    + condition.getId().getPath()
                                    + " progress="
                                    + progress
                                    + "/"
                                    + column.size()
                                    + " met="
                                    + met
                                    + " ctx="
                                    + context
                            );
                        }

                        if (met) {
                            this.announcer.met(i, columns.size(), condition, context);
                            this.conditionProgress.set(i, progress + 1);
                            this.conditionContext.set(i, new CompoundTag());
                        }
                    }
                }
            }
        }
    }

    private String stateText() {
        return "entry="
            + this.currentEntry
            + " paused="
            + this.paused
            + " completed="
            + this.completed
            + " held="
            + this.heldReason
            + " cooldown="
            + this.cooldown
            + " grace="
            + this.transientTicks
            + " parked="
            + this.parked
            + " path="
            + (this.path != null)
            + " stoppedAt="
            + (this.train.stoppedAt() == null ? "none" : this.train.stoppedAt().name)
            + " stationState="
            + this.train.stationState()
            + " scheduleHold="
            + this.train.scheduleHold()
            + " speed="
            + String.format(Locale.ROOT, "%.3f", this.train.speed())
            + " "
            + this.conditionText();
    }

    private String conditionText() {
        StringBuilder text = new StringBuilder("conditions=[");

        for (int i = 0; i < this.conditionProgress.size(); i++) {
            text.append(i == 0 ? "" : " ").append(i).append(":").append(this.conditionProgress.get(i)).append(this.conditionContext.get(i));
        }

        return text.append("]").toString();
    }

    private static boolean consistHasGhost(SableTrain train) {
        if (train.isGhost()) {
            return true;
        }

        for (UUID id : train.consistCache()) {
            SableTrain mate = SableTrainRegistry.bySubLevel(id);
            if (mate != null && mate.isGhost()) {
                return true;
            }
        }

        return false;
    }

    public boolean waitsForStationUnloaded() {
        if (!this.paused
            && !this.completed
            && this.state == SableScheduleRuntime.State.POST_TRANSIT
            && this.schedule != null
            && this.currentEntry < this.schedule.entries.size()) {
            List<List<ScheduleWaitCondition>> columns = this.schedule.entries.get(this.currentEntry).conditions;

            for (int i = 0; i < columns.size() && i < this.conditionProgress.size(); i++) {
                List<ScheduleWaitCondition> column = columns.get(i);
                int progress = this.conditionProgress.get(i);
                if (progress < column.size() && column.get(progress).needsStationUnloaded()) {
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean driftedOffStop() {
        if (this.parked && this.path != null) {
            double remaining = this.measureRemaining(this.path.target());
            return remaining != -1.0 && Math.abs(remaining + StationLanding.parkOffset) > 3.0;
        } else {
            return false;
        }
    }

    private void landed(AnalogControllerBlockEntity controller) {
        if (this.path != null && this.path.hop() != null) {
            this.clearRoute();
            this.parked = false;
            this.turntableAligning = false;
            this.state = SableScheduleRuntime.State.TURNTABLE;
            this.sync();
        } else {
            this.arrive(controller, true);
        }
    }

    private void tickTurntable(AnalogControllerBlockEntity controller) {
        TurntableRegistry.Exit hop = this.path == null ? null : this.path.hop();
        Turntable turntable = hop == null ? null : TurntableRegistry.byPlate(hop.plate());
        this.traceTurn("enter", hop, turntable);
        if (turntable == null) {
            this.traceTurn("turntable gone, registered " + TurntableRegistry.all().size(), hop, null);
            this.skipTurn("the turntable is gone");
        } else {
            TurntableRegistry.reserve(hop.plate());
            this.train.setScheduleHold(true);
            this.train.setTargetSpeed(0.0);
            this.command(controller, 0);
            TrainBrakes.holdSoft(this.train);
            if (Math.abs(this.train.speed()) > 0.03) {
                this.traceTurn("waiting to stop", hop, turntable);
            } else {
                TrackGraph graph = TurntableRegistry.graphHolding(hop.movingHead());
                if (graph == null) {
                    this.traceTurn("no graph holds the moving head", hop, turntable);
                } else {
                    double span = TurntableRegistry.spanOf(hop.plate());
                    double consistLength = TrainLength.consistLength(this.train.level(), this.train.car().subLevelId());
                    if (!this.turntableAligning && span + 0.5 < consistLength) {
                        LoconauticsConstants.LOGGER
                            .debug(
                                "[maneuver] {} plate overhang, span {} consist {}",
                                new Object[]{this.trainTag(), String.format("%.2f", span), String.format("%.2f", consistLength)}
                            );
                        this.traceTurn("overhang span " + span + " consist " + consistLength, hop, turntable);
                        TrainStatusChat.turntableOverhang(this.train);
                    } else if (!this.turntableAligning && TurntableRegistry.exitOpen(graph, hop.movingHead(), hop.worldHead())) {
                        this.traceTurn("exit already open", hop, turntable);
                        this.advanceManeuver();
                        this.skipTurn("the exit is already open");
                    } else if (hop.entry() && TurntableRegistry.occupied(hop.plate())) {
                        this.traceTurn("entry waits, plate occupied", hop, turntable);
                    } else if (!this.turntableAligning && !hop.entry() && !TurntableRegistry.wholeTrainOn(hop.plate(), this.train)) {
                        this.traceTurn("not wholly on the plate", hop, turntable);
                        turntable.turntableClearAlignment();
                        this.skipTurn("the train is not wholly on the plate");
                    } else if (!turntable.turntablePowered()) {
                        this.traceTurn("no power", hop, turntable);
                        TrainStatusChat.turntableNoPower(this.train);
                    } else if (!this.turntableAligning) {
                        this.traceTurn("start", hop, turntable);
                        LoconauticsConstants.LOGGER
                            .debug(
                                "[maneuver] {} plate turn starts, entry head {} moving head {} world head {} degrees {} backs out {} entry hop {} turn needed {}",
                                new Object[]{
                                    this.trainTag(),
                                    hop.entryHead(),
                                    hop.movingHead(),
                                    hop.worldHead(),
                                    String.format("%.1f", hop.degrees()),
                                    ManeuverPlan.backsOut(hop),
                                    hop.entry(),
                                    String.format("%.1f", TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead()))
                                }
                            );
                        this.traceFacing("plate before turn");
                        turntable.turntableAlignTo(hop.movingHead(), hop.worldHead());
                        this.turntableAligning = true;
                    } else if (!turntable.turntableAligned()) {
                        this.traceTurn("turning", hop, turntable);
                    } else {
                        boolean welded = TurntableRegistry.weldAligned(turntable, hop.movingHead(), hop.worldHead());
                        if (!TurntableRegistry.exitOpen(graph, hop.movingHead(), hop.worldHead())) {
                            this.traceTurn("aligned but exit not open, welded " + welded, hop, turntable);
                        } else {
                            turntable.turntableClearAlignment();
                            TurntableRegistry.noteServed(hop.plate());
                            LoconauticsConstants.LOGGER
                                .debug(
                                    "[maneuver] {} plate turn done, error left {} degrees",
                                    this.trainTag(),
                                    String.format("%.2f", TurntableRegistry.alignmentError(turntable, hop.movingHead(), hop.worldHead()))
                                );
                            this.traceFacing("plate after turn");
                            TrainStatusChat.turntableReady(this.train);
                            this.idleTurns = 0;
                            this.advanceManeuver();
                            this.resumeAfterTurn();
                        }
                    }
                }
            }
        }
    }

    private void skipTurn(String why) {
        LoconauticsConstants.LOGGER.debug("[maneuver] {} plate turn skipped, {}", this.trainTag(), why);
        if (++this.idleTurns >= 3) {
            this.idleTurns = 0;
            this.startCooldown();
            this.noPath("turntable", why, true);
        }

        this.resumeAfterTurn();
    }

    private void resumeAfterTurn() {
        if (this.path != null && this.path.hop() != null) {
            TurntableRegistry.reserve(this.path.hop().plate());
        }

        this.turntableAligning = false;
        this.terminalSense = 0;
        this.brakeSense = 0;
        this.lastRemaining = -1.0;
        this.quietReplan = true;
        this.needsReplan = true;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
    }

    private void arrive(AnalogControllerBlockEntity controller, boolean stopped) {
        if (this.reversalStop()) {
            this.turnAround(controller);
        } else {
            if (!stopped) {
                this.command(controller, 0);
            }

            if (this.maneuver) {
                LoconauticsConstants.LOGGER.debug("[maneuver] {} arrived at {}, maneuver over", this.trainTag(), this.stationName());
                this.endManeuver();
            }

            this.clearRoute();
            this.parked = stopped;
            this.departedFrom = null;
            this.state = SableScheduleRuntime.State.POST_TRANSIT;
            if (this.path != null && this.path.station() != null) {
                this.train.setCurrentStation(this.path.station());
                this.train.setAtStation(this.path.station());
                if (stopped) {
                    this.train.setStationState(SableTrain.StationState.STOPPED);
                }

                TrainMailTransfer.run(this.train, this.path.station());
            }

            if (this.postalRepeat) {
                this.postalRepeat = false;
                this.state = SableScheduleRuntime.State.PRE_TRANSIT;
                this.startCooldown();
                this.sync();
            } else {
                this.loconautics$setTripped(false);
                this.resetConditions();
                ScheduleGhostTrace.event(this.train, "arrived, conditions reset entry=" + this.currentEntry + " stopped=" + stopped);
                if (this.schedule.entries.get(this.currentEntry).conditions.isEmpty()) {
                    this.announcer.parked(this.stationName(), stopped);
                }

                this.sync();
            }
        }
    }

    private void wrapOrFinish(AnalogControllerBlockEntity controller) {
        if (this.currentEntry < this.schedule.entries.size()) {
            this.sync();
        } else if (!this.schedule.cyclic) {
            this.currentEntry = this.schedule.entries.size() - 1;
            this.finish(controller);
        } else {
            this.traceCycle("wrapOrFinish");
            this.currentEntry = 0;
            this.state = SableScheduleRuntime.State.PRE_TRANSIT;
            this.sync();
        }
    }

    private void depart(AnalogControllerBlockEntity controller) {
        GlobalStation leaving = this.path != null && this.path.station() != null ? this.path.station() : this.train.atStation();
        this.departedFrom = leaving == null ? null : leaving.id;
        this.train.setScheduleHold(false);
        this.train.setCurrentStation(null);
        this.parked = false;
        this.brakeSense = 0;
        this.conditionProgress.clear();
        this.conditionContext.clear();
        int next = this.currentEntry + 1;
        if (next >= this.schedule.entries.size()) {
            if (!this.schedule.cyclic) {
                this.finish(controller);
                return;
            }

            this.traceCycle("depart wrap");
            next = 0;
        }

        this.currentEntry = next;
        this.state = SableScheduleRuntime.State.PRE_TRANSIT;
        this.sync();
    }

    private void traceCycle(String where) {
        GlobalStation at = this.train == null ? null : this.train.atStation();
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver-cycle] {} {} entry={} of {} maneuver={} reversalUsed={} plan={} plannedRel={} former={} berthed={} atStation={} speed={} state={}",
                new Object[]{
                    this.trainTag(),
                    where,
                    this.currentEntry,
                    this.schedule == null ? -1 : this.schedule.entries.size(),
                    this.maneuver,
                    this.reversalUsed,
                    this.maneuverPlan,
                    this.plannedRel(),
                    this.former.size(),
                    this.berthed.size(),
                    at == null ? "none" : at.name,
                    this.train == null ? "none" : String.format("%.2f", this.train.speed()),
                    this.state
                }
            );
    }

    private void sync() {
        if (this.train.level() != null) {
            ScheduleProgressStore.get(this.train.level().getServer()).putProgress(this.train.id(), this.saveProgress());
        }
    }

    private void finish(AnalogControllerBlockEntity controller) {
        this.paused = true;
        this.completed = true;
        this.train.setSpeed(0.0);
        this.train.setTargetSpeed(0.0);
        this.train.setScheduleHold(true);
        this.clearRoute();
        ConsistNose.forget(this.train);
        if (controller != null) {
            controller.clearScheduleControl();
        }

        this.announcer.complete();
        this.currentEntry = 0;
        this.sync();
    }

    private boolean brakeForRed(AnalogControllerBlockEntity controller, double gap) {
        double v = Math.abs(this.train.speed());
        double brakingDist = v * v / 0.006;
        double engageAt = Math.max(4.0, 1.2 * brakingDist);
        if (gap > engageAt) {
            return false;
        }

        this.signalHeld = true;
        this.train.setScheduleHold(true);
        if (this.signalParked) {
            this.train.setTargetSpeed(0.0);
            this.command(controller, 0);
            return true;
        }

        if (!SignalSnap.canSnap(this.train, gap) && !SignalSnap.wentPast(gap)) {
            double mag = this.cappedSpeed(StationLanding.approachSpeed(this.train, gap));
            this.train.setTargetSpeed(this.bodyDirection() * mag);
            this.train.setServoBrake(true);
            if (controller != null) {
                this.command(controller, StationLanding.powerNotches(this.train, mag) * this.leverFor(this.bodyDirection()));
            }

            return true;
        } else {
            boolean past = SignalSnap.wentPast(gap);
            SignalSnap.snap(this.train, past ? 0.0 : this.noseDirection() * gap);
            this.command(controller, 0);
            this.signalParked = true;
            return true;
        }
    }

    private void clearSignalHold() {
        if (this.signalHeld) {
            this.signalHeld = false;
            this.signalParked = false;
            this.train.setScheduleHold(false);
        }
    }

    private boolean centersOnPlate() {
        return this.path.hop() != null && !this.path.hop().entry();
    }

    private double landingOffset() {
        if (this.path != null && this.path.hop() != null) {
            return this.centersOnPlate() ? this.noseToBodyMiddle() : -3.0;
        } else if (this.path != null && this.path.station() == null) {
            return 0.0;
        } else {
            return this.tailLanding ? -StationLanding.parkOffset : StationLanding.parkOffset;
        }
    }

    private double noseToBodyMiddle() {
        UUID carId = this.train.car().subLevelId();
        Vec3 wheel = ConsistNose.wheelPos(this.train);
        Vec3 along = ConsistNose.noseTangent(this.train);
        double measured = wheel != null && along != null ? TrainLength.toBodyMiddle(this.train.level(), carId, wheel, along) : Double.NaN;
        if (!Double.isNaN(measured)) {
            return measured;
        }

        Vector3d[] ref = wheel != null && TrainConsist.connectedSubLevels(this.train.level(), carId).size() <= 1
            ? TrainRailAdvance.bogeyReference(null, this.train.car())
            : null;
        return ref == null ? TrainLength.consistLength(this.train.level(), carId) * 0.5 : wheel.distanceTo(new Vec3(ref[0].x, ref[0].y, ref[0].z));
    }

    private double aheadOfTarget(TrackGraphLocation loc, double remaining) {
        double onEdge = this.signedTerminalOffset(loc);
        if (Double.isNaN(onEdge)) {
            return remaining;
        }

        if (this.terminalSense == 0) {
            this.terminalSense = this.path.hop() != null ? this.noseDirection() : (onEdge >= 0.0 ? 1 : -1);
        }

        return this.terminalSense * onEdge;
    }

    private void brakeToward(AnalogControllerBlockEntity controller, TrackGraphLocation loc, double remaining) {
        double offset = this.landingOffset();
        double error = this.aheadOfTarget(loc, remaining) + offset;
        int dir = this.terminalSense != 0 ? this.terminalSense : this.noseDirection();
        double gap = Math.abs(error);
        if (this.brakeSense == 0) {
            this.brakeSense = error >= 0.0 ? 1 : -1;
        }

        if (StationLanding.canSnap(this.train, gap) || StationLanding.wentPast(error, this.brakeSense)) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[nose2c] schedule {} lands error {} dir {} terminalSense {} flip {} nose car {}",
                    new Object[]{
                        LogFormat.shortId(this.train.id()),
                        String.format("%.3f", error),
                        dir,
                        this.terminalSense,
                        ConsistNose.frameFlip(this.train),
                        LogFormat.shortId(ConsistNose.of(this.train).car().id())
                    }
                );
            this.traceLanding(loc, error);
            StationLanding.snapConsist(this.train, this.stopEnd(), dir * error, this.path.hop() == null ? 0.5 : 0.0);
            this.traceLanding(loc, error);
            this.train.setScheduleHold(true);
            this.command(controller, 0);
            this.landed(controller);
        } else if (this.brakeSense < 0) {
            boolean plate = this.path.hop() != null;
            if (this.centersOnPlate() && Math.abs(this.train.speed()) > 0.03) {
                this.train.setTargetSpeed(0.0);
                this.train.setServoBrake(true);
                this.command(controller, 0);
            } else {
                StationLanding.snapConsist(this.train, this.stopEnd(), plate ? dir * error : 0.0, 0.5);
                this.train.setScheduleHold(true);
                this.command(controller, 0);
                this.landed(controller);
            }
        } else {
            double approach = this.path.hop() != null ? StationLanding.gentleApproachSpeed(this.train, gap) : StationLanding.approachSpeed(this.train, gap);
            double mag = Math.min(this.cappedSpeed(approach), this.curveCap());
            double railTarget = (this.tailLanding ? this.bodyDirection() : dir * ConsistNose.frameFlip(this.train)) * mag;
            this.traceBrake(loc, remaining, error, mag);
            this.train.setTargetSpeed(railTarget);
            this.train.setServoBrake(true);
            if (controller != null) {
                this.command(controller, StationLanding.powerNotches(this.train, mag) * this.leverFor(railTarget < 0.0 ? -1 : 1));
            }
        }
    }

    private void traceBrake(TrackGraphLocation loc, double remaining, double error, double mag) {
        if (this.maneuver && this.train.level().getGameTime() % 10L == 0L) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} brake end {} measured {} error {} offset {} speed {} approach {} gap to mark lead {} trail {}, to the end of the last edge lead {} trail {}",
                    new Object[]{
                        this.trainTag(),
                        this.tailLanding ? "trailing" : "leading",
                        String.format("%.2f", remaining),
                        String.format("%.3f", error),
                        String.format("%.3f", this.landingOffset()),
                        String.format("%.3f", this.train.speed()),
                        String.format("%.3f", mag),
                        this.markGap(ConsistNose.of(this.train), loc),
                        this.markGap(ConsistNose.tail(this.train), loc),
                        this.endGap(ConsistNose.of(this.train)),
                        this.endGap(ConsistNose.tail(this.train))
                    }
                );
        }
    }

    private void traceBerthGap() {
        TrackGraph graph = this.leadRail().graph();
        TravellingPoint lead = this.pointOf(this.stopEnd());
        Vec3 here = graph != null && lead != null ? lead.getPosition(graph) : null;

        for (UUID id : this.berthed) {
            SableTrain wagon = SableTrainRegistry.get(id);
            if (wagon != null && here != null) {
                for (ConsistNose.Nose end : List.of(ConsistNose.of(wagon), ConsistNose.tail(wagon))) {
                    Vec3 there = end.point() == null ? null : end.point().getPosition(graph);
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[maneuver] {} berth gap, lead wheel {} to wheel of train {} at {} is {}",
                            new Object[]{
                                this.trainTag(),
                                dirText(here),
                                LogFormat.shortId(id),
                                dirText(there),
                                there == null ? "unknown" : String.format("%.2f", here.distanceTo(there))
                            }
                        );
                }
            }
        }
    }

    private void tracePlatePark(double error) {
        TurntableRegistry.Exit hop = this.path == null ? null : this.path.hop();
        TrackHostFrames.Frame frame = hop == null ? null : TrackHostFrames.frameOf(hop.plate());
        Vec3 center = this.train.car().carriage().moveCenter();
        Vec3 axis = frame == null ? null : frame.axis();
        Vec3 wheel = ConsistNose.wheelPos(this.train);
        LoconauticsConstants.LOGGER
            .debug(
                "[platepark] {} rel {} dir {} tailLanding {} plate {} error {} offset {} noseToMiddle {} bodyMiddle {} axis {} wheel {} middleToAxis {} wheelToAxis {} speed {}",
                new Object[]{
                    this.trainTag(),
                    this.plannedRel(),
                    this.path == null ? 0 : this.path.direction(),
                    this.tailLanding,
                    hop != null && !hop.entry(),
                    String.format("%.3f", error),
                    String.format("%.3f", this.landingOffset()),
                    hop != null && !hop.entry() ? String.format("%.3f", this.noseToBodyMiddle()) : "n/a",
                    dirText(center),
                    dirText(axis),
                    dirText(wheel),
                    center != null && axis != null ? String.format("%.3f", center.distanceTo(axis)) : "n/a",
                    wheel != null && axis != null ? String.format("%.3f", wheel.distanceTo(axis)) : "n/a",
                    String.format("%.3f", this.train.speed())
                }
            );
    }

    private void traceLanding(TrackGraphLocation loc, double error) {
        if (this.maneuver) {
            this.traceBerthGap();
            this.tracePlatePark(error);
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} landing on the {} end, error {} speed {}, gap to mark lead {} trail {}, to the end of the last edge lead {} trail {}, nose car {} tail car {}",
                    new Object[]{
                        this.trainTag(),
                        this.tailLanding ? "trailing" : "leading",
                        String.format("%.3f", error),
                        String.format("%.3f", this.train.speed()),
                        this.markGap(ConsistNose.of(this.train), loc),
                        this.markGap(ConsistNose.tail(this.train), loc),
                        this.endGap(ConsistNose.of(this.train)),
                        this.endGap(ConsistNose.tail(this.train)),
                        LogFormat.shortId(ConsistNose.of(this.train).car().id()),
                        LogFormat.shortId(ConsistNose.tail(this.train).car().id())
                    }
                );
        }
    }

    private String endGap(ConsistNose.Nose end) {
        List<Couple<TrackNode>> route = this.path == null ? null : this.path.route();
        TrackGraph graph = this.leadRail().graph();
        if (route != null && !route.isEmpty() && graph != null) {
            Couple<TrackNode> last = route.get(route.size() - 1);
            TrackEdge edge = graph.getConnection(last);
            if (edge == null) {
                return "unknown";
            }

            TrackGraphLocation tip = new TrackGraphLocation();
            tip.graph = graph;
            tip.edge = Couple.create(((TrackNode)last.getFirst()).getLocation(), ((TrackNode)last.getSecond()).getLocation());
            tip.position = edge.getLength();
            boolean deadEnd = graph.getConnectionsFrom((TrackNode)last.getSecond()).size() <= 1;
            return this.markGap(end, tip) + (deadEnd ? " dead end" : " open");
        } else {
            return "unknown";
        }
    }

    private String markGap(ConsistNose.Nose end, TrackGraphLocation loc) {
        double gap = this.measureFrom(end, loc).distance();
        return gap == -1.0 ? "unknown" : String.format("%.2f", gap);
    }

    @Nullable
    private static ServerSubLevel subLevelOf(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        UUID id = train.car().subLevelId();
        if (container != null && id != null) {
            return container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
        } else {
            return null;
        }
    }

    public void status(String key, Component text) {
        if (!key.equals(this.lastStatus)) {
            this.lastStatus = key;
            this.announce(text);
        }
    }

    public void clearStatus() {
        this.lastStatus = null;
        if (this.train != null) {
            TrainStatusChat.successfulNavigation(this.train);
        }
    }

    public void noPath(String filter, String why, boolean anyMatch) {
        this.status(
            anyMatch ? "no_path:" + why : "no_stops",
            anyMatch ? LoconauticsLang.translate("train.status.no_path").component() : CreateLang.translateDirect("schedule.no_stops", new Object[0])
        );
        if (this.train != null) {
            if (anyMatch) {
                TrainStatusChat.failedNavigation(this.train);
            } else {
                TrainStatusChat.failedNavigationNoTarget(this.train, filter);
            }
        }
    }

    public void announce(String text) {
        this.announce(Component.literal(text));
    }

    public void announce(Component text) {
        this.announcer.line(text);
    }

    public ScheduleAnnouncer announcer() {
        return this.announcer;
    }

    @Nullable
    private String stationName() {
        return this.path != null && this.path.station() != null ? this.path.station().name : null;
    }

    @EventBusSubscriber(modid = "loconautics")
    public static final class Lifecycle {
        @SubscribeEvent
        public static void onServerStopped(ServerStoppedEvent event) {
            SableScheduleRuntime.RUNTIMES.clear();
            ConsistNose.clearAll();
        }
    }

    public enum State {
        PRE_TRANSIT,
        IN_TRANSIT,
        TURNTABLE,
        POST_TRANSIT;
    }
}
