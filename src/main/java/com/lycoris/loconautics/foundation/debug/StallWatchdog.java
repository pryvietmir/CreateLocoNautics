package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.portal.PortalTrace;
import java.util.StringJoiner;

public final class StallWatchdog {
    private static final long POLL_MS = 100L;
    private static final long FIRST_DUMP_MS = 400L;
    private static final long SECOND_DUMP_MS = 1500L;
    private static final int FRAMES = 30;
    private static volatile long lastTickNanos;
    private static volatile long tickCount;
    private static volatile Thread serverThread;
    private static Thread watcher;

    private StallWatchdog() {
    }

    public static void onServerTick() {
        // Fix: the watchdog only ever reports while portal tracing is on, so don't spin up
        // a permanent background thread polling 10x/s for everyone else.
        if (!PortalTrace.on()) {
            return;
        }

        serverThread = Thread.currentThread();
        lastTickNanos = System.nanoTime();
        tickCount++;
        if (watcher == null) {
            watcher = new Thread(StallWatchdog::watch, "loconautics stall watchdog");
            watcher.setDaemon(true);
            watcher.start();
        }
    }

    private static void watch() {
        long dumpedTick = -1L;
        int dumps = 0;

        while (true) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException interrupted) {
                return;
            }

            Thread target = serverThread;
            if (target != null && PortalTrace.on()) {
                long tick = tickCount;
                if (tick != dumpedTick) {
                    dumpedTick = tick;
                    dumps = 0;
                }

                long stallMs = (System.nanoTime() - lastTickNanos) / 1000000L;
                boolean due = dumps == 0 ? stallMs >= 400L : dumps == 1 && stallMs >= 1500L;
                if (due) {
                    dumps++;
                    StringJoiner trace = new StringJoiner("\n    at ", "\n    at ", "");
                    StackTraceElement[] frames = target.getStackTrace();

                    for (int i = 0; i < Math.min(30, frames.length); i++) {
                        trace.add(frames[i].toString());
                    }

                    LoconauticsConstants.LOGGER.warn("[stall] server thread stuck {}ms in tick {}, stack:{}", new Object[]{stallMs, tick, trace});
                }
            }
        }
    }
}
