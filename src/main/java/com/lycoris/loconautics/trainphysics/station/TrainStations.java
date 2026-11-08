package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.computercraft.CcControl;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.trainmail.TrainMailTransfer;
import com.lycoris.loconautics.network.LoconauticsNetwork;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.door.TrainDoors;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainBrakes;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IEdgePointListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.theme.Color;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class TrainStations {
    public static final double STATION_PARK_RADIUS = 3.0;
    static final int STATION_NAME_COLOR = 7358000;
    static final double LEFT_STATION_DISTANCE_SQR = 16.0;
    static final int DOOR_SETTLE_TICKS = 5;
    static final double DOOR_CLOSE_SPEED = 0.02;
    static final int APPROACH_COLOR = 16761412;
    static final int ARRIVED_COLOR = 5413141;
    static final int REMAINING_COLOR = 5524805;
    static final double DEPART_SPEED = 0.02;
    static final int PARK_SETTLE_TICKS = 5;
    public static int stationTickCounter;
    private static final Map<UUID, SableTrain> OWNERS = new HashMap<>();
    private static int ownersTick = Integer.MIN_VALUE;

    public static void tickStation(SableTrain train, AnalogControllerBlockEntity controller) {
        SableTrain owner = stopOwner(train);
        if (owner != train) {
            mirrorStop(train, owner);
            train.setDwellTicks(train.stationState() == SableTrain.StationState.STOPPED ? train.dwellTicks() + 1 : 0);
            if (!CcControl.holdsDoors(train.id())) {
                TrainDoors.setDoors(train, doorsShouldOpen(train));
            }
        } else {
            tickStationState(train, controller);
            train.setDwellTicks(train.stationState() == SableTrain.StationState.STOPPED ? train.dwellTicks() + 1 : 0);
            TrainMailTransfer.tick(train);
            if (!CcControl.holdsDoors(train.id())) {
                TrainDoors.setDoors(train, doorsShouldOpen(train));
            }
        }
    }

    private static boolean doorsShouldOpen(SableTrain train) {
        return train.stationState() == SableTrain.StationState.STOPPED ? train.dwellTicks() >= 5 : train.doorsOpen() && Math.abs(train.speed()) <= 0.02;
    }

    private static void tickStationState(SableTrain train, AnalogControllerBlockEntity controller) {
        AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
        boolean attended = drive != null && (drive.hasUser() || drive.isScheduleControlled()) || CcControl.held(train.id());
        boolean parked = Math.abs(train.speed()) <= 0.02
            && (train.stationState() == SableTrain.StationState.STOPPED || train.scheduleHold() || attended || train.atStation() != null);
        boolean stopping = train.stationState() == SableTrain.StationState.STOPPING || train.stationState() == SableTrain.StationState.STOPPED;
        boolean standing = !train.isPoseSettling() && Math.abs(train.speed()) <= 0.02;
        boolean guardTripped = train.isDerailed() || train.isPhysics() || !train.isPowered() && !parked && !stopping && !standing;
        if (guardTripped) {
            if (!train.isDerailed() && !train.isPhysics()) {
                double sight = train.speed() * train.speed() / 0.006;
                TrainStations.StationTarget coasting = scanAhead(train, Math.max(32.0, 1.5 * sight), null);
                train.setSighted(coasting == null ? null : coasting.station(), coasting == null ? Double.MAX_VALUE : coasting.distance());
            } else {
                train.setSighted(null, Double.MAX_VALUE);
            }

            if (train.stationState() != SableTrain.StationState.RUNNING) {
                train.setStationState(SableTrain.StationState.RUNNING);
                train.setCurrentStation(null);
                train.setDistanceToStation(Double.MAX_VALUE);
            }
        } else {
            boolean schedulePulling = train.isGhost()
                ? train.isScheduleDriven() && Math.abs(train.targetSpeed()) > 1.0E-6
                : drive != null && drive.isScheduleControlled() && drive.getCurrentPower() != 0;
            boolean pulling = schedulePulling
                || !train.isGhost() && drive != null && drive.getCurrentPower() != 0
                || CcControl.isDriving(train.id())
                || Math.abs(ConsistPower.targetFor(train)) > 1.0E-6;
            ConsistPower.Result consistPower = ConsistPower.resultFor(train);
            boolean engineTurning = consistPower != null && consistPower.engineTurning();
            boolean spaceHeld = controller != null && controller.isSpaceHeld();
            boolean wantsStop = spaceHeld && engineTurning || train.scheduleHold() || train.isScheduleDriven();
            if (!pulling
                && train.stationState() != SableTrain.StationState.STOPPED
                && !TrainSignals.consistHeldAtRed(train)
                && Math.abs(train.targetSpeed()) <= 1.0E-6
                && Math.abs(train.speed()) <= 0.02) {
                TrainStations.StationTarget near = scanStation(train, 3.0, null, 3.0);
                int heading = near != null && !StationLanding.wheelOnMark(near.distance()) ? ConsistNose.senseOf(train) : 0;
                if (heading != 0) {
                    near = scoutDirection(noseRail(train), nosePoint(train), 3.0, heading, null, 0.0);
                }

                if (near != null && (wantsStop || StationLanding.wheelOnMark(near.distance()))) {
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[nose2c] car {} relatches {} at {} scan direction {} heading {}",
                            new Object[]{
                                ConsistNose.shortId(train.id()), near.station().name, String.format("%.3f", near.distance()), near.direction(), heading
                            }
                        );
                    issue255(train, "relatch", near.station(), near.distance(), near.direction());
                    StationLanding.snapConsist(train, near.direction() * near.distance());
                    train.setCurrentStation(near.station());
                    train.setDistanceToStation(0.0);
                    train.setStationDirection(near.direction());
                    train.setStationState(SableTrain.StationState.STOPPED);
                    train.setAtStation(near.station());
                    return;
                }
            }

            boolean forwardHeld = controller != null && controller.isForwardHeld();
            boolean reverseHeld = controller != null && controller.isReverseHeld();
            ServerPlayer operator = controller != null ? SableTrainDriver.operatorOf(train, controller) : null;
            boolean resend = stationTickCounter % 10 == 0;
            switch (train.stationState()) {
                case RUNNING:
                    double sight = train.speed() * train.speed() / 0.006;
                    TrainStations.StationTarget target = scanAhead(train, Math.max(32.0, 1.5 * sight), null);
                    train.setSighted(target == null ? null : target.station(), target == null ? Double.MAX_VALUE : target.distance());
                    if (target == null || target.distance() < StationLanding.hardStopDistance(train)) {
                        return;
                    }

                    if (spaceHeld && engineTurning) {
                        train.setCurrentStation(target.station());
                        train.setDistanceToStation(target.distance());
                        train.setApproachTotal(target.distance());
                        train.setStationDirection(target.direction());
                        train.setStationState(SableTrain.StationState.STOPPING);
                        if (controller != null) {
                            controller.setLocked(false);
                        }
                    } else if (operator != null && resend && engineTurning) {
                        sendApproachPrompt(operator, target.station());
                    }
                    break;
                case STOPPING:
                    if (!spaceHeld || !engineTurning) {
                        train.setStationState(SableTrain.StationState.RUNNING);
                        train.setCurrentStation(null);
                        train.setDistanceToStation(Double.MAX_VALUE);
                        return;
                    }

                    double searchRange = Math.max(32.0, train.distanceToStation() + 8.0);
                    double direction = train.stationDirection();
                    TrainStations.StationTarget live = scoutDirection(noseRail(train), nosePoint(train), searchRange, direction, train.currentStation(), 0.0);
                    if (live == null) {
                        StationLanding.applySnap(train, 0.0);
                        if (controller != null) {
                            controller.setLocked(false);
                            controller.setCurrentPower(0);
                        }

                        train.setStationState(SableTrain.StationState.STOPPED);
                        train.setAtStation(train.currentStation());
                        return;
                    }

                    double distance = live.distance();
                    train.setDistanceToStation(distance);
                    double mag = StationLanding.approachSpeed(train, distance);
                    boolean snap = StationLanding.canSnap(train, distance);
                    if (snap) {
                        issue255(train, "landing snap", train.currentStation(), distance, direction);
                        StationLanding.snapConsist(train, direction * distance);
                        if (controller != null) {
                            controller.setLocked(false);
                            controller.setCurrentPower(0);
                        }

                        train.setStationState(SableTrain.StationState.STOPPED);
                        train.setAtStation(train.currentStation());
                        return;
                    }

                    double bodyDirection = direction * ConsistNose.frameFlip(train);
                    train.setTargetSpeed(train.isPowered() && engineTurning ? bodyDirection * mag : 0.0);
                    train.setServoBrake(true);
                    if (controller != null) {
                        controller.setCurrentPower((int)Math.signum(bodyDirection) * StationLanding.powerNotches(train, mag));
                    }

                    if (operator != null) {
                        sendApproachBar(operator, train, distance);
                    }
                    break;
                case STOPPED:
                    boolean commanded = pulling || forwardHeld || reverseHeld;
                    if (!commanded) {
                        train.setTargetSpeed(0.0);
                        TrainBrakes.holdSoft(train);
                        if (controller != null) {
                            controller.setCurrentPower(0);
                        }
                    }

                    boolean rolled = train.dwellTicks() >= 5 && Math.abs(train.speed()) > 0.02;
                    if (commanded || rolled) {
                        if (operator != null && commanded) {
                            sendStationPrompt(operator, "loconautics.train.departing_from", train.currentStation());
                        }

                        train.setStationState(SableTrain.StationState.DEPARTING);
                        train.setCurrentStation(null);
                        train.setDistanceToStation(Double.MAX_VALUE);
                        return;
                    }

                    if (operator != null && resend) {
                        sendStationPrompt(operator, "loconautics.train.arrived_at", train.currentStation());
                    }
                    break;
                case DEPARTING:
                    train.setStationState(SableTrain.StationState.RUNNING);
            }
        }
    }

    public static boolean parkOnMark(SableTrain train, GlobalStation station) {
        if (!train.isDerailed() && !train.isPhysics() && !(Math.abs(train.speed()) > 0.02)) {
            TrainStations.StationTarget near = scanStation(train, 3.0, station, 3.0);
            if (near == null) {
                return false;
            }

            issue255(train, "park on mark", near.station(), near.distance(), near.direction());
            StationLanding.snapConsist(train, near.direction() * near.distance(), 0.0);
            AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
            if (drive != null) {
                drive.setLocked(false);
                drive.setCurrentPower(0);
            }

            train.setCurrentStation(near.station());
            train.setDistanceToStation(0.0);
            train.setStationDirection(near.direction());
            train.setStationState(SableTrain.StationState.STOPPED);
            train.setAtStation(near.station());
            return true;
        } else {
            return false;
        }
    }

    public static void updateStationPresence(SableTrain train) {
        if (train.atStation() != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            SableTrain.Car car = train.car();
            if (container != null && car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                Vector3dc p = sub.logicalPose().position();
                Vector3dc anchor = train.atStationPos();
                if (anchor == null) {
                    train.setAtStationPos(new Vector3d(p));
                } else {
                    double dx = p.x() - anchor.x();
                    double dz = p.z() - anchor.z();
                    if (dx * dx + dz * dz > 16.0) {
                        train.setAtStation(null);
                    }
                }
            }
        }
    }

    public static boolean isStationInRange(SableTrain train, GlobalStation station, double maxDistance, double frontOvershoot) {
        return train != null && station != null && !train.isDerailed() ? scanStation(train, maxDistance, station, frontOvershoot) != null : false;
    }

    public static boolean sameStation(GlobalStation a, GlobalStation b) {
        return a == b || a != null && b != null && a.id != null && a.id.equals(b.id);
    }

    static TrainStations.StationTarget scanStation(SableTrain train, double maxDistance, GlobalStation target) {
        return scanStation(train, maxDistance, target, 0.0);
    }

    static TrainStations.StationTarget scanStation(SableTrain train, double maxDistance, GlobalStation target, double frontOvershoot) {
        return scoutBothWays(noseRail(train), nosePoint(train), maxDistance, target, frontOvershoot);
    }

    static TrainStations.StationTarget scanAhead(SableTrain train, double maxDistance, GlobalStation target) {
        int sense = Math.abs(train.speed()) > 0.02 ? ConsistNose.senseOf(train) : 0;
        return sense == 0 ? scanStation(train, maxDistance, target) : scoutDirection(noseRail(train), nosePoint(train), maxDistance, sense, target, 0.0);
    }

    private static boolean scheduleDrives(SableTrain car) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(car.id());
        return runtime != null && runtime.drivesTrain();
    }

    public static SableTrain stopOwner(SableTrain train) {
        ServerLevel level = train.level();
        UUID id = train.car() == null ? null : train.car().subLevelId();
        if (level != null && id != null) {
            if (ownersTick != stationTickCounter) {
                ownersTick = stationTickCounter;
                OWNERS.clear();
            }

            SableTrain known = OWNERS.get(id);
            if (known != null) {
                return known;
            }

            List<SableTrain> cars = new ArrayList<>();

            for (UUID other : TrainConsist.connectedSubLevels(level, id)) {
                SableTrain candidate = SableTrainRegistry.bySubLevel(other);
                if (candidate != null && candidate.car() != null && candidate.car().subLevelId() != null && !candidate.isDerailed()) {
                    cars.add(candidate);
                }
            }

            if (cars.isEmpty()) {
                return train;
            }

            List<TrainStations.OwnerCandidate> candidates = new ArrayList<>();

            for (SableTrain car : cars) {
                candidates.add(
                    new TrainStations.OwnerCandidate(
                        car,
                        SableTrainDriver.operatedControllerIn(car) == null,
                        !scheduleDrives(car),
                        SableTrainDriver.driverRank(car),
                        SableTrainDriver.controllerIn(car) == null
                    )
                );
            }

            candidates.sort(
                Comparator.comparing(TrainStations.OwnerCandidate::idle)
                    .thenComparing(TrainStations.OwnerCandidate::unscheduled)
                    .thenComparingInt(TrainStations.OwnerCandidate::rank)
                    .thenComparing(TrainStations.OwnerCandidate::noCab)
                    .thenComparing(candidate -> candidate.car().car().subLevelId())
            );
            SableTrain owner = candidates.get(0).car();

            for (SableTrain car : cars) {
                OWNERS.put(car.car().subLevelId(), owner);
            }

            return owner;
        } else {
            return train;
        }
    }

    private static void mirrorStop(SableTrain train, SableTrain owner) {
        train.setStationState(owner.stationState());
        train.setSighted(owner.sightedStation(), owner.sightedDistance());
        train.setCurrentStation(owner.currentStation());
        train.setDistanceToStation(owner.distanceToStation());
        train.setStationDirection(owner.stationDirection());
        if (train.atStation() != owner.atStation()) {
            train.setAtStation(owner.atStation());
        }
    }

    private static void issue255(SableTrain train, String how, GlobalStation station, double gap, double direction) {
        ConsistNose.Nose nose = ConsistNose.of(train);
        StringBuilder bogeys = new StringBuilder();

        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            bogeys.append(bogey.rail() == nose.rail() ? "NOSE " : "")
                .append(bogey.rail().center() == null ? "none" : String.format("%.2f,%.2f", bogey.rail().center().x, bogey.rail().center().z))
                .append(' ');
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[issue255] {} car={} station={} gap={} dir={} nowCar={} noseCar={} sense={} flip={} speed={} cars={} noseOnThisCar={} bogeysOfThisCar={}",
                new Object[]{
                    how,
                    ConsistNose.shortId(train.id()),
                    station == null ? "none" : station.name,
                    String.format("%.3f", gap),
                    direction,
                    ConsistNose.shortId(train.car().subLevelId()),
                    ConsistNose.shortId(nose.car() == null ? null : nose.car().car().subLevelId()),
                    ConsistNose.senseOf(train),
                    ConsistNose.frameFlip(train),
                    String.format("%.3f", train.speed()),
                    TrainConsist.orderedChain(train.level(), train.car().subLevelId()).size(),
                    nose.car() == train,
                    bogeys
                }
            );
    }

    public static TravellingPoint nosePoint(SableTrain train) {
        return ConsistNose.point(train);
    }

    public static RailCarriage noseRail(SableTrain train) {
        return ConsistNose.rail(train);
    }

    public static SableTrain.Bogey frontBogey(SableTrain train, List<SableTrain.Bogey> bogeys) {
        Vec3 nose = SableTrainDriver.axleNose(train);
        if (nose == null) {
            return bogeys.get(bogeys.size() - 1);
        }

        SableTrain.Bogey best = bogeys.get(0);
        double bestDot = -Double.MAX_VALUE;

        for (SableTrain.Bogey bogey : bogeys) {
            Vector3d center = bogey.rail() == null ? null : bogey.rail().center();
            if (center != null) {
                double dot = center.x * nose.x + center.y * nose.y + center.z * nose.z;
                if (dot > bestDot) {
                    bestDot = dot;
                    best = bogey;
                }
            }
        }

        return best;
    }

    static TrainStations.StationTarget scoutBothWays(
        RailCarriage carriage, TravellingPoint origin, double maxDistance, GlobalStation target, double frontOvershoot
    ) {
        return origin != null && origin.edge != null
            ? nearer(
                scoutDirection(carriage, origin, maxDistance, 1.0, target, frontOvershoot),
                scoutDirection(carriage, origin, maxDistance, -1.0, target, frontOvershoot)
            )
            : null;
    }

    static TrainStations.StationTarget nearer(TrainStations.StationTarget a, TrainStations.StationTarget b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            return a.distance() <= b.distance() ? a : b;
        }
    }

    static TrainStations.StationTarget scoutDirection(
        RailCarriage carriage, TravellingPoint origin, double maxDistance, double direction, GlobalStation target, double frontOvershoot
    ) {
        if (carriage != null && carriage.graph() != null && origin != null && origin.edge != null) {
            TrainStations.ScanReport report = scout(carriage, origin, maxDistance, direction, target, frontOvershoot);
            return report.station() == null ? null : new TrainStations.StationTarget(report.station(), report.distance(), direction);
        } else {
            return null;
        }
    }

    private static TrainStations.ScanReport scout(
        RailCarriage carriage, TravellingPoint origin, double maxDistance, double direction, GlobalStation target, double frontOvershoot
    ) {
        TravellingPoint scout = new TravellingPoint(origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
        scout.travel(
            carriage.graph(),
            -direction * StationLanding.parkOffset,
            scout.steer(SteerDirection.NONE, carriage.upNormal()),
            scout.ignoreEdgePoints(),
            scout.ignoreTurns(),
            scout.ignorePortals()
        );
        GlobalStation[] found = new GlobalStation[]{null};
        double[] foundDistance = new double[]{0.0};
        int[] edgePoints = new int[]{0};
        List<String> seen = new ArrayList<>();
        IEdgePointListener listener = (distance, pair) -> {
            edgePoints[0]++;
            if (!(pair.getFirst() instanceof GlobalStation station)) {
                return false;
            } else {
                Couple var13x = (Couple)pair.getSecond();
                boolean approachable = station.canApproachFrom((TrackNode)var13x.getSecond());
                boolean matches = target == null || sameStation(station, target);
                seen.add(station.name + " @" + String.format(Locale.ROOT, "%.3f", distance) + " canApproach=" + approachable + " matchesTarget=" + matches);
                if (matches && (approachable || !(Math.abs(distance) > frontOvershoot))) {
                    found[0] = station;
                    foundDistance[0] = Math.abs(distance);
                    return true;
                } else {
                    return false;
                }
            }
        };
        scout.travel(
            carriage.graph(),
            direction * maxDistance,
            scout.steer(SteerDirection.NONE, carriage.upNormal()),
            listener,
            scout.ignoreTurns(),
            scout.ignorePortals()
        );
        return new TrainStations.ScanReport(found[0], foundDistance[0], edgePoints[0], seen);
    }

    static void sendApproachPrompt(ServerPlayer player, GlobalStation station) {
        if (station != null) {
            Component name = Component.literal(station.name).withStyle(s -> s.withColor(7358000));
            Component text = Component.translatable("loconautics.train.approach_station", new Object[]{Component.keybind("key.jump"), name});
            LoconauticsNetwork.sendStationPrompt(player, text, false);
        }
    }

    static void sendApproachBar(ServerPlayer player, SableTrain train, double distance) {
        double total = train.approachTotal();
        if (total <= 0.0) {
            sendStationPrompt(player, "loconautics.train.approaching_station", train.currentStation());
        } else {
            double f = distance / total;
            int progress = (int)(Mth.clamp(1.0 - (1.0 - f) * (1.0 - f), 0.0, 1.0) * 30.0);
            int filledColor = Color.mixColors(5413141, 16761412, progress / 30.0F);
            int restColor = progress == 0 ? 5413141 : 5524805;
            MutableComponent bar = Component.literal("|".repeat(30 - progress))
                .withStyle(s -> s.withColor(filledColor))
                .append(Component.literal("|".repeat(progress)).withStyle(s -> s.withColor(restColor)));
            LoconauticsNetwork.sendStationPrompt(player, bar, true);
        }
    }

    static void sendStationPrompt(ServerPlayer player, String key, GlobalStation station) {
        if (station != null) {
            Component name = Component.literal(station.name).withStyle(s -> s.withColor(7358000));
            LoconauticsNetwork.sendStationPrompt(player, Component.translatable(key, new Object[]{name}), false);
        }
    }

    private TrainStations() {
    }

    private record OwnerCandidate(SableTrain car, boolean idle, boolean unscheduled, int rank, boolean noCab) {
    }

    public record ScanReport(GlobalStation station, double distance, int edgePointsSeen, List<String> stationsSeen) {
    }

    record StationTarget(GlobalStation station, double distance, double direction) {
    }
}
