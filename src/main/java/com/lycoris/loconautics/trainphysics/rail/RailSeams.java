package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.common.sable.TrackHostFramesClient;
import com.lycoris.loconautics.common.turntable.Turntable;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.simibubi.create.Create;
import com.simibubi.create.api.event.TrackGraphMergeEvent;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.joml.Vector3d;

public final class RailSeams {
    public static final double MAX_SEAM_GAP = 1.0;
    private static final double MIN_SEAM_LENGTH = 0.001;
    private static final Map<ResourceKey<Level>, Level> CLIENT_LEVELS = new ConcurrentHashMap<>();
    private static final int SWEEP_INTERVAL = 20;
    private static final int MAX_WELDS_PER_SWEEP = 8;
    private static final double FACING_DOT = -0.9;
    private static final ThreadLocal<Boolean> CREATE_TRAIN_TRAVEL = new ThreadLocal<>();
    private static int sweepTicks;

    private RailSeams() {
    }

    public static void beginCreateTrainTravel() {
        CREATE_TRAIN_TRAVEL.set(Boolean.TRUE);
    }

    public static void endCreateTrainTravel() {
        CREATE_TRAIN_TRAVEL.remove();
    }

    public static boolean createTrainTravelling() {
        return CREATE_TRAIN_TRAVEL.get() != null;
    }

    public static void trackClientLevel(Level level) {
        if (level != null && level.isClientSide()) {
            CLIENT_LEVELS.put(level.dimension(), level);
        }
    }

    // Fix: CLIENT_LEVELS used to only ever grow, keeping unloaded ClientLevels (with all their
    // chunks and entities) alive after leaving a world.
    public static void untrackClientLevel(Level level) {
        if (level != null) {
            CLIENT_LEVELS.values().removeIf(tracked -> tracked == level);
        }
    }

    public static void clearClientLevels() {
        CLIENT_LEVELS.clear();
    }

    public static boolean isSeam(TrackNodeLocation a, TrackNodeLocation b) {
        return Boolean.TRUE.equals(seamState(a, b));
    }

    public static Boolean seamState(TrackNodeLocation a, TrackNodeLocation b) {
        if (a != null && b != null && a.dimension != null && a.dimension.equals(b.dimension)) {
            SubLevelContainer container = anyContainer(a.dimension);
            if (container == null) {
                return null;
            }

            boolean plotA = inPlotGrid(container, a);
            boolean plotB = inPlotGrid(container, b);
            if (plotA != plotB) {
                return Boolean.TRUE;
            }

            if (!plotA) {
                return Boolean.FALSE;
            }

            ChunkPos cellA = plotCellOf(container, a);
            ChunkPos cellB = plotCellOf(container, b);
            return cellA != null && cellB != null ? !cellA.equals(cellB) : null;
        } else {
            return Boolean.FALSE;
        }
    }

    private static ChunkPos plotCellOf(SubLevelContainer container, TrackNodeLocation location) {
        Vec3 v = location.getLocation();
        LevelPlot plot = container.getPlot(new ChunkPos(BlockPos.containing(v.x, v.y, v.z)));
        return plot == null ? null : plot.plotPos;
    }

    public static boolean isSeam(Couple<TrackNodeLocation> crossing) {
        return crossing != null && isSeam((TrackNodeLocation)crossing.getFirst(), (TrackNodeLocation)crossing.getSecond());
    }

    public static boolean crossable(Couple<TrackNodeLocation> crossing) {
        if (crossing == null) {
            return false;
        }

        TrackNodeLocation a = (TrackNodeLocation)crossing.getFirst();
        TrackNodeLocation b = (TrackNodeLocation)crossing.getSecond();
        return isSeam(a, b) && aligned(a, b);
    }

    public static boolean aligned(TrackNodeLocation a, TrackNodeLocation b) {
        Vec3 pa = worldPosOf(a);
        Vec3 pb = worldPosOf(b);
        return pa != null && pb != null && pa.distanceToSqr(pb) <= 1.0;
    }

    public static ServerSubLevel serverHostOf(TrackNodeLocation location) {
        return location == null ? null : (hostIn(serverLevel(location.dimension), location) instanceof ServerSubLevel sub ? sub : null);
    }

    public static SubLevel clientHostOf(TrackNodeLocation location) {
        return location == null ? null : hostIn(CLIENT_LEVELS.get(location.dimension), location);
    }

