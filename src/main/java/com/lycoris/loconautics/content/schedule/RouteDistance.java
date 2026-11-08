package com.lycoris.loconautics.content.schedule;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.List;
import net.createmod.catnip.data.Couple;

public final class RouteDistance {
    public static final double UNKNOWN = -1.0;

    private RouteDistance() {
    }

    public static double remaining(TravellingPoint point, TrackGraph graph, List<Couple<TrackNode>> route, TrackGraphLocation target) {
        return measure(point, graph, route, target).distance();
    }

    public static RouteDistance.Measure measure(TravellingPoint point, TrackGraph graph, List<Couple<TrackNode>> route, TrackGraphLocation target) {
        if (point != null && point.edge != null && point.node1 != null && point.node2 != null && graph != null && target != null && target.edge != null) {
            TrackNodeLocation a = (TrackNodeLocation)target.edge.getFirst();
            TrackNodeLocation b = (TrackNodeLocation)target.edge.getSecond();
            if (onEdge(point, a, b)) {
                return new RouteDistance.Measure(Math.max(0.0, alongTo(point, a, target.position)), "on the target edge");
            }

            if (route != null && !route.isEmpty()) {
                RouteDistance.Start found = startOf(point, route);
                if (found.index() < 0) {
                    return RouteDistance.Measure.unknown("neither end of " + describe(point) + " starts a hop of the route");
                }

                double total = found.total();
                String how = found.how();

                for (int i = found.index(); i < route.size(); i++) {
                    Couple<TrackNode> hop = route.get(i);
                    TrackEdge edge = graph.getConnection(hop);
                    if (edge == null) {
                        return RouteDistance.Measure.unknown(how + ", but hop " + i + " has no edge in the graph");
                    }

                    if (isTargetEdge(hop, a, b)) {
                        boolean sameSense = ((TrackNode)hop.getFirst()).getLocation().equals(a);
                        double onTarget = sameSense ? target.position : edge.getLength() - target.position;
                        return new RouteDistance.Measure(
                            total + onTarget, how + ", target on hop " + i + String.format(", %.2f to that hop plus %.2f into it", total, onTarget)
                        );
                    }

                    total += edge.getLength();
                }

                return RouteDistance.Measure.unknown(how + ", but the target edge is not in the rest of the route");
            } else {
                return RouteDistance.Measure.unknown("off the target edge with no route to walk");
            }
        } else {
            return RouteDistance.Measure.unknown("no point, no graph or no target");
        }
    }

    private static RouteDistance.Start startOf(TravellingPoint point, List<Couple<TrackNode>> route) {
        int here = indexOfEdge(route, point.node1, point.node2);
        if (here >= 0) {
            boolean sameSense = route.get(here).getFirst() == point.node1;
            return new RouteDistance.Start(
                here + 1,
                sameSense ? point.edge.getLength() - point.position : point.position,
                "on route hop " + here + (sameSense ? " the same way round" : " the other way round")
            );
        }

        int start = indexOfHopFrom(route, point.node2);
        if (start >= 0) {
            return new RouteDistance.Start(start, point.edge.getLength() - point.position, "route leaves by node2 at hop " + start);
        }

        start = indexOfHopFrom(route, point.node1);
        return new RouteDistance.Start(start, point.position, "route leaves by node1 at hop " + start);
    }

    public static void walkAhead(TravellingPoint point, TrackGraph graph, List<Couple<TrackNode>> route, double horizon, RouteDistance.EdgeAhead visit) {
        if (point != null && point.edge != null && point.node1 != null && point.node2 != null && graph != null) {
            visit.accept(point.edge, 0.0);
            if (route != null && !route.isEmpty()) {
                RouteDistance.Start found = startOf(point, route);
                double total = found.total();

                for (int i = Math.max(0, found.index()); found.index() >= 0 && i < route.size() && total < horizon; i++) {
                    TrackEdge edge = graph.getConnection(route.get(i));
                    if (edge == null) {
                        return;
                    }

                    visit.accept(edge, total);
                    total += edge.getLength();
                }
            }
        }
    }

