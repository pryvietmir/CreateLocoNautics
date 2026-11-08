package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.List;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class StationBerth {
    private StationBerth() {
    }

    @Nullable
    static StationBerth.Berth measure(TrackGraph graph, @Nullable TravellingPoint lead, List<Couple<TrackNode>> route, SableTrain parked, double ownLength) {
        if (graph != null && lead != null && lead.edge != null && lead.node1 != null && lead.node2 != null) {
            TrackGraphLocation near = null;
            TravellingPoint nearPoint = null;
            double toNear = Double.MAX_VALUE;

            for (ConsistNose.Nose end : List.of(ConsistNose.of(parked), ConsistNose.tail(parked))) {
                TrackGraphLocation loc = locationOf(end.point());
                if (loc != null) {
                    RouteDistance.Measure measured = RouteDistance.measure(lead, graph, route, loc);
                    if (measured.known() && measured.distance() < toNear) {
                        toNear = measured.distance();
                        near = loc;
                        nearPoint = end.point();
                    }
                }
            }

            if (near == null) {
                return null;
            }

            double gap = 6.0;
            double need = gap + ownLength + 2.0;
            double back = Math.min(gap, toNear);
            double reach = toNear + ownLength;
            if (RouteDistance.onTargetEdge(lead, near)) {
                trace(graph, parked, nearPoint, toNear, back, reach, "none on the lead edge");
                return new StationBerth.Berth(parked, onLeadEdge(graph, lead, near, back), reach, need, true);
            }

            int index = indexOf(route, near);
            if (index < 0) {
                return null;
            }

            double behind = clearBehind(graph, route, near, index);
            double free = Math.min(reach, behind);
            trace(graph, parked, nearPoint, toNear, back, reach, junctionText(graph, route, index) + " " + behind + " behind");
            TrackGraphLocation stop = retreat(graph, route, near, index, back);
            return new StationBerth.Berth(parked, stop, free, need, RouteDistance.onTargetEdge(lead, stop));
        } else {
            return null;
        }
    }

    private static void trace(TrackGraph graph, SableTrain parked, TravellingPoint end, double toNear, double back, double reach, String junction) {
        Vec3 world = end == null ? null : end.getPosition(graph);
        LoconauticsConstants.LOGGER
            .debug(
                "[maneuver] berth measure: train {} near wheel {} is {} from the lead end, stop is {} short of it, reach {}, last switch {}",
                new Object[]{
                    ConsistNose.shortId(parked.id()),
                    world == null ? "unknown" : String.format("(%.1f,%.1f,%.1f)", world.x, world.y, world.z),
                    String.format("%.2f", toNear),
                    String.format("%.2f", back),
                    String.format("%.2f", reach),
                    junction
                }
            );
    }

    private static String junctionText(TrackGraph graph, List<Couple<TrackNode>> route, int index) {
        for (int i = index; i >= 0; i--) {
            TrackNode node = (TrackNode)route.get(i).getFirst();
            if (graph.getConnectionsFrom(node).size() > 2) {
                return node.getLocation().getLocation().toString();
            }
        }

        return "none on the route";
    }

    @Nullable
    private static TrackGraphLocation locationOf(@Nullable TravellingPoint point) {
        if (point != null && point.edge != null && point.node1 != null && point.node2 != null) {
            TrackGraphLocation loc = new TrackGraphLocation();
            loc.edge = Couple.create(point.node1.getLocation(), point.node2.getLocation());
            loc.position = point.position;
            return loc;
        } else {
            return null;
        }
    }

    private static TrackGraphLocation onLeadEdge(TrackGraph graph, TravellingPoint lead, TrackGraphLocation near, double back) {
        boolean sameSense = lead.node1.getLocation().equals(near.edge.getFirst());
        double nearAt = sameSense ? near.position : lead.edge.getLength() - near.position;
        double side = nearAt >= lead.position ? 1.0 : -1.0;
        TrackGraphLocation stop = new TrackGraphLocation();
        stop.graph = graph;
        stop.edge = Couple.create(lead.node1.getLocation(), lead.node2.getLocation());
        stop.position = Math.max(0.0, Math.min(lead.edge.getLength(), nearAt - side * back));
        return stop;
    }

    private static int indexOf(List<Couple<TrackNode>> route, TrackGraphLocation loc) {
        for (int i = 0; i < route.size(); i++) {
            Couple<TrackNode> hop = route.get(i);
            boolean forward = ((TrackNode)hop.getFirst()).getLocation().equals(loc.edge.getFirst())
                && ((TrackNode)hop.getSecond()).getLocation().equals(loc.edge.getSecond());
            boolean backward = ((TrackNode)hop.getFirst()).getLocation().equals(loc.edge.getSecond())
                && ((TrackNode)hop.getSecond()).getLocation().equals(loc.edge.getFirst());
            if (forward || backward) {
                return i;
            }
        }

        return -1;
    }

    private static double lengthOf(TrackGraph graph, Couple<TrackNode> hop) {
        TrackEdge edge = graph.getConnection(hop);
        return edge == null ? 0.0 : edge.getLength();
    }

    private static double positionIn(TrackGraph graph, Couple<TrackNode> hop, TrackGraphLocation loc) {
        boolean sameSense = ((TrackNode)hop.getFirst()).getLocation().equals(loc.edge.getFirst());
        return sameSense ? loc.position : lengthOf(graph, hop) - loc.position;
    }

    private static double clearBehind(TrackGraph graph, List<Couple<TrackNode>> route, TrackGraphLocation near, int index) {
        double walked = positionIn(graph, route.get(index), near);

        for (int i = index; i >= 0; i--) {
            if (graph.getConnectionsFrom((TrackNode)route.get(i).getFirst()).size() > 2) {
                return walked;
            }

            if (i > 0) {
                walked += lengthOf(graph, route.get(i - 1));
            }
        }

        return Double.MAX_VALUE;
    }

    private static TrackGraphLocation retreat(TrackGraph graph, List<Couple<TrackNode>> route, TrackGraphLocation near, int index, double back) {
        int i = index;
        double at = positionIn(graph, route.get(i), near);

        double left;
        for (left = back; left > at && i > 0; at = lengthOf(graph, route.get(--i))) {
            left -= at;
        }

        TrackGraphLocation stop = new TrackGraphLocation();
        stop.graph = graph;
        stop.edge = Couple.create(((TrackNode)route.get(i).getFirst()).getLocation(), ((TrackNode)route.get(i).getSecond()).getLocation());
        stop.position = Math.max(0.0, at - left);
        return stop;
    }

    record Berth(SableTrain parked, TrackGraphLocation stop, double free, double need, boolean local) {
        boolean fits() {
            return this.free + 1.0E-6 >= this.need;
        }
    }
}
