package com.lycoris.loconautics.common.turntable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.TrackHostFrameSync;
import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.foundation.LogFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "loconautics")
public final class TurntableGhosts {
    private static final int SAVE_INTERVAL_TICKS = 200;
    private static final Map<UUID, GhostTurntable> GHOSTS = new ConcurrentHashMap<>();
    private static final Set<TurntableGhosts.Source> LIVE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FAILED = ConcurrentHashMap.newKeySet();
    @Nullable
    private static TurntableGhostStore store;
    private static int tick;

    private TurntableGhosts() {
    }

    public static Collection<GhostTurntable> all() {
        return new ArrayList<>(GHOSTS.values());
    }

    public static void trackLive(TurntableGhosts.Source source) {
        LIVE.add(source);
    }

    public static void untrackLive(TurntableGhosts.Source source) {
        LIVE.remove(source);
    }

    public static void markDirty() {
        TurntableGhostStore current = store;
        if (current != null) {
            current.setDirty();
        }
    }

    public static void ghostify(TurntableGhosts.Source source) {
        GhostTurntable ghost = source.toGhost();
        if (ghost != null && ghost.frame() != null) {
            GHOSTS.put(ghost.plateSubLevelId(), ghost);
            if (ghost.turntableScheduleDriven()) {
                TurntableRegistry.register(ghost);
            }

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.overworld() != null && store == null) {
                store = TurntableGhostStore.get(server);
            }

            TrackHostFrameSync.requestSync();
            markDirty();
            LoconauticsConstants.LOGGER
                .debug(
                    "[turntableghost] plate {} ghosted at {} degrees, powered {}, schedule {}",
                    new Object[]{
                        LogFormat.shortId(ghost.plateSubLevelId()),
                        String.format("%.2f", ghost.motion().targetAngleDegrees()),
                        ghost.turntablePowered(),
                        ghost.turntableScheduleDriven()
                    }
                );
        }
    }

    @Nullable
    public static GhostTurntable take(@Nullable UUID plate) {
        GhostTurntable ghost = plate == null ? null : GHOSTS.remove(plate);
        if (ghost == null) {
            return null;
        }

        TurntableRegistry.dropGhost(ghost);
        TrackHostFrameSync.requestSync();
        markDirty();
        LoconauticsConstants.LOGGER
            .debug("[turntableghost] plate {} loaded again at {} degrees", LogFormat.shortId(plate), String.format("%.2f", ghost.motion().targetAngleDegrees()));
        return ghost;
    }

    public static void drop(UUID plate) {
        GhostTurntable ghost = GHOSTS.remove(plate);
        if (ghost != null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            ServerLevel level = server == null ? null : server.getLevel(ghost.dimension());
            ghost.signals().release(level);
            TurntableRegistry.unregister(plate);
            TurntableRegistry.forgetPlate(plate);
            FAILED.remove(plate);
            markDirty();
            LoconauticsConstants.LOGGER.debug("[turntableghost] plate {} ghost dropped, its body or bearing is gone", LogFormat.shortId(plate));
        }
    }

    public static void serverTick(MinecraftServer server) {
        tick++;
        if (!GHOSTS.isEmpty()) {
            for (GhostTurntable ghost : GHOSTS.values()) {
                tickOne(ghost, server);
            }

            if (tick % 200 == 0) {
                markDirty();
            }
        }
    }

    private static void tickOne(GhostTurntable ghost, MinecraftServer server) {
        try {
            ghost.tick(server);
            if (ghost.orphaned(server)) {
                TrackHostFrames.forget(ghost.plateSubLevelId());
                drop(ghost.plateSubLevelId());
            }
        } catch (RuntimeException e) {
            if (FAILED.add(ghost.plateSubLevelId())) {
                LoconauticsConstants.LOGGER
                    .error("[turntableghost] plate {} failed to tick, it will keep failing quietly", LogFormat.shortId(ghost.plateSubLevelId()), e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        store = TurntableGhostStore.get(server);

        for (CompoundTag tag : store.loaded()) {
            GhostTurntable ghost = GhostTurntable.read(tag);
            if (ghost != null && TurntableRegistry.byPlate(ghost.plateSubLevelId()) == null) {
                GHOSTS.put(ghost.plateSubLevelId(), ghost);
                if (ghost.turntableScheduleDriven()) {
                    TurntableRegistry.register(ghost);
                }

                LoconauticsConstants.LOGGER
                    .debug(
                        "[turntableghost] plate {} restored as a ghost at {} degrees",
                        LogFormat.shortId(ghost.plateSubLevelId()),
                        String.format("%.2f", ghost.motion().targetAngleDegrees())
                    );
            }
        }

        store.loaded().clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (TurntableGhosts.Source source : new ArrayList<>(LIVE)) {
            ghostify(source);
        }

        markDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GHOSTS.clear();
        LIVE.clear();
        FAILED.clear();
        store = null;
        tick = 0;
    }

    static {
        TrackHostFrames.onForget(TurntableGhosts::drop);
    }

    public interface Source {
        @Nullable
        GhostTurntable toGhost();
    }
}
