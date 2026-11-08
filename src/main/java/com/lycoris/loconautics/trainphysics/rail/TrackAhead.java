package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TrackAhead {
    public static final double DEFAULT_RANGE = 256.0;
    public static final double MAX_RANGE = 1024.0;
    public static final double DEFAULT_SLOPE_DELTA = 1.0;
    private static final double GAP_RANGE = 12.0;
    private static final double MIN_CURVE_DEGREES = 1.0;
    private static final int MAX_PIECES = 4096;

    private TrackAhead() {
    }

    public static TrackAhead.Report scan(SableTrain train, double range) {
        if (!train.isDerailed() && !train.isPhysics() && train.car() != null) {
            RailCarriage carriage = TrainStations.noseRail(train);
            TravellingPoint nose = TrainStations.nosePoint(train);
            TrackGraph graph = carriage == null ? null : carriage.graph();
            if (graph != null && nose != null && nose.edge != null) {
                boolean forward = SableTrainDriver.noseSense(train, nose) > 0;
                TrackNode from = forward ? nose.node1 : nose.node2;
                TrackNode to = forward ? nose.node2 : nose.node1;
                TrackEdge edge = forward ? nose.edge : (TrackEdge)graph.getConnectionsFrom(from).get(to);
                if (edge == null) {
                    return null;
                }

                double at = Mth.clamp(forward ? nose.position : nose.edge.getLength() - nose.position, 0.0, edge.getLength());
                UUID startHost = hostOf(from);
                List<TrackAhead.Piece> pieces = new ArrayList<>();
                double walked = 0.0;
                double slope = Double.NaN;
                Vec3 out = null;

                for (int i = 0; i < 4096 && walked < range && !edge.isInterDimensional(); i++) {
                    boolean seam = RailSeams.isSeam(from.getLocation(), to.getLocation());
                    double run = Math.max(0.0, edge.getLength() - at);
                    TrackAhead.Piece piece = seam ? seamPiece(walked, run, to, slope) : railPiece(edge, from, at, walked, run, slope);
                    pieces.add(piece);
                    if (!seam) {
                        slope = piece.slopeEnd();
                        out = worldDir(from, edge.getDirection(false));
                    }

                    walked += run;
                    Entry<TrackNode, TrackEdge> next = straightest(graph, from, to, edge, out);
                    if (next == null) {
                        TrackAhead.End end = new TrackAhead.End(walked, RailSeams.worldPosOf(to.getLocation()), hostOf(to));
                        return new TrackAhead.Report(startHost, pieces, end, gapBeyond(train, graph, end, out));
                    }

                    from = to;
                    to = next.getKey();
                    edge = next.getValue();
                    at = 0.0;
                }

                return new TrackAhead.Report(startHost, pieces, null, null);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static TrackAhead.Piece railPiece(TrackEdge edge, TrackNode from, double at, double start, double run, double carriedSlope) {
        double length = edge.getLength();
        if (length < 1.0E-6) {
            return new TrackAhead.Piece(start, run, hostOf(from), false, false, carriedSlope, carriedSlope, 0.0, null);
        }

        Vec3 head = worldDir(from, edge.getDirectionAt(at));
        Vec3 tail = worldDir(from, edge.getDirectionAt(length));
        double degrees = edge.isTurn() ? yawDegrees(head, tail) : 0.0;
        return new TrackAhead.Piece(start, run, hostOf(from), false, edge.isTurn(), slopeOf(head, carriedSlope), slopeOf(tail, carriedSlope), degrees, null);
    }

    private static TrackAhead.Piece seamPiece(double start, double run, TrackNode to, double carriedSlope) {
        return new TrackAhead.Piece(start, run, hostOf(to), true, false, carriedSlope, carriedSlope, 0.0, RailSeams.worldPosOf(to.getLocation()));
    }

    private static Entry<TrackNode, TrackEdge> straightest(TrackGraph graph, TrackNode from, TrackNode to, TrackEdge edge, Vec3 out) {
        Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(to);
        if (connections == null) {
            return null;
        }

        Entry<TrackNode, TrackEdge> best = null;
        double bestDot = -Double.MAX_VALUE;

        for (Entry<TrackNode, TrackEdge> entry : connections.entrySet()) {
            if (entry.getKey() != from && edge.canTravelTo(entry.getValue())) {
                Vec3 in = worldDir(to, entry.getValue().getDirection(true));
                double dot = out != null && in != null ? out.dot(in) : 0.0;
                if (dot > bestDot) {
                    bestDot = dot;
                    best = entry;
                }
            }
        }

        return best;
    }

    private static TrackAhead.Gap gapBeyond(SableTrain train, TrackGraph graph, TrackAhead.End end, Vec3 along) {
        if (end.world() != null && along != null && train.level() != null) {
            ServerSubLevel self = SubLevelData.resolve(train.level(), train.car().subLevelId());
            ServerSubLevel endHost = end.host() == null ? null : SubLevelData.resolve(train.level(), end.host());
            Vec3 up = new Vec3(0.0, 1.0, 0.0);

            for (double gap = 0.0; gap <= 12.0; gap++) {
                SableRailHop.Probe probe = SableRailHop.probe(train.level(), graph, endHost, self, end.world().add(along.scale(gap)), along, up, 1.0, false);
                SableRailHop.Result hit = probe == null ? null : probe.hit();
                if (hit != null) {
                    UUID host = hit.sub() == null ? null : hit.sub().getUniqueId();
                    return new TrackAhead.Gap(end.distance() + gap, gap, host, hit.railPoint());
                }
            }

            return null;
        } else {
            return null;
        }
    }

    public static Double slopeAt(TrackAhead.Report report, double distance) {
        for (TrackAhead.Piece piece : report.pieces()) {
            if (!(distance > piece.start() + piece.length()) && !Double.isNaN(piece.slopeStart())) {
                double t = piece.length() < 1.0E-6 ? 0.0 : (distance - piece.start()) / piece.length();
                return piece.slopeStart() + (piece.slopeEnd() - piece.slopeStart()) * Mth.clamp(t, 0.0, 1.0);
            }
        }

        return null;
    }

    public static TrackAhead.SlopeChange nextSlopeChange(TrackAhead.Report report, double minDelta) {
        Double base = slopeAt(report, 0.0);
        if (base == null) {
            return null;
        }

        for (TrackAhead.Piece piece : report.pieces()) {
            if (!Double.isNaN(piece.slopeStart())) {
                double startOff = piece.slopeStart() - base;
                double endOff = piece.slopeEnd() - base;
                if (Math.abs(startOff) >= minDelta) {
                    return new TrackAhead.SlopeChange(piece.start(), base, piece.slopeStart());
                }

                if (Math.abs(endOff) >= minDelta) {
                    double t = (Math.signum(endOff) * minDelta - startOff) / (endOff - startOff);
                    return new TrackAhead.SlopeChange(piece.start() + piece.length() * Mth.clamp(t, 0.0, 1.0), base, piece.slopeEnd());
                }
            }
        }

        return null;
    }

    public static TrackAhead.Curve nextCurve(TrackAhead.Report report) {
        List<TrackAhead.Piece> pieces = report.pieces();

        for (int i = 0; i < pieces.size(); i++) {
            TrackAhead.Piece first = pieces.get(i);
            if (first.turn() && !(Math.abs(first.turnDegrees()) < 1.0)) {
                double length = 0.0;
                double degrees = 0.0;

                for (int j = i; j < pieces.size(); j++) {
                    TrackAhead.Piece piece = pieces.get(j);
                    if (!piece.turn() || Math.signum(piece.turnDegrees()) != Math.signum(first.turnDegrees())) {
                        break;
                    }

                    length += piece.length();
                    degrees += piece.turnDegrees();
                }

                double radians = Math.toRadians(Math.abs(degrees));
                return new TrackAhead.Curve(first.start(), length, degrees, radians < 1.0E-6 ? 0.0 : length / radians, first.host());
            }
        }

        return null;
    }

    public static TrackAhead.TrackChange nextChange(TrackAhead.Report report, boolean toSubLevel) {
        UUID current = report.startHost();

        for (TrackAhead.Piece piece : report.pieces()) {
            if (piece.seam() && !Objects.equals(piece.host(), current)) {
                UUID before = current;
                current = piece.host();
                if (toSubLevel ? current != null && !current.equals(report.startHost()) : current == null) {
                    return new TrackAhead.TrackChange(piece.start(), before, current, true, 0.0, piece.at());
                }
            }
        }

        TrackAhead.Gap gap = report.gap();
        return gap == null || Objects.equals(gap.host(), current) || (toSubLevel ? gap.host() == null : gap.host() != null)
            ? null
            : new TrackAhead.TrackChange(gap.distance(), current, gap.host(), false, gap.gap(), gap.world());
    }

    public static TrackAhead.TrackChange firstChange(TrackAhead.Report report) {
        UUID current = report.startHost();

        for (TrackAhead.Piece piece : report.pieces()) {
            if (piece.seam() && !Objects.equals(piece.host(), current)) {
                return new TrackAhead.TrackChange(piece.start(), current, piece.host(), true, 0.0, piece.at());
            }
        }

        TrackAhead.Gap gap = report.gap();
        return gap != null && !Objects.equals(gap.host(), current)
            ? new TrackAhead.TrackChange(gap.distance(), current, gap.host(), false, gap.gap(), gap.world())
            : null;
    }

    private static UUID hostOf(TrackNode node) {
        ServerSubLevel host = RailSeams.serverHostOf(node.getLocation());
        return host == null ? null : host.getUniqueId();
    }

    private static Vec3 worldDir(TrackNode node, Vec3 direction) {
        return RailSeams.worldDirOf(node.getLocation(), direction);
    }

    private static double slopeOf(Vec3 direction, double fallback) {
        return direction == null ? fallback : Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1.0, 1.0)));
    }

    private static double yawDegrees(Vec3 from, Vec3 to) {
        if (from != null && to != null) {
            double cross = from.x * to.z - from.z * to.x;
            double dot = from.x * to.x + from.z * to.z;
            return Math.toDegrees(Math.atan2(cross, dot));
        } else {
            return 0.0;
        }
    }

    public record Curve(double distance, double length, double degrees, double radius, UUID host) {
    }

    public record End(double distance, Vec3 world, UUID host) {
    }

    public record Gap(double distance, double gap, UUID host, Vec3 world) {
    }

    public record Piece(double start, double length, UUID host, boolean seam, boolean turn, double slopeStart, double slopeEnd, double turnDegrees, Vec3 at) {
    }

    public record Report(UUID startHost, List<TrackAhead.Piece> pieces, TrackAhead.End end, TrackAhead.Gap gap) {
    }

    public record SlopeChange(double distance, double from, double to) {
    }

    public record TrackChange(double distance, UUID from, UUID to, boolean joined, double gap, Vec3 world) {
    }
}
