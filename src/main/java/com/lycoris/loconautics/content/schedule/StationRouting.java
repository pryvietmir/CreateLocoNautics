package com.lycoris.loconautics.content.schedule;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.StationOccupancy;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.createmod.catnip.data.Couple;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class StationRouting {
    private static final String NOSE_AGAINST_TEXT = " would end with its nose against the station facing";

    private StationRouting() {
    }

    @Nullable
    public static StationRouting.Plan plan(SableScheduleRuntime runtime, Predicate<GlobalStation> wanted) {
        return plan(runtime, wanted, true);
    }

    @Nullable
    public static StationRouting.Plan plan(SableScheduleRuntime runtime, Predicate<GlobalStation> wanted, boolean spread) {
        return plan(runtime, wanted, spread, false);
    }

    @Nullable
    public static StationRouting.Plan plan(SableScheduleRuntime runtime, Predicate<GlobalStation> wanted, boolean spread, boolean landFront) {
        RailCarriage rail = TrainStations.noseRail(runtime.train());
        TravellingPoint nose = TrainStations.nosePoint(runtime.train());
        TrackGraph graph = RailSeams.liveGraphOf(nose, rail == null ? null : rail.graph());
        if (graph != null && nose != null) {
            int sense = SableTrainDriver.noseSense(runtime.train(), nose);
            ScheduleReadiness.Reach reach = reachOf(runtime.train());
            Vec3 axleFront = SableTrainDriver.axleNose(runtime.train());
            Vec3 heading = SableTrainDriver.headingNose(runtime.train());
            Boolean headingLeadsAxle = axleFront != null && heading != null ? axleFront.dot(heading) >= 0.0 : null;
            if (headingLeadsAxle != null && !headingLeadsAxle) {
                reach = new ScheduleReadiness.Reach(reach.backward(), reach.forward());
            }

            double length = consistLength(runtime);
            SchedulePathfinder.Routing routing = SchedulePathfinder.Routing.through(length).around(RouteTraffic.around(runtime.train(), graph));
            boolean maneuver = runtime.maneuver();
            boolean twoCabs = reach.forward() && reach.backward();
            boolean noseAlong = maneuver && landFront && !twoCabs;
            SchedulePathfinder.Trace trace = maneuver ? new SchedulePathfinder.Trace() : null;
            if (trace != null) {
                routing = routing.tracing(trace);
                traceHeading(runtime, graph, nose, sense);
            }

            if (maneuver) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[maneuver] {} nose rule: cabs {} (cab way {} other way {}) heading leads axle {} landFront {} so the nose must end along the station facing {}, any leg sense may get there",
                        new Object[]{
                            shortId(runtime.train()), twoCabs ? "two" : "one", reach.forward(), reach.backward(), headingLeadsAxle, landFront, noseAlong
                        }
                    );
                reach = new ScheduleReadiness.Reach(true, true);
            }

            int forced = maneuver ? runtime.plannedRel() : 0;
            StationRouting.Plan plan = null;

            for (int pass = 0; pass < 2; pass++) {
                int only = pass == 0 ? forced : 0;
                plan = new StationRouting.Plan();
                plan.nose = nose;
                plan.maneuver = maneuver;
                plan.noseAlong = noseAlong;
                plan.backRoom = maneuver ? clearanceOf(runtime, length) : 0.0;
                plan.trace = trace;
                if (reach.forward() && only >= 0) {
                    planAll(
                        plan,
                        runtime,
                        graph,
                        sense,
                        1,
                        routing.reversing(reach.backward()).axleLeading(headingLeadsAxle).arriving(noseAlong, 1),
                        wanted,
                        spread
                    );
                    traceLeg(plan, runtime, 1, sense);
                }

                if (reach.backward() && only <= 0) {
                    planAll(
                        plan,
                        runtime,
                        graph,
                        -sense,
                        -1,
                        routing.reversing(reach.forward()).axleLeading(headingLeadsAxle == null ? null : !headingLeadsAxle).arriving(noseAlong, -1),
                        wanted,
                        spread
                    );
                    traceLeg(plan, runtime, -1, -sense);
                }

                if (only == 0 || plan.best != null || plan.waitFor != null) {
                    break;
                }

                runtime.maneuverPlanFailed(String.join("; ", new LinkedHashSet<>(plan.rejected)));
            }

            if (maneuver) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[maneuver] {} plan gate: best={} waitFor={} anyMatch={} reversalAllowed={} forced={} reach={}/{} sense={} rejected={}",
                        new Object[]{
                            shortId(runtime.train()),
                            plan.best != null,
                            plan.waitFor != null,
                            plan.anyMatch,
                            runtime.reversalAllowed(),
                            forced,
                            reach.forward(),
                            reach.backward(),
                            sense,
                            plan.rejected
                        }
                    );
            }

            if (maneuver && plan.best == null && plan.waitFor == null && plan.stayPut != null) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[maneuver-cycle] {} no loop back to {}, staying put instead of reversing out and in",
                        shortId(runtime.train()),
                        plan.stayPut.station().name
                    );
                plan.best = plan.stayPut;
                plan.bestRel = plan.stayRel;
                plan.bestCost = 0.0;
                plan.berthed = null;
                plan.rejected.clear();
                runtime.clearStatus();
                runtime.startCooldown();
            }

            if (maneuver && runtime.reversalAllowed()) {
                if (plan.best == null && plan.waitFor == null) {
                    planReversal(plan, runtime, graph, sense, length, routing, wanted, false);
                } else if (plan.best != null && plan.best.hop() != null) {
                    preferJunction(plan, runtime, graph, sense, length, routing, wanted);
                }
            }

            if (maneuver) {
                runtime.recordBerth(plan.best != null ? plan.berthed : null);
                runtime.explainManeuver(plan.best == null && plan.waitFor == null && plan.cause != null ? plan.cause.component() : null);
                traceExits(runtime, trace);
            }

            if (maneuver && plan.best != null && plan.reversal) {
                runtime.recordReversalLeg(plan.bestRel);
            } else if (maneuver && plan.best != null) {
                runtime.recordManeuverLeg(plan.best, plan.bestRel);
            }

            List<String> unique = new ArrayList<>(new LinkedHashSet<>(plan.rejected));
            plan.rejected.clear();
            plan.rejected.addAll(unique);
            return plan;
        } else {
            return null;
        }
    }

    private static void traceHeading(SableScheduleRuntime runtime, TrackGraph graph, TravellingPoint nose, int sense) {
        TrackNode ahead = sense >= 0 ? nose.node2 : nose.node1;
        TrackNode behind = sense >= 0 ? nose.node1 : nose.node2;
        int aheadWays = ahead == null ? -1 : graph.getConnectionsFrom(ahead).size() - 1;
        int behindWays = behind == null ? -1 : graph.getConnectionsFrom(behind).size() - 1;
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} start heading: cab end looks sense {} on edge {}->{}, ways on past the cab end {} (dead end ahead {}), ways on past the tail end {}",
                new Object[]{
                    shortId(runtime.train()),
                    sense,
                    nose.node1 == null ? "none" : nose.node1.getLocation(),
                    nose.node2 == null ? "none" : nose.node2.getLocation(),
                    aheadWays,
                    aheadWays <= 0,
                    behindWays
                }
            );
    }

    private static void traceLeg(StationRouting.Plan plan, SableScheduleRuntime runtime, int rel, int sense) {
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} leg {} sense {} tried, best {} reason {}",
                new Object[]{
                    shortId(runtime.train()), rel > 0 ? "cab first" : "backing", sense, plan.best != null, plan.cause == null ? "none" : plan.cause.kind()
                }
            );
    }

    private static void traceExits(SableScheduleRuntime runtime, SchedulePathfinder.Trace trace) {
        if (trace != null) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} plate exits offered {} times, {} distinct search lines follow",
                    new Object[]{shortId(runtime.train()), trace.offered(), trace.lines().size()}
                );

            for (String line : trace.lines()) {
                LoconauticsConstants.LOGGER.debug("[maneuver] {} search {}", shortId(runtime.train()), line);
            }
        }
    }

    @Nullable
    static Boolean travelAlongFacing(SableTrain train, SchedulePath path) {
        RailCarriage rail = TrainStations.noseRail(train);
        TravellingPoint nose = TrainStations.nosePoint(train);
        TrackGraph graph = RailSeams.liveGraphOf(nose, rail == null ? null : rail.graph());
        if (graph != null && nose != null && path.station() != null) {
            TrackGraphLocation loc = SchedulePlanner.locationOf(graph, path.station());
            TrackNode a = graph.locateNode((TrackNodeLocation)loc.edge.getFirst());
            TrackNode b = graph.locateNode((TrackNodeLocation)loc.edge.getSecond());
            if (a != null && b != null) {
                List<Couple<TrackNode>> route = path.route();
                if (route != null && !route.isEmpty()) {
                    Couple<TrackNode> last = route.get(route.size() - 1);
                    if (last.getFirst() == a && last.getSecond() == b) {
                        return Boolean.TRUE;
                    } else {
                        return last.getFirst() == b && last.getSecond() == a ? Boolean.FALSE : null;
                    }
                } else {
                    return Double.isNaN(SchedulePlanner.aheadOnCurrentEdge(nose, a, b, loc)) ? null : Boolean.TRUE;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static ScheduleReadiness.Reach reachOf(SableTrain train) {
        return train.isGhost()
            ? ScheduleReadiness.Reach.FORWARD
            : ScheduleReadiness.reach(SableTrainDriver.axleSub(train), train.id(), SableTrainDriver.axleFacing(train));
    }

    static boolean twoCabs(SableTrain train) {
        ScheduleReadiness.Reach reach = reachOf(train);
        return reach.forward() && reach.backward();
    }

    private static void preferJunction(
        StationRouting.Plan plan,
        SableScheduleRuntime runtime,
        TrackGraph graph,
        int sense,
        double length,
        SchedulePathfinder.Routing routing,
        Predicate<GlobalStation> wanted
    ) {
        StationRouting.Plan junction = new StationRouting.Plan();
        junction.nose = plan.nose;
        junction.maneuver = true;
        junction.noseAlong = plan.noseAlong;
        junction.backRoom = plan.backRoom;
        junction.trace = plan.trace;
        planReversal(junction, runtime, graph, sense, length, routing, wanted, true);
        String id = shortId(runtime.train());
        if (junction.best == null) {
            LoconauticsConstants.LOGGER
                .debug("[maneuver] {} plate plan cost {} kept, no junction reversal reaches the station", id, String.format("%.1f", plan.bestCost));
        } else {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} junction reversal cost {} replaces the plate plan cost {}, the reversal needs no turn and the nose rule holds on its second leg",
                    new Object[]{id, String.format("%.1f", junction.bestCost), String.format("%.1f", plan.bestCost)}
                );
            plan.best = junction.best;
            plan.bestRel = junction.bestRel;
            plan.bestCost = junction.bestCost;
            plan.reversal = true;
            plan.waitFor = null;
            plan.berthed = null;
            plan.noRoom = null;
        }
    }

    private static void planReversal(
        StationRouting.Plan plan,
        SableScheduleRuntime runtime,
        TrackGraph graph,
        int sense,
        double length,
        SchedulePathfinder.Routing routing,
        Predicate<GlobalStation> wanted,
        boolean skipTaken
    ) {
        double span = wheelSpan(plan, runtime, graph);
        double clearance = (span > 0.0 ? span : length) + 1.0;
        traceConsist(runtime, length, span, clearance);
        double roomFound = 0.0;
        boolean tooShort = false;
        boolean roomEnough = false;
        List<GlobalStation> stations = new ArrayList<>();

        for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
            if (wanted.test(station) && !station.assembling) {
                stations.add(station);
            }
        }

        double best = Double.POSITIVE_INFINITY;

        for (int rel : new int[]{1, -1}) {
            TravellingPoint leadEnd = leadPoint(plan, runtime.train(), rel);
            int travel = rel * (leadEnd == plan.nose ? sense : SableTrainDriver.noseSense(runtime.train(), leadEnd));
            SchedulePathfinder.Routing second = routing.reversing(true).arriving(plan.noseAlong, -rel);
            List<SchedulePathfinder.Reversal> points = SchedulePathfinder.reversalPoints(graph, leadEnd, travel, clearance);
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} reversal search sense {} found {} candidate points, clearance {}",
                    new Object[]{shortId(runtime.train()), rel > 0 ? "cab first" : "backing", points.size(), clearance}
                );

            for (int i = 0; i < Math.min(points.size(), 6); i++) {
                SchedulePathfinder.Reversal p = points.get(i);
                LoconauticsConstants.LOGGER
                    .debug(
                        "[maneuver] {} point {} {} {}->{} stop {} cost {} past {} edges {}",
                        new Object[]{
                            shortId(runtime.train()),
                            rel > 0 ? "cab first" : "backing",
                            i,
                            p.from().getLocation(),
                            p.to().getLocation(),
                            String.format("%.1f", p.position()),
                            String.format("%.1f", p.cost()),
                            String.format("%.1f", p.past()),
                            p.edges().size()
                        }
                    );
            }

            int tries = 0;

            for (SchedulePathfinder.Reversal point : points) {
                if (point.past() + 1.0E-6 < clearance) {
                    tooShort = true;
                    roomFound = Math.max(roomFound, point.past());
                } else {
                    roomEnough = true;
                    double lead = point.cost() + 2.0 * point.position();
                    if (lead >= best) {
                        break;
                    }

                    if (++tries > 128) {
                        LoconauticsConstants.LOGGER.debug("[maneuver] {} reversal search stopped after {} points", shortId(runtime.train()), tries - 1);
                        break;
                    }

                    for (GlobalStation station : stations) {
                        if (!skipTaken || !verdict(plan, runtime, graph, station, null, rel, new ArrayList<>()).occupied()) {
                            TrackGraphLocation loc = SchedulePlanner.locationOf(graph, station);
                            SchedulePathfinder.Path back = SchedulePathfinder.findFrom(
                                graph,
                                point.to(),
                                point.from(),
                                graph.locateNode((TrackNodeLocation)loc.edge.getFirst()),
                                graph.locateNode((TrackNodeLocation)loc.edge.getSecond()),
                                false,
                                second
                            );
                            traceReturn(graph, runtime, rel, point, station, loc, second, back, lead, best);
                            if (back != null && !(lead + back.cost() >= best)) {
                                StationRouting.Cause tight = plan.noseAlong ? backingRoom(graph, plan, station, loc, back.edges(), back.landRel()) : null;
                                if (tight != null) {
                                    plan.rejected.add(station.name + tight.text());
                                    plan.blame(tight);
                                } else {
                                    best = lead + back.cost();
                                    TrackGraphLocation stop = new TrackGraphLocation();
                                    stop.graph = graph;
                                    stop.edge = Couple.create(point.from().getLocation(), point.to().getLocation());
                                    stop.position = point.position();
                                    plan.best = new SchedulePath(stop, travel, point.edges(), null);
                                    plan.bestRel = rel;
                                    plan.bestCost = best;
                                    plan.reversal = true;
                                    LoconauticsConstants.LOGGER
                                        .debug(
                                            "[maneuver] {} reversal chosen: leg one {} blocks then {} along the edge, {} past the last switch (needs {}, wheel span {} plus {}), the {} end lands on the stop, leg two {} blocks to \"{}\", total {}",
                                            new Object[]{
                                                shortId(runtime.train()),
                                                String.format("%.1f", point.cost()),
                                                String.format("%.1f", point.position()),
                                                String.format("%.1f", point.past()),
                                                String.format("%.1f", clearance),
                                                String.format("%.1f", span),
                                                String.format("%.1f", 1.0),
                                                rel > 0 ? "cab" : "tail",
                                                String.format("%.1f", back.cost()),
                                                station.name,
                                                String.format("%.1f", best)
                                            }
                                        );
                                }
                            }
                        }
                    }
                }
            }
        }

        if (plan.best == null) {
            blameReversal(plan, runtime, tooShort, roomEnough, clearance, roomFound);
        }
    }

    private static double clearanceOf(SableScheduleRuntime runtime, double length) {
        double span = bogeySpan(runtime);
        return (span > 0.0 ? span : length) + 2.0;
    }

    private static double wheelSpan(StationRouting.Plan plan, SableScheduleRuntime runtime, TrackGraph graph) {
        double span = bogeySpan(runtime);
        if (span <= 0.0) {
            return 0.0;
        } else {
            List<TrainLength.BogeyPlace> places = TrainLength.places(runtime.train().level(), runtime.train().car().subLevelId());
            ConsistNose.Nose tail = ConsistNose.tail(runtime.train());
            if (plan.nose != null && plan.nose.edge != null && tail != null && tail.point() != null && tail.point().edge != null) {
                Vec3 head = tail.rail().worldPointOf(plan.nose);
                Vec3 last = tail.rail().worldPointOf(tail.point());
                Vector3d first = places.get(0).world();
                Vector3d end = places.get(places.size() - 1).world();
                double headReach = head.distanceTo(new Vec3(first.x, first.y, first.z));
                double tailReach = last.distanceTo(new Vec3(end.x, end.y, end.z));
                return span + headReach + tailReach;
            } else {
                return span;
            }
        }
    }

    private static void blameReversal(
        StationRouting.Plan plan, SableScheduleRuntime runtime, boolean tooShort, boolean roomEnough, double clearance, double roomFound
    ) {
        boolean plateTurn = plan.trace != null && plan.trace.offered() > 0;
        String id = shortId(runtime.train());
        if (tooShort && !roomEnough && !plateTurn) {
            plan.blame(
                new StationRouting.Cause(
                    StationRouting.Why.NO_ROOM, "", " has no room to reverse", String.format("%.0f", clearance), String.format("%.0f", roomFound)
                )
            );
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} no room to reverse, needs {} past the switch, track has {}",
                    new Object[]{id, String.format("%.1f", clearance), String.format("%.1f", roomFound)}
                );
        } else if (plan.cause == null) {
            plan.blame(new StationRouting.Cause(StationRouting.Why.NO_TURN, "", " has no way to turn the train"));
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} reversal found nothing, dead end, room short {} room enough {} plate exits offered {} reason {}",
                new Object[]{id, tooShort, roomEnough, plateTurn, plan.cause == null ? "none" : plan.cause.kind()}
            );
    }

    private static void traceReturn(
        TrackGraph graph,
        SableScheduleRuntime runtime,
        int rel,
        SchedulePathfinder.Reversal point,
        GlobalStation station,
        TrackGraphLocation loc,
        SchedulePathfinder.Routing routing,
        SchedulePathfinder.Path back,
        double lead,
        double best
    ) {
        TrackNode a = graph.locateNode((TrackNodeLocation)loc.edge.getFirst());
        TrackNode b = graph.locateNode((TrackNodeLocation)loc.edge.getSecond());
        String relName = rel > 0 ? "cab first" : "backing";
        if (back != null) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} return {} point {}->{} at {} lead {} station \"{}\" ok, back {} total {} best {}",
                    new Object[]{
                        shortId(runtime.train()),
                        relName,
                        point.from().getLocation(),
                        point.to().getLocation(),
                        String.format("%.1f", point.position()),
                        String.format("%.1f", lead),
                        station.name,
                        String.format("%.1f", back.cost()),
                        String.format("%.1f", lead + back.cost()),
                        best
                    }
                );
        } else {
            SchedulePathfinder.Path undirected = SchedulePathfinder.findFrom(graph, point.to(), point.from(), a, b, false, routing);
            SchedulePathfinder.Path bare = SchedulePathfinder.findFrom(graph, point.to(), point.from(), a, b, false, SchedulePathfinder.Routing.NONE);
            SchedulePathfinder.Path forwardOn = SchedulePathfinder.findFrom(graph, point.from(), point.to(), a, b, false, SchedulePathfinder.Routing.NONE);
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} return {} point {}->{} at {} past {} lead {} station \"{}\" on edge {}->{} FAILED, undirected={} noRouting={} sameWayNoRouting={} conn(from)={} conn(to)={}",
                    new Object[]{
                        shortId(runtime.train()),
                        relName,
                        point.from().getLocation(),
                        point.to().getLocation(),
                        String.format("%.1f", point.position()),
                        String.format("%.1f", point.past()),
                        String.format("%.1f", lead),
                        station.name,
                        loc.edge.getFirst(),
                        loc.edge.getSecond(),
                        undirected == null ? "null" : String.format("%.1f", undirected.cost()),
                        bare == null ? "null" : String.format("%.1f", bare.cost()),
                        forwardOn == null ? "null" : String.format("%.1f", forwardOn.cost()),
                        graph.getConnectionsFrom(point.from()).size(),
                        graph.getConnectionsFrom(point.to()).size()
                    }
                );
        }
    }

    private static double bogeySpan(SableScheduleRuntime runtime) {
        if (runtime.train() != null && runtime.train().car() != null && runtime.train().level() != null) {
            List<TrainLength.BogeyPlace> places = TrainLength.places(runtime.train().level(), runtime.train().car().subLevelId());
            return places.size() < 2 ? 0.0 : places.get(places.size() - 1).offset();
        } else {
            return 0.0;
        }
    }

    private static void traceConsist(SableScheduleRuntime runtime, double length, double span, double clearance) {
        ServerLevel level = runtime.train().level();
        UUID id = runtime.train().car().subLevelId();
        List<String> chain = new ArrayList<>();

        for (UUID car : TrainConsist.orderedChain(level, id)) {
            chain.add(car.toString().substring(0, 8));
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] {} consist chain {} linked {} bodyLength {} bogeySpan {} clearance {}",
                new Object[]{
                    shortId(runtime.train()),
                    chain,
                    TrainConsist.connectedSubLevels(level, id).size(),
                    String.format("%.1f", length),
                    String.format("%.1f", span),
                    String.format("%.1f", clearance)
                }
            );
    }

    private static double consistLength(SableScheduleRuntime runtime) {
        return runtime.train() != null && runtime.train().car() != null && runtime.train().level() != null
            ? TrainLength.consistLength(runtime.train().level(), runtime.train().car().subLevelId())
            : 0.0;
    }

    private static void planAll(
        StationRouting.Plan plan,
        SableScheduleRuntime runtime,
        TrackGraph graph,
        int sense,
        int rel,
        SchedulePathfinder.Routing routing,
        Predicate<GlobalStation> wanted,
        boolean spread
    ) {
        TravellingPoint nose = plan.nose;
        List<String> rejected = plan.rejected;
        double bestCost = plan.bestCost;

        for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
            if (wanted.test(station)) {
                plan.anyMatch = true;
                TrackGraphLocation loc = SchedulePlanner.locationOf(graph, station);
                TrackNode a = graph.locateNode((TrackNodeLocation)loc.edge.getFirst());
                TrackNode b = graph.locateNode((TrackNodeLocation)loc.edge.getSecond());
                if (a == null || b == null) {
                    rejected.add(station.name + " sits on an edge whose nodes are not in the graph");
                } else if (station.assembling) {
                    rejected.add(station.name + " is in assembly mode, take the station out of assembly mode");
                } else {
                    boolean alreadyHere = TrainStations.sameStation(station, runtime.train().atStation());
                    if (alreadyHere && !runtime.justDeparted(station) && Math.abs(runtime.train().speed()) <= 0.03) {
                        runtime.clearStatus();
                        plan.best = new SchedulePath(loc, sense, ImmutableList.of(), station);
                        plan.bestCost = 0.0;
                        plan.bestRel = rel;
                        plan.waitFor = null;
                        plan.berthed = null;
                        return;
                    }

                    if (alreadyHere && plan.maneuver && Math.abs(runtime.train().speed()) <= 0.03) {
                        plan.stayPut = new SchedulePath(loc, sense, ImmutableList.of(), station);
                        plan.stayRel = rel;
                    }

                    boolean judged = spread && !alreadyHere;
                    double ahead = alreadyHere ? Double.NaN : SchedulePlanner.aheadOnCurrentEdge(nose, a, b, loc);
                    if (Double.isNaN(ahead) || Math.signum(ahead) != sense) {
                        SchedulePathfinder.Path found = SchedulePathfinder.findAhead(graph, nose, a, b, sense, !plan.maneuver, routing);
                        if (found == null) {
                            StationRouting.Cause cause = causeOf(runtime, graph, nose, a, b, sense, routing, plan.noseAlong, station.name);
                            rejected.add(station.name + cause.text());
                            plan.blame(cause);
                            traceRefused(plan, runtime, station, loc, rel, sense);
                        } else {
                            StationRouting.Cause tight = plan.noseAlong && found.hop() == null
                                ? backingRoom(graph, plan, station, loc, found.edges(), found.landRel())
                                : null;
                            if (tight != null) {
                                rejected.add(station.name + tight.text());
                                plan.blame(tight);
                            } else {
                                StationRouting.Verdict verdict = judged
                                    ? verdict(plan, runtime, graph, station, found.hop() == null ? found.edges() : null, rel, rejected)
                                    : StationRouting.Verdict.FREE;
                                double cost = found.cost() + penaltyOf(verdict);
                                if (!(cost >= bestCost)) {
                                    SchedulePath leg = SchedulePlanner.toTurntable(graph, found, reason -> rejected.add(station.name + " " + reason));
                                    if (leg != null || found.hop() == null) {
                                        bestCost = cost;
                                        plan.bestCost = bestCost;
                                        plan.bestRel = rel;
                                        traceChosen(plan, runtime, station, loc, rel, found);
                                        pick(
                                            plan,
                                            verdict,
                                            station,
                                            leg != null ? leg : verdict.settle(new SchedulePath(loc, found.direction(), found.edges(), station))
                                        );
                                    }
                                }
                            }
                        }
                    } else if (!noseAgainstHere(plan, runtime, station, rel)) {
                        StationRouting.Verdict verdict = judged
                            ? verdict(plan, runtime, graph, station, ImmutableList.of(), rel, rejected)
                            : StationRouting.Verdict.FREE;
                        double gap = Math.abs(ahead) + penaltyOf(verdict);
                        if (gap < bestCost) {
                            bestCost = gap;
                            plan.bestCost = gap;
                            plan.bestRel = rel;
                            pick(plan, verdict, station, verdict.settle(new SchedulePath(loc, sense, ImmutableList.of(), station)));
                        }
                    }
                }
            }
        }
    }

    private static boolean noseAgainstHere(StationRouting.Plan plan, SableScheduleRuntime runtime, GlobalStation station, int rel) {
        if (plan.noseAlong && rel <= 0) {
            plan.rejected.add(station.name + " would end with its nose against the station facing");
            plan.blame(new StationRouting.Cause(StationRouting.Why.NOSE_AGAINST, station.name, " would end with its nose against the station facing"));
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} refused {}, the stop is on the current edge and backing there ends with the nose against the facing",
                    shortId(runtime.train()),
                    station.name
                );
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static StationRouting.Cause backingRoom(
        TrackGraph graph, StationRouting.Plan plan, GlobalStation station, TrackGraphLocation loc, List<Couple<TrackNode>> edges, int landRel
    ) {
        if (landRel <= 0 && !edges.isEmpty()) {
            TrackNode a = graph.locateNode((TrackNodeLocation)loc.edge.getFirst());
            TrackNode b = graph.locateNode((TrackNodeLocation)loc.edge.getSecond());
            Couple<TrackNode> last = edges.get(edges.size() - 1);
            boolean along = last.getFirst() == a && last.getSecond() == b;
            if (along || last.getFirst() == b && last.getSecond() == a) {
                TrackEdge edge = graph.getConnection(Couple.create(a, b));
                if (edge == null) {
                    return null;
                }

                double toEdgeEnd = along ? edge.getLength() - loc.position : loc.position;
                double room = roomPast(graph, along ? a : b, along ? b : a, toEdgeEnd, plan.backRoom);
                LoconauticsConstants.LOGGER
                    .debug(
                        "[maneuver] backing into {} travels {} the facing, track past the mark {} blocks, the consist needs {}",
                        new Object[]{station.name, along ? "along" : "against", String.format("%.1f", room), String.format("%.1f", plan.backRoom)}
                    );
                return room >= plan.backRoom
                    ? null
                    : new StationRouting.Cause(
                        StationRouting.Why.END_ROOM,
                        station.name,
                        " ends too soon behind its stop to back into",
                        String.format("%.0f", plan.backRoom),
                        String.format("%.0f", room)
                    );
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static double roomPast(TrackGraph graph, TrackNode behind, TrackNode at, double room, double enough) {
        while (room < enough) {
            TrackNode next = null;
            TrackEdge edge = null;
            int ways = 0;

            for (Entry<TrackNode, TrackEdge> entry : graph.getConnectionsFrom(at).entrySet()) {
                if (entry.getKey() != behind) {
                    ways++;
                    next = entry.getKey();
                    edge = entry.getValue();
                }
            }

            if (ways == 0) {
                return room;
            }

            if (ways > 1) {
                return Double.POSITIVE_INFINITY;
            }

            room += edge.getLength();
            behind = at;
            at = next;
        }

        return room;
    }

    private static String facingOf(TrackGraphLocation loc) {
        return loc.edge.getFirst() + "->" + loc.edge.getSecond();
    }

    private static void traceRefused(StationRouting.Plan plan, SableScheduleRuntime runtime, GlobalStation station, TrackGraphLocation loc, int rel, int sense) {
        if (plan.noseAlong) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} no route to {} with the nose along its facing {}, leg {} sense {}, the reason is in the plan gate line",
                    new Object[]{shortId(runtime.train()), station.name, facingOf(loc), rel > 0 ? "cab first" : "backing", sense}
                );
        }
    }

    private static void traceChosen(
        StationRouting.Plan plan, SableScheduleRuntime runtime, GlobalStation station, TrackGraphLocation loc, int rel, SchedulePathfinder.Path found
    ) {
        if (plan.noseAlong) {
            TurntableRegistry.Exit hop = found.hop();
            String exit = hop == null
                ? "no plate"
                : (
                    hop.entry()
                        ? "plate entry, leaves the choice to the turn"
                        : (ManeuverPlan.backsOut(hop) ? "plate exit that backs out" : "plate exit that spins")
                            + ", the cab end leads after it "
                            + (SchedulePathfinder.Routing.relAfter(hop, rel) > 0)
                );
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} nose rule picked {} leg {} direction {} lands {} station faces {} via {}",
                    new Object[]{
                        shortId(runtime.train()),
                        station.name,
                        rel > 0 ? "cab first" : "backing",
                        found.direction(),
                        hop != null ? "on a plate first" : (found.landRel() > 0 ? "cab first, nose along the facing" : "backing, nose along the facing"),
                        facingOf(loc),
                        exit
                    }
                );
        }
    }

    private static double penaltyOf(StationRouting.Verdict verdict) {
        return verdict.occupied() ? 300.0 : 0.0;
    }

    private static void pick(StationRouting.Plan plan, StationRouting.Verdict verdict, GlobalStation station, SchedulePath path) {
        plan.best = verdict.occupied() ? null : path;
        plan.waitFor = verdict.occupied() ? station : null;
        plan.berthed = verdict.berth() == null ? null : verdict.berth().parked();
        plan.noRoom = verdict.noRoom();
    }

    private static StationRouting.Verdict verdict(
        StationRouting.Plan plan,
        SableScheduleRuntime runtime,
        TrackGraph graph,
        GlobalStation station,
        @Nullable List<Couple<TrackNode>> route,
        int rel,
        List<String> rejected
    ) {
        SableTrain train = runtime.train();
        SableTrain heading = SableScheduleRuntime.headingTo(station, train);
        if (heading != null) {
            rejected.add(station.name + " is already claimed by train " + shortId(heading) + " on its way there");
            return StationRouting.Verdict.TAKEN;
        }

        SableTrain parked = StationOccupancy.parkedAt(station, train);
        if (parked == null) {
            return StationRouting.Verdict.FREE;
        }

        StationBerth.Berth berth = plan.maneuver && route != null
            ? StationBerth.measure(graph, leadPoint(plan, train, rel), route, parked, consistLength(runtime))
            : null;
        if (berth != null && berth.fits()) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} berth at {} behind train {}, {} free, needs {}",
                    new Object[]{shortId(train), station.name, shortId(parked), String.format("%.1f", berth.free()), String.format("%.1f", berth.need())}
                );
            return new StationRouting.Verdict(false, berth, null);
        }

        if (berth != null) {
            String why = station.name + " has no room for " + Math.round(berth.need()) + " blocks, " + Math.round(Math.min(berth.free(), 1000000.0)) + " free";
            LoconauticsConstants.LOGGER.debug("[maneuver] {} {}, train {} is parked there", new Object[]{shortId(train), why, shortId(parked)});
            rejected.add(why);
            return new StationRouting.Verdict(true, null, why);
        }

        if (runtime.former().contains(parked.id())) {
            return StationRouting.Verdict.FREE;
        }

        rejected.add(station.name + " already has train " + shortId(parked) + " parked");
        return StationRouting.Verdict.TAKEN;
    }

    private static TravellingPoint leadPoint(StationRouting.Plan plan, SableTrain train, int rel) {
        TravellingPoint tail = rel < 0 ? ConsistNose.tail(train).point() : null;
        return tail != null && tail.edge != null ? tail : plan.nose;
    }

    private static String shortId(SableTrain train) {
        return train.id().toString().substring(0, 8);
    }

    private static boolean anyPlateShorterThan(double trainLength) {
        for (Turntable turntable : TurntableRegistry.all()) {
            if (TurntableRegistry.spanOf(turntable.plateSubLevelId()) + 0.5 < trainLength) {
                return true;
            }
        }

        return false;
    }

    private static StationRouting.Cause causeOf(
        SableScheduleRuntime runtime,
        TrackGraph graph,
        TravellingPoint nose,
        TrackNode a,
        TrackNode b,
        int sense,
        SchedulePathfinder.Routing routing,
        boolean noseAlong,
        String station
    ) {
        double trainLength = routing.trainLength();
        if (noseAlong) {
            boolean anyEnd = SchedulePathfinder.findAhead(graph, nose, a, b, sense, false, routing.arriving(false, routing.rel()).tracing(null)) != null;
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] {} why {} sense {}: a route exists when the nose may point either way {}",
                    new Object[]{shortId(runtime.train()), station, sense, anyEnd}
                );
            if (anyEnd) {
                return new StationRouting.Cause(StationRouting.Why.NOSE_AGAINST, station, " would end with its nose against the station facing");
            }
        }

        if (SchedulePathfinder.findAhead(graph, nose, a, b, sense) != null) {
            return new StationRouting.Cause(StationRouting.Why.WRONG_SIDE, station, " is only reachable entering from the wrong side, flip the station marker");
        } else if (SchedulePathfinder.findAhead(graph, nose, a, b, sense, true, SchedulePathfinder.Routing.through(0.0)) != null) {
            return new StationRouting.Cause(
                StationRouting.Why.PLATE,
                station,
                anyPlateShorterThan(trainLength)
                    ? " is only reachable by turning on a turntable shorter than the train"
                    : " is only reachable over a turntable this train cannot use in this direction right now"
            );
        } else {
            return SchedulePathfinder.find(graph, nose, a, b, true) != null
                ? new StationRouting.Cause(
                    StationRouting.Why.BEHIND, station, " is only reachable behind the locomotive, turn it around or drive it there by hand"
                )
                : new StationRouting.Cause(StationRouting.Why.NOT_CONNECTED, station, " is not connected to this track");
        }
    }

    record Cause(StationRouting.Why kind, String station, String text, String need, String have) {
        Cause(StationRouting.Why kind, String station, String text) {
            this(kind, station, text, "", "");
        }

        Component component() {
            return LoconauticsLang.translate("train.status.maneuver_why." + this.kind.name().toLowerCase(Locale.ROOT), this.station, this.need, this.have)
                .component();
        }
    }

    public static final class Plan {
        public SchedulePath best;
        public boolean anyMatch;
        public double bestCost = Double.POSITIVE_INFINITY;
        public int bestRel;
        public boolean reversal;
        public boolean maneuver;
        public boolean noseAlong;
        public double backRoom;
        @Nullable
        public SchedulePathfinder.Trace trace;
        public TravellingPoint nose;
        @Nullable
        public GlobalStation waitFor;
        @Nullable
        public String noRoom;
        @Nullable
        public SableTrain berthed;
        @Nullable
        public StationRouting.Cause cause;
        @Nullable
        public SchedulePath stayPut;
        public int stayRel;
        public final List<String> rejected = new ArrayList<>();

        void blame(StationRouting.Cause found) {
            if (this.cause == null || found.kind().ordinal() < this.cause.kind().ordinal()) {
                this.cause = found;
            }
        }
    }

    private record Verdict(boolean occupied, @Nullable StationBerth.Berth berth, @Nullable String noRoom) {
        static final StationRouting.Verdict FREE = new StationRouting.Verdict(false, null, null);
        static final StationRouting.Verdict TAKEN = new StationRouting.Verdict(true, null, null);

        SchedulePath settle(SchedulePath path) {
            return this.berth == null
                ? path
                : new SchedulePath(
                    this.berth.stop(),
                    path.direction(),
                    (List<Couple<TrackNode>>)(this.berth.local() ? ImmutableList.of() : path.route()),
                    path.station(),
                    path.hop()
                );
        }
    }

    enum Why {
        NOSE_AGAINST,
        END_ROOM,
        NO_ROOM,
        PLATE,
        BEHIND,
        WRONG_SIDE,
        NO_TURN,
        NOT_CONNECTED;
    }
}
