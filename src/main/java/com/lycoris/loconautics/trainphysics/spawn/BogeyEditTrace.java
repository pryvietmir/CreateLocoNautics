package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;

public final class BogeyEditTrace {
    private static final String TAG = "[bogeyedit]";
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, BogeyEditTrace.Timeline> TIMELINES = new HashMap<>();
    private static final long STALE_TICKS = 40L;
    private static final long EASE_PRINT_TICKS = 10L;

    private BogeyEditTrace() {
    }

    public static void phase(SableTrain train, String bucket, String detail) {
        if (train != null && train.level() != null) {
            long now = train.level().getGameTime();
            BogeyEditTrace.Timeline line = TIMELINES.get(train.id());
            if (line == null || now - line.last > 40L) {
                line = new BogeyEditTrace.Timeline(now);
                TIMELINES.put(train.id(), line);
            }

            if (!line.bucket.isEmpty()) {
                line.ticks.merge(line.bucket, now - line.last, Long::sum);
            }

            line.last = now;
            boolean changed = !bucket.equals(line.bucket);
            boolean easePrint = bucket.equals("easing") && now - line.lastEasePrint >= 10L;
            line.bucket = bucket;
            if (changed || easePrint) {
                line.lastEasePrint = now;
                LoconauticsConstants.LOGGER.info("{} car {} t+{}: {} {}", new Object[]{"[bogeyedit]", train.id(), now - line.start, bucket, detail});
            }
        }
    }

    static void seated(SableTrain train) {
        BogeyEditTrace.Timeline line = train == null ? null : TIMELINES.remove(train.id());
        if (line != null) {
            long now = train.level().getGameTime();
            if (!line.bucket.isEmpty()) {
                line.ticks.merge(line.bucket, now - line.last, Long::sum);
            }

            StringBuilder out = new StringBuilder();
            out.append("[bogeyedit]")
                .append(" car ")
                .append(train.id())
                .append(" rerail took ")
                .append(now - line.start)
                .append(" ticks, of which settle ")
                .append(line.ticks.getOrDefault("settle", 0L))
                .append(", easing ")
                .append(line.ticks.getOrDefault("easing", 0L))
                .append(", cooldown ")
                .append(line.ticks.getOrDefault("cooldown", 0L));

            for (Entry<String, Long> entry : line.ticks.entrySet()) {
                if (!entry.getKey().equals("settle") && !entry.getKey().equals("easing") && !entry.getKey().equals("cooldown")) {
                    out.append(", ").append(entry.getKey()).append(" ").append(entry.getValue());
                }
            }

            LoconauticsConstants.LOGGER.info(out.toString());
        }
    }

    static void forget(UUID trainId) {
        TIMELINES.remove(trainId);
    }

    static void refuse(ServerSubLevel sub, String reason) {
        SableTrain train = sub == null ? null : SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train != null) {
            refuse(train, reason);
        }
    }

    static void refuse(SableTrain train, String reason) {
        if (train != null && train.car() != null) {
            long now = System.currentTimeMillis();
            Long last = LAST.get(train.id());
            if (last == null || now - last >= 1000L) {
                LAST.put(train.id(), now);
                ServerLevel level = train.level();
                SableTrain.Car car = train.car();
                ServerSubLevel sub = SubLevelData.resolve(level, car.subLevelId());
                StringBuilder out = new StringBuilder();
                out.append("[bogeyedit]")
                    .append(" car ")
                    .append(train.id())
                    .append(" refused: ")
                    .append(reason)
                    .append(" | derailed=")
                    .append(train.isDerailed())
                    .append(" listed=")
                    .append(car.bogeys().size())
                    .append(" spacing=")
                    .append(car.carriage() == null ? "none" : String.format("%.2f", car.carriage().bogeySpacing()));
                if (sub != null) {
                    out.append(" inPlot=")
                        .append(SubLevelSplitWatcher.scanBogeyPlotPositions(sub.getPlot()).size())
                        .append(" ignored=")
                        .append(SubLevelSplitWatcher.ignoredCount(train.id()));
                }

                for (SableTrain.Bogey bogey : car.bogeys()) {
                    RailCarriage rail = bogey.rail();
                    out.append(" [")
                        .append(bogey.localPos().toShortString())
                        .append(" rail=")
                        .append(rail != null && rail.center() != null)
                        .append(" off=")
                        .append(train.isBogeyOffTrack(bogey.localPos()));
                    if (sub != null && car.carriage() != null) {
                        out.append(" under=").append(SableTrainSpawner.railUnderBogey(level, sub, bogey, SableTrainSpawner.currentFacing(sub, car)) != null);
                    }

                    out.append(" pad=").append(GhostWheelBodies.rigState(train, bogey.localPos())).append("]");
                }

                LoconauticsConstants.LOGGER.info(out.toString());
            }
        }
    }

    private static final class Timeline {
        final long start;
        long last;
        long lastEasePrint;
        String bucket = "";
        final Map<String, Long> ticks = new LinkedHashMap<>();

        Timeline(long start) {
            this.start = start;
            this.last = start;
        }
    }
}