    public static Vec3 worldPosOf(TrackNodeLocation location) {
        return location == null ? null : worldPosIn(poseOf(location, true), location);
    }

    public static Pose3dc serverPoseOf(TrackNodeLocation location) {
        return location == null ? null : poseOf(location, true);
    }

    public static boolean hostBodyMissing(TrackNodeLocation location) {
        return location != null && inPlotGrid(location) && serverHostOf(location) == null;
    }

    public static UUID hostIdOf(TrackNodeLocation location) {
        ServerSubLevel host = serverHostOf(location);
        if (host != null) {
            return host.getUniqueId();
        } else {
            return inPlotGrid(location) ? TrackHostFrames.hostId(location) : null;
        }
    }

    private static Pose3dc poseOf(TrackNodeLocation location, boolean server) {
        SubLevel host = (SubLevel)(server ? serverHostOf(location) : clientHostOf(location));
        if (host != null) {
            return host.logicalPose();
        } else if (!inPlotGrid(location)) {
            return null;
        } else {
            return server ? TrackHostFrames.poseAt(location) : TrackHostFramesClient.poseAt(location);
        }
    }

    private static Vec3 worldPosIn(Pose3dc pose, TrackNodeLocation location) {
        Vec3 v = location.getLocation();
        if (pose == null) {
            return v;
        }

        Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static Pose3dc clientPoseOf(TrackNodeLocation location) {
        return location == null ? null : poseOf(location, false);
    }

    public static Vec3 worldPointOf(TrackNodeLocation frame, Vec3 point) {
        Pose3dc pose = frame != null && point != null ? poseOf(frame, true) : null;
        if (pose == null) {
            return point;
        }

        Vector3d w = pose.transformPosition(new Vector3d(point.x, point.y, point.z), new Vector3d());
        return new Vec3(w.x, w.y, w.z);
    }

    public static boolean inPlotGrid(TrackNodeLocation location) {
        SubLevelContainer container = location == null ? null : anyContainer(location.dimension);
        return container != null && inPlotGrid(container, location);
    }

    private static boolean serverSide() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    public static double seamLength(TrackNodeLocation a, TrackNodeLocation b) {
        boolean server = serverSide();
        Vec3 pa = worldPosIn(poseOf(a, server), a);
        Vec3 pb = worldPosIn(poseOf(b, server), b);
        double gap = pa.distanceTo(pb);
        return Double.isFinite(gap) && gap <= 1.0 ? Math.max(gap, 0.001) : 0.001;
    }

    public static Vec3 seamPoint(TrackNodeLocation a, TrackNodeLocation b, double t) {
        boolean server = serverSide();
        Pose3dc poseA = poseOf(a, server);
        Vec3 pa = worldPosIn(poseA, a);
        Vec3 pb = worldPosIn(poseOf(b, server), b);
        if (pa.distanceToSqr(pb) > 1.0) {
            return a.getLocation();
        }

        Vec3 w = pa.lerp(pb, Mth.clamp(t, 0.0, 1.0));
        if (poseA == null) {
            return w;
        }

        Vector3d l = poseA.transformPositionInverse(new Vector3d(w.x, w.y, w.z), new Vector3d());
        return new Vec3(l.x, l.y, l.z);
    }

    public static Vec3 worldDirOf(TrackNodeLocation location, Vec3 direction) {
        if (location != null && direction != null) {
            Pose3dc pose = poseOf(location, true);
            if (pose == null) {
                return direction;
            }

            Vector3d w = pose.transformNormal(new Vector3d(direction.x, direction.y, direction.z), new Vector3d());
            return new Vec3(w.x, w.y, w.z).normalize();
        } else {
            return null;
        }
    }

    public static void sweep() {
        if (sweepTicks++ % 20 == 0) {
            pruneStaleSeams();
            int passes = 8;

            while (passes > 0 && weldOneSeam()) {
                passes--;
            }
        }
    }

    private static boolean weldOneSeam() {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks != null && !networks.isEmpty()) {
            List<RailSeams.Railhead> heads = new ArrayList<>();

            for (TrackGraph graph : new ArrayList<>(networks.values())) {
                for (TrackNodeLocation location : new ArrayList<>(graph.getNodes())) {
                    RailSeams.Railhead head = railheadOf(graph, graph.locateNode(location));
                    if (head != null) {
                        heads.add(head);
                    }
                }
            }

            for (int i = 0; i < heads.size(); i++) {
                RailSeams.Railhead x = heads.get(i);

                for (int j = i + 1; j < heads.size(); j++) {
                    RailSeams.Railhead y = heads.get(j);
                    if (!(x.world.distanceToSqr(y.world) > 1.0)
                        && !(x.alongRail.dot(y.alongRail) > -0.9)
                        && isSeam(x.node.getLocation(), y.node.getLocation())
                        && seamOf(x.graph, x.node) == null
                        && seamOf(y.graph, y.node) == null
                        && !onTurningPlate(x.node.getLocation())
                        && !onTurningPlate(y.node.getLocation())
                        && weld(x.graph, x.node, y.graph, y.node, x.material)) {
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static boolean onTurningPlate(TrackNodeLocation location) {
        Turntable plate = TurntableRegistry.byPlate(hostIdOf(location));
        return plate != null && !plate.turntableSettled();
    }

    public static int unweldSeamsAt(TrackNodeLocation head) {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks != null && head != null) {
            int dropped = 0;

            for (TrackGraph graph : new ArrayList<>(networks.values())) {
                TrackNode node = graph.locateNode(head);
                if (node != null) {
                    int before = dropped;

                    for (TrackNode other : new ArrayList<>(graph.getConnectionsFrom(node).keySet())) {
                        TrackEdge edge = graph.getConnection(Couple.create(node, other));
                        if (edge != null && edge.isInterDimensional() && isSeam(head, other.getLocation())) {
                            graph.disconnectNodes(node, other);
                            dropped++;
                        }
                    }

                    if (dropped > before) {
                        resync(graph);
                    }
                }
            }

            return dropped;
        } else {
            return 0;
        }
    }

    private static TrackNode seamOf(TrackGraph graph, TrackNode node) {
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        if (connections == null) {
            return null;
        }

        for (Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (entry.getValue().isInterDimensional() && isSeam(node.getLocation(), entry.getKey().getLocation())) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static void pruneStaleSeams() {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks != null) {
            for (TrackGraph graph : new ArrayList<>(networks.values())) {
                int dropped = 0;

                for (TrackNodeLocation location : new ArrayList<>(graph.getNodes())) {
                    TrackNode node = graph.locateNode(location);
                    Map<TrackNode, TrackEdge> connections = node == null ? null : graph.getConnectionsFrom(node);
                    if (connections != null) {
                        TrackNode kept = null;

                        for (TrackNode other : new ArrayList<>(connections.keySet())) {
                            TrackEdge edge = connections.get(other);
                            if (edge != null && edge.isInterDimensional() && isSeam(location, other.getLocation())) {
                                if (kept == null && aligned(location, other.getLocation())) {
                                    kept = other;
                                } else {
                                    graph.disconnectNodes(node, other);
                                    dropped++;
                                }
                            }
                        }
                    }
                }

                if (dropped > 0) {
                    resync(graph);
                }
            }
        }
    }

    private static void resync(TrackGraph graph) {
        graph.markDirty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Create.RAILWAYS.sync.sendFullGraphTo(graph, player);
            }
        }
    }

    public static RailSeams.Railhead railheadOf(TrackGraph graph, TrackNode node) {
        if (node == null) {
            return null;
        }

        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
        if (connections == null) {
            return null;
        }

        List<TrackEdge> rails = connections.values().stream().filter(edge -> !edge.isInterDimensional()).limit(2L).toList();
        if (rails.size() != 1) {
            return null;
        }

        TrackEdge rail = rails.get(0);
        TrackNodeLocation at = node.getLocation();
        Vec3 world = worldPosOf(at);
        Vec3 along = worldDirOf(at, rail.node1 == node ? rail.getDirection(true) : rail.getDirection(false).scale(-1.0));
        return world != null && along != null ? new RailSeams.Railhead(graph, node, world, along, rail.getTrackMaterial()) : null;
    }

    public static TrackGraph mergeGraphs(TrackGraph graphA, TrackGraph graphB) {
        if (graphA != graphB && graphA != null && graphB != null) {
            TrackGraph from = graphA.getNodes().size() < graphB.getNodes().size() ? graphA : graphB;
            TrackGraph into = from == graphA ? graphB : graphA;
            NeoForge.EVENT_BUS.post(new TrackGraphMergeEvent(from, into));
            from.transferAll(into);
            Create.RAILWAYS.removeGraphAndGroup(from);
            Create.RAILWAYS.sync.graphRemoved(from);
            return into;
        } else {
            return graphA == null ? graphB : graphA;
        }
    }

    public static TrackGraph liveGraphOf(TravellingPoint point, TrackGraph held) {
        TrackGraph live = point != null && point.node1 != null ? TurntableRegistry.graphHolding(point.node1.getLocation()) : null;
        return live != null ? live : held;
    }

    public static boolean weld(TrackGraph graphA, TrackNode a, TrackGraph graphB, TrackNode b, TrackMaterial material) {
        if (graphA != null && graphB != null && a != null && b != null) {
            TrackNodeLocation la = a.getLocation();
            TrackNodeLocation lb = b.getLocation();
            if (!isSeam(la, lb)) {
                return false;
            } else {
                Vec3 pa = worldPosOf(la);
                Vec3 pb = worldPosOf(lb);
                double gap = pa.distanceTo(pb);
                if (gap > 1.0) {
                    return false;
                } else {
                    GlobalRailwayManager manager = Create.RAILWAYS;
                    TrackGraph graph = mergeGraphs(graphA, graphB);
                    TrackNode n1 = graph.locateNode(la);
                    TrackNode n2 = graph.locateNode(lb);
                    if (n1 == null || n2 == null) {
                        return false;
                    } else if (graph.getConnection(Couple.create(n1, n2)) != null) {
                        return true;
                    } else if (seamOf(graph, n1) == null && seamOf(graph, n2) == null) {
                        TrackMaterial mat = material == null ? TrackMaterial.ANDESITE : material;
                        TrackEdge forward = new TrackEdge(n1, n2, null, mat);
                        TrackEdge backward = new TrackEdge(n2, n1, null, mat);
                        graph.putConnection(n1, n2, forward);
                        graph.putConnection(n2, n1, backward);
                        manager.sync.edgeAdded(graph, n1, n2, forward);
                        manager.sync.edgeAdded(graph, n2, n1, backward);
                        graph.markDirty();
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } else {
            return false;
        }
    }

    public static boolean weldNow(TrackNodeLocation a, TrackNodeLocation b) {
        GlobalRailwayManager manager = Create.RAILWAYS;
        Map<UUID, TrackGraph> networks = manager == null ? null : manager.trackNetworks;
        if (networks != null && a != null && b != null) {
            TrackGraph graphA = null;
            TrackGraph graphB = null;

            for (TrackGraph graph : new ArrayList<>(networks.values())) {
                if (graphA == null && graph.locateNode(a) != null) {
                    graphA = graph;
                }

                if (graphB == null && graph.locateNode(b) != null) {
                    graphB = graph;
                }
            }

            if (graphA != null && graphB != null) {
                RailSeams.Railhead head = railheadOf(graphA, graphA.locateNode(a));
                return weld(graphA, graphA.locateNode(a), graphB, graphB.locateNode(b), head == null ? null : head.material());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static SubLevel hostAt(Level level, BlockPos pos) {
        SubLevelContainer container = level != null && pos != null ? SubLevelContainer.getContainer(level) : null;
        if (container != null && container.inBounds(pos.getX() >> 4, pos.getZ() >> 4)) {
            LevelPlot plot = container.getPlot(new ChunkPos(pos));
            return plot == null ? null : plot.getSubLevel();
        } else {
            return null;
        }
    }

    private static SubLevel hostIn(Level level, TrackNodeLocation location) {
        if (location != null && location.dimension != null) {
            SubLevelContainer container = level == null ? null : SubLevelContainer.getContainer(level);
            if (container != null && inPlotGrid(container, location)) {
                Vec3 v = location.getLocation();
                LevelPlot plot = container.getPlot(new ChunkPos(BlockPos.containing(v.x, v.y, v.z)));
                return plot == null ? null : plot.getSubLevel();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static boolean inPlotGrid(SubLevelContainer container, TrackNodeLocation location) {
        return container.inBounds(Mth.floor(location.getX() / 2.0) >> 4, Mth.floor(location.getZ() / 2.0) >> 4);
    }

    private static SubLevelContainer anyContainer(ResourceKey<Level> dimension) {
        Level level = serverLevel(dimension);
        if (level == null) {
            level = CLIENT_LEVELS.get(dimension);
        }

        return level == null ? null : SubLevelContainer.getContainer(level);
    }

    private static Level serverLevel(ResourceKey<Level> dimension) {
        MinecraftServer server = dimension == null ? null : ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getLevel(dimension);
    }

    public record Railhead(TrackGraph graph, TrackNode node, Vec3 world, Vec3 alongRail, TrackMaterial material) {
    }
}
