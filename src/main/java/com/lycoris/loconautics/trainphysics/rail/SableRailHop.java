package com.lycoris.loconautics.trainphysics.rail;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.sublevel.SablePlotFrame;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackPropagator;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class SableRailHop {
    private static final double SEARCH_RADIUS = 3.0;
    private static final double ORIENT_SCORE_WEIGHT = 0.04;
    private static final double[] PROBE_STEPS = new double[]{0.0, 0.75, 1.5, 2.25, 3.0};
    private static final double[] NO_SWEEP = new double[]{0.0};
    private static final double[] AHEAD_STEPS = new double[]{1.0, 2.0, 3.0};

    private SableRailHop() {
    }

    public static SableRailHop.Probe probe(
        ServerLevel level,
        TrackGraph fromGraph,
        ServerSubLevel fromSub,
        ServerSubLevel selfBody,
        Vec3 worldPos,
        Vec3 worldDir,
        Vec3 worldVert,
        double maxOffset,
        boolean sweepAhead
    ) {
        if (level != null && worldPos != null && worldDir != null) {
            Vec3 dir = worldDir.lengthSqr() > 1.0E-9 ? worldDir.normalize() : new Vec3(1.0, 0.0, 0.0);

            for (double step : sweepAhead ? PROBE_STEPS : NO_SWEEP) {
                Vec3 at = worldPos.add(dir.scale(step));
                SableRailHop.Result r = scan(level, fromSub, selfBody, at, worldDir, worldVert, fromGraph, maxOffset);
                if (r != null) {
                    return new SableRailHop.Probe(r, step, "");
                }
            }

            return new SableRailHop.Probe(null, 0.0, "no rail in range");
        } else {
            return null;
        }
    }

    public static boolean ownGraphTrackAhead(ServerLevel level, TrackGraph graph, ServerSubLevel host, Vec3 worldPos, Vec3 worldDir) {
        if (level != null && graph != null && worldPos != null && worldDir != null && !(worldDir.lengthSqr() < 1.0E-9)) {
            Vec3 dir = worldDir.normalize();
            Vec3 localAt = host == null ? worldPos : toLocalPos(host, worldPos);
            Vec3 localDir = host == null ? dir : toLocalDir(host, dir).normalize();
            BlockPos standingOn = BlockPos.containing(localAt);

            for (double step : AHEAD_STEPS) {
                Vec3 probe = localAt.add(localDir.scale(step));

                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos pos = BlockPos.containing(probe).above(dy);
                    if (!pos.equals(standingOn) && isOwnGraphTrack(level, graph, pos, localDir)) {
                        return true;
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static boolean isOwnGraphTrack(ServerLevel level, TrackGraph graph, BlockPos pos, Vec3 localDir) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ITrackBlock track) {
            Pair var8 = track.getNearestTrackAxis(level, pos, state, localDir);
            TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(level, pos, (AxisDirection)var8.getSecond(), (Vec3)var8.getFirst());
            if (loc != null && loc.graph != null) {
                return loc.graph == graph;
            }

            TrackPropagator.onRailAdded(level, pos, state);
            return true;
        } else {
            return false;
        }
    }

    private static SableRailHop.Result scan(
        ServerLevel level,
        ServerSubLevel fromSub,
        ServerSubLevel selfBody,
        Vec3 worldPos,
        Vec3 worldDir,
        Vec3 worldVert,
        TrackGraph excludeGraph,
        double maxOffset
    ) {
        if (level != null && worldPos != null && worldDir != null) {
            Vec3 dir = worldDir.lengthSqr() > 1.0E-9 ? worldDir.normalize() : new Vec3(1.0, 0.0, 0.0);
            Vec3 vert = worldVert != null && worldVert.lengthSqr() > 1.0E-9 ? worldVert.normalize() : new Vec3(0.0, 1.0, 0.0);
            double radius = Math.max(3.0, maxOffset + 1.0);
            BoundingBox3d bb = new BoundingBox3d(
                worldPos.x - radius, worldPos.y - radius, worldPos.z - radius, worldPos.x + radius, worldPos.y + radius, worldPos.z + radius
            );
            SableRailHop.Best best = new SableRailHop.Best();
            scanHost(level, null, worldPos, dir, vert, excludeGraph, maxOffset, best);

            for (SubLevel sl : Sable.HELPER.getAllIntersecting(level, bb)) {
                if (sl instanceof ServerSubLevel ssl && !ssl.isRemoved() && ssl != fromSub && ssl != selfBody) {
                    scanHost(level, ssl, worldPos, dir, vert, excludeGraph, maxOffset, best);
                }
            }

            return best.location == null ? null : new SableRailHop.Result(best.location.graph, best.location, best.sub, best.railPoint);
        } else {
            return null;
        }
    }

    private static double maxOrientScore() {
        return 1.0 - Math.cos(Math.toRadians((Double)LoconauticsConfig.RAIL_HOP_MAX_ANGLE.get()));
    }

    private static void scanHost(
        ServerLevel level, ServerSubLevel host, Vec3 worldPos, Vec3 worldDir, Vec3 worldVert, TrackGraph excludeGraph, double maxOffset, SableRailHop.Best best
    ) {
        Vec3 localPos;
        Vec3 localDir;
        Vec3 localVert;
        if (host == null) {
            localPos = worldPos;
            localDir = worldDir;
            localVert = worldVert;
        } else {
            localPos = toLocalPos(host, worldPos);
            localDir = toLocalDir(host, worldDir);
            localVert = toLocalDir(host, worldVert);
        }

        double maxOffsetSq = maxOffset * maxOffset;

        for (int x = Mth.floor(localPos.x) - 1; x <= Mth.ceil(localPos.x) + 1; x++) {
            for (int y = Mth.floor(localPos.y) - 2; y <= Mth.ceil(localPos.y) + 2; y++) {
                for (int z = Mth.floor(localPos.z) - 1; z <= Mth.ceil(localPos.z) + 1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ITrackBlock track) {
                        Pair<Vec3, AxisDirection> axis = track.getNearestTrackAxis(level, pos, state, new Vec3(localDir.x, localDir.y, localDir.z));
                        Vec3 delta = (Vec3)axis.getFirst();
                        double deltaLen = delta.length();
                        if (!(deltaLen < 1.0E-6)) {
                            TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(level, pos, (AxisDirection)axis.getSecond(), delta);
                            if (loc == null || loc.graph == null) {
                                TrackPropagator.onRailAdded(level, pos, state);
                            } else if (excludeGraph == null || loc.graph != excludeGraph) {
                                Vec3 vertN = track.getUpNormal(level, pos, state).normalize();
                                Vec3 lat = delta.cross(vertN).normalize();
                                Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + track.getElevationAtCenter(level, pos, state), pos.getZ() + 0.5);
                                Vec3 start = center.subtract(delta.scale(0.5));
                                if (inSegmentRange(start, delta, vertN, lat, localPos, localVert)) {
                                    double orientScore = 1.0 - Math.abs(localDir.dot(delta) / deltaLen);
                                    if (!(orientScore > maxOrientScore())) {
                                        double distScore = distanceSqLinePoint(center, delta, localPos);
                                        if (!(distScore > maxOffsetSq)) {
                                            double score = distScore + 0.04 * orientScore;
                                            if (!(score >= best.score)) {
                                                best.score = score;
                                                best.location = loc;
                                                best.sub = host;
                                                best.railPoint = host == null ? center : toWorldPos(host, center);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static Vec3 toLocalPos(ServerSubLevel sub, Vec3 world) {
        return SablePlotFrame.toLocalPos(sub, world);
    }

    private static Vec3 toWorldPos(ServerSubLevel sub, Vec3 local) {
        return SablePlotFrame.toWorldPos(sub, local);
    }

    private static Vec3 toLocalDir(ServerSubLevel sub, Vec3 world) {
        return SablePlotFrame.toLocalDir(sub, world);
    }

    private static boolean inSegmentRange(Vec3 start, Vec3 delta, Vec3 vert, Vec3 lat, Vec3 localPos, Vec3 localVert) {
        return inProjectionRange(start, delta, vert, localPos, localVert) && inLineRange(start, vert, lat, localPos, localVert);
    }

    private static boolean inLineRange(Vec3 start, Vec3 vert, Vec3 lat, Vec3 localPos, Vec3 localVert) {
        if (vert.dot(localVert) < 0.5) {
            return false;
        } else {
            double vDist = projT(start, vert, localPos);
            if (!(vDist < -1.5) && !(vDist > 0.375)) {
                double lDist = projT(start, lat, localPos);
                return lDist >= -0.5 && lDist <= 0.5;
            } else {
                return false;
            }
        }
    }

    private static boolean inProjectionRange(Vec3 start, Vec3 delta, Vec3 vert, Vec3 localPos, Vec3 localVert) {
        double t = intersectPlaneLine(localPos, localVert, start, vert);
        if (Double.isNaN(t)) {
            return false;
        }

        Vec3 i = localPos.add(localVert.scale(t));
        double projT = projT(start, delta, i);
        return projT >= -0.03125 && projT <= 1.03125;
    }

    private static double projT(Vec3 start, Vec3 dir, Vec3 p) {
        double d2 = dir.lengthSqr();
        return d2 < 1.0E-12 ? 0.0 : p.subtract(start).dot(dir) / d2;
    }

    private static double distanceSqLinePoint(Vec3 center, Vec3 delta, Vec3 p) {
        double t = projT(center, delta, p);
        Vec3 perp = p.subtract(center).subtract(delta.scale(t));
        return perp.lengthSqr();
    }

    private static double intersectPlaneLine(Vec3 lp, Vec3 ld, Vec3 pp, Vec3 pn) {
        double denom = ld.dot(pn);
        return Math.abs(denom) < 1.0E-9 ? Double.NaN : pp.subtract(lp).dot(pn) / denom;
    }

    private static final class Best {
        double score = Double.MAX_VALUE;
        TrackGraphLocation location;
        ServerSubLevel sub;
        Vec3 railPoint;
    }

    public record Probe(SableRailHop.Result hit, double step, String detail) {
    }

    public record Result(TrackGraph graph, TrackGraphLocation location, ServerSubLevel sub, Vec3 railPoint) {
    }
}
