package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class StationChunkTickets {
    public static final int RADIUS_CHUNKS = 1;
    public static final int MAX_DWELL_TICKS = 6000;
    private static final Map<UUID, StationChunkTickets.Held> HELD = new HashMap<>();
    private static final Set<UUID> CAPPED = new HashSet<>();
    private static final int MAX_WHY_LINES = 300;
    private static int why;
    private static TicketController controller;

    private StationChunkTickets() {
    }

    public static void register(RegisterTicketControllersEvent event) {
        controller = new TicketController(LoconauticsConstants.id("station_dwell"), (level, helper) -> {
            for (UUID owner : helper.getEntityTickets().keySet()) {
                helper.removeAllTickets(owner);
            }
        });
        event.register(controller);
    }

    public static void update(SableTrain train) {
        BlockPos ghostHold = TrainResidency.ghostHoldAnchor(train);
        BlockPos anchor = ghostHold != null ? ghostHold : stationAnchorIfEnabled(train);
        if (anchor == null) {
            CAPPED.remove(train.id());
            if (!holdChainAfterEmbody(train)) {
                release(train.id(), "nothing left to hold chunks for");
            }
        } else if (train.isGhost() && TrainResidency.pullBlocked(train)) {
            release(train.id(), "sable flapped this body");
        } else if (ghostHold != null) {
            boolean lost = TrainResidency.bodyLost(train);
            if (!lost) {
                CAPPED.remove(train.id());
            } else if (CAPPED.contains(train.id())) {
                return;
            }

            acquire(train, new ChunkPos(anchor), lost);
        } else if (!CAPPED.contains(train.id())) {
            acquire(train, new ChunkPos(anchor), true);
            holdChainAfterEmbody(train);
        }
    }

    private static boolean holdChainAfterEmbody(SableTrain train) {
        if (TrainResidency.embodiedWithin(train, 100L) && controller != null) {
            Set<ChunkPos> need = TrainResidency.chainChunks(train);
            if (need.isEmpty()) {
                return false;
            }

            ServerLevel level = train.level();
            StationChunkTickets.Held held = HELD.get(train.id());
            Set<ChunkPos> chunks = new HashSet<>(need);
            ChunkPos center = held == null ? need.iterator().next() : held.center();
            long since = level.getGameTime();
            if (held != null && held.level() == level) {
                chunks.addAll(held.chunks());
                since = held.sinceTick();
            } else if (held != null) {
                release(train.id(), "level changed");
            }

            Set<ChunkPos> already = held != null && held.level() == level ? held.chunks() : Set.of();

            for (ChunkPos pos : chunks) {
                if (!already.contains(pos)) {
                    controller.forceChunk(level, train.id(), pos.x, pos.z, true, true);
                }
            }

            HELD.put(train.id(), new StationChunkTickets.Held(level, center, since, chunks));
            if (held == null) {
                logWhy("grace hold", train, chunks);
            }

            return true;
        } else {
            return false;
        }
    }

    public static void clearCap(UUID trainId) {
        CAPPED.remove(trainId);
    }

    public static void release(UUID trainId, String reason) {
        StationChunkTickets.Held held = HELD.remove(trainId);
        if (held != null) {
            if (why < 300) {
                why++;
                LoconauticsConstants.LOGGER
                    .debug("[thrashwhy] release train={} reason={} chunks={}", new Object[]{LogFormat.shortId(trainId), reason, held.chunks().size()});
            }

            dropTickets(trainId, held);
        }
    }

    public static void releaseAll() {
        for (Entry<UUID, StationChunkTickets.Held> entry : new HashMap<>(HELD).entrySet()) {
            dropTickets(entry.getKey(), entry.getValue());
        }

        HELD.clear();
        CAPPED.clear();
    }

    public static void retain(Set<UUID> live) {
        CAPPED.retainAll(live);

        for (UUID id : new HashMap<>(HELD).keySet()) {
            if (!live.contains(id)) {
                release(id, "train is gone");
            }
        }
    }

    public static String describe(SableTrain train) {
        StationChunkTickets.Held held = HELD.get(train.id());
        if (held == null) {
            if (!(Boolean)LoconauticsConfig.STATION_LOAD_CHUNKS.get()) {
                return "station chunk loading is off";
            } else if (waitsForStationUnloaded(train)) {
                return "no station ticket, the schedule waits for the station to unload";
            } else {
                return CAPPED.contains(train.id()) ? "station ticket capped out" : "no station ticket";
            }
        } else {
            return "holding " + held.chunks().size() + " chunks at " + held.center();
        }
    }

    public static String summary() {
        Set<StationChunkTickets.ChunkKey> chunks = new HashSet<>();

        for (StationChunkTickets.Held held : HELD.values()) {
            for (ChunkPos pos : held.chunks()) {
                chunks.add(new StationChunkTickets.ChunkKey(held.level(), pos.toLong()));
            }
        }

        return HELD.size() + " train(s) holding station chunks, " + chunks.size() + " distinct chunk(s) pinned, " + CAPPED.size() + " capped out";
    }

    private static void dropTickets(UUID trainId, StationChunkTickets.Held held) {
        if (controller != null) {
            for (ChunkPos pos : held.chunks()) {
                controller.forceChunk(held.level(), trainId, pos.x, pos.z, false, true);
            }
        }
    }

    private static void acquire(SableTrain train, ChunkPos center, boolean capped) {
        ServerLevel level = train.level();
        StationChunkTickets.Held held = HELD.get(train.id());
        long now = level.getGameTime();
        if (held != null && held.center().equals(center) && held.level() == level) {
            if (capped && now - held.sinceTick() > 6000L) {
                CAPPED.add(train.id());
                release(train.id(), "dwell cap of 6000 ticks reached");
                LoconauticsConstants.LOGGER
                    .debug(
                        "[ghostedge] chunk hold capped train={} ticks={} ghost={}",
                        new Object[]{LogFormat.shortId(train.id()), now - held.sinceTick(), train.isGhost()}
                    );
            }
        } else {
            if (held != null) {
                release(train.id(), "station moved");
            }

            if (controller != null) {
                Set<ChunkPos> chunks = new HashSet<>();
                forEachChunk(center, (cx, cz) -> {
                    controller.forceChunk(level, train.id(), cx, cz, true, true);
                    chunks.add(new ChunkPos(cx, cz));
                });
                HELD.put(train.id(), new StationChunkTickets.Held(level, center, now, chunks));
                logWhy(train.isGhost() ? "ghost station hold" : "station hold", train, chunks);
            }
        }
    }

    private static void logWhy(String what, SableTrain train, Set<ChunkPos> chunks) {
        if (why < 300) {
            why++;
            LoconauticsConstants.LOGGER
                .debug(
                    "[thrashwhy] {} train={} ghost={} stationState={} scheduleHold={} chunks={}",
                    new Object[]{what, LogFormat.shortId(train.id()), train.isGhost(), train.stationState(), train.scheduleHold(), chunks}
                );
        }
    }

    private static BlockPos stationAnchorIfEnabled(SableTrain train) {
        return LoconauticsConfig.STATION_LOAD_CHUNKS.get() ? stationAnchor(train) : null;
    }

    private static BlockPos stationAnchor(SableTrain train) {
        if (train.stationState() != SableTrain.StationState.STOPPED && !train.scheduleHold()) {
            return null;
        }

        if (waitsForStationUnloaded(train)) {
            return null;
        }

        GlobalStation station = train.stoppedAt();
        return station == null ? null : station.getBlockEntityPos();
    }

    private static boolean waitsForStationUnloaded(SableTrain train) {
        SableScheduleRuntime runtime = SableScheduleRuntime.get(train.id());
        return runtime != null && runtime.waitsForStationUnloaded();
    }

    private static void forEachChunk(ChunkPos center, StationChunkTickets.ChunkAction action) {
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                action.run(cx, cz);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkAction {
        void run(int var1, int var2);
    }

    private record ChunkKey(ServerLevel level, long chunk) {
    }

    private record Held(ServerLevel level, ChunkPos center, long sinceTick, Set<ChunkPos> chunks) {
    }
}
