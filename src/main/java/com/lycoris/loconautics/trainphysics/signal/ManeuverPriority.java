package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.station.ConsistNose;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

final class ManeuverPriority {
    private static final double PARALLEL = 0.001;
    private static final Map<String, String> LAST = new HashMap<>();

    static boolean isManeuvering(UUID trainId) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(trainId);
        return runtime != null && runtime.maneuver();
    }

    static boolean mustWait(SableTrain self, SableTrain other, boolean otherHolds, boolean selfHolds, TrackNode junction) {
        if (otherHolds != selfHolds) {
            log(self, other, "reservation", otherHolds);
            return otherHolds;
        } else {
            double cross = approachCross(self, other, junction);
            if (Math.abs(cross) > 0.001) {
                boolean wait = cross < 0.0;
                log(self, other, "right of way", wait);
                return wait;
            } else {
                boolean wait = self.id().compareTo(other.id()) > 0;
                log(self, other, "lower uuid", wait);
                return wait;
            }
        }
    }

    private static double approachCross(SableTrain self, SableTrain other, TrackNode junction) {
        Vec3 a = approach(self, junction);
        Vec3 b = approach(other, junction);
        return a != null && b != null ? a.x * b.z - a.z * b.x : 0.0;
    }

    private static Vec3 approach(SableTrain train, TrackNode junction) {
        RailCarriage rail = train.car() == null ? null : train.car().carriage();
        TrackGraph graph = rail == null ? null : rail.graph();
        TravellingPoint nose = TrainStations.nosePoint(train);
        if (graph != null && nose != null && nose.edge != null) {
            Vec3 from = rail.worldPointOf(nose);
            Vec3 to = junction.getLocation().getLocation();
            Vec3 flat = new Vec3(to.x - from.x, 0.0, to.z - from.z);
            double length = flat.length();
            return length < 1.0E-6 ? null : flat.scale(1.0 / length);
        } else {
            return null;
        }
    }

    private static void log(SableTrain self, SableTrain other, String rule, boolean wait) {
        String line = rule + wait;
        if (!line.equals(LAST.put(self.id().toString() + other.id(), line))) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[maneuver] priority {} vs {} decided by {}, {} waits",
                    new Object[]{
                        ConsistNose.shortId(self.id()),
                        ConsistNose.shortId(other.id()),
                        rule,
                        wait ? ConsistNose.shortId(self.id()) : ConsistNose.shortId(other.id())
                    }
                );
        }
    }

    private ManeuverPriority() {
    }
}