    public static String describe(TravellingPoint point) {
        return point != null && point.node1 != null && point.node2 != null && point.edge != null
            ? point.node1.getLocation().getLocation()
                + " to "
                + point.node2.getLocation().getLocation()
                + String.format(" at %.2f of %.2f", point.position, point.edge.getLength())
            : "no rail point";
    }

    public static String describe(TrackGraphLocation loc) {
        return loc != null && loc.edge != null
            ? ((TrackNodeLocation)loc.edge.getFirst()).getLocation()
                + " to "
                + ((TrackNodeLocation)loc.edge.getSecond()).getLocation()
                + String.format(" at %.2f", loc.position)
            : "no target";
    }

    public static String describeRoute(List<Couple<TrackNode>> route) {
        if (route == null) {
            return "no route";
        }

        if (route.isEmpty()) {
            return "empty route, the stop is on this edge";
        }

        StringBuilder text = new StringBuilder(route.size() + " hops");

        for (int i = 0; i < route.size() && i < 6; i++) {
            text.append(i == 0 ? ": " : " then ")
                .append(((TrackNode)route.get(i).getFirst()).getLocation().getLocation())
                .append(">")
                .append(((TrackNode)route.get(i).getSecond()).getLocation().getLocation());
        }

        return route.size() > 6 ? text.append(" ...").toString() : text.toString();
    }

    public static double signedOnEdge(TravellingPoint point, TrackGraphLocation target) {
        if (point != null
            && point.edge != null
            && point.node1 != null
            && point.node2 != null
            && target != null
            && target.edge != null
            && onEdge(point, (TrackNodeLocation)target.edge.getFirst(), (TrackNodeLocation)target.edge.getSecond())) {
            boolean sameSense = point.node1.getLocation().equals(target.edge.getFirst());
            double targetOnPoint = sameSense ? target.position : point.edge.getLength() - target.position;
            return targetOnPoint - point.position;
        } else {
            return Double.NaN;
        }
    }

    public static boolean onTargetEdge(TravellingPoint point, TrackGraphLocation target) {
        return point != null && point.node1 != null && point.node2 != null && target != null && target.edge != null
            ? onEdge(point, (TrackNodeLocation)target.edge.getFirst(), (TrackNodeLocation)target.edge.getSecond())
            : false;
    }

    private static boolean onEdge(TravellingPoint point, TrackNodeLocation a, TrackNodeLocation b) {
        TrackNodeLocation p1 = point.node1.getLocation();
        TrackNodeLocation p2 = point.node2.getLocation();
        return p1.equals(a) && p2.equals(b) || p1.equals(b) && p2.equals(a);
    }

    private static double alongTo(TravellingPoint point, TrackNodeLocation a, double targetPosition) {
        boolean sameSense = point.node1.getLocation().equals(a);
        double targetOnPoint = sameSense ? targetPosition : point.edge.getLength() - targetPosition;
        return Math.abs(targetOnPoint - point.position);
    }

    private static boolean isTargetEdge(Couple<TrackNode> hop, TrackNodeLocation a, TrackNodeLocation b) {
        TrackNodeLocation h1 = ((TrackNode)hop.getFirst()).getLocation();
        TrackNodeLocation h2 = ((TrackNode)hop.getSecond()).getLocation();
        return h1.equals(a) && h2.equals(b) || h1.equals(b) && h2.equals(a);
    }

    private static int indexOfEdge(List<Couple<TrackNode>> route, TrackNode n1, TrackNode n2) {
        for (int i = 0; i < route.size(); i++) {
            Couple<TrackNode> hop = route.get(i);
            if (hop.getFirst() == n1 && hop.getSecond() == n2 || hop.getFirst() == n2 && hop.getSecond() == n1) {
                return i;
            }
        }

        return -1;
    }

    private static int indexOfHopFrom(List<Couple<TrackNode>> route, TrackNode node) {
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).getFirst() == node) {
                return i;
            }
        }

        return -1;
    }

    public interface EdgeAhead {
        void accept(TrackEdge var1, double var2);
    }

    public record Measure(double distance, String reason) {
        public boolean known() {
            return this.distance != -1.0;
        }

        static RouteDistance.Measure unknown(String reason) {
            return new RouteDistance.Measure(-1.0, reason);
        }
    }

    private record Start(int index, double total, String how) {
    }
}
