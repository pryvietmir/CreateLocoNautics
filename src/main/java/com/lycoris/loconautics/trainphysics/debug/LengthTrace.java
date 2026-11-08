package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.debug.DebugLines;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class LengthTrace {
    private static final long DRAW_PERIOD = 10L;
    private static final long LOG_PERIOD = 40L;
    private static final int TTL = 100;
    private static final int CAR_COLOR = 65331;
    private static final int CONSIST_COLOR = 16776960;
    private static final int END_COLOR = 16777215;
    private static final double CAR_LIFT = 2.5;
    private static final double CONSIST_LIFT = 3.2;
    private static final double END_TICK = 1.0;
    private static boolean enabled;

    private LengthTrace() {
    }

    public static void tick() {
        if (enabled) {
            Set<UUID> seen = new HashSet<>();

            for (SableTrain train : SableTrainRegistry.all()) {
                ServerLevel level = train.level();
                if (level != null
                    && train.car() != null
                    && train.car().subLevelId() != null
                    && level.getGameTime() % 10L == 0L
                    && !seen.contains(train.car().subLevelId())) {
                    List<UUID> chain = TrainConsist.orderedChain(level, train.car().subLevelId());
                    seen.addAll(chain);
                    seen.add(train.car().subLevelId());
                    report(level, chain);
                }
            }
        }
    }

    private static void report(ServerLevel level, List<UUID> chain) {
        List<SableTrain> cars = new ArrayList<>();

        for (UUID id : chain) {
            SableTrain car = SableTrainRegistry.bySubLevel(id);
            if (car != null && car.car() != null) {
                cars.add(car);
            }
        }

        if (!cars.isEmpty()) {
            boolean log = level.getGameTime() % 40L == 0L;
            double consist = TrainLength.consistLength(level, cars.get(0).car().subLevelId());
            List<Vector3d> mids = new ArrayList<>();
            StringBuilder detail = new StringBuilder();

            for (SableTrain car : cars) {
                UUID id = car.car().subLevelId();
                ServerSubLevel sub = SubLevelData.resolve(level, id);
                Vector3d[] ref = sub == null ? null : TrainRailAdvance.bogeyReference(sub, car.car());
                double length = TrainLength.carLength(level, car);
                if (ref == null) {
                    detail.append(String.format("%n  car %s carLength=%.2f body=none, nothing drawn", LogFormat.shortId(id), length));
                } else {
                    mids.add(ref[0]);
                    Vector3d half = new Vector3d(ref[1]).normalize().mul(length / 2.0);
                    Vec3 from = lifted(new Vector3d(ref[0]).sub(half), 2.5);
                    Vec3 to = lifted(new Vector3d(ref[0]).add(half), 2.5);
                    String slot = "len:" + LogFormat.shortId(id);
                    DebugLines.line(level, slot, from, to, 65331, 0.125F, 100);
                    DebugLines.line(level, slot + ":a", from, from.add(0.0, 1.0, 0.0), 16777215, 0.125F, 100);
                    DebugLines.line(level, slot + ":b", to, to.add(0.0, 1.0, 0.0), 16777215, 0.125F, 100);
                    detail.append(
                        String.format(
                            "%n  car %s carLength=%.2f bogeySpacing=%.2f",
                            LogFormat.shortId(id),
                            length,
                            car.car().carriage() == null ? Double.NaN : car.car().carriage().bogeySpacing()
                        )
                    );
                }
            }

            double polyline = drawConsist(level, cars, mids);
            if (log) {
                LoconauticsConstants.LOGGER
                    .info(
                        "[lentrace] consist head={} cars={} consistLength={} polyline={} delta={}{}",
                        new Object[]{
                            LogFormat.shortId(cars.get(0).car().subLevelId()), cars.size(), fmt(consist), fmt(polyline), fmt(polyline - consist), detail
                        }
                    );
                logPairs(level, cars, mids);
            }
        }
    }

    private static double drawConsist(ServerLevel level, List<SableTrain> cars, List<Vector3d> mids) {
        if (mids.size() == cars.size() && mids.size() >= 2) {
            List<Vector3d> points = new ArrayList<>();
            Vector3d noseDir = new Vector3d((Vector3dc)mids.get(0)).sub((Vector3dc)mids.get(1)).normalize();
            points.add(new Vector3d((Vector3dc)mids.get(0)).add(noseDir.mul(TrainLength.endReach(level, cars, true))));
            points.addAll(mids);
            int last = mids.size() - 1;
            Vector3d tailDir = new Vector3d((Vector3dc)mids.get(last)).sub((Vector3dc)mids.get(last - 1)).normalize();
            points.add(new Vector3d((Vector3dc)mids.get(last)).add(tailDir.mul(TrainLength.endReach(level, cars, false))));
            double total = 0.0;

            for (int i = 0; i + 1 < points.size(); i++) {
                Vec3 from = lifted(points.get(i), 3.2);
                Vec3 to = lifted(points.get(i + 1), 3.2);
                DebugLines.line(level, "len:consist:" + LogFormat.shortId(cars.get(0).car().subLevelId()) + ":" + i, from, to, 16776960, 0.125F, 100);
                total += points.get(i).distance((Vector3dc)points.get(i + 1));
            }

            return total;
        } else {
            return Double.NaN;
        }
    }

    private static void logPairs(ServerLevel level, List<SableTrain> cars, List<Vector3d> mids) {
        if (mids.size() == cars.size()) {
            for (int i = 1; i < cars.size(); i++) {
                double prevHalf = TrainLength.carLength(level, cars.get(i - 1)) / 2.0;
                double half = TrainLength.carLength(level, cars.get(i)) / 2.0;
                LoconauticsConstants.LOGGER
                    .info(
                        "[lentrace] pair {} to {} centerSpacing={} actualMidGap={}",
                        new Object[]{
                            LogFormat.shortId(cars.get(i - 1).car().subLevelId()),
                            LogFormat.shortId(cars.get(i).car().subLevelId()),
                            fmt(TrainLength.centerSpacing(level, cars.get(i - 1), cars.get(i), prevHalf, half)),
                            fmt(mids.get(i - 1).distance((Vector3dc)mids.get(i)))
                        }
                    );
            }
        }
    }

    private static Vec3 lifted(Vector3d point, double lift) {
        return new Vec3(point.x, point.y + lift, point.z);
    }

    private static String fmt(double value) {
        return String.format("%.2f", value);
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
            Commands.literal("length")
                .executes(
                    ctx -> {
                        enabled = !enabled;
                        ((CommandSourceStack)ctx.getSource())
                            .sendSuccess(
                                () -> Component.literal(
                                    "length trace " + (enabled ? "on" : "off") + ", green is each car, yellow is the whole consist, white ticks mark car ends"
                                ),
                                false
                            );
                        return 1;
                    }
                )
        );
    }
}
