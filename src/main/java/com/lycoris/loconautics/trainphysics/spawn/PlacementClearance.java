package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class PlacementClearance {
    private static final double END_INSET = 0.5;
    private static final double MIN_DISTANCE = 0.5;
    private static final double EPSILON = 1.0E-9;

    private PlacementClearance() {
    }

    @Nullable
    public static PlacementClearance.Span between(@Nullable Vec3 a, @Nullable Vec3 b) {
        if (a != null && b != null) {
            Vec3 axis = b.subtract(a);
            double length = axis.length();
            if (length < 1.0E-9) {
                return new PlacementClearance.Span(a, b);
            }

            Vec3 trim = axis.scale(Math.min(0.5, length / 2.0) / length);
            return new PlacementClearance.Span(a.add(trim), b.subtract(trim));
        } else {
            return null;
        }
    }

    @Nullable
    public static PlacementClearance.Span extent(@Nullable Vec3 lead, @Nullable Vec3 trail, double length) {
        if (lead != null && trail != null) {
            Vec3 axis = lead.subtract(trail);
            if (axis.lengthSqr() < 1.0E-9) {
                return null;
            }

            Vec3 half = axis.normalize().scale(length / 2.0);
            Vec3 center = lead.add(trail).scale(0.5);
            return between(center.subtract(half), center.add(half));
        } else {
            return null;
        }
    }

    @Nullable
    public static SableTrain blocker(ServerLevel level, List<SableTrain> cars, List<TrackGraphLocation> spots, Vec3 upNormal) {
        Set<UUID> moving = new HashSet<>();

        for (SableTrain car : cars) {
            moving.add(car.id());
        }

        for (int i = 0; i < cars.size() && i < spots.size(); i++) {
            SableTrain car = cars.get(i);
            if (car.car().carriage() != null) {
                RailCarriage seat = RailCarriage.at(spots.get(i), upNormal, car.car().carriage().bogeySpacing(), 0.0);
                PlacementClearance.Span span = seat == null ? null : extent(seat.leadingPos(), seat.trailingPos(), TrainLength.carLength(level, car));
                SableTrain hit = span == null ? null : blocker(level, span, moving);
                if (hit != null) {
                    return hit;
                }
            }
        }

        return null;
    }

    @Nullable
    public static SableTrain blocker(ServerLevel level, PlacementClearance.Span span, Set<UUID> ignoredTrains) {
        for (SableTrain other : SableTrainRegistry.all()) {
            if (other.level() == level && !other.isGhost() && other.car() != null && !ignoredTrains.contains(other.id())) {
                PlacementClearance.Span there = spanOf(level, other);
                if (there != null && distanceSqr(span, there) < 0.25) {
                    return other;
                }
            }
        }

        return null;
    }

    @Nullable
    private static PlacementClearance.Span spanOf(ServerLevel level, SableTrain train) {
        double length = TrainLength.carLength(level, train);
        RailCarriage rail = train.car().carriage();
        if (rail != null && !train.isDerailed() && !train.isTrackless()) {
            PlacementClearance.Span railed = extent(rail.leadingPos(), rail.trailingPos(), length);
            if (railed != null) {
                return railed;
            }
        }

        ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
        if (sub == null) {
            return null;
        }

        Vector3d anchor = SableTrainSpawner.bogeyAnchorWorld(sub, train.car());
        Vec3 facing = SableTrainSpawner.currentFacing(sub, train.car());
        Vec3 center = new Vec3(anchor.x, anchor.y, anchor.z);
        Vec3 half = facing.scale(length / 2.0);
        return between(center.subtract(half), center.add(half));
    }

    private static double distanceSqr(PlacementClearance.Span a, PlacementClearance.Span b) {
        Vec3 d1 = a.to().subtract(a.from());
        Vec3 d2 = b.to().subtract(b.from());
        Vec3 r = a.from().subtract(b.from());
        double lengthA = d1.dot(d1);
        double lengthB = d2.dot(d2);
        double f = d2.dot(r);
        if (lengthA <= 1.0E-9 && lengthB <= 1.0E-9) {
            return r.lengthSqr();
        }

        double s;
        double t;
        if (lengthA <= 1.0E-9) {
            s = 0.0;
            t = Mth.clamp(f / lengthB, 0.0, 1.0);
        } else {
            double c = d1.dot(r);
            if (lengthB <= 1.0E-9) {
                t = 0.0;
                s = Mth.clamp(-c / lengthA, 0.0, 1.0);
            } else {
                double cross = d1.dot(d2);
                double denom = lengthA * lengthB - cross * cross;
                s = denom > 1.0E-9 ? Mth.clamp((cross * f - c * lengthB) / denom, 0.0, 1.0) : 0.0;
                t = (cross * s + f) / lengthB;
                if (t < 0.0) {
                    t = 0.0;
                    s = Mth.clamp(-c / lengthA, 0.0, 1.0);
                } else if (t > 1.0) {
                    t = 1.0;
                    s = Mth.clamp((cross - c) / lengthA, 0.0, 1.0);
                }
            }
        }

        return a.from().add(d1.scale(s)).distanceToSqr(b.from().add(d2.scale(t)));
    }

    public record Span(Vec3 from, Vec3 to) {
    }
}
