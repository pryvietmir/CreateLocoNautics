package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Pre;

@EventBusSubscriber(modid = "loconautics")
public final class TickMeter {
    public static final double YELLOW_GAP_MS = 55.0;
    public static final double RED_GAP_MS = 100.0;
    private static final int WINDOW = 20;
    private static final double NANOS_PER_MS = 1000000.0;
    private static final long RESTART_GAP_NANOS = 2000000000L;
    private static final int LOG_COOLDOWN_TICKS = 10;
    private static final long SAMPLE_AFTER_NANOS = 40000000L;
    private static final long SAMPLE_EVERY_MS = 5L;
    private static final int SAMPLE_FRAMES = 7;
    private static final int TOP_STACKS = 3;
    private static final long[] GAPS = new long[20];
    private static final Map<String, Integer> SAMPLES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Long> LEVEL_START = new LinkedHashMap<>();
    private static final Map<ResourceKey<Level>, Double> LEVEL_MS = new LinkedHashMap<>();
    private static volatile double tps = 20.0;
    private static volatile double gapMs = 50.0;
    private static volatile double workMs;
    private static long lastStart;
    private static long tickStart;
    private static int index;
    private static int filled;
    private static int sinceLog = 10;
    private static long lastGcMs;
    private static volatile Thread serverThread;
    private static volatile long lastStartNanos;
    private static Thread sampler;

    private TickMeter() {
    }

    public static double tps() {
        return tps;
    }

    public static double gapMs() {
        return gapMs;
    }

    public static double workMs() {
        return workMs;
    }

    @SubscribeEvent
    public static void onTickPre(Pre event) {
        tickStart = System.nanoTime();
        serverThread = Thread.currentThread();
        lastStartNanos = tickStart;
        if (sampler == null) {
            sampler = new Thread(TickMeter::sample, "loconautics tick sampler");
            sampler.setDaemon(true);
            sampler.start();
        }

        long gap = tickStart - lastStart;
        lastStart = tickStart;
        if (gap > 2000000000L) {
            index = 0;
            filled = 0;
            lastGcMs = totalGcMs();
        } else {
            gapMs = gap / 1000000.0;
            GAPS[index] = gap;
            index = (index + 1) % 20;
            filled = Math.min(filled + 1, 20);
            long total = 0L;

            for (int i = 0; i < filled; i++) {
                total += GAPS[i];
            }

            tps = Math.min(20.0, filled * 1.0E9 / total);
        }
    }

    @SubscribeEvent
    public static void onLevelPre(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVEL_START.put(level.dimension(), System.nanoTime());
        }
    }

    @SubscribeEvent
    public static void onLevelPost(Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Long start = LEVEL_START.get(level.dimension());
            if (start != null) {
                LEVEL_MS.put(level.dimension(), (System.nanoTime() - start) / 1000000.0);
            }
        }
    }

    @SubscribeEvent
    public static void onTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        workMs = (System.nanoTime() - tickStart) / 1000000.0;
        sinceLog++;
        if (!(gapMs < 55.0) && filled != 0 && sinceLog >= 10) {
            sinceLog = 0;
            long gc = totalGcMs();
            StringBuilder levels = new StringBuilder();

            for (Entry<ResourceKey<Level>, Double> entry : LEVEL_MS.entrySet()) {
                levels.append(' ').append(entry.getKey().location()).append('=').append(String.format("%.1f", entry.getValue())).append("ms");
            }

            LoconauticsConstants.LOGGER
                .info(
                    "[tickmeter] {} gap {} ms, tick work {} ms, gc since last log {} ms, tps {},{}",
                    new Object[]{
                        gapMs < 100.0 ? "YELLOW" : "RED",
                        String.format("%.1f", gapMs),
                        String.format("%.1f", workMs),
                        gc - lastGcMs,
                        String.format("%.1f", tps),
                        levels
                    }
                );
            lastGcMs = gc;
            List<Entry<String, Integer>> top = new ArrayList<>(SAMPLES.entrySet());
            top.sort(Entry.<String, Integer>comparingByValue().reversed());
            SAMPLES.clear();

            for (int i = 0; i < Math.min(3, top.size()); i++) {
                LoconauticsConstants.LOGGER.info("[tickmeter]   cause {} samples at {}", top.get(i).getValue(), top.get(i).getKey());
            }
        } else {
            if (gapMs < 55.0) {
                SAMPLES.clear();
            }
        }
    }

    private static void sample() {
        while (true) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException interrupted) {
                return;
            }

            Thread target = serverThread;
            if (target != null && System.nanoTime() - lastStartNanos >= 40000000L) {
                StackTraceElement[] frames = target.getStackTrace();
                if (frames.length != 0) {
                    StringBuilder key = new StringBuilder();

                    for (int i = 0; i < Math.min(7, frames.length); i++) {
                        if (i > 0) {
                            key.append(" < ");
                        }

                        key.append(shorten(frames[i]));
                    }

                    SAMPLES.merge(key.toString(), 1, Integer::sum);
                }
            }
        }
    }

    private static String shorten(StackTraceElement frame) {
        String cls = frame.getClassName();
        return cls.substring(cls.lastIndexOf(46) + 1) + "." + frame.getMethodName();
    }

    private static long totalGcMs() {
        long total = 0L;

        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, bean.getCollectionTime());
        }

        return total;
    }
}
