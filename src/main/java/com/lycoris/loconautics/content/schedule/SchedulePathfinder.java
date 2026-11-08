package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Map.Entry;
import net.createmod.catnip.data.Couple;
import org.jetbrains.annotations.Nullable;

public final class SchedulePathfinder {
    private static final double DEGREE_COST = 1.0;
    private static final double AXLE_TRAILING_COST = 20.0;
    private static final double DEAD_END_STOP_GAP = 2.0;

    private SchedulePathfinder() {
    }

    public static SchedulePathfinder.Path find(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB) {
        return find(graph, leading, targetA, targetB, false, SchedulePathfinder.Routing.NONE);
    }

    public static SchedulePathfinder.Path find(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB, boolean directed) {
        return find(graph, leading, targetA, targetB, directed, SchedulePathfinder.Routing.NONE);
    }

    public static SchedulePathfinder.Path find(
        TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB, boolean directed, SchedulePathfinder.Routing routing
    ) {
        if (leading.node1 != null && leading.node2 != null && targetA != null && targetB != null) {
            SchedulePathfinder.Result forward = search(graph, leading.node1, leading.node2, targetA, targetB, directed, routing);
            SchedulePathfinder.Result reverse = search(graph, leading.node2, leading.node1, targetA, targetB, directed, routing);
            if (forward == null && reverse == null) {
                return null;
            }

            boolean useForward = forward != null && (reverse == null || forward.cost <= reverse.cost);
            SchedulePathfinder.Result chosen = useForward ? forward : reverse;
            return pathOf(useForward ? 1 : -1, chosen);
        } else {
            return null;
        }
    }

    public static SchedulePathfinder.Path findAhead(TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB, int sense) {
        return findAhead(graph, leading, targetA, targetB, sense, false, SchedulePathfinder.Routing.NONE);
    }

    public static SchedulePathfinder.Path findAhead(
        TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB, int sense, boolean directed
    ) {
        return findAhead(graph, leading, targetA, targetB, sense, directed, SchedulePathfinder.Routing.NONE);
    }

    public static SchedulePathfinder.Path findAhead(
        TrackGraph graph, TravellingPoint leading, TrackNode targetA, TrackNode targetB, int sense, boolean directed, SchedulePathfinder.Routing routing
    ) {
        if (leading.node1 != null && leading.node2 != null && targetA != null && targetB != null) {
            SchedulePathfinder.Result found = sense >= 0
                ? search(graph, leading.node1, leading.node2, targetA, targetB, directed, routing)
                : search(graph, leading.node2, leading.node1, targetA, targetB, directed, routing);
            return found == null ? null : pathOf(sense >= 0 ? 1 : -1, found);
        } else {
            return null;
        }
    }

    private static SchedulePathfinder.Path pathOf(int direction, SchedulePathfinder.Result found) {
        return new SchedulePathfinder.Path(
            direction, found.distance(), found.cost(), found.edges(), found.hop(), found.hop() == null ? found.edges() : found.edgesToHop(), found.landRel()
        );
    }

