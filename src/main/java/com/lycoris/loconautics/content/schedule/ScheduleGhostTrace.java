package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

final class ScheduleGhostTrace {
    private static final long INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ScheduleGhostTrace() {
    }

    static boolean due(SableTrain train) {
        if (train.level() == null) {
            return false;
        }

        long now = train.level().getGameTime();
        Long last = LAST.get(train.id());
        if (last != null && now - last < 20L) {
            return false;
        }

        LAST.put(train.id(), now);
        return true;
    }

    static void log(SableTrain train, String text) {
        LoconauticsConstants.LOGGER
            .info("[schedghost] {} ghost={} residency={} {}", new Object[]{LogFormat.shortId(train.id()), train.isGhost(), train.residency(), text});
    }

    static void event(@Nullable SableTrain train, String text) {
        if (train == null) {
            LoconauticsConstants.LOGGER.info("[schedghost] event {}", text);
        } else {
            log(train, "event " + text);
        }
    }
}
