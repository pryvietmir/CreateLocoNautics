package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.compat.railways.RailwaysSwitches;
import com.lycoris.loconautics.foundation.debug.SteerTrace;
import com.lycoris.loconautics.foundation.math.VecMath;
import com.lycoris.loconautics.trainphysics.portal.PortalFrame;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.ITrackSelector;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.data.Couple;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class RailCarriage {
    private TrackGraph graph;
    private final TravellingPoint leading;
    private final TravellingPoint trailing;
    private boolean reachedEnd;
    private Couple<TrackNodeLocation> pendingPortal;
    private PortalFrame portal;
    private Vec3 steerTarget;
    private TurnLog turnLog;
    private boolean turnLeader;
    private long turnCursor;
    private TurnMemory turnMemory;
    private TurnMemory.BogeyId turnId;
    private TravellingPoint followTarget;
    private String junctionTag = "unset";
    private List<Couple<TrackNode>> path;
    private final double bogeySpacing;
    private final Vec3 upNormal;
    private double speed;
    private double travelIntent;
    private boolean stopped;
    private double lastTravelSign = 1.0;
    private boolean independent;
    private Vector3d forward0;
    private Vector3d up0;
    private static final double END_LATCH_TRAVEL = 0.001;
    private static final double DETECT_RANGE = 3.0;

    private RailCarriage(TrackGraph graph, TravellingPoint leading, TravellingPoint trailing, Vec3 upNormal, double bogeySpacing, double speed) {
        this.graph = graph;
        this.leading = leading;
        this.trailing = trailing;
        this.bogeySpacing = bogeySpacing;
        this.upNormal = upNormal;
        this.speed = speed;
        this.captureReference();
    }

    public static RailCarriage at(TrackGraphLocation location, Vec3 upNormal, double bogeySpacing, double speed) {
        TravellingPoint seed = RailFollower.pointAt(location);
        return seed == null ? null : seededAt(seed, location.graph, upNormal, bogeySpacing, speed);
    }

    public static RailCarriage seededAt(TravellingPoint seed, TrackGraph graph, Vec3 upNormal, double bogeySpacing, double speed) {
        if (seed != null && seed.edge != null && graph != null) {
            RailCarriage carriage = new RailCarriage(graph, copyOf(seed), copyOf(seed), upNormal, bogeySpacing, speed);
            carriage.seatSpan(seed, graph, bogeySpacing / 2.0);
            carriage.captureReference();
            return carriage;
        } else {
            return null;
        }
    }

    private static TravellingPoint copyOf(TravellingPoint src) {
        return new TravellingPoint(src.node1, src.node2, src.edge, src.position, src.upsideDown);
    }

    public TravellingPoint seedPoint() {
        if (this.trailing != null && this.trailing.edge != null) {
            TravellingPoint seed = copyOf(this.trailing);
            seed.travel(
                this.graph,
                this.bogeySpacing / 2.0,
                seed.steer(SteerDirection.NONE, this.upNormal),
                seed.ignoreEdgePoints(),
                seed.ignoreTurns(),
                seed.ignorePortals()
            );
            return seed.edge == null ? null : seed;
        } else {
            return null;
        }
    }

    private double clampStress(double stress) {
        double limit = this.bogeySpacing * 0.5;
        return stress < -limit ? -limit : (stress > limit ? limit : stress);
    }

    public Vec3 tick() {
        this.adoptLiveGraph();
        this.rebind();
        Vec3 hopCenterBefore = this.hopCenter();
        String hopKindBefore = this.hopKind(this.leading) + "/" + this.hopKind(this.trailing);
        double hopMoved = 0.0;
        if (!this.stopped && this.speed != 0.0) {
            Couple<TrackNodeLocation> heldPortal = this.pendingPortal;
            this.pendingPortal = null;
            boolean towardHigherPosition = this.travelIntent != 0.0 ? this.travelIntent > 0.0 : this.speed > 0.0;
            TravellingPoint front = towardHigherPosition == this.leadingAtHigherPosition() ? this.leading : this.trailing;
            TravellingPoint rear = front == this.leading ? this.trailing : this.leading;
            ITrackSelector chosenSelector = this.turnLog != null && !this.turnLeader
                ? this.replaySelector(front)
                : (
                    this.path != null && !this.path.isEmpty()
                        ? this.pathSelector()
                        : (this.followTarget != null && this.turnLog == null ? front.follow(this.followTarget) : this.steerSelector(front))
                );
            String selectorKind = this.turnLog != null && !this.turnLeader
                ? "replay"
                : (this.path != null && !this.path.isEmpty() ? "path" : (this.followTarget != null && this.turnLog == null ? "follow" : "steer"));
            ITrackSelector frontSelector = (g, pair) -> {
                Entry<TrackNode, TrackEdge> entry = (Entry<TrackNode, TrackEdge>)chosenSelector.apply(g, pair);
                LoconauticsConstants.LOGGER
                    .debug(
                        "[issue274] junction {} selector={} options={} picked={} of {} speed={} intent={} from={}",
                        new Object[]{
                            this.junctionTag,
                            selectorKind,
                            ((List)pair.getSecond()).size(),
                            ((List)pair.getSecond()).indexOf(entry),
                            nodeList((List<Entry<TrackNode, TrackEdge>>)pair.getSecond()),
                            String.format("%.3f", this.speed),
                            String.format("%.3f", this.travelIntent),
                            front.edge == null ? "none" : front.edge.node1.getLocation().getLocation() + ">" + front.edge.node2.getLocation().getLocation()
                        }
                    );
                return this.settleTurn(g, front, (Boolean)pair.getFirst(), (List<Entry<TrackNode, TrackEdge>>)pair.getSecond(), entry);
            };
            double stress = this.clampStress(this.bogeySpacing - this.leadingPos().distanceTo(this.trailingPos()));
            double frontCorrection = (towardHigherPosition ? 0.5 : -0.5) * stress;
            double rearCorrection = -frontCorrection;
            RailwaysSwitches.beginTravel(front, rear);

            double moved;
            try {
                moved = front.travel(
                    this.graph, this.speed + frontCorrection, frontSelector, front.ignoreEdgePoints(), front.ignoreTurns(), this.portalGate(front)
                );
                rear.travel(this.graph, moved + rearCorrection, rear.follow(front), rear.ignoreEdgePoints(), rear.ignoreTurns(), this.portalGate(rear));
            } finally {
                RailwaysSwitches.endTravel();
            }

            hopMoved = moved;
            if (this.pendingPortal == null && Math.abs(moved) <= 0.001) {
                this.pendingPortal = heldPortal;
            }

            if (this.pendingPortal != null) {
                this.reachedEnd = false;
            } else if (front.blocked) {
                if (!this.reachedEnd) {
                }

                this.reachedEnd = true;
                this.stopped = true;
            } else if (Math.abs(moved) > 0.001) {
                this.reachedEnd = false;
            }
        }

        this.traceHopSnap(hopCenterBefore, hopKindBefore, hopMoved);
        return this.leadingPos().add(this.trailingPos()).scale(0.5);
    }

    private Vec3 hopCenter() {
        Vec3 a = this.worldPos(this.leading);
        Vec3 b = this.worldPos(this.trailing);
        return a != null && b != null ? a.add(b).scale(0.5) : null;
    }

    private String hopKind(TravellingPoint p) {
        if (p != null && p.edge != null && p.edge.node1 != null) {
            TrackNodeLocation at = p.edge.node1.getLocation();
            if (p.edge.isInterDimensional()) {
                return RailSeams.isSeam(at, p.edge.node2.getLocation()) ? "SEAM" : "PORTAL";
            } else if (!RailSeams.inPlotGrid(at)) {
                return "WORLD";
            } else {
                return RailSeams.serverHostOf(at) != null ? "PLOT-live" : (poseOfEdge(p.edge) != null ? "PLOT-frame" : "PLOT-NOPOSE");
            }
        } else {
            return "none";
        }
    }

    private String hopPoint(TravellingPoint p) {
        if (p != null && p.edge != null) {
            Vec3 w = this.worldPos(p);
            Vec3 d = p.edge.getDirectionAt(p.position);
            Vec3 wd = d == null ? null : this.homeDir(p.edge, d);
            return String.format(
                "%s pos=%.3f/%.4f world=%s dir=%s blocked=%s", this.hopKind(p), p.position, p.edge.getLength(), hopVec(w), hopVec(wd), p.blocked
            );
        } else {
            return "none";
        }
    }

    public String moveLine() {
        Vec3 center = this.hopCenter();
        return String.format(
            "center=%s span=%.3f lead[%s] trail[%s]",
            hopVec(center),
            this.leadingPos().distanceTo(this.trailingPos()),
            this.hopPoint(this.leading),
            this.hopPoint(this.trailing)
        );
    }

    public Vec3 moveCenter() {
        return this.hopCenter();
    }

    private static String hopVec(Vec3 v) {
        return v == null ? "null" : String.format("(%.3f,%.3f,%.3f)", v.x, v.y, v.z);
    }

    private void traceHopSnap(Vec3 centerBefore, String kindBefore, double moved) {
        Vec3 centerAfter = this.hopCenter();
        String kindAfter = this.hopKind(this.leading) + "/" + this.hopKind(this.trailing);
        double jump = centerBefore != null && centerAfter != null ? centerAfter.distanceTo(centerBefore) : 0.0;
        double excess = Math.abs(jump - Math.abs(moved));
        boolean onSeam = kindAfter.contains("SEAM") || kindBefore.contains("SEAM");
        if (onSeam || !(excess < 0.2) || !kindBefore.equals(kindAfter)) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[hopsnap] rail={} kind {} -> {} moved={} centerJump={} excess={} gapSpan={} lead[{}] trail[{}] centerBefore={} centerAfter={}",
                    new Object[]{
                        System.identityHashCode(this) & 65535,
                        kindBefore,
                        kindAfter,
                        String.format("%.4f", moved),
                        String.format("%.4f", jump),
                        String.format("%.4f", excess),
                        String.format("%.4f", this.leadingPos().distanceTo(this.trailingPos())),
                        this.hopPoint(this.leading),
                        this.hopPoint(this.trailing),
                        hopVec(centerBefore),
                        hopVec(centerAfter)
                    }
                );
        }
    }

    private boolean leadingAtHigherPosition() {
        Vec3 tangent = this.edgeTangent();
        Vec3 lead = this.leadingPos();
        Vec3 trail = this.trailingPos();
        if (tangent != null && lead != null && trail != null) {
            Vec3 span = lead.subtract(trail);
            return span.lengthSqr() < 1.0E-9 || tangent.dot(span) >= 0.0;
        } else {
            return true;
        }
    }

    public boolean reachedEnd() {
        return this.reachedEnd;
    }

    public Vec3 railheadOutward() {
        TravellingPoint p = this.leading.blocked && this.leading.edge != null
            ? this.leading
            : (this.trailing.blocked && this.trailing.edge != null ? this.trailing : null);
        if (p != null && !p.edge.isInterDimensional()) {
            Vec3 d = p.edge.getDirectionAt(p.position);
            if (d != null && !(d.lengthSqr() < 1.0E-9)) {
                Vec3 w = this.homeDir(p.edge, d);
                if (w != null && !(w.lengthSqr() < 1.0E-9)) {
                    w = w.normalize();
                    return p.position * 2.0 >= p.edge.getLength() ? w : w.scale(-1.0);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean railheadWithin(double distance) {
        return this.blockedWithin(distance) || this.blockedWithin(-distance);
    }

    private boolean blockedWithin(double distance) {
        TravellingPoint probe = this.seedPoint();
        if (probe == null) {
            return false;
        }

        probe.travel(
            this.graph, distance, probe.steer(SteerDirection.NONE, this.upNormal), probe.ignoreEdgePoints(), probe.ignoreTurns(), probe.ignorePortals()
        );
        return probe.blocked;
    }

    public Couple<TrackNodeLocation> pendingPortal() {
        return this.pendingPortal;
    }

    public double travelIntent() {
        return this.travelIntent;
    }

    public PortalFrame portal() {
        return this.portal;
    }

    public void setPortal(PortalFrame frame) {
        this.portal = frame;
    }

    public void clearPortal() {
        this.pendingPortal = null;
        this.portal = null;
    }

    private IPortalListener portalGate(TravellingPoint point) {
        return crossing -> {
            if (RailSeams.crossable(crossing)) {
                return false;
            }

            if (this.portal == null) {
                this.portal = PortalFrame.of(this.graph, crossing, dimOf(point.edge));
            }

            if (this.portal != null && this.portal.isPortal(crossing)) {
                return false;
            }

            this.pendingPortal = crossing;
            return true;
        };
    }

    public boolean anyPointFar() {
        return this.isFar(this.leading) || this.isFar(this.trailing);
    }

    public PortalFrame portalWithin(double distance) {
        TravellingPoint seed = this.seedPoint();
        if (seed == null) {
            return null;
        }

        ResourceKey<Level> home = dimOf(seed.edge);
        PortalFrame ahead = this.portalAlong(seed, distance, home);
        return ahead != null ? ahead : this.portalAlong(this.seedPoint(), -distance, home);
    }

    private PortalFrame portalAlong(TravellingPoint probe, double distance, ResourceKey<Level> home) {
        AtomicReference<Couple<TrackNodeLocation>> hit = new AtomicReference<>();
        probe.travel(this.graph, distance, probe.steer(SteerDirection.NONE, this.upNormal), probe.ignoreEdgePoints(), probe.ignoreTurns(), crossing -> {
            if (RailSeams.crossable(crossing)) {
                return false;
            }

            hit.set(crossing);
            return true;
        });
        return hit.get() == null ? null : PortalFrame.of(this.graph, hit.get(), home);
    }

    public boolean isFar(TravellingPoint p) {
        return this.portal != null && p != null && p.edge != null && this.portal.farDim().equals(dimOf(p.edge));
    }

    private boolean isFarEdge(TrackEdge edge) {
        return this.portal != null && edge != null && this.portal.farDim().equals(dimOf(edge));
    }

    private static ResourceKey<Level> dimOf(TrackEdge edge) {
        return edge != null && edge.node1 != null ? edge.node1.getLocation().dimension : null;
    }

    private Vec3 toHome(TrackEdge edge, Vec3 v) {
        return v != null && this.isFarEdge(edge) ? this.portal.toNear(v) : v;
    }

    private Vec3 homeDir(TrackEdge edge, Vec3 d) {
        Vec3 w = worldDir(poseOfEdge(edge), d);
        return w != null && this.isFarEdge(edge) ? this.portal.dirToNear(w) : w;
    }

    public SableRailHop.Result railAheadTarget(ServerLevel level, ServerSubLevel selfBody, Vec3 worldPos, Vec3 tangent, Vec3 worldVert, boolean sweepAhead) {
        SableRailHop.Probe probe = this.railAheadProbe(level, selfBody, worldPos, tangent, worldVert, sweepAhead);
        return probe == null ? null : probe.hit();
    }

    public SableRailHop.Probe railAheadProbe(ServerLevel level, ServerSubLevel selfBody, Vec3 worldPos, Vec3 tangent, Vec3 worldVert, boolean sweepAhead) {
        return worldPos != null && !this.isFar(this.leading)
            ? SableRailHop.probe(level, this.graph, hostOf(this.leading), selfBody, worldPos, tangent, worldVert, 3.0, sweepAhead)
            : null;
    }

    public boolean weldAheadTo(SableRailHop.Result target) {
        if (target != null && target.location() != null && target.graph() != null && target.railPoint() != null) {
            this.adoptLiveGraph();
            TravellingPoint at = this.blockedNearest(target.railPoint());
            if (at != null && !this.isFar(at)) {
                Vec3 here = this.worldPos(at);
                if (here == null) {
                    return false;
                }

                TrackNode theirs = nearer(
                    target.graph().locateNode((TrackNodeLocation)target.location().edge.getFirst()),
                    target.graph().locateNode((TrackNodeLocation)target.location().edge.getSecond()),
                    here
                );
                if (theirs == null) {
                    return false;
                }

                TrackNode ours = nearer(at.node1, at.node2, RailSeams.worldPosOf(theirs.getLocation()));
                return RailSeams.weld(this.graph, ours, target.graph(), theirs, at.edge.getTrackMaterial());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private TravellingPoint blockedNearest(Vec3 to) {
        TravellingPoint a = this.leading.blocked && this.leading.edge != null ? this.leading : null;
        TravellingPoint b = this.trailing.blocked && this.trailing.edge != null ? this.trailing : null;
        if (a != null && b != null) {
            return this.worldPos(a).distanceToSqr(to) <= this.worldPos(b).distanceToSqr(to) ? a : b;
        } else {
            return a != null ? a : b;
        }
    }

    private static TrackNode nearer(TrackNode a, TrackNode b, Vec3 to) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            Vec3 pa = RailSeams.worldPosOf(a.getLocation());
            Vec3 pb = RailSeams.worldPosOf(b.getLocation());
            if (pa == null) {
                return b;
            } else {
                return pb != null && !(pa.distanceToSqr(to) <= pb.distanceToSqr(to)) ? b : a;
            }
        }
    }

    private boolean seatSpan(TravellingPoint seed, TrackGraph newGraph, double half) {
        this.graph = newGraph;
        copyPoint(this.leading, seed);
        this.leading
            .travel(
                this.graph,
                half,
                this.leading.steer(SteerDirection.NONE, this.upNormal),
                this.leading.ignoreEdgePoints(),
                this.leading.ignoreTurns(),
                this.leading.ignorePortals()
            );
        copyPoint(this.trailing, seed);
        this.trailing
            .travel(
                this.graph,
                -half,
                this.trailing.steer(SteerDirection.NONE, this.upNormal),
                this.trailing.ignoreEdgePoints(),
                this.trailing.ignoreTurns(),
                this.trailing.ignorePortals()
            );
        return this.worldPos(this.leading) != null && this.worldPos(this.trailing) != null;
    }

    private static ServerSubLevel hostOf(TravellingPoint p) {
        return p == null ? null : hostOfEdge(p.edge);
    }

    private static ServerSubLevel hostOfEdge(TrackEdge edge) {
        return edge != null && edge.node1 != null ? RailSeams.serverHostOf(edge.node1.getLocation()) : null;
    }

    private static Pose3dc poseOfEdge(TrackEdge edge) {
        return edge != null && edge.node1 != null ? RailSeams.serverPoseOf(edge.node1.getLocation()) : null;
    }

    private static Pose3dc poseOf(TravellingPoint p) {
        return p == null ? null : poseOfEdge(p.edge);
    }

    public boolean railHostMissing() {
        return hostMissing(this.leading) || hostMissing(this.trailing);
    }

    private static boolean hostMissing(TravellingPoint p) {
        return p != null && p.edge != null && p.edge.node1 != null && RailSeams.hostBodyMissing(p.edge.node1.getLocation());
    }

    public Vec3 worldPointOf(TravellingPoint p) {
        return this.worldPos(p);
    }

    private Vec3 worldPos(TravellingPoint p) {
        if (p != null && p.edge != null) {
            Vec3 v = p.edge.getLength() < 1.0E-9 ? nodePoint(p) : p.getPosition(this.graph);
            Pose3dc pose = poseOf(p);
            if (v == null) {
                return null;
            }

            if (pose != null) {
                Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
                v = new Vec3(w.x, w.y, w.z);
            }

            return this.toHome(p.edge, v);
        } else {
            return null;
        }
    }

    private static Vec3 nodePoint(TravellingPoint p) {
        Vec3 normal = p.node1.getNormal();
        return p.node1.getLocation().getLocation().add(p.upsideDown ? normal.scale(-1.0) : normal);
    }

    private static Vec3 worldDir(Pose3dc pose, Vec3 d) {
        if (d != null && pose != null) {
            Vector3d w = pose.transformNormal(new Vector3d(d.x, d.y, d.z), new Vector3d());
            return new Vec3(w.x, w.y, w.z);
        } else {
            return d;
        }
    }

    private static String nodeList(List<Entry<TrackNode, TrackEdge>> options) {
        StringBuilder out = new StringBuilder();

        for (Entry<TrackNode, TrackEdge> option : options) {
            out.append(option.getKey().getLocation().getLocation()).append(' ');
        }

        return out.toString().trim();
    }

    public void setJunctionTag(String tag) {
        this.junctionTag = tag;
    }

    private ITrackSelector steerSelector(TravellingPoint point) {
        return (g, pair) -> {
            List<Entry<TrackNode, TrackEdge>> targets = (List<Entry<TrackNode, TrackEdge>>)pair.getSecond();
            if (targets.size() == 1) {
                return this.recordTurn(targets.get(0));
            } else {
                boolean forward = (Boolean)pair.getFirst();
                Vec3 trajectory = point.edge.isInterDimensional()
                    ? this.forward()
                    : this.homeDir(point.edge, forward ? point.edge.getDirection(false) : point.edge.getDirection(true).scale(-1.0));
                if (this.steerTarget == null && this.followTarget != null) {
                    Entry<TrackNode, TrackEdge> followed = (Entry<TrackNode, TrackEdge>)point.follow(this.followTarget).apply(g, pair);
                    SteerTrace.pick(
                        this,
                        "follow target edge " + this.followTarget.edge.node1.getLocation().getLocation() + " pos " + this.followTarget.position,
                        targets.size(),
                        followed == targets.get(0) ? "first" : "other"
                    );
                    return this.recordTurn(followed);
                } else {
                    boolean unprompted = this.steerTarget == null;
                    Entry<TrackNode, TrackEdge> best = this.bestByGoal(targets, unprompted ? trajectory : this.steerTarget, unprompted);
                    Entry<TrackNode, TrackEdge> picked = best != null ? best : targets.get(0);
                    SteerTrace.pick(
                        this, unprompted ? "straight, no intent and no follow" : "intent", targets.size(), picked == targets.get(0) ? "first" : "other"
                    );
                    return this.recordTurn(picked);
                }
            }
        };
    }

    private Entry<TrackNode, TrackEdge> bestByGoal(List<Entry<TrackNode, TrackEdge>> targets, Vec3 goal, boolean straight) {
        Entry<TrackNode, TrackEdge> best = null;
        double bestScore = -Double.MAX_VALUE;

        for (Entry<TrackNode, TrackEdge> entry : targets) {
            Vec3 outgoing = this.homeDir(entry.getValue(), entry.getValue().getDirection(true));
            Vec3 far = this.homeDir(entry.getValue(), entry.getValue().getDirection(false));
            double near = goal.x * outgoing.x + goal.z * outgoing.z;
            double away = goal.x * far.x + goal.z * far.z;
            double score = straight ? near + away : near;
            if (score > bestScore) {
                bestScore = score;
                best = entry;
            }
        }

        return best;
    }

    public void setTurnMemory(TurnMemory memory, TurnMemory.BogeyId id) {
        this.turnMemory = memory;
        this.turnId = id;
    }

    private Entry<TrackNode, TrackEdge> settleTurn(
        TrackGraph g, TravellingPoint front, boolean forward, List<Entry<TrackNode, TrackEdge>> options, Entry<TrackNode, TrackEdge> own
    ) {
        if (this.turnMemory != null && front.edge != null) {
            if (options.size() < 2) {
                this.turnMemory.unwind(front.edge, forward, this.turnId);
                return own;
            } else {
                return this.turnMemory.settle(front.edge, forward, options, own, this.plannedTurn(g, options), this.turnId);
            }
        } else {
            return own;
        }
    }

    private Entry<TrackNode, TrackEdge> plannedTurn(TrackGraph g, List<Entry<TrackNode, TrackEdge>> options) {
        List<Couple<TrackNode>> route = this.turnMemory.route();
        if (route != null) {
            for (Couple<TrackNode> leg : route) {
                TrackEdge edge = g.getConnection(leg);

                for (Entry<TrackNode, TrackEdge> entry : options) {
                    if (entry.getValue() == edge) {
                        return entry;
                    }
                }
            }
        }

        Vec3 goal = this.turnMemory.intent();
        return goal == null ? null : this.bestByGoal(options, goal, false);
    }

    private Entry<TrackNode, TrackEdge> recordTurn(Entry<TrackNode, TrackEdge> entry) {
        if (this.turnLog != null && this.turnLeader) {
            this.turnLog.record(entry.getKey());
            this.turnCursor = this.turnLog.head();
        }

        return entry;
    }

    private ITrackSelector replaySelector(TravellingPoint point) {
        return (g, pair) -> {
            List<Entry<TrackNode, TrackEdge>> targets = (List<Entry<TrackNode, TrackEdge>>)pair.getSecond();
            TrackNode want = this.turnLog.at(this.turnCursor);
            if (want != null) {
                this.turnCursor++;

                for (Entry<TrackNode, TrackEdge> entry : targets) {
                    if (entry.getKey() == want) {
                        return entry;
                    }
                }
            }

            if (targets.size() == 1) {
                return targets.get(0);
            } else {
                return this.followTarget != null ? (Entry)point.follow(this.followTarget).apply(g, pair) : targets.get(0);
            }
        };
    }

    public void setTurnLog(TurnLog log, boolean leader) {
        if (this.turnLog != log) {
            this.turnLog = log;
            this.turnCursor = log == null ? 0L : log.head();
        }

        if (this.turnLeader != leader) {
            this.turnLeader = leader;
            this.turnCursor = log == null ? 0L : log.head();
        }
    }

    public long turnCursor() {
        return this.turnCursor;
    }

    public void setSteerTarget(Vec3 worldTarget) {
        this.steerTarget = worldTarget;
    }

    public Vec3 steerTarget() {
        return this.steerTarget;
    }

    private ITrackSelector pathSelector() {
        return (g, pair) -> {
            List<Entry<TrackNode, TrackEdge>> options = (List<Entry<TrackNode, TrackEdge>>)pair.getSecond();
            if (this.path != null && !this.path.isEmpty()) {
                for (int i = 0; i < this.path.size(); i++) {
                    TrackEdge targetEdge = g.getConnection(this.path.get(i));

                    for (Entry<TrackNode, TrackEdge> entry : options) {
                        if (entry.getValue() == targetEdge) {
                            this.path.subList(0, i + 1).clear();
                            return this.recordTurn(entry);
                        }
                    }
                }

                return this.recordTurn(options.get(0));
            } else {
                return this.recordTurn(options.get(0));
            }
        };
    }

    public List<Couple<TrackNode>> remainingPath() {
        return this.path;
    }

    public void setPath(List<Couple<TrackNode>> route) {
        this.path = route;
    }

    public void clearPath() {
        this.path = null;
    }

    public void recenterOn(TravellingPoint seed, TrackGraph seedGraph) {
        if (seed != null && seed.edge != null) {
            this.seatSpan(seed, seedGraph, this.bogeySpacing / 2.0);
            this.stopped = false;
            this.reachedEnd = false;
            this.pendingPortal = null;
        }
    }

    private static void copyPoint(TravellingPoint dst, TravellingPoint src) {
        dst.node1 = src.node1;
        dst.node2 = src.node2;
        dst.edge = src.edge;
        dst.position = src.position;
        dst.upsideDown = src.upsideDown;
        dst.blocked = false;
    }

    public TravellingPoint leading() {
        return this.leading;
    }

    public TravellingPoint trailing() {
        return this.trailing;
    }

    public boolean isOnCurve() {
        return this.leading != null && this.leading.edge != null && this.leading.edge.isTurn()
            || this.trailing != null && this.trailing.edge != null && this.trailing.edge.isTurn();
    }

    public boolean isOnMonorail() {
        return onMonorail(this.leading) || onMonorail(this.trailing);
    }

    private static boolean onMonorail(TravellingPoint point) {
        return point != null && point.edge != null && LoconauticsConstants.RAILWAYS_MONORAIL_MATERIAL.equals(point.edge.getTrackMaterial().id);
    }

    public TrackGraph graph() {
        return this.graph;
    }

    public ServerSubLevel activeSub() {
        return this.isFar(this.leading) ? null : hostOf(this.leading);
    }

    public Vec3 upNormal() {
        return this.upNormal;
    }

    public Vec3 leadingPos() {
        return this.worldPos(this.leading);
    }

    public Vec3 leadingRailPos() {
        return this.railPointOf(this.leading);
    }

    private Vec3 railPointOf(TravellingPoint p) {
        if (p != null && p.edge != null) {
            double len = p.edge.getLength();
            if (len < 1.0E-9) {
                return null;
            }

            Vec3 v = p.edge.getPosition(this.graph, p.position / len);
            Pose3dc pose = poseOf(p);
            if (v == null) {
                return null;
            }

            if (pose != null) {
                Vector3d w = pose.transformPosition(new Vector3d(v.x, v.y, v.z), new Vector3d());
                v = new Vec3(w.x, w.y, w.z);
            }

            return this.toHome(p.edge, v);
        } else {
            return null;
        }
    }

    public TrackMaterial trackMaterial() {
        TravellingPoint p = this.leading != null && this.leading.edge != null ? this.leading : this.trailing;
        return p != null && p.edge != null ? p.edge.getTrackMaterial() : null;
    }

    public RailCarriage.PointFrame railFrame(Vector3d reference) {
        RailCarriage.PointFrame a = this.pointFrame(this.leading, reference);
        RailCarriage.PointFrame b = this.pointFrame(this.trailing, reference);
        if (a != null && b != null) {
            Vector3d n = new Vector3d(a.n()).add(b.n());
            Vector3d t = new Vector3d(a.t()).add(b.t());
            if (!(n.lengthSquared() < 1.0E-9) && !(t.lengthSquared() < 1.0E-9)) {
                t.normalize();
                n.sub(new Vector3d(t).mul(n.dot(t)));
                return n.lengthSquared() < 1.0E-9 ? null : new RailCarriage.PointFrame(new Vector3d(a.c()).add(b.c()).mul(0.5), n.normalize(), t);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private RailCarriage.PointFrame pointFrame(TravellingPoint p, Vector3d reference) {
        Vec3 c = this.railPointOf(p);
        if (c == null) {
            return null;
        }

        double len = p.edge.getLength();
        Vec3 normal = p.edge.getNormal(this.graph, p.position / len);
        Vec3 dir = p.edge.getDirectionAt(p.position);
        if (normal != null && dir != null && !(dir.lengthSqr() < 1.0E-9)) {
            Pose3dc pose = poseOf(p);
            Vector3d n = new Vector3d(normal.x, normal.y, normal.z);
            if (pose != null) {
                pose.transformNormal(n);
            }

            if (this.isFarEdge(p.edge)) {
                n = this.portal.dirToNear(n);
            }

            if (p.upsideDown) {
                n.negate();
            }

            Vec3 worldDir = this.homeDir(p.edge, dir);
            Vector3d t = new Vector3d(worldDir.x, worldDir.y, worldDir.z);
            if (t.lengthSquared() < 1.0E-9) {
                return null;
            }

            t.normalize();
            if (t.dot(reference) < 0.0) {
                t.negate();
            }

            return new RailCarriage.PointFrame(new Vector3d(c.x, c.y, c.z), n.normalize(), t);
        } else {
            return null;
        }
    }

    public Vec3 trailingPos() {
        return this.worldPos(this.trailing);
    }

    public Vector3d center() {
        Vec3 a = this.worldPos(this.leading);
        Vec3 b = this.worldPos(this.trailing);
        return a != null && b != null ? new Vector3d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5) : null;
    }

    public Quaterniond orientation() {
        return this.orientationTo(this.forwardVec());
    }

    public Quaterniond chordFrameQuaternion() {
        Vec3 fw = this.forward();
        Vector3d f = new Vector3d(fw.x, fw.y, fw.z);
        if (f.lengthSquared() < 1.0E-9) {
            return new Quaterniond();
        }

        f.normalize();
        Vector3d up = VecMath.perpendicularUp(f, this.upRef());
        return basis(f, up).getNormalizedRotation(new Quaterniond()).normalize();
    }

    public Quaterniond leadingTangentQuaternion() {
        Quaterniond q = this.tangentQuaternion(this.leading);
        return q == null ? new Quaterniond() : q;
    }

    private Quaterniond tangentQuaternion(TravellingPoint point) {
        if (point != null && point.edge != null) {
            Vec3 d = point.edge.getDirectionAt(point.position);
            if (d != null && !(d.lengthSqr() < 1.0E-9)) {
                Pose3dc pose = poseOf(point);
                Vector3d f = new Vector3d(d.x, d.y, d.z).normalize();
                Vec3 worldTan = this.homeDir(point.edge, d);
                Vec3 fwd = this.forward();
                boolean agree = worldTan != null && worldTan.x * fwd.x + worldTan.z * fwd.z >= 0.0;
                if (!agree) {
                    f.negate();
                }

                Vector3d up = VecMath.perpendicularUp(f);
                Quaterniond q = basis(f, up).getNormalizedRotation(new Quaterniond()).normalize();
                if (pose != null) {
                    q = new Quaterniond(pose.orientation()).mul(q).normalize();
                }

                return this.isFarEdge(point.edge) ? this.portal.orientToNear(q) : q;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public Vector3d referenceForward() {
        return this.forward0;
    }

    public Vector3d referenceUp() {
        return this.up0;
    }

    public Quaterniond orientationTo(Vector3d forward) {
        if (this.forward0 == null) {
            this.captureReference();
            if (this.forward0 == null) {
                return new Quaterniond();
            }
        }

        if (forward != null && !(forward.lengthSquared() < 1.0E-9)) {
            Vector3d f = new Vector3d(forward).normalize();
            Vector3d up = VecMath.perpendicularUp(f, this.upRef());
            Matrix3d ref = basis(this.forward0, this.up0);
            Matrix3d now = basis(f, up);
            Matrix3d delta = now.mul(ref.transpose(new Matrix3d()), new Matrix3d());
            return delta.getNormalizedRotation(new Quaterniond()).normalize();
        } else {
            return new Quaterniond();
        }
    }

    private void captureReference() {
        Vector3d f = this.forwardVec();
        if (f != null) {
            this.forward0 = Math.abs(f.x) >= Math.abs(f.z) ? new Vector3d(Math.signum(f.x), 0.0, 0.0) : new Vector3d(0.0, 0.0, Math.signum(f.z));
            this.up0 = VecMath.perpendicularUp(this.forward0);
        }
    }

    private Vector3d forwardVec() {
        Vec3 pa = this.worldPos(this.leading);
        Vec3 pb = this.worldPos(this.trailing);
        if (pa != null && pb != null) {
            Vector3d a = new Vector3d(pa.x, pa.y, pa.z);
            Vector3d b = new Vector3d(pb.x, pb.y, pb.z);
            Vector3d f = a.sub(b, new Vector3d());
            return f.lengthSquared() < 1.0E-9 ? new Vector3d(1.0, 0.0, 0.0) : f.normalize();
        } else {
            return null;
        }
    }

    private Vector3d upRef() {
        Pose3dc pose = poseOf(this.leading);
        Vector3d local = this.restUp();
        Vector3d up = pose == null ? local : pose.transformNormal(local, new Vector3d());
        return this.isFar(this.leading) ? this.portal.dirToNear(up) : up;
    }

    private Vector3d restUp() {
        return this.upNormal != null && !(this.upNormal.lengthSqr() < 1.0E-9)
            ? new Vector3d(this.upNormal.x, this.upNormal.y, this.upNormal.z).normalize()
            : new Vector3d(0.0, 1.0, 0.0);
    }

    public Vec3 railUp() {
        Vector3d f = this.forwardVec();
        Vector3d up = f == null ? this.upRef() : VecMath.perpendicularUp(f, this.upRef());
        return new Vec3(up.x, up.y, up.z);
    }

    public double hostTilt() {
        Pose3dc pose = poseOf(this.leading);
        if (pose == null) {
            return 0.0;
        }

        Vector3d rest = this.restUp();
        Vector3d now = pose.transformNormal(new Vector3d(rest), new Vector3d());
        double dot = now.dot(rest);
        return Math.acos(dot < -1.0 ? -1.0 : (dot > 1.0 ? 1.0 : dot));
    }

    private static Matrix3d basis(Vector3d forward, Vector3d up) {
        Vector3d right = new Vector3d(up).cross(forward);
        Matrix3d m = new Matrix3d();
        m.setColumn(0, right);
        m.setColumn(1, up);
        m.setColumn(2, forward);
        return m;
    }

    public Vec3 forward() {
        Vec3 f = this.leadingPos().subtract(this.trailingPos());
        double len = f.length();
        return len < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : f.scale(1.0 / len);
    }

    public Vec3 localEdgeTangent() {
        if (this.leading != null && this.leading.edge != null) {
            Vec3 d = this.leading.edge.isInterDimensional() ? null : this.leading.edge.getDirectionAt(this.leading.position);
            return d != null && !(d.lengthSqr() < 1.0E-9) ? d.normalize() : null;
        } else {
            return null;
        }
    }

    public Vec3 edgeTangent() {
        if (this.leading != null && this.leading.edge != null) {
            Vec3 d = this.leading.edge.isInterDimensional() ? null : this.leading.edge.getDirectionAt(this.leading.position);
            if (d != null && !(d.lengthSqr() < 1.0E-9)) {
                Vec3 w = this.homeDir(this.leading.edge, d);
                return w != null && !(w.lengthSqr() < 1.0E-9) ? w.normalize() : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public double signedLateralCurvature() {
        if (this.leading == null || this.leading.edge == null) {
            return 0.0;
        }

        if (this.leading.edge.isInterDimensional()) {
            return 0.0;
        }

        Vec3 t0 = this.homeDir(this.leading.edge, this.leading.edge.getDirectionAt(this.leading.position));
        if (t0 != null && !(t0.lengthSqr() < 1.0E-9)) {
            t0 = t0.normalize();
            double ds = 0.5;
            double len = this.leading.edge.getLength();
            double ahead = this.leading.position + ds <= len ? this.leading.position + ds : this.leading.position - ds;
            Vec3 t1 = this.homeDir(this.leading.edge, this.leading.edge.getDirectionAt(ahead));
            if (t1 != null && !(t1.lengthSqr() < 1.0E-9)) {
                t1 = t1.normalize();
                Vec3 dT = t1.subtract(t0).scale(1.0 / ds);
                Vec3 lateral = this.railUp().cross(t0);
                return lateral.lengthSqr() < 1.0E-9 ? 0.0 : dT.dot(lateral.normalize());
            } else {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

    public boolean stopped() {
        return this.stopped;
    }

    public void unstick() {
        this.stopped = false;
    }

    public double lastTravelSign() {
        return this.lastTravelSign;
    }

    public void setLastTravelSign(double sign) {
        this.lastTravelSign = sign;
    }

    public TravellingPoint followTarget() {
        return this.followTarget;
    }

    public boolean isIndependent() {
        return this.independent;
    }

    public void setIndependent(boolean independent) {
        this.independent = independent;
        if (independent) {
            this.followTarget = null;
        }
    }

    public void followPoint(TravellingPoint target) {
        if (target != null && target != this.followTarget) {
            this.followTarget = target;
        }
    }

    public void clearFollowPoint() {
        this.followTarget = null;
    }

    public void adoptReferenceFrame(RailCarriage from) {
        if (from != null && from.forward0 != null) {
            this.forward0 = new Vector3d(from.forward0);
            this.up0 = from.up0 != null ? new Vector3d(from.up0) : VecMath.perpendicularUp(this.forward0);
        }
    }

    public boolean edgesValid() {
        return this.rebind();
    }

    private boolean rebind() {
        TrackEdge leadingBefore = this.leading.edge;
        TrackEdge trailingBefore = this.trailing.edge;
        boolean leadingOk = this.pointValid(this.leading);
        boolean trailingOk = this.pointValid(this.trailing);
        if (leadingOk && trailingOk && (this.leading.edge != leadingBefore || this.trailing.edge != trailingBefore)) {
            this.remapRoute();
        }

        return leadingOk && trailingOk;
    }

    private void remapRoute() {
        if (this.path != null) {
            for (int i = 0; i < this.path.size(); i++) {
                Couple<TrackNode> hop = this.path.get(i);
                TrackNode first = this.liveNode((TrackNode)hop.getFirst());
                TrackNode second = this.liveNode((TrackNode)hop.getSecond());
                if (first != hop.getFirst() || second != hop.getSecond()) {
                    this.path.set(i, Couple.create(first, second));
                }
            }
        }

        if (this.turnLog != null) {
            this.turnLog.remap(this::liveNode);
        }
    }

    private TrackNode liveNode(TrackNode node) {
        TrackNode live = node == null ? null : this.graph.locateNode(node.getLocation());
        return live == null ? node : live;
    }

    private boolean pointValid(TravellingPoint point) {
        if (point != null && point.node1 != null && point.node2 != null && point.edge != null) {
            this.adoptLiveGraph();
            TrackNode n1 = this.graph.locateNode(point.node1.getLocation());
            if (n1 == null) {
                return false;
            }

            point.node1 = n1;
            Map<TrackNode, TrackEdge> connections = this.graph.getConnectionsFrom(point.node1);
            TrackEdge edge = connections.get(point.node2);
            if (edge == null) {
                TrackNode n2 = this.graph.locateNode(point.node2.getLocation());
                if (n2 == null || (edge = connections.get(n2)) == null) {
                    return false;
                }

                point.node2 = n2;
            }

            point.edge = edge;
            point.position = Math.min(point.position, edge.getLength());
            return true;
        } else {
            return false;
        }
    }

    private void adoptLiveGraph() {
        Map<UUID, TrackGraph> networks = Create.RAILWAYS.trackNetworks;
        if (this.graph != null && networks != null) {
            TrackNodeLocation at = this.leading != null && this.leading.node1 != null ? this.leading.node1.getLocation() : null;
            if (at != null) {
                if (!networks.containsKey(this.graph.id) || this.graph.locateNode(at) == null) {
                    for (TrackGraph candidate : networks.values()) {
                        if (candidate.locateNode(at) != null) {
                            this.graph = candidate;
                            return;
                        }
                    }
                }
            }
        }
    }

    public double bogeySpacing() {
        return this.bogeySpacing;
    }

    public double speed() {
        return this.speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setTravelIntent(double intent) {
        this.travelIntent = intent;
    }

    public UUID graphId() {
        return this.graph.id;
    }

    public CompoundTag writeNbt(DimensionPalette dims) {
        if (this.forward0 == null) {
            this.captureReference();
        }

        CompoundTag tag = new CompoundTag();
        tag.put("Leading", this.leading.write(dims));
        tag.put("Trailing", this.trailing.write(dims));
        tag.putDouble("Spacing", this.bogeySpacing);
        tag.putBoolean("Stopped", this.stopped);
        tag.putBoolean("Independent", this.independent);
        tag.putDouble("UpX", this.upNormal.x);
        tag.putDouble("UpY", this.upNormal.y);
        tag.putDouble("UpZ", this.upNormal.z);
        if (this.forward0 != null) {
            tag.putDouble("FwdX", this.forward0.x);
            tag.putDouble("FwdY", this.forward0.y);
            tag.putDouble("FwdZ", this.forward0.z);
        }

        if (this.portal != null) {
            tag.put("PortalNear", this.portal.nearLoc().write(dims));
            tag.put("PortalFar", this.portal.farLoc().write(dims));
        }

        return tag;
    }

    public static RailCarriage restore(TrackGraph graph, CompoundTag tag, DimensionPalette dims) {
        TravellingPoint leading = TravellingPoint.read(tag.getCompound("Leading"), graph, dims);
        TravellingPoint trailing = TravellingPoint.read(tag.getCompound("Trailing"), graph, dims);
        if (leading.edge != null && trailing.edge != null) {
            Vec3 up = new Vec3(tag.getDouble("UpX"), tag.getDouble("UpY"), tag.getDouble("UpZ"));
            double spacing = tag.getDouble("Spacing");
            RailCarriage c = new RailCarriage(graph, leading, trailing, up, spacing, 0.0);
            if (tag.contains("FwdX")) {
                c.forward0 = new Vector3d(tag.getDouble("FwdX"), tag.getDouble("FwdY"), tag.getDouble("FwdZ"));
                c.up0 = VecMath.perpendicularUp(c.forward0);
            }

            c.stopped = tag.getBoolean("Stopped");
            c.independent = tag.getBoolean("Independent");
            if (tag.contains("PortalNear") && tag.contains("PortalFar")) {
                TrackNodeLocation near = TrackNodeLocation.read(tag.getCompound("PortalNear"), dims);
                TrackNodeLocation far = TrackNodeLocation.read(tag.getCompound("PortalFar"), dims);
                c.portal = PortalFrame.of(graph, Couple.create(near, far), near.dimension);
                if (c.portal == null) {
                    LoconauticsConstants.LOGGER
                        .warn(
                            "[portal] saved portal frame {} to {} could not be rebuilt on restore, graph={}",
                            new Object[]{near.dimension.location(), far.dimension.location(), graph != null}
                        );
                }
            }

            return c;
        } else {
            return null;
        }
    }

    public record PointFrame(Vector3d c, Vector3d n, Vector3d t) {
    }
}
