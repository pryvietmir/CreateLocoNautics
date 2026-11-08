package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class ConsistNose {
    private static final int PRUNE_TICKS = 200;
    private static final Map<UUID, ConsistNose.Nose> CACHE = new HashMap<>();
    private static final Map<UUID, ConsistNose.Nose> TAILS = new HashMap<>();
    private static final Map<UUID, ConsistNose.Memory> MEMORY = new HashMap<>();
    private static final Map<UUID, String> LOGGED = new HashMap<>();
    private static final Map<String, Integer> RELATIONS = new HashMap<>();
    private static int cacheTick = Integer.MIN_VALUE;

    public static void clearAll() {
        CACHE.clear();
        TAILS.clear();
        MEMORY.clear();
        LOGGED.clear();
        RELATIONS.clear();
    }

    public static void pinTravel(SableTrain train, Vec3 travel) {
        SableTrain ref = train != null && travel != null ? of(train).car() : null;
        if (ref != null && SableTrainDriver.scheduleDriveTangent(ref) != null) {
            pin(train, ref, signAgainst(travel, ref));
        }
    }

    public static void pinCab(SableTrain train) {
        Vec3 chord = train == null ? null : SableTrainDriver.scheduleDriveTangent(train);
        Vec3 cab = train == null ? null : SableTrainDriver.headingNose(train);
        if (cab == null) {
            cab = train == null ? null : SableTrainDriver.driverLook(train);
        }

        if (chord != null && cab != null) {
            pin(train, train, chord.dot(cab) < 0.0 ? -1.0 : 1.0);
        } else {
            forget(train);
        }
    }

    public static void forget(SableTrain train) {
        UUID id = idOf(train);
        if (id != null) {
            forget(id);
        }
    }

    private static void forget(UUID carId) {
        boolean removed = MEMORY.values().removeIf(memory -> memory.pinned() && memory.owner().equals(carId));
        if (removed) {
            CACHE.clear();
            TAILS.clear();
        }
    }

    private static void pin(SableTrain train, SableTrain ref, double sign) {
        UUID id = idOf(train);
        ServerLevel level = train.level();
        if (id != null && level != null) {
            ConsistNose.Memory memory = new ConsistNose.Memory(id, idOf(ref), sign, true);
            store(consistCars(train), memory);
            MEMORY.put(id, memory);
            CACHE.clear();
            TAILS.clear();
        }
    }

    private static double signAgainst(Vec3 dir, SableTrain car) {
        Vec3 chord = SableTrainDriver.scheduleDriveTangent(car);
        return chord != null && !(chord.dot(dir) >= 0.0) ? -1.0 : 1.0;
    }

    private static void store(List<SableTrain> cars, ConsistNose.Memory memory) {
        for (SableTrain car : cars) {
            UUID carId = car.car().subLevelId();
            ConsistNose.Memory old = MEMORY.get(carId);
            if (old == null || !old.pinned() || memory.pinned()) {
                MEMORY.put(carId, memory);
            }
        }
    }

    private static UUID idOf(SableTrain train) {
        return train != null && train.car() != null ? train.car().subLevelId() : null;
    }

    public static ConsistNose.Nose of(SableTrain train) {
        UUID id = idOf(train);
        if (id == null) {
            return local(train);
        }

        refreshCaches();
        return CACHE.computeIfAbsent(id, key -> {
            ConsistNose.Nose consist = fromChain(train, false);
            return consist != null ? consist : local(train);
        });
    }

    public static ConsistNose.Nose tail(SableTrain train) {
        UUID id = idOf(train);
        if (id == null) {
            return of(train);
        }

        refreshCaches();
        ConsistNose.Nose tail = TAILS.computeIfAbsent(id, key -> fromChain(train, true));
        return tail != null ? tail : of(train);
    }

    private static void refreshCaches() {
        if (cacheTick != TrainStations.stationTickCounter) {
            cacheTick = TrainStations.stationTickCounter;
            CACHE.clear();
            TAILS.clear();
            RELATIONS.clear();
            if (cacheTick % 200 == 0) {
                MEMORY.keySet().removeIf(carId -> SableTrainRegistry.bySubLevel(carId) == null);
                LOGGED.keySet().removeIf(carId -> SableTrainRegistry.bySubLevel(carId) == null);
            }
        }
    }

    public static TravellingPoint point(SableTrain train) {
        return of(train).point();
    }

    public static RailCarriage rail(SableTrain train) {
        return of(train).rail();
    }

    public static int senseOf(SableTrain train) {
        ServerLevel level = train.level();
        UUID id = idOf(train);
        int onNose = level != null && id != null ? tangentSign(of(train)) : 0;
        ConsistNose.Heading heading = onNose == 0 ? null : heading(consistCars(train));
        return heading == null ? 0 : (int)heading.refSign() * relation(level, heading.ref(), of(train).car()) * onNose;
    }

    public static int frameFlip(SableTrain train) {
        return train != null && train.car() != null ? frameFlip(train, of(train)) : 1;
    }

    public static int frameFlip(SableTrain train, ConsistNose.Nose nose) {
        ServerLevel level = train != null && train.car() != null ? train.level() : null;
        int onNose = level == null ? 0 : tangentSign(nose);
        return onNose == 0 ? 1 : relation(level, train, nose.car()) * onNose;
    }

    private static int tangentSign(ConsistNose.Nose nose) {
        Vec3 tangent = noseTangent(nose);
        Vec3 chord = tangent == null ? null : SableTrainDriver.scheduleDriveTangent(nose.car());
        if (chord == null) {
            return 0;
        } else {
            return chord.x * tangent.x + chord.z * tangent.z < 0.0 ? -1 : 1;
        }
    }

    private static int relation(ServerLevel level, SableTrain from, SableTrain to) {
        UUID fromId = idOf(from);
        UUID toId = idOf(to);
        return fromId != null && toId != null && !fromId.equals(toId)
            ? RELATIONS.computeIfAbsent(fromId + ">" + toId, key -> walkRelation(level, fromId, toId))
            : 1;
    }

    private static int walkRelation(ServerLevel level, UUID fromId, UUID toId) {
        List<UUID> chain = TrainConsist.orderedChain(level, fromId);
        int start = chain.indexOf(fromId);
        int end = chain.indexOf(toId);
        SableTrain first = SableTrainRegistry.bySubLevel(fromId);
        SableTrain last = SableTrainRegistry.bySubLevel(toId);
        Vec3 previous = first == null ? null : SableTrainDriver.scheduleDriveTangent(first);
        if (start >= 0 && end >= 0) {
            int step = end > start ? 1 : -1;
            int sign = 1;

            for (int i = start + step; i >= 0 && i < chain.size(); i += step) {
                SableTrain car = SableTrainRegistry.bySubLevel(chain.get(i));
                Vec3 chord = car == null ? null : SableTrainDriver.scheduleDriveTangent(car);
                if (chord != null && previous != null && previous.dot(chord) < 0.0) {
                    sign = -sign;
                }

                previous = chord != null ? chord : previous;
                if (i == end) {
                    break;
                }
            }

            return sign;
        } else {
            return previous != null && last != null && !(signAgainst(previous, last) > 0.0) ? -1 : 1;
        }
    }

    public static Vec3 wheelPos(SableTrain train) {
        ConsistNose.Nose nose = of(train);
        if (nose.rail() != null && nose.point() != null) {
            return nose.point() == nose.rail().leading() ? nose.rail().leadingPos() : nose.rail().trailingPos();
        } else {
            return null;
        }
    }

    public static Vec3 noseTangent(SableTrain train) {
        return train != null && train.car() != null ? noseTangent(of(train)) : null;
    }

    public static Vec3 noseTangent(ConsistNose.Nose nose) {
        TravellingPoint point = nose.point();
        return point != null && point.edge != null ? RailSeams.worldDirOf(point.edge.node1.getLocation(), point.edge.getDirectionAt(point.position)) : null;
    }

    private static ConsistNose.Nose fromChain(SableTrain train, boolean tail) {
        ServerLevel level = train.level();
        UUID id = idOf(train);
        if (level != null && id != null) {
            List<SableTrain> cars = consistCars(train);
            ConsistNose.Heading heading = heading(cars);
            if (heading == null) {
                return null;
            }

            Vec3 ahead = tail ? heading.dir().scale(-1.0) : heading.dir();
            ConsistNose.Nose chained = frontOfChain(level, id, cars, heading, ahead);
            if (chained != null) {
                if (!tail) {
                    trace(id, chained, heading, "chain");
                }

                return chained;
            } else {
                SableTrain bestCar = null;
                SableTrain.Bogey bestBogey = null;
                double bestReach = -Double.MAX_VALUE;
                List<SableTrain> ends = endCars(level, id, cars);

                for (SableTrain car : ends.isEmpty() ? cars : ends) {
                    for (SableTrain.Bogey bogey : car.car().bogeys()) {
                        if (scoutable(bogey.rail())) {
                            Vector3d center = bogey.rail().center();
                            double reach = center.x * ahead.x + center.y * ahead.y + center.z * ahead.z;
                            if (reach > bestReach) {
                                bestReach = reach;
                                bestBogey = bogey;
                                bestCar = car;
                            }
                        }
                    }
                }

                if (bestBogey == null) {
                    return null;
                }

                ConsistNose.Nose projected = new ConsistNose.Nose(bestCar, bestBogey.rail(), outerPoint(bestBogey.rail(), ahead));
                if (!tail) {
                    trace(id, projected, heading, "projection");
                }

                return projected;
            }
        } else {
            return null;
        }
    }

    private static void trace(UUID id, ConsistNose.Nose nose, ConsistNose.Heading heading, String how) {
        String line = how
            + " "
            + heading.source()
            + " nose car "
            + shortId(nose.car().car().subLevelId())
            + " ref car "
            + shortId(heading.ref().car().subLevelId())
            + " dir "
            + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", heading.dir().x, heading.dir().y, heading.dir().z);
        String key = how + heading.source() + nose.car().car().subLevelId() + nose.point().node1.getLocation() + signKey(heading.dir());
        if (!key.equals(LOGGED.put(id, key))) {
            LoconauticsConstants.LOGGER.debug("[nose2c] consist {} {}", shortId(id), line);
        }
    }

    private static String signKey(Vec3 dir) {
        return (dir.x < 0.0 ? "-" : "+") + (dir.z < 0.0 ? "-" : "+");
    }

    public static String shortId(UUID id) {
        return id == null ? "none" : id.toString().substring(0, 8);
    }

    private static ConsistNose.Heading heading(List<SableTrain> cars) {
        if (cars.isEmpty()) {
            return null;
        }

        ConsistNose.Heading pinned = remembered(cars, true);
        if (pinned != null) {
            return pinned;
        }

        ConsistNose.Heading moving = moving(cars);
        if (moving != null) {
            UUID refId = idOf(moving.ref());
            store(cars, new ConsistNose.Memory(refId, refId, moving.refSign(), false));
            return moving;
        }

        SableTrain driver = driverCar(cars);
        boolean seated = driver != null && SableTrainDriver.driverRank(driver) == 0;
        if (!seated || stopping(cars)) {
            ConsistNose.Heading kept = remembered(cars, false);
            if (kept != null) {
                return kept;
            }
        }

        return cab(cars, driver);
    }

    private static boolean stopping(List<SableTrain> cars) {
        for (SableTrain car : cars) {
            if (car.stationState() == SableTrain.StationState.STOPPING) {
                return true;
            }
        }

        return false;
    }

    private static ConsistNose.Heading remembered(List<SableTrain> cars, boolean pinnedOnly) {
        for (SableTrain car : sorted(cars)) {
            ConsistNose.Memory memory = MEMORY.get(car.car().subLevelId());
            if (memory != null && (!pinnedOnly || memory.pinned())) {
                SableTrain ref = carIn(cars, memory.ref());
                Vec3 chord = ref == null ? null : SableTrainDriver.scheduleDriveTangent(ref);
                if (chord != null) {
                    return new ConsistNose.Heading(ref, chord.scale(memory.sign()), memory.sign(), memory.pinned() ? "pinned" : "memory");
                }
            }
        }

        return null;
    }

    private static ConsistNose.Heading moving(List<SableTrain> cars) {
        SableTrain fastest = null;

        for (SableTrain car : sorted(cars)) {
            if (Math.abs(car.speed()) > 0.02
                && (fastest == null || Math.abs(car.speed()) > Math.abs(fastest.speed()))
                && SableTrainDriver.scheduleDriveTangent(car) != null) {
                fastest = car;
            }
        }

        if (fastest == null) {
            return null;
        }

        Vec3 chord = SableTrainDriver.scheduleDriveTangent(fastest);
        return new ConsistNose.Heading(fastest, chord.scale(Math.signum(fastest.speed())), Math.signum(fastest.speed()), "motion");
    }

    private static ConsistNose.Heading cab(List<SableTrain> cars, SableTrain driver) {
        Vec3 look = driver == null ? null : SableTrainDriver.driverLook(driver);
        if (look != null) {
            return new ConsistNose.Heading(driver, look, signAgainst(look, driver), "driver");
        }

        for (SableTrain car : sorted(cars)) {
            Vec3 axle = SableTrainDriver.headingNose(car);
            if (axle != null) {
                return new ConsistNose.Heading(car, axle, signAgainst(axle, car), "axle");
            }
        }

        return null;
    }

    private static List<SableTrain> sorted(List<SableTrain> cars) {
        List<SableTrain> ordered = new ArrayList<>(cars);
        ordered.sort(Comparator.comparing(car -> car.car().subLevelId()));
        return ordered;
    }

    private static SableTrain driverCar(List<SableTrain> cars) {
        SableTrain best = null;
        int bestRank = 3;

        for (SableTrain car : sorted(cars)) {
            int rank = SableTrainDriver.driverRank(car);
            if (rank < bestRank) {
                best = car;
                bestRank = rank;
            }
        }

        return best;
    }

    private static List<SableTrain> endCars(ServerLevel level, UUID id, List<SableTrain> cars) {
        List<UUID> chain = TrainConsist.orderedChain(level, id);
        if (chain.size() < 3) {
            return List.of();
        }

        SableTrain first = null;
        SableTrain last = null;

        for (SableTrain car : cars) {
            if (car.car().subLevelId().equals(chain.get(0))) {
                first = car;
            } else if (car.car().subLevelId().equals(chain.get(chain.size() - 1))) {
                last = car;
            }
        }

        return first != null && last != null ? List.of(first, last) : List.of();
    }

    public static boolean scoutable(RailCarriage rail) {
        if (rail != null && rail.graph() != null && rail.center() != null) {
            TravellingPoint lead = rail.leading();
            TravellingPoint trail = rail.trailing();
            return lead != null && lead.edge != null || trail != null && trail.edge != null;
        } else {
            return false;
        }
    }

    private static List<SableTrain> consistCars(SableTrain train) {
        boolean ghost = train.isGhost();
        Set<UUID> members = new HashSet<>();
        if (ghost) {
            members.addAll(train.consistCache());
            members.add(idOf(train));
        } else {
            members.addAll(TrainConsist.connectedSubLevels(train.level(), idOf(train)));
        }

        List<SableTrain> cars = new ArrayList<>();

        for (UUID other : members) {
            SableTrain car = SableTrainRegistry.bySubLevel(other);
            if (car != null && car.car() != null && !car.isDerailed() && (ghost || !car.isGhost())) {
                cars.add(car);
            }
        }

        return cars;
    }

    private static ConsistNose.Nose frontOfChain(ServerLevel level, UUID id, List<SableTrain> cars, ConsistNose.Heading heading, Vec3 look) {
        List<UUID> chain = TrainConsist.orderedChain(level, id);
        int at = chain.indexOf(heading.ref().car().subLevelId());
        if (chain.size() >= 2 && at >= 0) {
            Vector3d before = centerOf(carIn(cars, chain.get(Math.max(at - 1, 0))));
            Vector3d after = centerOf(carIn(cars, chain.get(Math.min(at + 1, chain.size() - 1))));
            if (before != null && after != null) {
                boolean towardLast = (after.x - before.x) * look.x + (after.y - before.y) * look.y + (after.z - before.z) * look.z >= 0.0;
                int step = towardLast ? -1 : 1;

                for (int i = towardLast ? chain.size() - 1 : 0; i >= 0 && i < chain.size(); i += step) {
                    SableTrain end = carIn(cars, chain.get(i));
                    if (end != null) {
                        int inward = i + step;
                        SableTrain neighbour = inward >= 0 && inward < chain.size() ? carIn(cars, chain.get(inward)) : null;
                        Vector3d from = neighbour == null ? null : centerOf(neighbour);
                        SableTrain.Bogey outer = outerBogey(end, from);
                        return outer == null ? null : new ConsistNose.Nose(end, outer.rail(), outerPoint(outer.rail(), look));
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static SableTrain carIn(List<SableTrain> cars, UUID id) {
        for (SableTrain car : cars) {
            if (car.car().subLevelId().equals(id)) {
                return car;
            }
        }

        return null;
    }

    private static Vector3d centerOf(SableTrain car) {
        if (car == null) {
            return null;
        }

        Vector3d sum = new Vector3d();
        int count = 0;

        for (SableTrain.Bogey bogey : car.car().bogeys()) {
            if (scoutable(bogey.rail())) {
                sum.add(bogey.rail().center());
                count++;
            }
        }

        return count == 0 ? null : sum.div(count);
    }

    private static SableTrain.Bogey outerBogey(SableTrain car, Vector3d from) {
        SableTrain.Bogey best = null;
        double bestGap = -Double.MAX_VALUE;

        for (SableTrain.Bogey bogey : car.car().bogeys()) {
            if (scoutable(bogey.rail())) {
                double gap = from == null ? 0.0 : bogey.rail().center().distanceSquared(from);
                if (best == null || gap > bestGap) {
                    best = bogey;
                    bestGap = gap;
                }
            }
        }

        return best;
    }

    private static TravellingPoint outerPoint(RailCarriage rail, Vec3 ahead) {
        TravellingPoint lead = rail.leading();
        TravellingPoint trail = rail.trailing();
        if (lead == null || lead.edge == null) {
            return trail;
        }

        if (trail != null && trail.edge != null) {
            Vec3 leadPos = rail.leadingPos();
            Vec3 trailPos = rail.trailingPos();
            if (ahead != null && leadPos != null && trailPos != null) {
                return leadPos.subtract(trailPos).dot(ahead) >= 0.0 ? lead : trail;
            } else {
                return lead;
            }
        } else {
            return lead;
        }
    }

    private static ConsistNose.Nose local(SableTrain train) {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        RailCarriage rail = bogeys.isEmpty() ? train.car().carriage() : TrainStations.frontBogey(train, bogeys).rail();
        if (!scoutable(rail)) {
            rail = train.car().carriage();
        }

        if (rail == null) {
            return new ConsistNose.Nose(train, null, null);
        }

        ConsistNose.Nose picked = new ConsistNose.Nose(train, rail, outerPoint(rail, SableTrainDriver.axleNose(train)));
        traceGhostNose(train, bogeys, picked);
        return picked;
    }

    private static void traceGhostNose(SableTrain train, List<SableTrain.Bogey> bogeys, ConsistNose.Nose picked) {
        if (train.isGhost()
            && train.level() != null
            && picked.rail() != null
            && picked.point() != null
            && TurntableTrace.due("ghostnose" + train.id(), train.level().getGameTime(), 10)) {
            StringBuilder ends = new StringBuilder();

            for (int i = 0; i < bogeys.size(); i++) {
                RailCarriage r = bogeys.get(i).rail();
                Vec3 a = r == null ? null : r.leadingPos();
                Vec3 b = r == null ? null : r.trailingPos();
                ends.append(" bogey")
                    .append(i)
                    .append(" lead z ")
                    .append(a == null ? "none" : String.format("%.2f", a.z))
                    .append(" trail z ")
                    .append(b == null ? "none" : String.format("%.2f", b.z));
            }

            Vec3 at = picked.point() == picked.rail().leading() ? picked.rail().leadingPos() : picked.rail().trailingPos();
            TurntableTrace.log(
                "ghostnose "
                    + shortId(train.id())
                    + " speed "
                    + String.format("%.3f", train.speed())
                    + " picked "
                    + (picked.point() == picked.rail().leading() ? "leading" : "trailing")
                    + " of bogey "
                    + bogeys.indexOf(bogeys.stream().filter(bx -> bx.rail() == picked.rail()).findFirst().orElse(null))
                    + " at z "
                    + (at == null ? "none" : String.format("%.2f", at.z))
                    + ends
            );
        }
    }

    private ConsistNose() {
    }

    private record Heading(SableTrain ref, Vec3 dir, double refSign, String source) {
    }

    private record Memory(UUID owner, UUID ref, double sign, boolean pinned) {
    }

    public record Nose(SableTrain car, RailCarriage rail, TravellingPoint point) {
    }
}
