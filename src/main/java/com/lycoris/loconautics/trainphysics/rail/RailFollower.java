package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.world.phys.Vec3;

public final class RailFollower {
    private final TrackGraph graph;
    private final TravellingPoint point;
    private final ITrackSelector selector;
    private double speed;
    private Vec3 lastPos;

    private RailFollower(TrackGraph graph, TravellingPoint point, Vec3 upNormal, double speed) {
        this.graph = graph;
        this.point = point;
        this.speed = speed;
        this.selector = point.steer(SteerDirection.NONE, upNormal);
        this.lastPos = point.edge != null ? point.getPosition(graph) : Vec3.ZERO;
    }

    public static RailFollower at(TrackGraphLocation location, Vec3 upNormal, double speed) {
        TravellingPoint point = pointAt(location);
        return point == null ? null : new RailFollower(location.graph, point, upNormal, speed);
    }

    public static TravellingPoint pointAt(TrackGraphLocation location) {
        TrackGraph graph = location.graph;
        TrackNode node1 = graph.locateNode((TrackNodeLocation)location.edge.getFirst());
        TrackNode node2 = graph.locateNode((TrackNodeLocation)location.edge.getSecond());
        if (node1 != null && node2 != null) {
            TrackEdge edge = (TrackEdge)graph.getConnectionsFrom(node1).get(node2);
            return edge == null ? null : new TravellingPoint(node1, node2, edge, location.position, false);
        } else {
            return null;
        }
    }

    public Vec3 tick() {
        if (this.point.edge == null) {
            return this.lastPos;
        }

        this.point.travel(this.graph, this.speed, this.selector, this.point.ignoreEdgePoints(), this.point.ignoreTurns(), this.point.ignorePortals());
        if (this.point.blocked && this.point.edge != null) {
            this.point.reverse(this.graph);
        }

        if (this.point.edge == null) {
            return this.lastPos;
        }

        this.lastPos = this.point.getPosition(this.graph);
        return this.lastPos;
    }

    public Vec3 position() {
        return this.point.edge == null ? this.lastPos : this.point.getPosition(this.graph);
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
}
