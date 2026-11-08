package com.lycoris.loconautics.content.schedule.map.client;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MapTrackOverlay {
    private MapTrackOverlay() {
    }

    public static List<MapTrackOverlay.Edge> collect(ResourceKey<Level> dim) {
        List<MapTrackOverlay.Edge> out = new ArrayList<>();
        if (Create.RAILWAYS.trackNetworks == null) {
            return out;
        }

        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation locA : graph.getNodes()) {
                if (dim.equals(locA.getDimension())) {
                    TrackNode a = graph.locateNode(locA);
                    if (a != null) {
                        for (Entry<TrackNode, TrackEdge> entry : graph.getConnectionsFrom(a).entrySet()) {
                            TrackNode b = entry.getKey();
                            TrackEdge edge = entry.getValue();
                            if (!edge.isInterDimensional() && a.getNetId() <= b.getNetId()) {
                                int seg = edge.isTurn() ? Math.max(1, edge.getTurn().getSegmentCount()) : 1;
                                double[] xs = new double[seg + 1];
                                double[] zs = new double[seg + 1];

                                for (int i = 0; i <= seg; i++) {
                                    Vec3 p = edge.getPosition(graph, (double)i / seg);
                                    xs[i] = p.x;
                                    zs[i] = p.z;
                                }

                                out.add(
                                    new MapTrackOverlay.Edge(
                                        graph.id,
                                        a.getLocation(),
                                        b.getLocation(),
                                        Math.min(a.getNetId(), b.getNetId()),
                                        Math.max(a.getNetId(), b.getNetId()),
                                        xs,
                                        zs
                                    )
                                );
                            }
                        }
                    }
                }
            }
        }

        return out;
    }

    public record Edge(UUID graphId, TrackNodeLocation n1, TrackNodeLocation n2, int netMin, int netMax, double[] xs, double[] zs) {
        public double arcLength() {
            double total = 0.0;

            for (int i = 1; i < this.xs.length; i++) {
                double dx = this.xs[i] - this.xs[i - 1];
                double dz = this.zs[i] - this.zs[i - 1];
                total += Math.sqrt(dx * dx + dz * dz);
            }

            return total;
        }

        public long key() {
            return (long)this.netMin << 32 | this.netMax & 4294967295L;
        }
    }
}
