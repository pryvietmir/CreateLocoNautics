package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IEdgePointListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import net.createmod.catnip.data.Couple;

public final class TrainSignalAccessor {
    public static final double QUERY_RANGE = 256.0;

    private TrainSignalAccessor() {
    }

    public static boolean isHeldAtRed(SableTrain train) {
        return TrainSignals.isHeld(train.id());
    }

    private static double travelDirection(SableTrain train) {
        double speed = train.speed();
        double target = train.targetSpeed();
        if (Math.abs(speed) > 1.0E-4) {
            return Math.signum(speed);
        } else {
            return Math.abs(target) > 1.0E-6 ? Math.signum(target) : Double.NaN;
        }
    }

    private static double nearer(double a, double b) {
        if (Double.isNaN(a)) {
            return b;
        } else {
            return Double.isNaN(b) ? a : Math.min(a, b);
        }
    }

    public static double gapToRed(SableTrain train) {
        double direction = travelDirection(train);
        return !Double.isNaN(direction)
            ? TrainSignals.gapToRed(train, direction, 256.0)
            : nearer(TrainSignals.gapToRed(train, 1.0, 256.0), TrainSignals.gapToRed(train, -1.0, 256.0));
    }

    public static double gapToAnySignal(SableTrain train) {
        TrainSignalAccessor.SignalAhead ahead = nextSignal(train);
        return ahead == null ? Double.NaN : ahead.distance();
    }

    public static TrainSignalAccessor.SignalAhead nextSignal(SableTrain train) {
        double direction = travelDirection(train);
        return !Double.isNaN(direction) ? scanAny(train, direction, 256.0) : nearer(scanAny(train, 1.0, 256.0), scanAny(train, -1.0, 256.0));
    }

    private static TrainSignalAccessor.SignalAhead nearer(TrainSignalAccessor.SignalAhead a, TrainSignalAccessor.SignalAhead b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else {
            return a.distance() <= b.distance() ? a : b;
        }
    }

    private static TrainSignalAccessor.SignalAhead scanAny(SableTrain train, double direction, double range) {
        if (!train.isDerailed() && !train.isPhysics() && train.car() != null) {
            RailCarriage carriage = train.car().carriage();
            TrackGraph graph = carriage == null ? null : carriage.graph();
            TravellingPoint origin = TrainStations.nosePoint(train);
            if (graph != null && origin != null && origin.edge != null) {
                TravellingPoint scout = new TravellingPoint(origin.node1, origin.node2, origin.edge, origin.position, origin.upsideDown);
                TrainSignalAccessor.SignalAhead[] found = new TrainSignalAccessor.SignalAhead[]{null};
                IEdgePointListener listener = (distance, pair) -> {
                    if (pair.getFirst() instanceof SignalBoundary signal) {
                        found[0] = new TrainSignalAccessor.SignalAhead(
                            signal, signal.isPrimary((TrackNode)((Couple)pair.getSecond()).getSecond()), Math.abs(distance) - SignalSnap.stopOffset
                        );
                        return true;
                    } else {
                        return false;
                    }
                };
                scout.travel(
                    graph, direction * range, scout.steer(SteerDirection.NONE, carriage.upNormal()), listener, scout.ignoreTurns(), scout.ignorePortals()
                );
                return found[0];
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public record SignalAhead(SignalBoundary signal, boolean primary, double distance) {
    }
}