    private static SchedulePathfinder.Result search(
        TrackGraph graph, TrackNode cameFrom, TrackNode start, TrackNode targetA, TrackNode targetB, boolean directed, SchedulePathfinder.Routing routing
    ) {
        PriorityQueue<SchedulePathfinder.Frontier> queue = new PriorityQueue<>();
        queue.add(new SchedulePathfinder.Frontier(start, cameFrom, 0.0, 0.0, new ArrayList<>(), null, List.of(), routing.rel()));
        Set<SchedulePathfinder.Visit> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            SchedulePathfinder.Frontier f = queue.poll();
            if (f.arrived) {
                return new SchedulePathfinder.Result(f.distance, f.cost, f.edges, f.hop, f.edgesToHop, f.rel);
            }

            if (visited.add(new SchedulePathfinder.Visit(Couple.create(f.cameFrom, f.node), f.rel))) {
                TrackEdge arrivedOn = graph.getConnection(Couple.create(f.cameFrom, f.node));
                Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(f.node);

                for (Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                    TrackNode next = entry.getKey();
                    if (next != f.cameFrom && stepAllowed(arrivedOn, f.node, next, entry.getValue())) {
                        List<Couple<TrackNode>> edges = new ArrayList<>(f.edges);
                        edges.add(Couple.create(f.node, next));
                        TrackEdge edge = entry.getValue();
                        double distance = f.distance + edge.getLength();
                        RouteTraffic traffic = routing.traffic();
                        double cost = f.cost + edge.getLength() + (traffic == null ? 0 : traffic.trainsOn(edge));
                        if (isTarget(f.node, next, targetA, targetB, directed)) {
                            boolean along = f.node == targetA && next == targetB;
                            boolean lands = routing.lands(along, f.rel);
                            if (routing.trace() != null && routing.noseAlong()) {
                                routing.trace()
                                    .note(
                                        "landing "
                                            + f.node.getLocation()
                                            + "->"
                                            + next.getLocation()
                                            + " travel "
                                            + (along ? "along" : "against")
                                            + " the facing, cab end "
                                            + (f.rel > 0 ? "leads" : "trails")
                                            + ", nose ends "
                                            + (along == f.rel > 0 ? "along" : "against")
                                            + " the facing, "
                                            + (lands ? "kept" : "dropped")
                                    );
                            }

                            if (lands) {
                                queue.add(new SchedulePathfinder.Frontier(next, f.node, distance, cost, edges, f.hop, f.edgesToHop, f.rel, true));
                            }
                        } else if (canNavigateVia(edge, next)) {
                            if (traffic != null) {
                                cost += traffic.pointsOn(edge, next);
                            }

                            queue.add(new SchedulePathfinder.Frontier(next, f.node, distance, cost, edges, f.hop, f.edgesToHop, f.rel));
                        }
                    }
                }

                queueTurntableExits(graph, f, queue, routing);
            }
        }

