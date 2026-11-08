package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.track.BezierConnection;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class ScheduleCurveSpeed {
    private static final long REFRESH_TICKS = 10L;
    private static final double SAFE_SHARE = 0.5;
    private static final double BRAKE_SHARE = 0.8;
    private static final double LOOK_MARGIN = 8.0;
    private static final int CURVE_SAMPLES = 16;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final Map<UUID, ScheduleCurveSpeed.Plan> PLANS = new ConcurrentHashMap<>();
    private static final Map<BezierConnection, Double> CURVATURE = new WeakHashMap<>();

    private ScheduleCurveSpeed() {
    }

    public static double allowedBpt(SableTrain train, TravellingPoint nose, TrackGraph graph, List<Couple<TrackNode>> route, double decelBpt2) {
        if ((Boolean)LoconauticsConfig.DERAIL_ON_CURVE.get() && !train.isGhost() && train.level() instanceof ServerLevel level && train.car() != null) {
            ScheduleCurveSpeed.Plan plan = PLANS.computeIfAbsent(train.id(), key -> new ScheduleCurveSpeed.Plan());
            long now = level.getGameTime();
            double speed = Math.abs(train.speed());
            double decel = Math.max(1.0E-4, decelBpt2 * 0.8);
            if (now - plan.refreshed >= 10L) {
                refresh(plan, level, train, nose, graph, route, speed, decel, now);
            } else {
                plan.travelled += speed;
            }

            double allowed = onCurveCap(plan);
            if (Double.isFinite(plan.aheadCap)) {
                double room = Math.max(0.0, plan.aheadAt - plan.travelled);
                allowed = Math.min(allowed, Math.sqrt(plan.aheadCap * plan.aheadCap + 2.0 * decel * room));
            }

            noteChange(plan, train, allowed, speed);
            return allowed;
        } else {
            PLANS.remove(train.id());
            return Double.POSITIVE_INFINITY;
        }
    }

    private static void refresh(
        ScheduleCurveSpeed.Plan plan,
        ServerLevel level,
        SableTrain train,
        TravellingPoint nose,
        TrackGraph graph,
        List<Couple<TrackNode>> route,
        double speed,
        double decel,
        long now
    ) {
        plan.refreshed = now;
        plan.travelled = 0.0;
        PLANS.keySet().removeIf(idx -> SableTrainRegistry.get(idx) == null);
        plan.gN = DimensionPhysicsData.getGravity(level).length();
        List<ScheduleCurveSpeed.Rated> cars = new ArrayList<>();

        for (UUID id : TrainConsist.orderedConsist(level, train.car().subLevelId())) {
            CarriageRoll.Roll roll = CarriageRoll.rollById(id);
            if (roll != null && roll.rolling && roll.half > 0.0) {
                cars.add(new ScheduleCurveSpeed.Rated(id, roll));
            }
        }

        plan.cars = cars;
        plan.aheadCap = Double.POSITIVE_INFINITY;
        plan.aheadAt = 0.0;
        if (!cars.isEmpty()) {
            double horizon = speed * speed / (2.0 * decel) + 8.0;
            double[] best = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0.0};
            RouteDistance.walkAhead(nose, graph, route, horizon, (edge, startsAt) -> {
                double cap = capFor(plan, curvatureOf(edge));
                double envelope = Math.sqrt(cap * cap + 2.0 * decel * startsAt);
                if (envelope < best[0]) {
                    best[0] = envelope;
                    best[1] = cap;
                    best[2] = startsAt;
                }
            });
            plan.aheadCap = best[1];
            plan.aheadAt = best[2];
        }
    }

    private static double onCurveCap(ScheduleCurveSpeed.Plan plan) {
        double cap = Double.POSITIVE_INFINITY;

        for (ScheduleCurveSpeed.Rated rated : plan.cars) {
            SableTrain car = SableTrainRegistry.bySubLevel(rated.car());
            if (car != null && car.car() != null) {
                for (SableTrain.Bogey bogey : car.car().bogeys()) {
                    RailCarriage rail = bogey.rail();
                    if (rail != null) {
                        cap = Math.min(cap, capFor(plan, curvatureOf(rail.leading())));
                        cap = Math.min(cap, capFor(plan, curvatureOf(rail.trailing())));
                    }
                }
            }
        }

        return cap;
    }

    private static double capFor(ScheduleCurveSpeed.Plan plan, double curvature) {
        if (curvature <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }

        double cap = Double.POSITIVE_INFINITY;

        for (ScheduleCurveSpeed.Rated rated : plan.cars) {
            cap = Math.min(cap, CarriageRoll.safeCurveSpeed(rated.roll(), plan.gN, curvature, 0.5));
        }

        return cap / 20.0;
    }

    private static double curvatureOf(TravellingPoint point) {
        return point == null ? 0.0 : curvatureOf(point.edge);
    }

    private static double curvatureOf(TrackEdge edge) {
        return edge != null && edge.isTurn() && edge.getTurn() != null ? CURVATURE.computeIfAbsent(edge.getTurn(), ScheduleCurveSpeed::sharpest) : 0.0;
    }

    private static double sharpest(BezierConnection turn) {
        double worst = 0.0;
        Vec3 previous = null;
        Vec3 last = turn.getPosition(0.0);

        for (int i = 1; i <= 16; i++) {
            Vec3 point = turn.getPosition(i / 16.0);
            Vec3 step = new Vec3(point.x - last.x, 0.0, point.z - last.z);
            double length = step.length();
            if (length > 1.0E-6) {
                Vec3 heading = step.scale(1.0 / length);
                if (previous != null) {
                    double angle = Math.acos(Math.max(-1.0, Math.min(1.0, previous.dot(heading))));
                    worst = Math.max(worst, angle / length);
                }

                previous = heading;
            }

            last = point;
        }

        return worst;
    }

    private static void noteChange(ScheduleCurveSpeed.Plan plan, SableTrain train, double allowed, double speed) {
        double shown = Double.isFinite(allowed) ? Math.round(allowed * 20.0) : Double.POSITIVE_INFINITY;
        if (Double.compare(shown, plan.lastCap) != 0) {
            plan.lastCap = shown;
            LoconauticsConstants.LOGGER
                .debug(
                    "[curvespeed] train={} allowed={} b/s speed={} b/s ahead cap={} b/s in {} blocks cars={}",
                    new Object[]{
                        LogFormat.shortId(train.id()),
                        Double.isFinite(allowed) ? LogFormat.f(allowed * 20.0) : "none",
                        LogFormat.f(speed * 20.0),
                        Double.isFinite(plan.aheadCap) ? LogFormat.f(plan.aheadCap * 20.0) : "none",
                        LogFormat.f(plan.aheadAt - plan.travelled),
                        plan.cars.size()
                    }
                );
        }
    }

    private static final class Plan {
        long refreshed = -4611686018427387904L;
        List<ScheduleCurveSpeed.Rated> cars = List.of();
        double gN;
        double aheadCap = Double.POSITIVE_INFINITY;
        double aheadAt;
        double travelled;
        double lastCap = Double.NaN;
    }

    private record Rated(UUID car, CarriageRoll.Roll roll) {
    }
}
