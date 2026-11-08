package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelOverlap;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Pre;
import org.joml.Vector3d;

@EventBusSubscriber(modid = "loconautics")
public final class PortalLagTrace {
    private static final long LEVEL_SLOW_MS = 60L;
    private static final long TICK_SLOW_MS = 100L;
    private static final long LOG_GAP_TICKS = 20L;
    private static final long NANOS_PER_MS = 1000000L;
    private static final Map<ResourceKey<Level>, Long> LEVEL_START = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LEVEL_MS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> LAST_LOGGED = new HashMap<>();
    private static long tickStart;

    private PortalLagTrace() {
    }

    @SubscribeEvent
    public static void onLevelPre(Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVEL_START.put(level.dimension(), System.nanoTime());
        }
    }

    @SubscribeEvent
    public static void onLevelPost(Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Long start = LEVEL_START.get(level.dimension());
            if (start != null) {
                long ms = (System.nanoTime() - start) / 1000000L;
                LEVEL_MS.put(level.dimension(), ms);
                long now = level.getGameTime();
                if (ms >= 60L && now - LAST_LOGGED.getOrDefault(level.dimension(), -20L) >= 20L) {
                    LAST_LOGGED.put(level.dimension(), now);
                    describe(level, ms);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTickPre(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent
    public static void onTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        long ms = (System.nanoTime() - tickStart) / 1000000L;
        if (ms >= 100L) {
            LoconauticsConstants.LOGGER.info("[portallag] tick took {} ms, levels={} {}", new Object[]{ms, LEVEL_MS, PortalTwins.debugCounts()});
        }
    }

    private static void describe(ServerLevel level, long ms) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            int bodies = 0;
            StringBuilder twins = new StringBuilder();

            for (ServerSubLevel sub : container.getAllSubLevels()) {
                bodies++;
                if (PortalTwins.isTwin(sub)) {
                    Vector3d pos = new Vector3d(sub.logicalPose().position());
                    StringBuilder touching = new StringBuilder();

                    for (ServerSubLevel other : container.getAllSubLevels()) {
                        SubLevelOverlap.Hit hit = other == sub ? null : SubLevelOverlap.test(sub, other);
                        if (hit != null) {
                            touching.append(' ').append(LogFormat.shortId(other)).append(String.format("(%.2f)", hit.depth()));
                        }
                    }

                    twins.append(
                        String.format(
                            "%n  twin %s at (%.1f %.1f %.1f) footprintLoaded=%s overlaps=[%s ]",
                            LogFormat.shortId(sub),
                            pos.x,
                            pos.y,
                            pos.z,
                            TrainResidency.footprintLoaded(level, pos),
                            touching
                        )
                    );
                }
            }

            LoconauticsConstants.LOGGER
                .info(
                    "[portallag] {} level tick took {} ms, subLevels={} {}{}",
                    new Object[]{level.dimension().location(), ms, bodies, PortalTwins.debugCounts(), twins}
                );
        }
    }
}