        return null;
    }

    private static boolean stepAllowed(@Nullable TrackEdge arrivedOn, TrackNode node, TrackNode next, TrackEdge edge) {
        return arrivedOn != null && !arrivedOn.canTravelTo(edge)
            ? false
            : !edge.isInterDimensional()
                || !RailSeams.isSeam(node.getLocation(), next.getLocation())
                || RailSeams.aligned(node.getLocation(), next.getLocation());
    }

    public static SchedulePathfinder.Path findFrom(
        TrackGraph graph, TrackNode cameFrom, TrackNode start, TrackNode targetA, TrackNode targetB, boolean directed, SchedulePathfinder.Routing routing
    ) {
        if (cameFrom != null && start != null && targetA != null && targetB != null) {
            SchedulePathfinder.Result found = search(graph, cameFrom, start, targetA, targetB, directed, routing);
            return found == null ? null : pathOf(1, found);
        } else {
            return null;
        }
    }

    public static List<SchedulePathfinder.Reversal> reversalPoints(TrackGraph graph, TravellingPoint leading, int sense, double clearance) {
        List<SchedulePathfinder.Reversal> points = new ArrayList<>();
        if (leading.node1 != null && leading.node2 != null) {
            TrackNode cameFrom = sense >= 0 ? leading.node1 : leading.node2;
            TrackNode start = sense >= 0 ? leading.node2 : leading.node1;
            PriorityQueue<SchedulePathfinder.Walk> queue = new PriorityQueue<>();
            queue.add(new SchedulePathfinder.Walk(start, cameFrom, 0.0, 0.0, new ArrayList<>()));
            Set<Couple<TrackNode>> visited = new HashSet<>();

            while (!queue.isEmpty()) {
                SchedulePathfinder.Walk w = queue.poll();
                if (visited.add(Couple.create(w.cameFrom, w.node))) {
                    TrackEdge arrivedOn = graph.getConnection(Couple.create(w.cameFrom, w.node));
                    Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(w.node);
                    double past = connections.size() > 2 ? 0.0 : w.past;
                    boolean onward = false;

                    for (Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
                        TrackNode next = entry.getKey();
                        TrackEdge edge = entry.getValue();
                        if (next != w.cameFrom && stepAllowed(arrivedOn, w.node, next, edge) && canNavigateVia(edge, next)) {
                            onward = true;
                            List<Couple<TrackNode>> edges = new ArrayList<>(w.edges);
                            edges.add(Couple.create(w.node, next));
                            double need = clearance - past;
                            if (need <= edge.getLength()) {
                                double stopAt = Math.max(0.0, need);
                                points.add(new SchedulePathfinder.Reversal(w.node, next, stopAt, w.cost, past + stopAt, edges));
                            }

                            queue.add(new SchedulePathfinder.Walk(next, w.node, w.cost + edge.getLength(), past + edge.getLength(), edges));
                        }
                    }

                    if (!onward && arrivedOn != null) {
                        double cost = w.edges.isEmpty() ? 0.0 : w.cost - arrivedOn.getLength();
                        double stop = Math.max(0.0, arrivedOn.getLength() - 2.0);
                        double pastStop = Math.max(0.0, w.past - (arrivedOn.getLength() - stop));
                        points.add(new SchedulePathfinder.Reversal(w.cameFrom, w.node, stop, cost, pastStop, w.edges));
                    }
                }
            }

            points.sort(Comparator.comparingDouble(p -> p.cost() + 2.0 * p.position()));
            return points;
        } else {
            return points;
        }
    }

    private static boolean canNavigateVia(TrackEdge edge, TrackNode towards) {
        EdgeData data = edge.getEdgeData();
        if (!data.hasPoints()) {
            return true;
        }

        for (TrackEdgePoint point : data.getPoints()) {
            if (!point.canNavigateVia(towards)) {
                return false;
            }
        }

        return true;
    }

    private static void queueTurntableExits(
        TrackGraph graph, SchedulePathfinder.Frontier f, PriorityQueue<SchedulePathfinder.Frontier> queue, SchedulePathfinder.Routing routing
    ) {
        if (routing.turntables() && TurntableRegistry.anyRegistered()) {
            SchedulePathfinder.Trace trace = routing.trace();
            List<TurntableRegistry.Exit> exits = TurntableRegistry.exits(graph, f.node, f.cameFrom, routing.trainLength(), routing.reversible());
            if (trace != null && exits.isEmpty()) {
                String refusal = TurntableRegistry.refusal(f.node, routing.trainLength());
                if (refusal != null) {
                    trace.note("plate head " + f.node.getLocation() + " gives no exits, " + refusal);
                }
            }

            for (TurntableRegistry.Exit exit : exits) {
                int after = SchedulePathfinder.Routing.relAfter(exit, f.rel);
                if (trace != null) {
                    trace.offered("exit " + describe(exit) + " offered, rel " + f.rel + " to " + after);
                }

                boolean backsOut = ManeuverPlan.backsOut(exit);
                queueHop(f, queue, exit, graph.locateNode(exit.worldHead()), routing.axleCost(backsOut), routing.noseAlong() ? after : f.rel, routing.traffic());
            }

            for (TurntableRegistry.Exit entry : TurntableRegistry.entries(graph, f.node, f.cameFrom, routing.trainLength())) {
                if (trace != null) {
                    trace.offered("entry " + describe(entry) + " offered, rel " + f.rel);
                }

                queueHop(f, queue, entry, graph.locateNode(entry.movingHead()), 0.0, f.rel, routing.traffic());
            }
        }
    }

    private static String describe(TurntableRegistry.Exit exit) {
        return (exit.entry() ? "entry" : (ManeuverPlan.backsOut(exit) ? "backs out" : "spins"))
            + " head "
            + exit.movingHead()
            + " to "
            + exit.worldHead()
            + " turn "
            + String.format("%.0f", exit.degrees());
    }

    private static void queueHop(
        SchedulePathfinder.Frontier f,
        PriorityQueue<SchedulePathfinder.Frontier> queue,
        TurntableRegistry.Exit exit,
        @Nullable TrackNode next,
        double extraCost,
        int rel,
        @Nullable RouteTraffic traffic
    ) {
        if (next != null && next != f.node) {
            List<Couple<TrackNode>> edges = new ArrayList<>(f.edges);
            edges.add(Couple.create(f.node, next));
            TurntableRegistry.Exit hop = f.hop == null ? exit : f.hop;
            List<Couple<TrackNode>> toHop = f.hop == null ? List.copyOf(f.edges) : f.edgesToHop;
            double turn = exit.degrees() * 1.0;
            double plate = f.hop == null && traffic != null ? traffic.plateCost(exit.plate()) : 0.0;
            queue.add(new SchedulePathfinder.Frontier(next, f.node, f.distance + turn, f.cost + turn + extraCost + plate, edges, hop, toHop, rel));
        }
    }

    private static boolean isTarget(TrackNode a, TrackNode b, TrackNode targetA, TrackNode targetB, boolean directed) {
        return a == targetA && b == targetB ? true : !directed && a == targetB && b == targetA;
    }

    private record Frontier(
        TrackNode node,
        TrackNode cameFrom,
        double distance,
        double cost,
        List<Couple<TrackNode>> edges,
        @Nullable TurntableRegistry.Exit hop,
        List<Couple<TrackNode>> edgesToHop,
        int rel,
        boolean arrived
    ) implements Comparable<SchedulePathfinder.Frontier> {
        Frontier(
            TrackNode node,
            TrackNode cameFrom,
            double distance,
            double cost,
            List<Couple<TrackNode>> edges,
            @Nullable TurntableRegistry.Exit hop,
            List<Couple<TrackNode>> edgesToHop,
            int rel
        ) {
            this(node, cameFrom, distance, cost, edges, hop, edgesToHop, rel, false);
        }

        public int compareTo(SchedulePathfinder.Frontier o) {
            return Double.compare(this.cost, o.cost);
        }
    }

    public record Path(
        int direction,
        double distance,
        double cost,
        List<Couple<TrackNode>> edges,
        @Nullable TurntableRegistry.Exit hop,
        List<Couple<TrackNode>> edgesToHop,
        int landRel
    ) {
    }

    private record Result(
        double distance, double cost, List<Couple<TrackNode>> edges, @Nullable TurntableRegistry.Exit hop, List<Couple<TrackNode>> edgesToHop, int landRel
    ) {
    }

    public record Reversal(TrackNode from, TrackNode to, double position, double cost, double past, List<Couple<TrackNode>> edges) {
    }

    public record Routing(
        boolean turntables,
        double trainLength,
        boolean reversible,
        @Nullable RouteTraffic traffic,
        @Nullable Boolean axleLeads,
        boolean noseAlong,
        int rel,
        @Nullable SchedulePathfinder.Trace trace
    ) {
        public static final SchedulePathfinder.Routing NONE = new SchedulePathfinder.Routing(false, 0.0, false, null, null, false, 0, null);

        public static SchedulePathfinder.Routing through(double trainLength) {
            return new SchedulePathfinder.Routing(true, trainLength, false, null, null, false, 0, null);
        }

        public SchedulePathfinder.Routing around(RouteTraffic traffic) {
            return new SchedulePathfinder.Routing(
                this.turntables, this.trainLength, this.reversible, traffic, this.axleLeads, this.noseAlong, this.rel, this.trace
            );
        }

        public SchedulePathfinder.Routing reversing(boolean reversible) {
            return new SchedulePathfinder.Routing(
                this.turntables, this.trainLength, reversible, this.traffic, this.axleLeads, this.noseAlong, this.rel, this.trace
            );
        }

        public SchedulePathfinder.Routing axleLeading(@Nullable Boolean axleLeads) {
            return new SchedulePathfinder.Routing(
                this.turntables, this.trainLength, this.reversible, this.traffic, axleLeads, this.noseAlong, this.rel, this.trace
            );
        }

        public SchedulePathfinder.Routing arriving(boolean noseAlong, int rel) {
            return new SchedulePathfinder.Routing(this.turntables, this.trainLength, this.reversible, this.traffic, this.axleLeads, noseAlong, rel, this.trace);
        }

        public SchedulePathfinder.Routing tracing(@Nullable SchedulePathfinder.Trace trace) {
            return new SchedulePathfinder.Routing(
                this.turntables, this.trainLength, this.reversible, this.traffic, this.axleLeads, this.noseAlong, this.rel, trace
            );
        }

        static int relAfter(TurntableRegistry.Exit exit, int rel) {
            return !exit.entry() && ManeuverPlan.backsOut(exit) ? -rel : rel;
        }

        boolean lands(boolean travelAlongFacing, int legRel) {
            return !this.noseAlong || travelAlongFacing == legRel > 0;
        }

        double axleCost(boolean departsAgainstSense) {
            return this.axleLeads != null && this.axleLeads == departsAgainstSense ? 20.0 : 0.0;
        }
    }

    public static final class Trace {
        private static final int MAX_LINES = 40;
        private final Set<String> lines = new LinkedHashSet<>();
        private int offered;

        void offered(String line) {
            this.offered++;
            this.note(line);
        }

        void note(String line) {
            if (this.lines.size() < 40) {
                this.lines.add(line);
            }
        }

        public int offered() {
            return this.offered;
        }

        public Set<String> lines() {
            return this.lines;
        }
    }

    private record Visit(Couple<TrackNode> edge, int rel) {
    }

    private record Walk(TrackNode node, TrackNode cameFrom, double cost, double past, List<Couple<TrackNode>> edges)
        implements Comparable<SchedulePathfinder.Walk> {
        public int compareTo(SchedulePathfinder.Walk o) {
            return Double.compare(this.cost, o.cost);
        }
    }
}
