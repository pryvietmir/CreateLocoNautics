package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.turntable.TurntableSignals;
import com.lycoris.loconautics.compat.computercraft.CcControl;
import com.lycoris.loconautics.compat.computercraft.CcEvents;
import com.lycoris.loconautics.compat.computercraft.CcNetworkEvents;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TrainSignals {
    static final double SIGNAL_DECELERATION = 0.003;
    static final double SIGNAL_STOP_MARGIN = 1.5;
    static final double SIGNAL_SCAN_MIN = 12.0;
    static final double SIGNAL_STOP_EPSILON = 0.05;
    static final double SPEED_EPSILON = 1.0E-4;
    static final double TARGET_EPSILON = 1.0E-6;
    private static final Map<UUID, Set<UUID>> OCCUPANTS = new HashMap<>();
    private static final Map<UUID, Double> HELD = new HashMap<>();
    private static final Map<UUID, String> MANEUVER_LOG = new HashMap<>();
    private static final Map<UUID, TrainSignals.Reservation> RESERVED = new HashMap<>();
    private static final Map<UUID, TrainSignals.Reservation> PREVIOUS = new HashMap<>();
    private static final Map<UUID, Map<UUID, TrainSignals.ChainClaim>> CHAINS = new HashMap<>();
    private static final int CHAIN_CLAIM_TTL = 1200;
    static final double CHAIN_REACH = 256.0;
    static final double RED_LIGHT_RANGE = 2.0;
    private static final Set<UUID> RAN_RED = new HashSet<>();

    private static TrainSignals.Reservation reservationOf(UUID groupId) {
        TrainSignals.Reservation now = RESERVED.get(groupId);
        return now != null ? now : PREVIOUS.get(groupId);
    }

    public static boolean reserved(UUID groupId) {
        return reservationOf(groupId) != null;
    }

    public static boolean reservedOtherThan(UUID groupId, UUID boundaryId) {
        TrainSignals.Reservation reservation = reservationOf(groupId);
        return reservation != null && !reservation.boundary().equals(boundaryId);
    }

    public static boolean occupied(UUID groupId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        return trains != null && !trains.isEmpty();
    }

    public static Set<UUID> occupantsOf(UUID groupId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        return trains == null ? Set.of() : Set.copyOf(trains);
    }

    public static UUID reservedBy(UUID groupId) {
        TrainSignals.Reservation reservation = reservationOf(groupId);
        return reservation == null ? null : reservation.train();
    }

    public static Set<UUID> groupsOf(UUID trainId) {
        Set<UUID> out = new HashSet<>();

        for (Entry<UUID, Set<UUID>> entry : OCCUPANTS.entrySet()) {
            if (entry.getValue().contains(trainId)) {
                out.add(entry.getKey());
            }
        }

        return out;
    }

    public static boolean occupiedByOther(UUID groupId, UUID trainId) {
        Set<UUID> trains = OCCUPANTS.get(groupId);
        return trains == null ? false : trains.size() > 1 || !trains.isEmpty() && !trains.contains(trainId);
    }

    public static void updateOccupancy() {
        OCCUPANTS.clear();
        PREVIOUS.clear();
        PREVIOUS.putAll(RESERVED);
        RESERVED.clear();
        Set<UUID> live = new HashSet<>();

        for (SableTrain train : SableTrainRegistry.all()) {
            live.add(train.id());
            occupy(train);
        }

        seedChainClaims(live);
        HELD.keySet().retainAll(live);
        MANEUVER_LOG.keySet().retainAll(live);
        RAN_RED.retainAll(live);
        CcControl.retain(live);
        CcNetworkEvents.tick();
    }

    private static void seedChainClaims(Set<UUID> live) {
        CHAINS.keySet().retainAll(live);
        CHAINS.forEach((trainId, claims) -> {
            claims.entrySet().removeIf(entry -> {
                Set<UUID> inside = OCCUPANTS.get(entry.getKey());
                return inside != null && inside.contains(trainId) || TrainStations.stationTickCounter - entry.getValue().madeAt() > 1200;
            });
            claims.forEach((groupId, claim) -> RESERVED.put(groupId, new TrainSignals.Reservation(trainId, claim.boundary())));
        });
        CHAINS.values().removeIf(Map::isEmpty);
    }

    private static void occupy(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car != null && !train.isDerailed()) {
            if (car.bogeys().isEmpty()) {
                occupyFrom(car.carriage(), train.id());
            } else {
                for (SableTrain.Bogey bogey : car.bogeys()) {
                    if (!train.isBogeyOffTrack(bogey.localPos())) {
                        occupyFrom(bogey.rail(), train.id());
                    }
                }
            }
        }
    }

    private static void occupyFrom(RailCarriage rail, UUID trainId) {
        if (rail != null) {
            TrackGraph graph = rail.graph();
            if (graph != null) {
                occupyAt(graph, rail.leading(), trainId);
                occupyAt(graph, rail.trailing(), trainId);
            }
        }
    }

    private static void occupyAt(TrackGraph graph, TravellingPoint point, UUID trainId) {
        if (point != null && point.edge != null) {
            UUID group = point.edge.getEdgeData().getGroupAtPosition(graph, point.position);
            if (group != null) {
                OCCUPANTS.computeIfAbsent(group, key -> new HashSet<>()).add(trainId);
            }
        }
    }

    static double scanRange(double speed) {
        double v = Math.abs(speed);
        return Math.max(12.0, 1.5 * (v * v) / 0.006 + 1.5);
    }

    public static double gapToRed(SableTrain train, double direction) {
        return gapToRed(train, direction, scanRange(Math.abs(train.speed())));
    }

    public static double gapToRed(SableTrain train, double direction, double range) {
        if (!train.isDerailed() && !train.isPhysics() && train.car() != null && direction != 0.0) {
            double v = Math.abs(train.speed());
            double distance = distanceToRed(train, Math.signum(direction), range, false);
            return Double.isNaN(distance) ? Double.NaN : distance - SignalSnap.stopOffset;
        } else {
            return Double.NaN;
        }
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "none" : String.format("%.2f", v);
    }

    public static void tickSignals(SableTrain train) {
        if (!train.isDerailed() && !train.isPhysics() && train.car() != null) {
            AnalogControllerBlockEntity controller = SableTrainDriver.controllerIn(train);
            boolean scheduled = controller != null ? controller.isScheduleControlled() : train.isGhost() && train.isScheduleDriven();
            if (!scheduled) {
                release(train);
                CHAINS.remove(train.id());
                awardRedLight(train);
            } else {
                RAN_RED.remove(train.id());
                double speed = train.speed();
                double target = train.targetSpeed();
                Double heldDirection = HELD.get(train.id());
                double direction;
                if (Math.abs(speed) > 1.0E-4) {
                    direction = Math.signum(speed);
                } else if (Math.abs(target) > 1.0E-6) {
                    direction = Math.signum(target);
                } else {
                    if (heldDirection == null) {
                        return;
                    }

                    direction = heldDirection;
                }

                if (Math.abs(target) > 1.0E-6 && Math.signum(target) != direction) {
                    release(train);
                    CHAINS.remove(train.id());
                } else if (heldDirection != null || !(Math.abs(target) <= 1.0E-6)) {
                    double v = Math.abs(speed);
                    double range = scanRange(v);
                    double distance = distanceToRed(train, direction, range, true);
                    if (Double.isNaN(distance)) {
                        release(train);
                    } else {
                        double gap = distance - 1.5;
                        double cap = gap <= 0.05 ? 0.0 : Math.sqrt(0.006 * gap);
                        if (cap < Math.abs(target)) {
                            train.setTargetSpeed(direction * cap);
                            train.setServoBrake(true);
                        }

                        if (!(cap > 0.0)) {
                            if (Math.abs(speed) <= 0.03) {
                                train.setSpeed(0.0);
                            }

                            if (HELD.put(train.id(), direction) == null) {
                                LoconauticsConstants.LOGGER
                                    .debug(
                                        "[nose2c] car {} holds at red, body direction {} flip {} gap {}",
                                        new Object[]{ConsistNose.shortId(train.id()), direction, ConsistNose.frameFlip(train), fmt(gap)}
                                    );
                                announce(train, "holding at a red signal");
                                CcEvents.queue(train, "train_signal_red", gap);
                            }
                        }
                    }
                }
            }
        } else {
            release(train);
            CHAINS.remove(train.id());
        }
    }

    public static boolean isHeld(UUID trainId) {
        return HELD.containsKey(trainId);
    }

    public static boolean consistHeldAtRed(SableTrain train) {
        for (UUID id : consistOf(train)) {
            SableScheduleRuntime runtime = SableScheduleRuntime.get(id);
            if (HELD.containsKey(id) || runtime != null && runtime.heldAtSignal()) {
                return true;
            }
        }

        return false;
    }

    private static void awardRedLight(SableTrain train) {
        double speed = train.speed();
        if (!(Math.abs(speed) <= 1.0E-4)) {
            double distance = distanceToRed(train, Math.signum(speed), 2.0, false);
            if (Double.isNaN(distance)) {
                RAN_RED.remove(train.id());
            } else if (RAN_RED.add(train.id())) {
                CcEvents.queue(train, "train_signal_passed_at_danger", distance);
                AnalogControllerBlockEntity driven = SableTrainDriver.operatedControllerIn(train);
                ServerPlayer player = driven == null ? null : SableTrainDriver.operatorOf(train, driven);
                if (player != null) {
                    AllAdvancements.RED_SIGNAL.awardTo(player);
                }
            }
        }
    }

    private static void release(SableTrain train) {
        if (HELD.remove(train.id()) != null) {
            announce(train, "signal cleared, proceeding");
            CcEvents.queue(train, "train_signal_clear");
        }
    }

    private static void announce(SableTrain train, String text) {
        String name = train.id().toString().substring(0, 8);
        if (train.level() != null) {
            Component line = Component.literal("[loco] train " + name + ": " + text);

            for (ServerPlayer player : train.level().players()) {
                player.sendSystemMessage(line);
            }
        }
    }

    private static double distanceToRed(SableTrain train, double direction, double range, boolean reserve) {
        RailCarriage carriage = train.car().carriage();
        TrackGraph graph = carriage == null ? null : carriage.graph();
        TravellingPoint origin = TrainStations.nosePoint(train);
        if (graph != null && origin != null && origin.edge != null) {
            TravellingPoint scout = new TravellingPoint(origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
            SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
            TrainSignals.SignalScan scan = new TrainSignals.SignalScan(
                train,
                consistOf(train),
                range,
                reserve,
                runtime == null ? null : runtime.targetStation(),
                runtime != null && runtime.maneuver() ? runtime.maneuverFriends() : null
            );
            scout.travel(
                graph,
                direction * ConsistNose.frameFlip(train) * Math.max(range, 256.0),
                routeSelector(runtime, scout.steer(SteerDirection.NONE, carriage.upNormal())),
                (distance, pair) -> scan.visit(Math.abs(distance), (TrackEdgePoint)pair.getFirst(), (TrackNode)((Couple)pair.getSecond()).getSecond()),
                scout.ignoreTurns(),
                scout.ignorePortals()
            );
            scan.finish();
            return scan.stopAt;
        } else {
            return Double.NaN;
        }
    }

    private static ITrackSelector routeSelector(SableScheduleRuntime runtime, ITrackSelector fallback) {
        List<Couple<TrackNode>> live = runtime == null ? null : runtime.leadRail().remainingPath();
        if (live != null && !live.isEmpty()) {
            List<Couple<TrackNode>> route = new ArrayList<>(live);
            return (graph, pair) -> {
                for (int i = 0; i < route.size(); i++) {
                    TrackEdge hop = graph.getConnection(route.get(i));

                    for (Entry<TrackNode, TrackEdge> option : (List)pair.getSecond()) {
                        if (option.getValue() == hop) {
                            route.subList(0, i + 1).clear();
                            return option;
                        }
                    }
                }

                return (Entry)fallback.apply(graph, pair);
            };
        } else {
            return fallback;
        }
    }

    public static Set<UUID> consistOf(SableTrain train) {
        Set<UUID> ids = new HashSet<>();
        ids.add(train.id());
        UUID subLevel = train.car() == null ? null : train.car().subLevelId();
        if (train.level() != null && subLevel != null) {
            for (UUID other : TrainConsist.connectedSubLevels(train.level(), subLevel)) {
                SableTrain car = SableTrainRegistry.bySubLevel(other);
                if (car != null) {
                    ids.add(car.id());
                }
            }

            return ids;
        } else {
            return ids;
        }
    }

    public static boolean blockedFor(SignalEdgeGroup group, Set<UUID> consist) {
        return blocked(group, consist, new HashSet<>());
    }

    private static boolean blocked(SignalEdgeGroup group, Set<UUID> consist, Set<SignalEdgeGroup> visited) {
        if (!visited.add(group)) {
            return false;
        }

        if (group.reserved == null && !group.trains.stream().anyMatch(otherx -> !consist.contains(otherx.id)) && !heldBySableTrain(group.id, consist)) {
            for (UUID crossing : group.intersecting.values()) {
                SignalEdgeGroup other = (SignalEdgeGroup)Create.RAILWAYS.signalEdgeGroups.get(crossing);
                if (other != null && blocked(other, consist, visited)) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    public static boolean heldBySableTrain(UUID groupId, Set<UUID> consist) {
        Set<UUID> occupants = OCCUPANTS.get(groupId);
        if (occupants != null && !consist.containsAll(occupants)) {
            return true;
        }

        TrainSignals.Reservation reservation = reservationOf(groupId);
        return reservation != null && !consist.contains(reservation.train());
    }

    private TrainSignals() {
    }

    private record ChainClaim(UUID boundary, int madeAt) {
    }

    private record Reservation(UUID train, UUID boundary) {
    }

    private static final class SignalScan {
        private final SableTrain train;
        private final Set<UUID> consist;
        private final double range;
        private final boolean reserve;
        private final GlobalStation destination;
        private final Set<UUID> former;
        private final Map<UUID, SignalBoundary> chain = new HashMap<>();
        private SignalBoundary chainStart;
        private double chainDistance;
        double stopAt = Double.NaN;
        int seen;

        SignalScan(SableTrain train, Set<UUID> consist, double range, boolean reserve, GlobalStation destination, Set<UUID> former) {
            this.former = former;
            this.train = train;
            this.consist = consist;
            this.range = range;
            this.reserve = reserve;
            this.destination = destination;
        }

        boolean visit(double distance, TrackEdgePoint point, TrackNode ahead) {
            this.seen++;
            boolean tracking = this.chainStart != null;
            if (!tracking && distance > this.range) {
                return true;
            } else if (point instanceof GlobalStation station
                && this.destination != null
                && TrainStations.sameStation(station, this.destination)
                && station.canApproachFrom(ahead)) {
                return true;
            } else if (point instanceof SignalBoundary signal) {
                boolean side = signal.isPrimary(ahead);
                if (this.former != null) {
                    return this.visitManeuver(distance, signal, side, ahead);
                }

                if (((Map)signal.blockEntities.get(side)).isEmpty()) {
                    return false;
                }

                UUID entering = (UUID)signal.groups.get(side);
                SignalEdgeGroup group = entering == null ? null : (SignalEdgeGroup)Create.RAILWAYS.signalEdgeGroups.get(entering);
                if (group == null) {
                    return false;
                }

                boolean cross = signal.types.get(side) == SignalType.CROSS_SIGNAL;
                boolean occupied = signal.isForcedRed(side) || TrainSignals.blocked(group, this.consist, new HashSet<>());
                if (!tracking) {
                    if (cross) {
                        this.chainStart = signal;
                        this.chainDistance = distance;
                        this.chain.put(entering, signal);
                    }

                    if (occupied) {
                        this.stopAt = distance;
                        return !cross;
                    }

                    if (!cross) {
                        this.claim(entering, signal);
                    }

                    return false;
                } else {
                    this.chain.put(entering, signal);
                    if (occupied) {
                        this.stopAt = this.chainDistance;
                        return !cross;
                    }

                    if (cross) {
                        return false;
                    }

                    if (!Double.isNaN(this.stopAt)) {
                        return true;
                    }

                    this.chainStart = null;
                    this.claimChain();
                    return false;
                }
            } else {
                return false;
            }
        }

        private boolean visitManeuver(double distance, SignalBoundary signal, boolean side, TrackNode ahead) {
            UUID entering = (UUID)signal.groups.get(side);
            SignalEdgeGroup group = entering == null ? null : (SignalEdgeGroup)Create.RAILWAYS.signalEdgeGroups.get(entering);
            if (group == null) {
                return false;
            }

            String why = this.maneuverHold(signal, side, entering, group, ahead);
            if (why != null) {
                this.stopAt = distance;
                this.logOnce(entering, "stop in " + distance(distance) + " m, " + why);
                return true;
            }

            this.logOnce(entering, null);
            if (this.reserve) {
                TrainSignals.RESERVED.put(entering, new TrainSignals.Reservation(this.train.id(), signal.getId()));
            }

            return false;
        }

        private static String distance(double value) {
            return TrainSignals.fmt(value);
        }

        private void logOnce(UUID groupId, String line) {
            String key = line == null ? "" : groupId + line;
            if (!key.equals(TrainSignals.MANEUVER_LOG.get(this.train.id()))) {
                if (line == null) {
                    TrainSignals.MANEUVER_LOG.remove(this.train.id());
                } else {
                    TrainSignals.MANEUVER_LOG.put(this.train.id(), key);
                    LoconauticsConstants.LOGGER
                        .debug("[maneuver] car {} group {}: {}", new Object[]{ConsistNose.shortId(this.train.id()), ConsistNose.shortId(groupId), line});
                }
            }
        }

        private String maneuverHold(SignalBoundary signal, boolean side, UUID groupId, SignalEdgeGroup group, TrackNode ahead) {
            for (BlockPos pos : ((Map)signal.blockEntities.get(side)).keySet()) {
                if (TurntableSignals.forcedRed(this.train.level(), pos)) {
                    return "turntable plate holds the signal at " + pos.toShortString();
                }
            }

            Set<UUID> inside = new HashSet<>(TrainSignals.occupantsOf(groupId));
            group.trains.forEach(otherx -> inside.add(otherx.id));
            TrainSignals.Reservation reservation = TrainSignals.reservationOf(groupId);
            UUID holder = reservation == null ? null : reservation.train();
            Set<UUID> foreign = new HashSet<>(inside);
            if (holder != null) {
                foreign.add(holder);
            }

            foreign.removeAll(this.consist);
            foreign.removeAll(this.former);
            if (foreign.isEmpty()) {
                return null;
            }

            TrainSignals.Reservation earlier = TrainSignals.PREVIOUS.get(groupId);
            UUID earlierHolder = earlier == null ? null : earlier.train();
            boolean selfHolds = earlierHolder != null && this.consist.contains(earlierHolder);

            for (UUID id : foreign) {
                boolean isInside = inside.contains(id);
                SableTrain other = SableTrainRegistry.get(id);
                if (isInside || other == null || !ManeuverPriority.isManeuvering(id)) {
                    return (isInside ? "train " : "reservation of train ")
                        + ConsistNose.shortId(id)
                        + (ManeuverPriority.isManeuvering(id) ? " (maneuver)" : " (scheduled)")
                        + " holds the group";
                }

                boolean otherHolds = id.equals(earlierHolder);
                if (ManeuverPriority.mustWait(this.train, other, otherHolds, selfHolds, ahead)) {
                    return "yields to maneuvering train " + ConsistNose.shortId(id);
                }
            }

            return null;
        }

        void finish() {
            if (this.chainStart != null && Double.isNaN(this.stopAt)) {
                this.claimChain();
            }
        }

        private void claimChain() {
            this.chain.forEach(this::claim);
            if (this.reserve) {
                Map<UUID, TrainSignals.ChainClaim> held = TrainSignals.CHAINS.computeIfAbsent(this.train.id(), key -> new HashMap<>());
                this.chain.forEach((groupId, signal) -> held.put(groupId, new TrainSignals.ChainClaim(signal.getId(), TrainStations.stationTickCounter)));
            }

            this.chain.clear();
        }

        private void claim(UUID groupId, SignalBoundary signal) {
            if (this.reserve) {
                TrainSignals.RESERVED.put(groupId, new TrainSignals.Reservation(this.train.id(), signal.getId()));
            }
        }
    }
}
