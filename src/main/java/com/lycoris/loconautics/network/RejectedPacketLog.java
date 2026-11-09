package com.lycoris.loconautics.network;

import com.lycoris.loconautics.LoconauticsConstants;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * Logs rejected (likely forged) client packets, at most once per player per {@link #INTERVAL_MS}.
 * Without a limit a cheating client could flood the server log by spamming packets that get rejected.
 */
public final class RejectedPacketLog {
    private static final long INTERVAL_MS = 10_000L;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private RejectedPacketLog() {
    }

    public static void warn(ServerPlayer player, String what) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < INTERVAL_MS) {
            return;
        }
        LAST.put(player.getUUID(), now);
        if (LAST.size() > 512) {
            LAST.values().removeIf(t -> now - t > INTERVAL_MS);
        }
        LoconauticsConstants.LOGGER.warn("[packet] {} rejected: {}", player.getGameProfile().getName(), what);
    }
}
