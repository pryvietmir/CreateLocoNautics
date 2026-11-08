package com.lycoris.loconautics.trainphysics.state;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.ScheduleConductor;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.debug.CartMoveTrace;
import com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace;
import com.lycoris.loconautics.trainphysics.debug.HostThrashTrace;
import com.lycoris.loconautics.trainphysics.drive.ConsistPower;
import com.lycoris.loconautics.trainphysics.drive.ExternalForceDerail;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.rail.RailFrame;
import com.lycoris.loconautics.trainphysics.signal.TrainSignals;
import com.lycoris.loconautics.trainphysics.spacing.CarExtent;
import com.lycoris.loconautics.trainphysics.spacing.TrainSpacing;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class TrainResidency {
    private static final Set<UUID> RECONCILE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> RAILHEAD_HELD = ConcurrentHashMap.newKeySet();
    private static final int SNATCH_RETRY_TICKS = 40;
    private static final Map<UUID, Integer> SNATCH_COOLDOWN = new ConcurrentHashMap<>();
    private static final Set<UUID> SNATCH_MISS_LOGGED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> SNATCH_MISSES = new ConcurrentHashMap<>();
    private static final int LOST_CHECK_MISSES = 30;
    private static final Map<UUID, Long> REFILED_AT = new ConcurrentHashMap<>();
    private static final int REFILE_BACKOFF_TICKS = 100;
    private static final Map<UUID, Long> DEFER_UNTIL = new ConcurrentHashMap<>();
    private static final Set<UUID> DEFER_LOGGED = ConcurrentHashMap.newKeySet();
    private static final int DEFER_RECHECK_TICKS = 20;
    private static final Set<UUID> VELOCITY_CHECK = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> SPLIT_PINNED = new ConcurrentHashMap<>();
    private static final Set<UUID> SPLIT_GAVE_UP = ConcurrentHashMap.newKeySet();
    private static final int MAX_SPLIT_PINS = 8;
    private static final Map<UUID, Integer> RESEAT_FAILS = new ConcurrentHashMap<>();
    private static final int RESEAT_RETRY_TICKS = 40;
    private static final int MAX_RESEAT_FAILS = 5;
    private static final Map<UUID, Long> UNLOADED_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GHOST_STEPPED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> EMBODIED_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PULL_BLOCKED_UNTIL = new ConcurrentHashMap<>();
    public static final long EMBODY_GRACE_TICKS = 100L;
    private static final long FLAP_TICKS = 20L;
    private static final long FLAP_HOLDOFF_TICKS = 100L;
    private static final long MATE_UNLOAD_HOLD_TICKS = 60L;
    public static final int POSE_SETTLE_TICKS = 20;
    private static final double RESEAT_IN_PLACE = 1.0;
    private static final int EXTENT_REFRESH_MASK = 15;

    public static void clear() {
        RECONCILE.clear();
        RAILHEAD_HELD.clear();
        SNATCH_COOLDOWN.clear();
        SNATCH_MISS_LOGGED.clear();
        SNATCH_MISSES.clear();
        REFILED_AT.clear();
        DEFER_UNTIL.clear();
        DEFER_LOGGED.clear();
        VELOCITY_CHECK.clear();
        SPLIT_PINNED.clear();
        SPLIT_GAVE_UP.clear();
        RESEAT_FAILS.clear();
        UNLOADED_AT.clear();
        GHOST_STEPPED.clear();
        EMBODIED_AT.clear();
        PULL_BLOCKED_UNTIL.clear();
        TrainSpacing.clear();
    }

    private TrainResidency() {
    }

    public static void onSubLevelAdded(SubLevel subLevel) {
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train != null && train.isGhost()) {
            RECONCILE.add(train.id());
        }
    }

    public static void queueReconcile(SableTrain train) {
        if (train != null) {
            RECONCILE.add(train.id());
        }
    }

    public static void onSubLevelUnloaded(SubLevel subLevel) {
        GhostConsistTrace.noteAnyUnload(subLevel);
        SableTrain train = SableTrainRegistry.bySubLevel(subLevel.getUniqueId());
        if (train != null) {
            if (train.isGhost()) {
                RECONCILE.remove(train.id());
            } else {
                if (subLevel instanceof ServerSubLevel sub) {
                    GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
                    if (pointer != null) {
                        train.setBodyPointer(pointer);
                    }

                    train.setBodyChunk(chunkOf(sub.logicalPose().position()));
                    train.setLastKnownPos(sub.logicalPose().position());
                    logUnload(train, sub);
                    GhostConsistTrace.noteUnload(train, sub);
                }

                train.setResidency(SableTrain.Residency.GHOST);
                long unloadedAt = train.level().getGameTime();
                UNLOADED_AT.put(train.id(), unloadedAt);
                if (unloadedAt - EMBODIED_AT.getOrDefault(train.id(), -4611686018427387904L) < 20L) {
                    PULL_BLOCKED_UNTIL.put(train.id(), unloadedAt + 100L);
                }

                RECONCILE.remove(train.id());
                SableTrainDriver.forgetBodyState(train);
                GhostWheelBodies.releaseAll(train);
                if (train.level().getServer() != null && train.level().getServer().isRunning()) {
                    SableTrainPersistence.persist(train);
                }
            }
        }
    }

    public static void cacheEmbodiedState(SableTrain train, ServerSubLevel sub) {
        GlobalSavedSubLevelPointer pointer = sub.getLastSerializationPointer();
        if (pointer != null) {
            train.setBodyPointer(pointer);
        }

        train.setBodyChunk(chunkOf(sub.logicalPose().position()));
        train.setLastKnownPos(sub.logicalPose().position());
        train.setConsistCache(TrainConsist.connectedSubLevels(train.level(), train.car().subLevelId()));
        if (train.extent() == null || (train.level().getGameTime() & 15L) == (train.id().hashCode() & 15)) {
            refreshExtent(train);
        }

        AnalogControllerBlockEntity controller = SableTrainDriver.controllerIn(train);
        train.setScheduleDriven(controller != null && controller.isScheduleControlled());
        double top = ConsistPower.peakTopBpt(train);
        if (top > 1.0E-4) {
            train.setGhostCruise(top);
        }
    }

    private static void refreshExtent(SableTrain train) {
        CarExtent measured = TrainLength.measure(train.level(), train);
        if (measured != null) {
            train.setExtent(measured);
        }
    }

    public static void tick(SableTrain train) {
        if (RECONCILE.contains(train.id())) {
            if (TrainSpacing.mayEmbody(train)) {
                RECONCILE.remove(train.id());
                if (embody(train) && !train.isGhost()) {
                    return;
                }
            }

            train.setResidency(SableTrain.Residency.GHOST);
        }

        if (train.isGhost()) {
            tickGhost(train);
        } else {
            ServerSubLevel sub = resolve(train);
            if (sub == null) {
                train.setResidency(SableTrain.Residency.GHOST);
            } else {
                if (VELOCITY_CHECK.remove(train.id())) {
                    logEmbodiedVelocity(train, sub);
                }

                cacheEmbodiedState(train, sub);
            }
        }
    }

    private static void tickGhost(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car != null && car.subLevelId() != null) {
            Vector3d cursor = railCursor(car);
            if (!train.isDerailed() && !train.isPhysics()) {
                boolean split = hasEmbodiedMate(train);
                if (staffLockedGhost(train)) {
                    train.setTargetSpeed(0.0);
                    train.setSpeed(0.0);
                } else {
                    SableScheduleRuntime.tick(train, null);
                    TrainStations.tickStation(train, null);
                    TrainSignals.tickSignals(train);
                    stepGhostConsist(train, split);
                }

                cursor = railCursor(car);
                boolean loaded = footprintLoaded(train.level(), cursor) && !car.carriage().railHostMissing();
                updateSplitPin(train, cursor, split);
                StationChunkTickets.update(train);
                if (loaded) {
                    snatchBody(train, cursor);
                } else {
                    TrainSpacing.release(train);
                }
            } else {
                if (RAILHEAD_HELD.remove(train.id()) | SPLIT_PINNED.remove(train.id()) != null) {
                    StationChunkTickets.release(train.id(), "ghost derailed");
                }

                ChunkPos fell = train.bodyChunk();
                if (fell != null && footprintLoaded(train.level(), fell)) {
                    snatchBody(train, cursor);
                }
            }
        }
    }

    private static boolean staffLockedGhost(SableTrain train) {
        Set<UUID> ids = new HashSet<>(train.consistCache());
        ids.add(train.car().subLevelId());
        return ExternalForceDerail.staffLockedById(train.level(), ids);
    }

    private static void stepGhostConsist(SableTrain train, boolean split) {
        long now = train.level().getGameTime();
        if (GHOST_STEPPED.getOrDefault(train.id(), -1L) != now) {
            List<SableTrain> group = GhostConsist.ghosts(train, true);
            SableTrain leader = GhostConsist.leader(group);
            if (split) {
                leader.setTargetSpeed(0.0);
            }

            leader.tickMotion();
            double[] sense = new double[group.size()];

            for (int i = 0; i < group.size(); i++) {
                SableTrain member = group.get(i);
                sense[i] = member == leader ? 1.0 : GhostConsist.senseAgainst(member, leader);
                if (member != leader) {
                    member.setTargetSpeed(leader.targetSpeed() * sense[i]);
                    member.setSpeed(leader.speed() * sense[i]);
                }
            }

            double speedIn = leader.speed();
            double ds = TrainSpacing.ghostStep(leader);
            boolean end = false;

            for (SableTrain member : group) {
                end |= TrainRailAdvance.anyReachedEnd(member.car()) || TrainRailAdvance.anyPendingPortal(member.car());
            }

            if (end) {
                ds = 0.0;
            }

            for (int i = 0; i < group.size(); i++) {
                SableTrain member = group.get(i);
                if (end) {
                    RAILHEAD_HELD.add(member.id());
                } else {
                    RAILHEAD_HELD.remove(member.id());
                }

                if (member != leader) {
                    member.setSpeed(leader.speed() * sense[i]);
                }

                GhostConsistTrace.noteStep(member, speedIn * sense[i], ds * sense[i], split, end, group.size(), member == leader ? null : leader);
                if (ds != 0.0) {
                    TrainRailAdvance.advanceRail(member.car(), ds * sense[i]);
                }

                CartMoveTrace.step(member, ds * sense[i]);
                GHOST_STEPPED.put(member.id(), now);
            }
        }
    }

    private static void updateSplitPin(SableTrain train, Vector3d cursor, boolean split) {
        UUID id = train.id();
        if (!split) {
            SPLIT_PINNED.remove(id);
            SPLIT_GAVE_UP.remove(id);
        } else {
            long now = train.level().getGameTime();
            Long since = SPLIT_PINNED.get(id);
            if (since == null) {
                if (!SPLIT_GAVE_UP.contains(id) && !bodyLost(train) && cursor != null && !inPlotGrid(train.level(), cursor)) {
                    int pinned = 0;

                    for (UUID mate : train.consistCache()) {
                        if (SPLIT_PINNED.containsKey(mate)) {
                            pinned++;
                        }
                    }

                    if (pinned < 8) {
                        SPLIT_PINNED.put(id, now);
                        LoconauticsConstants.LOGGER
                            .debug(
                                "[ghostedge] split pin taken train={} cursor={} pinnedMates={} consist={}",
                                new Object[]{LogFormat.shortId(id), v3(cursor), pinned, train.consistCache().size()}
                            );
                    }
                }
            } else {
                boolean lost = bodyLost(train);
                if (lost || now - since > 6000L) {
                    SPLIT_PINNED.remove(id);
                    SPLIT_GAVE_UP.add(id);
                    LoconauticsConstants.LOGGER
                        .debug("[ghostedge] split pin dropped train={} reason={}", LogFormat.shortId(id), lost ? "body is lost" : "dwell cap of 6000 ticks");
                }
            }
        }
    }

    public static boolean bodyLost(SableTrain train) {
        return SNATCH_MISSES.getOrDefault(train.id(), 0) >= 30;
    }

    private static void snatchBody(SableTrain train, Vector3d cursor) {
        Integer wait = SNATCH_COOLDOWN.get(train.id());
        if (wait != null) {
            if (wait > 1) {
                SNATCH_COOLDOWN.put(train.id(), wait - 1);
                return;
            }

            SNATCH_COOLDOWN.remove(train.id());
        }

        String notReady = matesNotReady(train);
        if (notReady != null) {
            GhostConsistTrace.noteSnatch(train, "a mate is not ready, " + notReady);
        } else if (!TrainSpacing.mayEmbody(train)) {
            GhostConsistTrace.noteSnatch(train, "embody gate closed");
        } else if (resolve(train) != null) {
            RECONCILE.remove(train.id());
            embody(train);
            embodyMates(train);
        } else if (pullBlocked(train)) {
            GhostConsistTrace.noteSnatch(train, "sable took the body back right after it came back");
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container != null) {
                UUID subId = train.car().subLevelId();
                HostThrashTrace.snatch(train, train.car().carriage().railHostMissing(), cursor == null ? "none" : chunkOf(cursor).toString());
                SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
                HoldingSubLevel holding = map.getHoldingSubLevel(subId);
                GlobalSavedSubLevelPointer pointer = holding != null ? holding.pointer() : train.bodyPointer();
                List<ChunkPos> candidates = candidateChunks(train, holding);
                if (candidates.isEmpty()) {
                    GhostConsistTrace.noteSnatch(train, "no candidate chunks, a body with no pointer and no chunk can never be found");
                } else {
                    short storageIndex = pointer == null ? 0 : pointer.storageIndex();
                    short subLevelIndex = pointer == null ? 0 : pointer.subLevelIndex();

                    for (ChunkPos candidate : candidates) {
                        map.snatchAndLoad(new GlobalSavedSubLevelPointer(candidate, storageIndex, subLevelIndex), subId);
                        if (resolve(train) != null) {
                            train.setBodyChunk(candidate);
                            RECONCILE.remove(train.id());
                            embody(train);
                            embodyMates(train);
                            return;
                        }
                    }

                    SNATCH_COOLDOWN.put(train.id(), 40);
                    int misses = SNATCH_MISSES.merge(train.id(), 1, Integer::sum);
                    if (SNATCH_MISS_LOGGED.add(train.id())) {
                        LoconauticsConstants.LOGGER
                            .warn(
                                "[residency] body {} of train {} was not found in any of {} candidate holding chunks, pointer {}, bodyChunk {}, holdingKnown={}, misses={}",
                                new Object[]{
                                    LogFormat.shortId(subId),
                                    LogFormat.shortId(train.id()),
                                    candidates.size(),
                                    pointer,
                                    train.bodyChunk(),
                                    holding != null,
                                    misses
                                }
                            );
                        LoconauticsConstants.LOGGER
                            .warn(
                                "[ghostedge] miss train={} candidates={} storedBounds={} grownBounds={}",
                                new Object[]{
                                    LogFormat.shortId(train.id()),
                                    candidates,
                                    holding == null ? "none" : boundsText(holding.data().bounds(), 0.0),
                                    holding == null ? "none" : boundsText(holding.data().bounds(), 1.0)
                                }
                            );
                    }

                    if (misses == 30 || misses > 30 && misses % 300 == 0) {
                        LoconauticsConstants.LOGGER
                            .warn(
                                "[residency] body {} of train {} is still missing after {} misses, {}",
                                new Object[]{LogFormat.shortId(subId), LogFormat.shortId(train.id()), misses, lostReport(train, container, subId, pointer)}
                            );
                    }
                }
            }
        }
    }

    private static String matesNotReady(SableTrain train) {
        for (SableTrain mate : GhostConsist.ghosts(train, true)) {
            if (mate != train && !bodyLost(mate)) {
                Vector3d at = railCursor(mate.car());
                if (at == null || !footprintLoaded(mate.level(), at)) {
                    return LogFormat.shortId(mate.id()) + (at == null ? " has no rail point" : " has unloaded chunks at " + v3(at));
                }
            }
        }

        return null;
    }

    private static void embodyMates(SableTrain train) {
        if (!train.isGhost()) {
            for (SableTrain mate : GhostConsist.ghosts(train, false)) {
                if (mate != train && resolve(mate) != null) {
                    RECONCILE.remove(mate.id());
                    embody(mate);
                }
            }
        }
    }

    private static String lostReport(SableTrain train, ServerSubLevelContainer container, UUID subId, GlobalSavedSubLevelPointer pointer) {
        StringBuilder out = new StringBuilder();
        MinecraftServer server = train.level().getServer();
        boolean live = false;
        if (server != null) {
            for (ServerLevel other : server.getAllLevels()) {
                ServerSubLevelContainer otherContainer = SubLevelContainer.getContainer(other);
                if (otherContainer != null && otherContainer.getSubLevel(subId) != null) {
                    out.append("live in ").append(other.dimension().location()).append(", ");
                    live = true;
                }
            }
        }

        if (!live) {
            out.append("not live in any level, ");
        }

        List<GlobalSavedSubLevelPointer> pointers = new ArrayList<>(2);
        if (pointer != null) {
            pointers.add(pointer);
        }

        if (train.bodyPointer() != null && !train.bodyPointer().equals(pointer)) {
            pointers.add(train.bodyPointer());
        }

        if (pointers.isEmpty()) {
            out.append("no saved pointer to check");
        }

        for (GlobalSavedSubLevelPointer saved : pointers) {
            SubLevelData data = container.getHoldingChunkMap().getStorage().attemptLoadSubLevel(saved.chunkPos(), saved.local());
            out.append("disk ")
                .append(saved)
                .append(data == null ? " has nothing" : (data.uuid().equals(subId) ? " holds this body" : " holds body " + LogFormat.shortId(data.uuid())))
                .append(", ");
        }

        return out.toString();
    }

    private static List<ChunkPos> candidateChunks(SableTrain train, HoldingSubLevel holding) {
        List<ChunkPos> out = new ArrayList<>();
        ChunkPos primary = holding != null ? chunkOf(new Vector3d(holding.data().pose().position())) : train.bodyChunk();
        if (primary != null) {
            out.add(primary);
        }

        if (holding != null) {
            BoundingBox3d bounds = holding.data().bounds();
            addSpan(
                out,
                Mth.floor(bounds.minX() - 1.0) >> 4,
                Mth.floor(bounds.maxX() + 1.0) >> 4,
                Mth.floor(bounds.minZ() - 1.0) >> 4,
                Mth.floor(bounds.maxZ() + 1.0) >> 4
            );
        } else if (primary != null) {
            addSpan(out, primary.x - 1, primary.x + 1, primary.z - 1, primary.z + 1);
        }

        if (train.bodyChunk() != null && !out.contains(train.bodyChunk())) {
            out.add(train.bodyChunk());
        }

        if (train.bodyPointer() != null && !out.contains(train.bodyPointer().chunkPos())) {
            out.add(train.bodyPointer().chunkPos());
        }

        return out;
    }

    private static void addSpan(List<ChunkPos> out, int minX, int maxX, int minZ, int maxZ) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                if (!out.contains(pos)) {
                    out.add(pos);
                }
            }
        }
    }

    private static ChunkPos chunkOf(Vector3dc pos) {
        return new ChunkPos((int)Math.floor(pos.x()) >> 4, (int)Math.floor(pos.z()) >> 4);
    }

    private static boolean embody(SableTrain train) {
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return true;
        }

        HostThrashTrace.embody(train, train.car().carriage().railHostMissing(), footprintLoaded(train.level(), railCursor(train.car())));
        Vector3d origin = new Vector3d();
        Quaterniond orient = SableTrainDriver.seatPose(sub, train, origin);
        boolean reseat = !train.isDerailed() && !train.isPhysics() && orient != null;
        if (reseat) {
            TrainResidency.Target target = guardTarget(train, sub, origin, orient);
            if (target == TrainResidency.Target.DEFERRED) {
                RECONCILE.add(train.id());
                return false;
            }

            if (target == TrainResidency.Target.PARKED) {
                return true;
            }
        }

        SableTrainDriver.forgetBodyState(train);
        train.beginPoseSettle(20);
        boolean seated = false;
        if (reseat) {
            Vector3d before = new Vector3d(sub.logicalPose().position());
            if (before.distance(origin) <= 1.0) {
                SableTeleportation.writePose(train.level(), sub, origin, orient);
                seated = true;
            } else {
                SableTeleportation.Result placed = SableTeleportation.teleport(sub, train.level(), origin, orient);
                if (placed.ok()) {
                    sub = placed.moved();
                    seated = true;
                } else {
                    if (retryReseat(train, placed.failure())) {
                        return false;
                    }

                    ServerSubLevel back = resolve(train);
                    if (back == null) {
                        return true;
                    }

                    sub = back;
                }
            }
        }

        if (seated) {
            SableTrainDriver.seedRailVelocity(sub, train.car(), train);
            VELOCITY_CHECK.add(train.id());
        }

        zeroLeverOfStoppedGhost(train, sub);
        GhostConsistTrace.noteEmbodied(train);
        train.setResidency(SableTrain.Residency.EMBODIED);
        EMBODIED_AT.put(train.id(), train.level().getGameTime());
        SNATCH_COOLDOWN.remove(train.id());
        SNATCH_MISSES.remove(train.id());
        SNATCH_MISS_LOGGED.remove(train.id());
        DEFER_UNTIL.remove(train.id());
        DEFER_LOGGED.remove(train.id());
        RESEAT_FAILS.remove(train.id());
        SPLIT_PINNED.remove(train.id());
        SPLIT_GAVE_UP.remove(train.id());
        RAILHEAD_HELD.remove(train.id());
        StationChunkTickets.clearCap(train.id());
        GhostWheelBodies.rebuild(train);
        cacheEmbodiedState(train, sub);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        ScheduleConductor.broadcastBlazeHats(sub);
        return true;
    }

    private static void zeroLeverOfStoppedGhost(SableTrain train, ServerSubLevel sub) {
        if (!(Math.abs(train.targetSpeed()) > 1.0E-6) || !(Math.abs(train.speed()) > 0.03)) {
            for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                if (controller.getCurrentPower() != 0 && !controller.hasUser()) {
                    GhostConsistTrace.noteLever(train, controller.getCurrentPower());
                    controller.setCurrentPower(0);
                }
            }
        }
    }

    private static boolean retryReseat(SableTrain train, String failure) {
        UUID id = train.id();
        int fails = RESEAT_FAILS.merge(id, 1, Integer::sum);
        LoconauticsConstants.LOGGER
            .warn("[ghost] {} could not be reseated off disk: {} [ghostedge] attempt {} of {}", new Object[]{LogFormat.shortId(id), failure, fails, 5});
        if (fails >= 5) {
            RESEAT_FAILS.remove(id);
            return false;
        } else {
            train.beginPoseSettle(0);
            DEFER_UNTIL.put(id, train.level().getGameTime() + 40L);
            RECONCILE.add(id);
            return true;
        }
    }

    private static TrainResidency.Target guardTarget(SableTrain train, ServerSubLevel sub, Vector3d origin, Quaterniond orient) {
        if (sub.logicalPose().position().distance(origin) <= 1.0) {
            return TrainResidency.Target.PROCEED;
        }

        ServerLevel level = train.level();
        UUID id = train.id();
        long now = level.getGameTime();
        Long until = DEFER_UNTIL.get(id);
        if (until != null && now < until) {
            return TrainResidency.Target.DEFERRED;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return TrainResidency.Target.PROCEED;
        }

        boolean blind = train.car() != null && train.car().carriage().railHostMissing();
        if (!blind && targetLoaded(level, sub, origin)) {
            DEFER_UNTIL.remove(id);
            DEFER_LOGGED.remove(id);
            return TrainResidency.Target.PROCEED;
        }

        boolean aboard = !blind && playerAboard(level, sub);
        if (!blind && !aboard) {
            DEFER_UNTIL.remove(id);
            DEFER_LOGGED.remove(id);
            Long last = REFILED_AT.get(id);
            if (last != null && now - last < 100L) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[ghostedge] refile skipped train={} body={} reason=refiled {} ticks ago and sable brought it back",
                        new Object[]{LogFormat.shortId(train.id()), LogFormat.shortId(sub), now - last}
                    );
                return TrainResidency.Target.PROCEED;
            } else if (hasEmbodiedMate(train)) {
                LoconauticsConstants.LOGGER
                    .debug("[ghostedge] refile skipped train={} body={} reason=a mate is embodied", LogFormat.shortId(train.id()), LogFormat.shortId(sub));
                return TrainResidency.Target.PROCEED;
            } else {
                int chain = SubLevelHelper.getLoadingDependencyChain(sub).size();
                if (chain > 1) {
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[ghostedge] refile skipped train={} body={} reason=loading chain of {}",
                            new Object[]{LogFormat.shortId(train.id()), LogFormat.shortId(sub), chain}
                        );
                    return TrainResidency.Target.PROCEED;
                } else {
                    refile(train, sub, container, origin, orient, now);
                    return TrainResidency.Target.PARKED;
                }
            }
        } else {
            DEFER_UNTIL.put(id, now + 20L);
            if (DEFER_LOGGED.add(id)) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[ghostedge] embody deferred train={} body={} reason={} target={} bodyAt={}",
                        new Object[]{
                            LogFormat.shortId(train.id()),
                            LogFormat.shortId(sub),
                            blind ? "the body its rail sits on is not loaded" : "a player is aboard and the cursor chunks are not loaded",
                            v3(origin),
                            v3(sub.logicalPose().position())
                        }
                    );
            }

            return TrainResidency.Target.DEFERRED;
        }
    }

    private static boolean targetLoaded(ServerLevel level, ServerSubLevel sub, Vector3d origin) {
        BoundingBox3dc box = sub.boundingBox();
        Vector3d at = sub.logicalPose().position();
        double dx = origin.x - at.x;
        double dz = origin.z - at.z;
        int minX = Mth.floor(box.minX() + dx - 1.0) >> 4;
        int maxX = Mth.floor(box.maxX() + dx + 1.0) >> 4;
        int minZ = Mth.floor(box.minZ() + dz - 1.0) >> 4;
        int maxZ = Mth.floor(box.maxZ() + dz + 1.0) >> 4;

        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean playerAboard(ServerLevel level, ServerSubLevel sub) {
        BoundingBox3dc box = sub.boundingBox();
        return !level.getPlayers(
                player -> {
                    Vec3 c = player.getBoundingBox().getCenter();
                    return c.x >= box.minX() - 1.0
                        && c.x <= box.maxX() + 1.0
                        && c.y >= box.minY() - 1.0
                        && c.y <= box.maxY() + 1.0
                        && c.z >= box.minZ() - 1.0
                        && c.z <= box.maxZ() + 1.0;
                }
            )
            .isEmpty();
    }

    private static void refile(SableTrain train, ServerSubLevel sub, ServerSubLevelContainer container, Vector3d origin, Quaterniond orient, long now) {
        UUID subId = sub.getUniqueId();
        Vector3d from = new Vector3d(sub.logicalPose().position());
        ChunkPos filing = chunkOf(origin);
        GhostWheelBodies.releaseAll(train);
        SableTrainDriver.forgetBodyState(train);
        SableTeleportation.writePose(train.level(), sub, origin, orient);
        sub.updateBoundingBox();
        String bounds = boundsText(sub.boundingBox(), 1.0);
        SubLevelHoldingChunkMap map = container.getHoldingChunkMap();
        map.moveToUnloaded(sub, filing);
        train.setBodyChunk(filing);
        HoldingSubLevel filed = map.getHoldingSubLevel(subId);
        if (filed != null && filed.pointer() != null) {
            train.setBodyPointer(filed.pointer());
        }

        REFILED_AT.put(train.id(), now);
        LoconauticsConstants.LOGGER
            .debug(
                "[ghostedge] stale return refiled train={} body={} from={} to={} chunk={} grownBounds={}",
                new Object[]{LogFormat.shortId(train.id()), LogFormat.shortId(subId), v3(from), v3(origin), filing, bounds}
            );
        SableTrainPersistence.persist(train);
    }

    private static void logEmbodiedVelocity(SableTrain train, ServerSubLevel sub) {
        RigidBodyHandle handle = RigidBodyHandle.of(sub);
        RailFrame frame = RailFrame.of(sub, train);
        double raw = handle == null ? Double.NaN : handle.getLinearVelocity(new Vector3d()).length();
        LoconauticsConstants.LOGGER
            .debug(
                "[ghostedge] velocity train={} mode={} bodySpeed={} alongRail={} railSpeed={} blocks per second",
                new Object[]{LogFormat.shortId(train.id()), frame.mode(), f3(raw), f3(frame.speed()), f3(train.speed() * 20.0)}
            );
    }

    private static void logUnload(SableTrain train, ServerSubLevel sub) {
        MinecraftServer server = train.level().getServer();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (server != null && server.isRunning() && container != null) {
            HoldingSubLevel holding = container.getHoldingChunkMap().getHoldingSubLevel(sub.getUniqueId());
            LoconauticsConstants.LOGGER
                .debug(
                    "[ghostedge] unload train={} body={} bodyChunk={} pointer={} filed={} storedBounds={} candidates={}",
                    new Object[]{
                        LogFormat.shortId(train.id()),
                        LogFormat.shortId(sub),
                        train.bodyChunk(),
                        train.bodyPointer(),
                        holding != null,
                        holding == null ? "none" : boundsText(holding.data().bounds(), 0.0),
                        candidateChunks(train, holding)
                    }
                );
        }
    }

    private static String boundsText(BoundingBox3dc box, double grow) {
        return String.format(Locale.ROOT, "x %.1f to %.1f, z %.1f to %.1f", box.minX() - grow, box.maxX() + grow, box.minZ() - grow, box.maxZ() + grow);
    }

    private static String v3(Vector3dc v) {
        return String.format(Locale.ROOT, "(%.1f,%.1f,%.1f)", v.x(), v.y(), v.z());
    }

    private static String f3(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static boolean hasEmbodiedMate(SableTrain train) {
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }

        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && !other.isGhost()) {
                return true;
            }
        }

        return false;
    }

    public static boolean embodiedWithin(SableTrain train, long ticks) {
        return !train.isGhost() && train.level().getGameTime() - EMBODIED_AT.getOrDefault(train.id(), -4611686018427387904L) < ticks;
    }

    public static boolean pullBlocked(SableTrain train) {
        Long until = PULL_BLOCKED_UNTIL.get(train.id());
        return until != null && train.level().getGameTime() < until;
    }

    public static Set<ChunkPos> chainChunks(SableTrain train) {
        Set<ChunkPos> out = new HashSet<>();
        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return out;
        }

        for (ServerSubLevel body : SubLevelHelper.getLoadingDependencyChain(sub)) {
            BoundingBox3dc box = body.boundingBox();
            int minX = Mth.floor(box.minX() - 1.0) >> 4;
            int maxX = Mth.floor(box.maxX() + 1.0) >> 4;
            int minZ = Mth.floor(box.minZ() - 1.0) >> 4;
            int maxZ = Mth.floor(box.maxZ() + 1.0) >> 4;

            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    out.add(new ChunkPos(cx, cz));
                }
            }
        }

        return out;
    }

    public static boolean holdForGhostedConsist(SableTrain train) {
        if (train.isGhost()) {
            return false;
        }

        TrainSpacing.limitLoaded(train);
        Set<UUID> consist = train.consistCache();
        if (consist.size() <= 1) {
            return false;
        }

        long now = train.level().getGameTime();

        for (UUID id : consist) {
            SableTrain other = SableTrainRegistry.bySubLevel(id);
            if (other != null && other != train && (other.isGhost() || now - UNLOADED_AT.getOrDefault(other.id(), -4611686018427387904L) < 60L)) {
                train.setTargetSpeed(0.0);
                return true;
            }
        }

        return false;
    }

    public static String forceGhost(SableTrain train) {
        if (train.isGhost()) {
            return LogFormat.shortId(train.id()) + " is already a ghost";
        }

        ServerSubLevel sub = resolve(train);
        if (sub == null) {
            return LogFormat.shortId(train.id()) + " has no body to unload";
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return "no sub level container in " + train.level().dimension().location();
        }

        cacheEmbodiedState(train, sub);
        container.getHoldingChunkMap().moveToUnloaded(sub, chunkOf(sub.logicalPose().position()));
        return LogFormat.shortId(train.id()) + " body pushed to the holding chunk, it should be a ghost now";
    }

    public static BlockPos ghostHoldAnchor(SableTrain train) {
        if (train.isGhost() && train.car() != null && (RAILHEAD_HELD.contains(train.id()) || SPLIT_PINNED.containsKey(train.id()))) {
            Vector3d cursor = railCursor(train.car());
            return cursor != null && !inPlotGrid(train.level(), cursor) ? BlockPos.containing(cursor.x, cursor.y, cursor.z) : null;
        } else {
            return null;
        }
    }

    public static boolean inPlotGrid(ServerLevel level, Vector3d pos) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container != null && container.inBounds(pos);
    }

    public static Vector3d railCursor(SableTrain.Car car) {
        Vector3d center = new Vector3d();
        return SableTrainDriver.bodyPose(car, center) == null ? null : center;
    }

    public static boolean footprintLoaded(ServerLevel level, Vector3d pos) {
        return level != null && pos != null ? footprintLoaded(level, chunkOf(pos)) : false;
    }

    private static boolean footprintLoaded(ServerLevel level, ChunkPos center) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null && container.inBounds(center)) {
            return false;
        }

        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, cx, cz)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static void forget(Set<UUID> live) {
        RECONCILE.retainAll(live);
        SNATCH_COOLDOWN.keySet().retainAll(live);
        SNATCH_MISSES.keySet().retainAll(live);
        SNATCH_MISS_LOGGED.retainAll(live);
        REFILED_AT.keySet().retainAll(live);
        DEFER_UNTIL.keySet().retainAll(live);
        DEFER_LOGGED.retainAll(live);
        VELOCITY_CHECK.retainAll(live);
        SPLIT_PINNED.keySet().retainAll(live);
        SPLIT_GAVE_UP.retainAll(live);
        RESEAT_FAILS.keySet().retainAll(live);
        RAILHEAD_HELD.retainAll(live);
        UNLOADED_AT.keySet().retainAll(live);
        GHOST_STEPPED.keySet().retainAll(live);
        EMBODIED_AT.keySet().retainAll(live);
        PULL_BLOCKED_UNTIL.keySet().retainAll(live);
        TrainSpacing.forget(live);
    }

    public static String describe(SableTrain train) {
        return LogFormat.shortId(train.id())
            + " "
            + train.residency()
            + " pointer="
            + train.bodyPointer()
            + " bodyChunk="
            + train.bodyChunk()
            + " consist="
            + train.consistCache().size()
            + " splitPin="
            + SPLIT_PINNED.containsKey(train.id())
            + " scheduleDriven="
            + train.isScheduleDriven()
            + " cruise="
            + String.format("%.3f", train.ghostCruise());
    }

    public static String traceFlags(SableTrain train) {
        UUID id = train.id();
        Long deferred = DEFER_UNTIL.get(id);
        return String.format(
            Locale.ROOT,
            "railheadHeld=%s splitPin=%s gaveUp=%s reconcile=%s snatchMisses=%d lost=%s deferTicksLeft=%d reseatFails=%d",
            RAILHEAD_HELD.contains(id),
            SPLIT_PINNED.containsKey(id),
            SPLIT_GAVE_UP.contains(id),
            RECONCILE.contains(id),
            SNATCH_MISSES.getOrDefault(id, 0),
            bodyLost(train),
            deferred == null ? 0L : Math.max(0L, deferred - train.level().getGameTime()),
            RESEAT_FAILS.getOrDefault(id, 0)
        );
    }

    private static ServerSubLevel resolve(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container == null) {
            return null;
        }

        UUID subId = train.car() == null ? null : train.car().subLevelId();
        return subId != null && container.getSubLevel(subId) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
    }

    private enum Target {
        PROCEED,
        PARKED,
        DEFERRED;
    }
}
