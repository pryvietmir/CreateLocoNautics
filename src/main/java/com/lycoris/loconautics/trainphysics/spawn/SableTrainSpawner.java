package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.network.RejectedPacketLog;
import net.minecraft.world.item.Item;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.SubLevelAssemblyUtil;
import com.lycoris.loconautics.common.sable.SableTeleportation;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.knuckle.KnuckleBlockEntity;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.content.schedule.ScheduleConductor;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.network.packets.train.SableTrainSyncPacket;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.drive.SableTrainDriver;
import com.lycoris.loconautics.trainphysics.drive.SeatBlend;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.portal.TrainPortalCrossing;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.rail.RailEdgeSearch;
import com.lycoris.loconautics.trainphysics.rail.RailFollower;
import com.lycoris.loconautics.trainphysics.rail.RailPick;
import com.lycoris.loconautics.trainphysics.rail.RailSeams;
import com.lycoris.loconautics.trainphysics.state.SableTrainPersistence;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.station.StationLastStyle;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.lycoris.loconautics.trainphysics.sublevel.SablePlotFrame;
import com.lycoris.loconautics.trainphysics.teleport.TeleportStrands;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.IPortalListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphHelper;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.GatherResult;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.GatherResult.State;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimMathUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableTrainSpawner {
    private static final double REACH = 12.0;
    private static final int MAX_BLOCKS = 4096;
    static UUID lastTrainId;
    private static final double GRID_ALIGN_TOLERANCE = Math.toRadians(1.5);
    private static final double DERAIL_RAY_RADIUS = 2.5;
    private static final double RELOCATE_REACH = 24.0;
    private static final int CHORD_STEPS = 8;
    private static final double CHORD_TOLERANCE = 0.01;
    private static final double HEADING_PROBE = 0.1;
    static final int RERAIL_COOLDOWN_TICKS = 60;
    static final double RAILHEAD_PROBE = 1.0;
    static final double RERAIL_MAX_LATERAL = 1.25;
    private static final double RERAIL_MAX_DROP = 1.5;
    private static final double RERAIL_MAX_YAW_DEGREES = 45.0;

    private SableTrainSpawner() {
    }

    public static int spawn(ServerPlayer player, double speed, boolean physics) {
        if (!(Boolean)LoconauticsConfig.ENABLE_SABLE_TRAINS.get()) {
            msg(player, "Sable physics mode is disabled in the config (foundation.enableSableTrains)");
            return 0;
        }

        ServerLevel level = player.serverLevel();
        SableTrain.Car car = buildCar(player, level, null);
        if (car == null) {
            return 0;
        }

        UUID id = UUID.randomUUID();
        SableTrain train = new SableTrain(id, level, car, physics);
        train.setOwner(player.getUUID());
        train.setTargetSpeed(speed);
        SableTrainRegistry.register(train);
        nameCar(level, car, defaultTrainName());
        SubLevelSplitWatcher.ensureObserver(level);
        lastTrainId = id;
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        msg(
            player,
            String.format(
                "%s cart spawned speed=%.2f, id %s, join carts together with steel cable", physics ? "PHYSICS" : "pinned", speed, id.toString().substring(0, 8)
            )
        );
        return 1;
    }

    @Nullable
    public static SableTrain assembleAt(ServerLevel level, BlockPos anchor, Vec3 railDir, @Nullable UUID owner, @Nullable String name) {
        SableTrainSpawner.TrackHit track = findRailBelow(level, anchor, railDir);
        if (track == null) {
            LoconauticsConstants.LOGGER.debug("[sabletrain] assemble ABORT: no rail under {}", anchor);
            return null;
        }

        int railY = track.railY();
        GatherResult gather = SubLevelAssemblyHelper.gatherConnectedBlocks(
            anchor, level, 4096, (from, fromState, cand, candState, dir) -> cand.getY() > railY && !(candState.getBlock() instanceof ITrackBlock)
        );
        if (gather.assemblyState() == State.SUCCESS && gather.blocks() != null) {
            if (!hasBogey(level, gather.boundingBox(), railY)) {
                LoconauticsConstants.LOGGER.debug("[sabletrain] assemble ABORT: no bogey in the {} blocks gathered at {}", gather.blocks().size(), anchor);
                return null;
            }

            SableTrain.Car car = seatCar(level, gather.blocks(), gather.boundingBox(), railDir, anchor);
            if (car == null) {
                LoconauticsConstants.LOGGER.debug("[sabletrain] assemble ABORT: seatCar refused {} blocks at {}", gather.blocks().size(), anchor);
                return null;
            }

            SableTrain train = new SableTrain(UUID.randomUUID(), level, car, false);
            if (owner != null) {
                train.setOwner(owner);
            }

            SableTrainRegistry.register(train);
            nameCar(level, car, name != null && !name.isBlank() ? name : defaultTrainName());
            SubLevelSplitWatcher.ensureObserver(level);
            lastTrainId = train.id();
            SableTrainPersistence.persist(train);
            SableTrainSyncPacket.broadcast(train);
            return train;
        } else {
            LoconauticsConstants.LOGGER.debug("[sabletrain] assemble ABORT: gather at {} came back {}", anchor, gather.assemblyState());
            return null;
        }
    }

    private static SableTrain.Car buildCar(ServerPlayer player, ServerLevel level, Vec3 dirHint) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(12.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
        if (hit.getType() == Type.MISS) {
            msg(player, "look at the cart you built (on a rail), within 12 blocks");
            return null;
        }

        BlockPos origin = hit.getBlockPos();
        if (level.getBlockState(origin).getBlock() instanceof ITrackBlock) {
            msg(player, "look at the CART, not the rail itself");
            return null;
        }

        Vec3 railDir = dirHint != null ? dirHint : view;
        SableTrainSpawner.TrackHit track = findRailBelow(level, origin, railDir);
        if (track == null) {
            msg(player, "no Create rail found under/near the cart");
            return null;
        }

        int railY = track.railY();
        GatherResult gather = SubLevelAssemblyHelper.gatherConnectedBlocks(
            origin, level, 4096, (from, fromState, cand, candState, dir) -> cand.getY() > railY && !(candState.getBlock() instanceof ITrackBlock)
        );
        if (gather.assemblyState() == State.SUCCESS && gather.blocks() != null) {
            Set<BlockPos> blocks = gather.blocks();
            BoundingBox3i bounds = gather.boundingBox();
            if (!hasBogey(level, bounds, railY)) {
                msg(player, "the cart needs a Create train bogey (the wheels) to be assembled into a train");
                return null;
            } else {
                SableTrain.Car car = seatCar(level, blocks, bounds, railDir, origin);
                if (car == null) {
                    msg(player, "couldn't seat the rail body (no rail/graph under the cart?)");
                    return null;
                } else {
                    return car;
                }
            }
        } else {
            msg(player, "couldn't gather a cart above the rail (" + gather.assemblyState() + "), make sure cart blocks sit above the track");
            return null;
        }
    }

    static boolean hasRail(ServerLevel level, Set<BlockPos> blocks, BoundingBox3i bounds, Vec3 railDir, BlockPos preferredAnchor) {
        return railFor(level, bounds, bodyAnchorOf(blocks, preferredAnchor), railDir, bogeysIn(level, blocks)) != null;
    }

    private static BlockPos bodyAnchorOf(Set<BlockPos> blocks, BlockPos preferredAnchor) {
        return preferredAnchor != null && blocks.contains(preferredAnchor) ? preferredAnchor : blocks.iterator().next();
    }

    private static List<BlockPos> bogeysIn(ServerLevel level, Set<BlockPos> blocks) {
        List<BlockPos> bogeyPositions = new ArrayList<>();

        for (BlockPos p : blocks) {
            if (level.getBlockState(p).getBlock() instanceof AbstractBogeyBlock) {
                bogeyPositions.add(p.immutable());
            }
        }

        return bogeyPositions;
    }

    private static RailCarriage railFor(ServerLevel level, BoundingBox3i bounds, BlockPos bodyAnchor, Vec3 railDir, List<BlockPos> bogeyPositions) {
        int cx = (bounds.minX() + bounds.maxX()) / 2;
        int cz = (bounds.minZ() + bounds.maxZ()) / 2;
        SableTrainSpawner.TrackHit track = findRailBelow(level, new BlockPos(cx, bounds.minY(), cz), railDir);
        if (track == null) {
            track = findRailBelow(level, bodyAnchor, railDir);
        }

        if (track == null) {
            for (BlockPos bp : bogeyPositions) {
                track = findRailBelow(level, bp, railDir, WheelColliders.isUpsideDown(level.getBlockState(bp)));
                if (track != null) {
                    break;
                }
            }
        }

        if (track == null) {
            return null;
        }

        double spacing = Math.max(1.0, Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()));
        return RailCarriage.at(track.location(), track.upNormal(), spacing, 0.0);
    }

    static SableTrain.Car seatCar(ServerLevel level, Set<BlockPos> blocks, BoundingBox3i bounds, Vec3 railDir, BlockPos preferredAnchor) {
        List<BlockPos> bogeyPositions = bogeysIn(level, blocks);
        bogeyPositions.sort(Comparator.comparingDouble(p -> p.getX() * railDir.x + p.getZ() * railDir.z));
        Set<BlockPos> hangingBogeys = new HashSet<>();

        for (BlockPos p : bogeyPositions) {
            if (WheelColliders.isUpsideDown(level.getBlockState(p))) {
                hangingBogeys.add(p);
            }
        }

        BlockPos bodyAnchor = bodyAnchorOf(blocks, preferredAnchor);
        RailCarriage carriage = railFor(level, bounds, bodyAnchor, railDir, bogeyPositions);
        if (carriage == null) {
            return null;
        }

        ServerSubLevel sub = SubLevelAssemblyUtil.assemble(level, bodyAnchor, blocks, bounds);
        if (sub == null) {
            return null;
        }

        StationAssembly.moveGlueIntoSubLevel(level, blocks, bounds, sub, bodyAnchor);
        List<SableTrain.Bogey> bogeys = new ArrayList<>();

        for (BlockPos bp : bogeyPositions) {
            SableTrainSpawner.TrackHit bogeyTrack = findRailBelow(level, bp, railDir, hangingBogeys.contains(bp));
            if (bogeyTrack == null) {
                LoconauticsConstants.LOGGER.warn("[sabletrain] seat: NO rail found under bogey at {}, this bogey will not follow the track", bp);
            } else {
                RailCarriage bogeyRail = seatFollowerUnder(bogeyTrack, worldOfSeat(bogeyTrack, bp));
                if (bogeyRail != null) {
                    Vector3d seated = bogeyRail.center();
                    bogeys.add(new SableTrain.Bogey(bp.subtract(bodyAnchor), bogeyRail));
                }
            }
        }

        sortBogeysAlongReference(bogeys, carriage.referenceForward());
        Vec3 leadW = carriage.leadingPos();
        Vec3 trailW = carriage.trailingPos();
        Vec3 localLeadVec = sub.logicalPose().transformPositionInverse(leadW);
        Vec3 localTrailVec = sub.logicalPose().transformPositionInverse(trailW);
        Vector3dc localLead = new Vector3d(localLeadVec.x, localLeadVec.y, localLeadVec.z);
        Vector3dc localTrail = new Vector3d(localTrailVec.x, localTrailVec.y, localTrailVec.z);
        return new SableTrain.Car(sub.getUniqueId(), carriage, bogeys, localLead, localTrail);
    }

    public static int setSpeedAll(double speed) {
        int n = 0;

        for (SableTrain train : SableTrainRegistry.all()) {
            train.setTargetSpeed(speed);
            n++;
        }

        return n;
    }

    public static int clear() {
        int n = 0;

        for (SableTrain train : SableTrainRegistry.all()) {
            remove(train);
            n++;
        }

        lastTrainId = null;
        RerailApproach.clearAll();
        return n;
    }

    public static void remove(SableTrain train) {
        GhostWheelBodies.releaseAll(train);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null) {
            SableTrain.Car car = train.car();
            if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
            }
        }

        SableTrainRegistry.remove(train.id());
        SableTrainPersistence.unpersist(train.level().getServer(), train.id());
        SableTrainSyncPacket.broadcastRemoval(train.car().subLevelId());
    }

    public static SableTrain findParkedTrain(ServerLevel level, GlobalStation station) {
        if (station == null) {
            return null;
        }

        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() == level && !train.isDerailed() && isParkedAt(train, station)) {
                return train;
            }
        }

        return null;
    }

    private static SableTrain findStandingTrain(ServerLevel level, GlobalStation station) {
        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() == level && !train.isGhost() && !train.isDerailed() && TrainStations.isStationInRange(train, station, 3.0, 0.0)) {
                return train;
            }
        }

        return null;
    }

    public static SableTrain findApproachingTrain(ServerLevel level, GlobalStation station) {
        if (station == null) {
            return null;
        }

        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() == level && !train.isDerailed() && approachDistance(train, station) < Double.MAX_VALUE) {
                return train;
            }
        }

        return null;
    }

    public static double approachDistance(SableTrain train, GlobalStation station) {
        if (train != null && station != null) {
            if (train.stationState() == SableTrain.StationState.STOPPING && sameStation(train.currentStation(), station)) {
                return train.distanceToStation();
            } else {
                return sameStation(train.sightedStation(), station) ? train.sightedDistance() : Double.MAX_VALUE;
            }
        } else {
            return Double.MAX_VALUE;
        }
    }

    private static boolean sameStation(GlobalStation a, GlobalStation b) {
        return a != null && b != null && (a == b || a.id != null && a.id.equals(b.id));
    }

    public static String defaultTrainName() {
        return CreateLang.translateDirect("train.unnamed", new Object[0]).getString();
    }

    public static void nameCar(ServerLevel level, SableTrain.Car car, String name) {
        ServerSubLevel sub = SubLevelData.resolve(level, car.subLevelId());
        if (sub != null) {
            sub.setName(name);
        }
    }

    public static String trainName(ServerLevel level, SableTrain train) {
        if (train == null) {
            return "";
        }

        ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
        if (sub != null && sub.getName() != null) {
            train.setName(sub.getName());
        }

        return train.name();
    }

    public static List<Integer> consistCarriageIcons(ServerLevel level, SableTrain front) {
        List<Integer> out = new ArrayList<>();
        if (front == null) {
            return out;
        }

        out.add(spanOf(front));
        UUID frontId = front.car().subLevelId();

        for (ServerSubLevel s : TrainConsist.subLevelsBehind(level, frontId)) {
            SableTrain t = SableTrainRegistry.bySubLevel(s.getUniqueId());
            if (t != null) {
                out.add(spanOf(t));
            }
        }

        return out;
    }

    private static int spanOf(SableTrain train) {
        return train.isTrackless() ? 1 : (int)Math.max(1L, Math.round(train.car().carriage().bogeySpacing()));
    }

    public static void setTrainNameFromStation(ServerPlayer player, BlockPos stationPos, String name) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(stationPos) instanceof StationBlockEntity station) {
            SableTrain front = findParkedTrain(level, station.getStation());
            if (front != null && name != null && !name.isBlank()) {
                nameConsist(level, front, name);
            }
        }
    }

    public static void setTrainStyleFromStation(ServerPlayer player, BlockPos stationPos, int mapColorIndex, String iconType) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(stationPos) instanceof StationBlockEntity station) {
            SableTrain front = findParkedTrain(level, station.getStation());
            if (front != null) {
                Set<UUID> consist = TrainConsist.connectedSubLevels(level, front.car().subLevelId());

                for (SableTrain train : SableTrainRegistry.all()) {
                    if (train.level() == level && consist.contains(train.car().subLevelId())) {
                        train.setMapColorIndex(mapColorIndex);
                        train.setIconType(iconType);
                    }
                }
            }
        }
    }

    public static void nameConsist(ServerLevel level, SableTrain front, String base) {
        UUID frontId = front.car().subLevelId();
        ServerSubLevel frontSub = TrainConsist.resolveFront(level, frontId);
        if (frontSub != null) {
            List<ServerSubLevel> behind = TrainConsist.subLevelsBehind(level, frontId);
            frontSub.setName(base);

            for (int i = 0; i < behind.size(); i++) {
                behind.get(i).setName(base.isEmpty() ? base : base + " Carriage " + (i + 2));
            }

            LoconauticsConstants.LOGGER.debug("[sabletrain] named consist '{}' ({} car(s)) from station", base, behind.size() + 1);
        }
    }

    public static boolean disassembleFromStation(ServerPlayer player, BlockPos stationPos) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(stationPos) instanceof StationBlockEntity station) {
            GlobalStation global = station.getStation();
            if (global == null) {
                return false;
            }

            SableTrain parked = findParkedTrain(level, global);
            boolean latched = parked != null;
            if (parked == null) {
                parked = findStandingTrain(level, global);
            }

            if (parked == null) {
                msg(player, "no parked train found at this station");
                return false;
            }

            if (!TrainStations.parkOnMark(parked, global) && !latched) {
                msg(player, "the train has to be stopped on the station mark to disassemble");
                return false;
            }

            Set<UUID> consist = TrainConsist.connectedSubLevels(level, parked.car().subLevelId());

            for (SableTrain train : SableTrainRegistry.all()) {
                if (train.level() == level && !train.isDerailed() && consist.contains(train.car().subLevelId())) {
                    ServerSubLevel sub = SubLevelData.resolve(level, train.car().subLevelId());
                    if (sub != null && !isGridAligned(train, sub)) {
                        msg(player, "the train has to be level and square to the grid to disassemble");
                        return false;
                    }
                }
            }

            String parkedName = trainName(level, parked);
            if (!parkedName.isBlank()) {
                station.lastDisassembledTrainName = Component.literal(parkedName);
            }

            station.lastDisassembledMapColorIndex = parked.mapColorIndex();
            ((StationLastStyle)station).loconautics$setLastIcon(parked.iconType());
            int disassembled = 0;

            for (SableTrain train : new ArrayList<>(SableTrainRegistry.all())) {
                if (train.level() == level
                    && !train.isDerailed()
                    && consist.contains(train.car().subLevelId())
                    && disassembleSableTrain(train, stationPos, player)) {
                    disassembled++;
                }
            }

            LoconauticsConstants.LOGGER.debug("[sabletrain] station at {} disassembled {} Sable car(s) of the parked consist", stationPos, disassembled);
            return disassembled > 0;
        } else {
            return false;
        }
    }

    private static boolean isParkedAt(SableTrain train, GlobalStation station) {
        if (train.stationState() != SableTrain.StationState.STOPPED) {
            return false;
        }

        GlobalStation at = train.atStation();
        return at != null && (at == station || at.id != null && at.id.equals(station.id));
    }

    public static boolean disassembleSableTrain(SableTrain train) {
        return disassembleSableTrain(train, null, null);
    }

    public static boolean disassembleSableTrain(SableTrain train, @Nullable BlockPos stationPos, @Nullable ServerPlayer sender) {
        ServerLevel level = train.level();
        UUID subId = train.car().subLevelId();
        ServerSubLevel sub = SubLevelData.resolve(level, subId);
        if (sub == null) {
            return false;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        ArticulationLinks.release(level, sub);
        squareUpForDisassembly(level, train, sub, stationPos);
        sub = SubLevelData.resolve(level, subId);
        if (sub == null) {
            return false;
        }

        GhostWheelBodies.releaseAll(train);
        BlockPos anchor = sub.getPlot().getCenterBlock();
        ServerSubLevel host = train.car().carriage() == null ? null : train.car().carriage().activeSub();
        Vec3 drawn = sub.logicalPose().transformPosition(Vec3.atCenterOf(anchor));
        BlockPos goal = BlockPos.containing(host == null ? drawn : SablePlotFrame.toLocalPos(host, drawn));
        double yaw = SimMathUtils.getClosestYaw(orientInHost(train, sub));
        int turns = -Mth.floor(yaw / (Math.PI / 2) + 0.5);
        Rotation rotation = SimAssemblyHelper.rotationFrom90DegRots(turns);
        ScheduleConductor.returnScheduleOnDisassembly(level, train.id(), sub, drawn, sender);
        SimAssemblyHelper.disassembleSubLevel(level, sub, anchor, goal, rotation, true);
        SableTrainRegistry.remove(train.id());
        RerailApproach.forget(train.id());
        SableTrainPersistence.unpersist(level.getServer(), train.id());
        SableTrainSyncPacket.broadcastRemoval(subId);
        if (container.getSubLevel(subId) == sub) {
            container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
        }

        LoconauticsConstants.LOGGER
            .debug(
                "[sabletrain] disassembled train {} (sub-level {}) at {} into {}",
                new Object[]{train.id(), subId, goal, host == null ? "the world" : host.getUniqueId()}
            );
        return true;
    }

    private static Quaterniond orientInHost(SableTrain train, ServerSubLevel sub) {
        ServerSubLevel host = train.car().carriage() == null ? null : train.car().carriage().activeSub();
        return host == null
            ? new Quaterniond(sub.logicalPose().orientation())
            : new Quaterniond(host.logicalPose().orientation()).conjugate().mul(sub.logicalPose().orientation());
    }

    private static boolean isGridAligned(SableTrain train, ServerSubLevel sub) {
        Quaterniond orient = orientInHost(train, sub);
        if (orient.transform(new Vector3d(0.0, 1.0, 0.0)).y < Math.cos(GRID_ALIGN_TOLERANCE)) {
            return false;
        }

        double quarter = Math.PI / 2;
        double yaw = SimMathUtils.getClosestYaw(orient);
        return Math.abs(yaw - Math.round(yaw / quarter) * quarter) < GRID_ALIGN_TOLERANCE;
    }

    private static void squareUpForDisassembly(ServerLevel level, SableTrain train, ServerSubLevel sub, @Nullable BlockPos stationPos) {
        Vector3d anchor = bogeyAnchorWorld(sub, train.car());
        Vec3 fwd = currentFacing(sub, train.car());
        SableTrainSpawner.TrackHit hit = findRailBelow(level, BlockPos.containing(anchor.x, anchor.y, anchor.z), fwd);
        if (hit == null && stationPos != null) {
            hit = findRailBelow(level, stationPos, fwd);
        }

        if (hit != null) {
            seatCarAt(level, train, hit.location(), hit.upNormal(), fwd, true, true);
        }
    }

    public static SableTrain pickLookedAtTrain(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        SableTrain best = null;
        double bestPerp = 2.5;

        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.level() == level
                && train.car().subLevelId() != null
                && container.getSubLevel(train.car().subLevelId()) instanceof ServerSubLevel sub
                && !sub.isRemoved()) {
                Vector3dc p = sub.logicalPose().position();
                Vec3 center = new Vec3(p.x(), p.y(), p.z());
                double along = center.subtract(eye).dot(look);
                if (!(along < 0.0) && !(along > 12.0)) {
                    double perp = eye.add(look.scale(along)).distanceTo(center);
                    if (perp < bestPerp) {
                        best = train;
                        bestPerp = perp;
                    }
                }
            }
        }

        return best;
    }

    @Nullable
    public static TrackGraphLocation lookedAtTrack(ServerPlayer player) {
        SableTrainSpawner.RailSeat seat = lookedAtRailSeat(player);
        return seat == null ? null : seat.location();
    }

    @Nullable
    public static SableTrainSpawner.RailSeat lookedAtRailSeat(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(12.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, player));
        if (hit.getType() == Type.MISS) {
            return null;
        } else {
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ITrackBlock track) {
                Pair var11 = track.getNearestTrackAxis(level, pos, state, look);
                TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(level, pos, (AxisDirection)var11.getSecond(), (Vec3)var11.getFirst());
                return loc != null && loc.graph != null ? new SableTrainSpawner.RailSeat(loc, track.getUpNormal(level, pos, state), look) : null;
            } else {
                return null;
            }
        }
    }

    public static int derailLookedAt(ServerPlayer player) {
        SableTrain best = pickLookedAtTrain(player);
        if (best == null) {
            msg(player, "look at a Sable cart within 12 blocks to derail it");
            return 0;
        } else if (best.isDerailed()) {
            msg(player, "that cart is already off the rails");
            return 0;
        } else {
            best.setDerailed(true);
            SableTrainPersistence.persist(best);
            SableTrainSyncPacket.broadcast(best);
            msg(player, "derailed cart " + best.id().toString().substring(0, 8) + ", released from the rail");
            return 1;
        }
    }

    public static boolean withinRelocateReach(ServerPlayer player, BlockPos pos) {
        return Sable.HELPER.distanceSquaredWithSubLevels(player.level(), player.getEyePosition(), Vec3.atCenterOf(pos)) <= 576.0;
    }

    private static void refuseRelocation(boolean speak, ServerPlayer player, String reason, @Nullable String say) {
        if (speak) {
            LoconauticsConstants.LOGGER.debug("[sabletrain] relocate ABORT: {}", reason);
            if (say != null) {
                msg(player, say);
            }
        }
    }

    @Nullable
    private static SableTrainSpawner.RelocationPlan planRelocation(ServerPlayer player, UUID subLevelId, RailPick pick, Vec3 lookAngle, boolean speak) {
        if (!(Boolean)LoconauticsConfig.WRENCH_RELOCATION_ENABLED.get()) {
            return null;
        }

        BlockPos pos = pick.pos();
        ServerLevel level = player.serverLevel();
        SableTrain train = SableTrainRegistry.bySubLevel(subLevelId);
        if (train == null || train.level() != level) {
            refuseRelocation(speak, player, "train for sub-level " + subLevelId + " not found here", null);
            return null;
        }

        if ((Boolean)LoconauticsConfig.WRENCH_RELOCATION_DERAILED_ONLY.get() && !train.isDerailed()) {
            return null;
        }

        if (!withinRelocateReach(player, pos)) {
            refuseRelocation(speak, player, pos + " out of reach", null);
            return null;
        }

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ITrackBlock track) {
            Vec3 var19 = lookAngle;
            ServerSubLevel railHost = RailSeams.hostAt(level, pos) instanceof ServerSubLevel s ? s : null;
            if (railHost != null) {
                var19 = SablePlotFrame.toLocalDir(railHost, lookAngle);
            }

            TrackGraphLocation loc = pick.graphLocation(level, track, state, var19);
            if (loc != null && loc.graph != null) {
                Vec3 upNormal = track.getUpNormal(level, pos, state);
                List<SableTrain> cars = chainCars(level, subLevelId);
                if (cars.isEmpty()) {
                    refuseRelocation(speak, player, "no car of the train is on the rails", "that train has no car on the rails to relocate");
                    return null;
                }

                int coupled = TrainConsist.orderedChain(level, subLevelId).size();
                if (cars.size() < coupled) {
                    refuseRelocation(
                        speak,
                        player,
                        coupled - cars.size() + " coupled car(s) have no rail seat",
                        coupled - cars.size() + " coupled car(s) are not on the rails, the whole train has to be seated first"
                    );
                    return null;
                }

                for (SableTrain car : cars) {
                    UUID carId = car.car().subLevelId();
                    if (SableTeleportation.isInFlight(carId) || TrainPortalCrossing.straddle(carId) != null) {
                        refuseRelocation(speak, player, "the train is crossing a portal", "the train is crossing a portal, relocate it once it is across");
                        return null;
                    }
                }

                Vec3 nose = ConsistFacing.noseFirst(level, cars);
                SableTrainSpawner.ChainSpots chain = planChain(cars, loc, upNormal, var19);
                if (chain == null) {
                    refuseRelocation(speak, player, "not enough track at " + pos, "not enough track to relocate the whole train here");
                    return null;
                } else {
                    return new SableTrainSpawner.RelocationPlan(cars, chain, ConsistFacing.reversed(level, cars, nose), upNormal);
                }
            } else {
                refuseRelocation(speak, player, "no track-graph location at " + pos, null);
                return null;
            }
        } else {
            refuseRelocation(speak, player, pos + " is not a track block", null);
            return null;
        }
    }

    public static boolean relocationBlockedByTrain(ServerPlayer player, UUID subLevelId, RailPick pick, Vec3 lookAngle) {
        SableTrainSpawner.RelocationPlan plan = planRelocation(player, subLevelId, pick, lookAngle, false);
        return plan != null && PlacementClearance.blocker(player.serverLevel(), plan.cars(), plan.chain().spots(), plan.upNormal()) != null;
    }

    private static boolean holdsRelocator(ServerPlayer player) {
        Item relocator = LoconauticsRegistries.CREATIVE_TRAIN_RELOCATOR.get();
        return player.getMainHandItem().is(relocator) || player.getOffhandItem().is(relocator);
    }

    public static void relocate(ServerPlayer player, UUID subLevelId, RailPick pick, Vec3 lookAngle) {
        // Fix: the "holding a relocator" check used to live only on the client, so a forged
        // SableTrainRelocatePacket could pull any train by its sub-level id. Enforce it here.
        if (!holdsRelocator(player)) {
            RejectedPacketLog.warn(player, "relocate without a relocator in hand");
            player.displayClientMessage(Component.translatable("message.loconautics.relocator.need_relocator"), true);
            return;
        }

        if (LoconauticsConfig.WRENCH_RELOCATION_REQUIRE_OWNERSHIP.get() && !player.hasPermissions(2)) {
            SableTrain target = SableTrainRegistry.bySubLevel(subLevelId);
            UUID owner = target == null ? null : target.owner();
            if (owner != null && !owner.equals(player.getUUID())) {
                RejectedPacketLog.warn(player, "relocate of someone else's train");
                player.displayClientMessage(Component.translatable("message.loconautics.relocator.not_owner"), true);
                return;
            }
        }

        SableTrainSpawner.RelocationPlan plan = planRelocation(player, subLevelId, pick, lookAngle, true);
        if (plan != null) {
            BlockPos pos = pick.pos();
            ServerLevel level = player.serverLevel();
            List<SableTrain> cars = plan.cars();
            List<TrackGraphLocation> spots = plan.chain().spots();
            Vec3 upNormal = plan.upNormal();
            if (PlacementClearance.blocker(level, cars, spots, upNormal) != null) {
                refuseRelocation(true, player, "another train is on the track at " + pos, null);
            } else {
                List<SableTrainSpawner.SeatPose> seats = new ArrayList<>(cars.size());

                for (int i = 0; i < cars.size(); i++) {
                    Vec3 heading = plan.chain().heads().get(i);
                    Vec3 carLook = plan.reversed()[i] ? heading.reverse() : heading;
                    SableTrainSpawner.SeatPose seat = seatPoseAt(level, cars.get(i), spots.get(i), upNormal, carLook, false);
                    if (seat == null) {
                        refuseRelocation(
                            true,
                            player,
                            "car " + (i + 1) + " of " + cars.size() + " has no pose at " + pos,
                            "car " + (i + 1) + " of " + cars.size() + " does not fit the track there, nothing was moved"
                        );
                        return;
                    }

                    seats.add(seat);
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[sabletrain] relocate car {} of {} {}", new Object[]{i + 1, cars.size(), plan.reversed()[i] ? "turned around" : "kept its facing"}
                        );
                }

                Set<UUID> moving = new HashSet<>();

                for (SableTrain car : cars) {
                    moving.add(car.car().subLevelId());
                }

                TeleportStrands.Carry strands = TeleportStrands.capture(level, moving);
                int placed = 0;
                ArticulationLinks.suspend(level, cars);

                for (int i = 0; i < cars.size(); i++) {
                    if (settleSeat(level, cars.get(i), seats.get(i), true)) {
                        placed++;
                    }
                }

                ArticulationLinks.resume(level, cars);
                TeleportStrands.restore(strands, Map.of(), level, Map.of());
                reengageKnuckles(level, cars);
                LoconauticsConstants.LOGGER.debug("[sabletrain] relocated consist of {}/{} car(s) starting at {}", new Object[]{placed, cars.size(), pos});
            }
        }
    }

    private static void reengageKnuckles(ServerLevel level, List<SableTrain> cars) {
        for (SableTrain car : cars) {
            ServerSubLevel sub = SubLevelData.resolve(level, car.car().subLevelId());
            if (sub != null) {
                for (KnuckleBlockEntity knuckle : SubLevelBlocks.findAll(sub, KnuckleBlockEntity.class)) {
                    knuckle.releaseCoupling();
                }
            }
        }
    }

    static boolean facesAgainst(ServerLevel level, SableTrain car, @Nullable Vec3 nose) {
        ServerSubLevel sub = nose == null ? null : SubLevelData.resolve(level, car.car().subLevelId());
        if (sub == null) {
            return false;
        }

        Vec3 facing = currentFacing(sub, car.car());
        return facing.x * nose.x + facing.z * nose.z < 0.0;
    }

    public static List<SableTrain> chainCars(ServerLevel level, UUID subLevelId) {
        List<UUID> order = TrainConsist.orderedChain(level, subLevelId);
        if (order.indexOf(subLevelId) >= order.size() / 2) {
            Collections.reverse(order);
        }

        List<SableTrain> cars = new ArrayList<>();

        for (UUID carId : order) {
            SableTrain car = SableTrainRegistry.bySubLevel(carId);
            if (car != null && car.level() == level && car.car() != null && car.car().carriage() != null) {
                cars.add(car);
            } else {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[issue278] chainCars dropped {} of {}: registered={} sameLevel={} hasCar={} hasCarriage={}",
                        new Object[]{
                            carId.toString().substring(0, 8),
                            order.size(),
                            car != null,
                            car != null && car.level() == level,
                            car != null && car.car() != null,
                            car != null && car.car() != null && car.car().carriage() != null
                        }
                    );
            }
        }

        return cars;
    }

    @Nullable
    public static List<TrackGraphLocation> chainSpots(List<SableTrain> cars, TrackGraphLocation front, Vec3 upNormal, Vec3 lookAngle) {
        SableTrainSpawner.ChainSpots chain = planChain(cars, front, upNormal, lookAngle);
        return chain == null ? null : chain.spots();
    }

    @Nullable
    public static SableTrainSpawner.ChainSpots planChain(List<SableTrain> cars, TrackGraphLocation front, Vec3 upNormal, Vec3 lookAngle) {
        List<TrackGraphLocation> spots = new ArrayList<>();
        List<Vec3> heads = new ArrayList<>();
        double cursor = 0.0;
        double prevHalf = 0.0;
        SableTrain prev = null;
        Vec3 prevAt = null;

        for (SableTrain car : cars) {
            double half = TrainLength.carLength(car.level(), car) / 2.0;
            double step = prev == null ? TrainLength.endReach(car.level(), cars, true) : TrainLength.centerSpacing(car.level(), prev, car, prevHalf, half);
            cursor += step;
            prev = car;
            TrackGraphLocation carLoc = locationBack(front, upNormal, lookAngle, cursor, true);
            if (carLoc == null) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[sabletrain] chain ABORT: track too short, car {} of {} ran off the rail at cursor {}",
                        new Object[]{spots.size() + 1, cars.size(), cursor}
                    );
                return null;
            }

            Vec3 at = pointOf(carLoc);

            for (int i = 0; prevAt != null && at != null && i < 8; i++) {
                double seen = at.distanceTo(prevAt);
                double gap = step - seen;
                if (Math.abs(gap) < 0.01 || seen > step * 2.0) {
                    break;
                }

                TrackGraphLocation moved = locationBack(front, upNormal, lookAngle, cursor + gap, true);
                if (moved == null) {
                    break;
                }

                cursor += gap;
                carLoc = moved;
                at = pointOf(moved);
            }

            spots.add(carLoc);
            heads.add(headingAt(front, upNormal, lookAngle, cursor, at));
            prevAt = at;
            prevHalf = half;
        }

        return new SableTrainSpawner.ChainSpots(spots, heads);
    }

    private static Vec3 headingAt(TrackGraphLocation front, Vec3 upNormal, Vec3 look, double cursor, @Nullable Vec3 at) {
        TrackGraphLocation nearer = at == null ? null : locationBack(front, upNormal, look, Math.max(cursor - 0.1, 0.0), true);
        Vec3 to = nearer == null ? null : pointOf(nearer);
        if (to == null) {
            return look;
        }

        Vec3 flat = new Vec3(to.x - at.x, 0.0, to.z - at.z);
        return flat.lengthSqr() < 1.0E-9 ? look : flat.normalize();
    }

    @Nullable
    private static Vec3 pointOf(TrackGraphLocation loc) {
        TravellingPoint point = RailFollower.pointAt(loc);
        return point == null ? null : point.getPosition(loc.graph);
    }

    public static Vector3d bogeyAnchorWorld(ServerSubLevel sub, SableTrain.Car car) {
        List<SableTrain.Bogey> bogeys = car.chain();
        return bogeys.isEmpty()
            ? SubLevelData.position(sub)
            : SubLevelData.worldOfOffset(sub, bogeys.get(0).localPos()).add(SubLevelData.worldOfOffset(sub, bogeys.get(bogeys.size() - 1).localPos())).mul(0.5);
    }

    public static void sortBogeysAlongReference(List<SableTrain.Bogey> bogeys, @Nullable Vector3d reference) {
        if (reference != null && bogeys.size() >= 2) {
            double fx = reference.x;
            double fz = reference.z;
            bogeys.sort(Comparator.comparingDouble(b -> b.localPos().getX() * fx + b.localPos().getZ() * fz));
        }
    }

    public static Vec3 currentFacing(ServerSubLevel sub, SableTrain.Car car) {
        Vector3d reference = car.carriage() == null ? null : car.carriage().referenceForward();
        if (reference != null && reference.lengthSquared() > 1.0E-9) {
            Vector3d facing = SubLevelData.directionOf(sub, reference);
            if (Math.hypot(facing.x, facing.z) > 1.0E-6) {
                return new Vec3(facing.x, 0.0, facing.z).normalize();
            }
        }

        if (car.carriage() == null) {
            return car.bogeys().isEmpty()
                ? new Vec3(1.0, 0.0, 0.0)
                : TracklessCars.railDirection(sub, sub.getPlot().getCenterBlock().offset(car.bogeys().get(0).localPos()));
        } else {
            return car.carriage().forward();
        }
    }

    @Nullable
    static List<SableTrainSpawner.BogeySeat> bogeySeats(ServerLevel level, ServerSubLevel sub, SableTrain train) {
        List<SableTrain.Bogey> bogeys = train.car().bogeys();
        if (bogeys.isEmpty()) {
            return null;
        }

        Vec3 carFacing = currentFacing(sub, train.car());
        List<SableTrainSpawner.BogeySeat> seats = new ArrayList<>(bogeys.size());

        for (SableTrain.Bogey bogey : bogeys) {
            Vector3d world = SubLevelData.worldOfOffset(sub, bogey.localPos());
            SableTrainSpawner.TrackHit hit = findRailBelow(
                level, BlockPos.containing(world.x, world.y, world.z), bogeyFacing(level, sub, bogey, carFacing), hangs(sub, bogey)
            );
            if (hit == null) {
                return null;
            }

            RailCarriage rail = seatFollowerUnder(hit, world);
            if (rail == null || rail.center() == null) {
                return null;
            }

            seats.add(new SableTrainSpawner.BogeySeat(bogey, hit, rail, world));
        }

        return seats;
    }

    private static boolean place(ServerLevel level, SableTrain train, ServerSubLevel sub, SableTrainSpawner.SeatPose seat, boolean teleport) {
        if (!teleport) {
            Vector3d fromPos = new Vector3d(sub.logicalPose().position());
            Quaterniond fromQ = new Quaterniond(sub.logicalPose().orientation());
            SeatBlend.begin(sub, seat.origin(), seat.orient());
            writePose(level, sub, seat.origin(), seat.orient());
            GhostWheelBodies.carryWheels(train, fromPos, fromQ, seat.origin(), seat.orient());
            return true;
        }

        SableTrainDriver.forgetBodyState(train);
        SableTeleportation.Result placed = SableTeleportation.teleport(sub, level, seat.origin(), seat.orient());
        if (placed.ok()) {
            return true;
        }

        LoconauticsConstants.LOGGER.warn("[sabletrain] could not seat car {}: {}", train.id(), placed.failure());
        return false;
    }

    @Nullable
    public static SableTrainSpawner.SeatPose seatPoseAt(
        ServerLevel level, SableTrain train, TrackGraphLocation loc, Vec3 upNormal, Vec3 lookAngle, boolean perBogey
    ) {
        UUID subLevelId = train.car().subLevelId();
        double spacing = train.isTrackless() ? tracklessSpacing(train.car()) : train.car().carriage().bogeySpacing();
        RailCarriage carriage = RailCarriage.at(loc, upNormal, spacing, 0.0);
        if (carriage == null) {
            LoconauticsConstants.LOGGER.warn("[sabletrain] relocate ABORT: no seed point at the target for {}", subLevelId);
            return null;
        }

        if (carriage.center() == null) {
            LoconauticsConstants.LOGGER
                .warn(
                    "[sabletrain] relocate ABORT: the {} block span at the target ran off the end of the track for {}",
                    String.format("%.2f", spacing),
                    subLevelId
                );
            return null;
        }

        carriage.adoptReferenceFrame(train.car().carriage());
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (train.car().bogeys().isEmpty()) {
            LoconauticsConstants.LOGGER.warn("[sabletrain] relocate ABORT: the car has no bogeys to seat, for {}", subLevelId);
            return null;
        }

        List<SableTrain.Bogey> bogeys = perBogey && sub != null ? seatBogeysUnderThemselves(level, sub, train, lookAngle) : null;
        if (bogeys == null) {
            bogeys = seatBogeysAlongRail(train, train.car().bogeys(), loc, upNormal, lookAngle);
        }

        if (bogeys == null) {
            return null;
        }

        Vector3d target = bogeyMidpoint(bogeys);
        if (target == null) {
            LoconauticsConstants.LOGGER
                .warn(
                    "[sabletrain] relocate ABORT: a bogey seat has no center, {} of {} landed on track for {}",
                    new Object[]{liveSeats(bogeys), bogeys.size(), subLevelId}
                );
            return null;
        }

        Vector3d chord = bogeyChord(bogeys);
        if (chord == null) {
            Vec3 cf = carriage.forward();
            chord = new Vector3d(cf.x, cf.y, cf.z);
            if (cf.x * lookAngle.x + cf.z * lookAngle.z < 0.0) {
                chord.negate();
            }
        }

        if (chord.lengthSquared() < 1.0E-9) {
            LoconauticsConstants.LOGGER
                .warn(
                    "[sabletrain] relocate ABORT: {} bogey seat(s) all landed on the same point and the carriage span is degenerate too, for {}",
                    bogeys.size(),
                    subLevelId
                );
            return null;
        } else {
            Quaterniond orient = carriage.orientationTo(chord);
            if (!isFinite(target) || orient == null || !isFinite(orient)) {
                LoconauticsConstants.LOGGER.warn("[sabletrain] relocate ABORT: degenerate pose for {}", subLevelId);
                return null;
            } else if (sub == null) {
                return new SableTrainSpawner.SeatPose(null, orient, carriage, bogeys);
            } else {
                Vector3d localAnchor = seatAnchorLocal(sub, train.car(), bogeys);
                Pose3d pose = sub.logicalPose();
                Quaterniond keepOrient = new Quaterniond(pose.orientation());
                pose.orientation().set(orient);
                Vector3d anchorAfterRot = pose.transformPosition(new Vector3d(localAnchor), new Vector3d());
                pose.orientation().set(keepOrient);
                Vector3d origin = new Vector3d(pose.position()).add(target).sub(anchorAfterRot);
                if (!isFinite(origin)) {
                    LoconauticsConstants.LOGGER.warn("[sabletrain] relocate ABORT: non-finite origin for {}", subLevelId);
                    return null;
                } else {
                    return new SableTrainSpawner.SeatPose(origin, orient, carriage, bogeys);
                }
            }
        }
    }

    private static double tracklessSpacing(SableTrain.Car car) {
        Vector3dc lead = car.localLead();
        Vector3dc trail = car.localTrail();
        return lead != null && trail != null ? Math.max(1.0, lead.distance(trail)) : 1.0;
    }

    public static void writePose(ServerLevel level, ServerSubLevel sub, Vector3d position, Quaterniond orient) {
        SableTeleportation.writePose(level, sub, position, orient);
    }

    public static boolean seatCarAt(
        ServerLevel level, SableTrain train, TrackGraphLocation loc, Vec3 upNormal, Vec3 lookAngle, boolean perBogey, boolean teleport
    ) {
        SableTrainSpawner.SeatPose seat = seatPoseAt(level, train, loc, upNormal, lookAngle, perBogey);
        return seat != null && settleSeat(level, train, seat, teleport);
    }

    private static boolean settleSeat(ServerLevel level, SableTrain train, SableTrainSpawner.SeatPose seat, boolean teleport) {
        RailCarriage carriage = seat.carriage();
        List<SableTrain.Bogey> bogeys = seat.bogeys();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        UUID subLevelId = train.car().subLevelId();
        ServerSubLevel sub = container != null && container.getSubLevel(subLevelId) instanceof ServerSubLevel s && !s.isRemoved() ? s : null;
        if (sub != null && seat.origin() != null && !place(level, train, sub, seat, teleport)) {
            return false;
        }

        train.relocate(carriage, bogeys);
        seedTravelSigns(seat, carriage, bogeys);
        if (teleport) {
            RerailBalance.noteManualRerail(train);
        }

        train.setDerailed(false);
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        train.setStationState(SableTrain.StationState.RUNNING);
        train.setCurrentStation(null);
        train.setAtStation(null);
        GhostWheelBodies.rebuild(train);
        SableTrainPersistence.persist(train);
        SableTrainSyncPacket.broadcast(train);
        LoconauticsConstants.LOGGER
            .debug("[sabletrain] seated car {} on {} bogey(s) at {}", new Object[]{train.id(), bogeys.size(), LogFormat.v3d(seat.origin())});
        if (KnuckleTuning.trace) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[knuckle] seated state car={} axleSign={} servo={} following={} brakeHold={} handbrake={} scheduleDriven={} lead={} trail={} settle={} bogeys={} railVsBody={}",
                    new Object[]{
                        train.id(),
                        train.axleSign(),
                        train.servoBrake(),
                        train.isFollowing(),
                        train.brakeHold(),
                        train.handbrake(),
                        train.isScheduleDriven(),
                        train.car().localLead() != null,
                        train.car().localTrail() != null,
                        train.isPoseSettling(),
                        bogeys.size(),
                        railVsBody(seat, bogeys)
                    }
                );
        }

        return true;
    }

    private static String railVsBody(SableTrainSpawner.SeatPose seat, List<SableTrain.Bogey> bogeys) {
        Vector3d reference = seat.carriage().referenceForward();
        Vec3 tangent = bogeys.isEmpty() ? null : bogeys.get(0).rail().edgeTangent();
        if (reference != null && tangent != null) {
            Vector3d turned = seat.orient().transform(new Vector3d(reference), new Vector3d());
            double dot = (tangent.x * turned.x + tangent.z * turned.z) * bogeys.get(0).rail().lastTravelSign();
            return String.format("%.2f", dot);
        } else {
            return "n/a";
        }
    }

    private static void seedTravelSigns(SableTrainSpawner.SeatPose seat, RailCarriage carriage, List<SableTrain.Bogey> bogeys) {
        Vector3d reference = carriage.referenceForward();
        if (reference != null) {
            Vector3d turned = seat.orient().transform(new Vector3d(reference), new Vector3d());
            Vec3 facing = new Vec3(turned.x, 0.0, turned.z);
            TrainRailAdvance.seedTravelSign(carriage, facing);

            for (SableTrain.Bogey bogey : bogeys) {
                TrainRailAdvance.seedTravelSign(bogey.rail(), facing);
            }
        }
    }

    private static List<SableTrain.Bogey> seatBogeysUnderThemselves(ServerLevel level, ServerSubLevel sub, SableTrain train, Vec3 lookAngle) {
        List<SableTrain.Bogey> source = train.car().bogeys();
        if (source.isEmpty()) {
            return null;
        }

        List<SableTrain.Bogey> out = new ArrayList<>(source.size());

        for (SableTrain.Bogey bogey : source) {
            RailCarriage rail = railUnderBogey(level, sub, bogey, lookAngle);
            if (rail == null) {
                return null;
            }

            out.add(new SableTrain.Bogey(bogey.localPos(), rail));
        }

        return out;
    }

    private static List<SableTrain.Bogey> seatBogeysAlongRail(
        SableTrain train, List<SableTrain.Bogey> source, TrackGraphLocation loc, Vec3 upNormal, Vec3 lookAngle
    ) {
        if (source.isEmpty()) {
            return null;
        }

        Vector3d axis = TrainLength.lengthAxis(train.car(), source);
        int n = source.size();
        double[] offsets = new double[n];
        Integer[] order = new Integer[n];
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            BlockPos lp = source.get(i).localPos();
            offsets[i] = new Vector3d(lp.getX(), lp.getY(), lp.getZ()).dot(axis);
            low = Math.min(low, offsets[i]);
            high = Math.max(high, offsets[i]);
            order[i] = i;
        }

        double mid = (low + high) * 0.5;

        for (int i = 0; i < n; i++) {
            offsets[i] -= mid;
        }

        Arrays.sort(order, Comparator.comparingDouble(ix -> offsets[ix]));
        List<SableTrain.Bogey> bogeys = new ArrayList<>(n);
        Integer[] var16 = order;
        int var17 = var16.length;

        for (int var18 = 0; var18 < var17; var18++) {
            int i = var16[var18];
            TrackGraphLocation spot = locationBack(loc, upNormal, lookAngle, -offsets[i]);
            if (spot == null) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[sabletrain] relocate ABORT: track runs out {} blocks from the click, bogey {} of {}",
                        new Object[]{String.format("%.2f", offsets[i]), bogeys.size() + 1, n}
                    );
                return null;
            }

            RailCarriage rail = RailCarriage.at(spot, upNormal, 1.0, 0.0);
            if (rail == null) {
                LoconauticsConstants.LOGGER.debug("[sabletrain] relocate ABORT: no follower seat for bogey {} of {}", bogeys.size() + 1, n);
                return null;
            }

            bogeys.add(new SableTrain.Bogey(source.get(i).localPos(), rail));
        }

        return bogeys;
    }

    private static Vector3d seatAnchorLocal(ServerSubLevel sub, SableTrain.Car car, List<SableTrain.Bogey> bogeys) {
        BlockPos plot = sub.getPlot().getCenterBlock();
        BlockPos first = plot.offset(bogeys.get(0).localPos());
        BlockPos last = plot.offset(bogeys.get(bogeys.size() - 1).localPos());
        Vector3d acc = new Vector3d(
            (first.getX() + last.getX()) * 0.5 + 0.5, (first.getY() + last.getY()) * 0.5 + 0.5, (first.getZ() + last.getZ()) * 0.5 + 0.5
        );
        Vector3dc lead = car.localLead();
        Vector3dc trail = car.localTrail();
        if (lead != null && trail != null) {
            acc.y = (lead.y() + trail.y()) * 0.5;
        }

        return acc;
    }

    private static int liveSeats(List<SableTrain.Bogey> bogeys) {
        int live = 0;

        for (SableTrain.Bogey b : bogeys) {
            if (b.rail().center() != null) {
                live++;
            }
        }

        return live;
    }

    private static Vector3d bogeyMidpoint(List<SableTrain.Bogey> bogeys) {
        Vector3d acc = new Vector3d();
        int n = 0;

        for (SableTrain.Bogey b : bogeys) {
            Vector3d c = b.rail().center();
            if (c == null) {
                return null;
            }

            acc.add(c);
            n++;
        }

        return n == 0 ? null : acc.div(n);
    }

    private static Vector3d bogeyChord(List<SableTrain.Bogey> bogeys) {
        if (bogeys.size() < 2) {
            return null;
        } else {
            Vector3d rear = bogeys.get(0).rail().center();
            Vector3d front = bogeys.get(bogeys.size() - 1).rail().center();
            if (rear != null && front != null) {
                Vector3d chord = new Vector3d(front).sub(rear);
                return chord.lengthSquared() < 1.0E-9 ? null : chord;
            } else {
                return null;
            }
        }
    }

    private static Vec3 railTangent(TravellingPoint p, TrackGraph graph, Vec3 upNormal) {
        Vec3 a = p.getPosition(graph);
        TravellingPoint probe = new TravellingPoint(p.node1, p.node2, p.edge, p.position, p.upsideDown);
        probe.travel(graph, 0.1, probe.steer(SteerDirection.NONE, upNormal), probe.ignoreEdgePoints(), probe.ignoreTurns(), probe.ignorePortals());
        return probe.getPosition(graph).subtract(a);
    }

    public static TrackGraphLocation locationBack(TrackGraphLocation from, Vec3 upNormal, Vec3 look, double dist) {
        return locationBack(from, upNormal, look, dist, false);
    }

    public static TrackGraphLocation locationBack(TrackGraphLocation from, Vec3 upNormal, Vec3 look, double dist, boolean refusePortals) {
        TravellingPoint p = RailFollower.pointAt(from);
        if (p == null) {
            return null;
        }

        TrackGraph graph = from.graph;
        if (Math.abs(dist) > 1.0E-6) {
            Vec3 tan = railTangent(p, graph, upNormal);
            double sign = tan.x * look.x + tan.z * look.z >= 0.0 ? -1.0 : 1.0;
            IPortalListener portals = refusePortals ? portal -> true : p.ignorePortals();
            p.travel(graph, sign * dist, p.steer(SteerDirection.NONE, upNormal), p.ignoreEdgePoints(), p.ignoreTurns(), portals);
            if (p.edge == null || p.blocked) {
                return null;
            }
        }

        TrackGraphLocation out = new TrackGraphLocation();
        out.graph = graph;
        out.edge = Couple.create(p.node1.getLocation(), p.node2.getLocation());
        out.position = p.position;
        return out;
    }

    public static boolean reseatOnRail(SableTrain train, ServerSubLevel sub) {
        ServerLevel level = train.level();
        Vec3 fwd = train.car().carriage().forward();
        Vector3dc p = sub.logicalPose().position();
        SableTrainSpawner.TrackHit bodyHit = findRailBelow(level, BlockPos.containing(p.x(), p.y(), p.z()), fwd);
        if (bodyHit == null) {
            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                Vector3d bogeyWorld = sub.logicalPose()
                    .transformPosition(
                        new Vector3d(
                            sub.getPlot().getCenterBlock().getX() + bogey.localPos().getX() + 0.5,
                            sub.getPlot().getCenterBlock().getY() + bogey.localPos().getY() + 0.5,
                            sub.getPlot().getCenterBlock().getZ() + bogey.localPos().getZ() + 0.5
                        ),
                        new Vector3d()
                    );
                bodyHit = findRailBelow(level, BlockPos.containing(bogeyWorld.x, bogeyWorld.y, bogeyWorld.z), fwd);
                if (bodyHit != null) {
                    break;
                }
            }
        }

        if (bodyHit == null) {
            return false;
        }

        double spacing = train.car().carriage().bogeySpacing();
        RailCarriage carriage = RailCarriage.at(bodyHit.location(), bodyHit.upNormal(), spacing, 0.0);
        if (carriage == null) {
            return false;
        }

        carriage.adoptReferenceFrame(train.car().carriage());
        Vector3d c = carriage.center();
        Vec3 cf = carriage.forward();
        if (c != null) {
            double ds0 = (p.x() - c.x) * cf.x + (p.z() - c.z) * cf.z;
            if (Math.abs(ds0) > 0.001) {
                carriage.setSpeed(ds0);
                carriage.tick();
                carriage.setSpeed(0.0);
            }
        }

        List<SableTrain.Bogey> bogeys = new ArrayList<>();

        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            BlockPos bogeyWorld = sub.getPlot().getCenterBlock().offset(bogey.localPos());
            Vector3d w = sub.logicalPose()
                .transformPosition(new Vector3d(bogeyWorld.getX() + 0.5, bogeyWorld.getY() + 0.5, bogeyWorld.getZ() + 0.5), new Vector3d());
            SableTrainSpawner.TrackHit hit = findRailBelow(level, BlockPos.containing(w.x, w.y, w.z), fwd, hangs(sub, bogey));
            if (hit == null) {
                return false;
            }

            RailCarriage rail = seatFollowerUnder(hit, w);
            if (rail == null) {
                return false;
            }

            bogeys.add(new SableTrain.Bogey(bogey.localPos(), rail));
        }

        double keepSpeed = train.speed();
        SableTrain.StationState keepState = train.stationState();
        GlobalStation keepStation = train.currentStation();
        double keepDistance = train.distanceToStation();
        int keepDwell = train.dwellTicks();
        train.relocate(carriage, bogeys);
        train.setSpeed(keepSpeed);
        if (keepState == SableTrain.StationState.STOPPED) {
            train.keepStop(keepState, keepStation, keepDistance, keepDwell);
        }

        LoconauticsConstants.LOGGER.debug("[sabletrain] re-seated train {} on the live track graph (rail broken/rebuilt nearby)", train.id());
        return true;
    }

    public static boolean tryRerailOffTrackBogeys(SableTrain train) {
        if ((Boolean)LoconauticsConfig.AUTO_RERAIL_ENABLED.get() && !train.isDerailed() && train.hasOffTrackBogey()) {
            ServerLevel level = train.level();
            UUID subLevelId = train.car().subLevelId();
            ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
            if (sub == null) {
                return false;
            }

            Vec3 fwd = train.car().carriage().forward();
            if (RerailApproach.swingOntoRail(level, sub, train)) {
                return false;
            }

            List<SableTrain.Bogey> updated = new ArrayList<>(train.car().bogeys().size());
            boolean anyRecovered = false;

            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                if (train.isBogeyOffTrack(bogey.localPos()) && train.bogeyRerailReady(bogey.localPos(), 60)) {
                    RailCarriage rail = acceptableSeatFor(level, sub, bogey, fwd, null, 1.25);
                    if (rail == null) {
                        GhostWheelBodies.clearAlign(train, bogey.localPos());
                        updated.add(bogey);
                    } else if (!GhostWheelBodies.alignToRail(train, bogey.localPos(), rail.chordFrameQuaternion())) {
                        updated.add(bogey);
                    } else {
                        TrainRailAdvance.seedTravelSign(rail, currentFacing(sub, train.car()));
                        updated.add(new SableTrain.Bogey(bogey.localPos(), rail));
                        train.clearOffTrackBogey(bogey.localPos());
                        anyRecovered = true;
                        LoconauticsConstants.LOGGER
                            .debug("[sabletrain] car {} bogey {} found rail again, back on track", train.id(), bogey.localPos().toShortString());
                    }
                } else {
                    updated.add(bogey);
                }
            }

            if (anyRecovered) {
                train.setBogeys(updated);
            }

            return anyRecovered;
        } else {
            return false;
        }
    }

    @Nullable
    static RailCarriage acceptableSeatFor(
        ServerLevel level, ServerSubLevel sub, SableTrain.Bogey bogey, Vec3 fwd, @Nullable TrackGraph graph, double maxLateral
    ) {
        RailCarriage rail = railUnderBogey(level, sub, bogey, fwd);
        String refused = rail == null
            ? String.format(
                "no rail under the bogey at %s, scanned r%d and %d down",
                LogFormat.v3d(SubLevelData.worldOfOffset(sub, bogey.localPos())),
                LoconauticsConfig.RAIL_SEARCH_RADIUS.get(),
                LoconauticsConfig.RAIL_SEARCH_VERTICAL_RANGE.get()
            )
            : (
                rail.railheadWithin(1.0)
                    ? "a railhead is within 1.0"
                    : (
                        graph != null && rail.graph() != graph
                            ? "that rail is on another graph"
                            : (!seatFitsBogey(level, sub, bogey, rail, fwd, maxLateral) ? seatMisfit(level, sub, bogey, rail, fwd, maxLateral) : null)
                    )
            );
        if (refused != null) {
            BogeyEditTrace.refuse(sub, "seat for bogey " + bogey.localPos().toShortString() + ": " + refused);
            return null;
        } else {
            return rail;
        }
    }

    private static String seatMisfit(ServerLevel level, ServerSubLevel sub, SableTrain.Bogey bogey, RailCarriage rail, Vec3 fwd, double maxLateral) {
        Vector3d center = rail.center();
        if (center == null) {
            return "the seat has no center";
        }

        Vector3d world = SubLevelData.worldOfOffset(sub, bogey.localPos());
        return String.format(
            "seat misfit, bogey %s rail %s drop %.3f/%.1f lateral %.3f/%.1f yaw %.1f/%.1f",
            LogFormat.v3d(world),
            LogFormat.v3d(center),
            world.y - center.y,
            1.5,
            Math.hypot(world.x - center.x, world.z - center.z),
            maxLateral,
            SubLevelData.axisDiffDeg(SubLevelData.of(bogeyFacing(level, sub, bogey, fwd)), SubLevelData.of(rail.forward())),
            45.0
        );
    }

    private static boolean seatFitsBogey(ServerLevel level, ServerSubLevel sub, SableTrain.Bogey bogey, RailCarriage rail, Vec3 fwd, double maxLateral) {
        Vector3d center = rail.center();
        if (center == null) {
            return false;
        }

        Vector3d world = SubLevelData.worldOfOffset(sub, bogey.localPos());
        BlockState bogeyState = level.getBlockState(sub.getPlot().getCenterBlock().offset(bogey.localPos()));
        double drop = WheelColliders.isUpsideDown(bogeyState) ? center.y - world.y : world.y - center.y;
        return !(drop < 0.0) && !(drop > 1.5) && !(Math.hypot(world.x - center.x, world.z - center.z) > maxLateral)
            ? SubLevelData.axisDiffDeg(SubLevelData.of(bogeyFacing(level, sub, bogey, fwd)), SubLevelData.of(rail.forward())) <= 45.0
            : false;
    }

    public static RailCarriage railUnderBogey(ServerLevel level, ServerSubLevel sub, SableTrain.Bogey bogey, Vec3 fwd) {
        Vec3 facing = bogeyFacing(level, sub, bogey, fwd);
        Vector3d world = SubLevelData.worldOfOffset(sub, bogey.localPos());
        SableTrainSpawner.TrackHit hit = findRailBelow(level, BlockPos.containing(world.x, world.y, world.z), facing, hangs(sub, bogey));
        return hit == null ? null : seatFollowerUnder(hit, world);
    }

    private static boolean hangs(ServerSubLevel sub, SableTrain.Bogey bogey) {
        return WheelColliders.isUpsideDown(sub.getLevel().getBlockState(sub.getPlot().getCenterBlock().offset(bogey.localPos())));
    }

    private static RailCarriage seatFollowerUnder(SableTrainSpawner.TrackHit hit, Vector3d world) {
        RailCarriage rail = RailCarriage.at(hit.location(), hit.upNormal(), 1.0, 0.0);
        if (rail == null) {
            return null;
        }

        Vector3d center = rail.center();
        if (center == null) {
            return rail;
        }

        Vec3 fwd = rail.forward();
        double ds0 = (world.x - center.x) * fwd.x + (world.z - center.z) * fwd.z;
        if (Math.abs(ds0) > searchReach()) {
            LoconauticsConstants.LOGGER
                .warn(
                    "[sabletrain] seat: the rail under ({}, {}, {}) reads {} blocks away, refusing it",
                    new Object[]{String.format("%.1f", world.x), String.format("%.1f", world.y), String.format("%.1f", world.z), String.format("%.2f", ds0)}
                );
            return null;
        }

        for (int pass = 0; pass < 4 && Math.abs(ds0) > 0.001; pass++) {
            rail.setSpeed(ds0);
            rail.tick();
            rail.setSpeed(0.0);
            center = rail.center();
            if (center == null) {
                break;
            }

            fwd = rail.forward();
            ds0 = (world.x - center.x) * fwd.x + (world.z - center.z) * fwd.z;
        }

        return rail;
    }

    private static Vector3d worldOfSeat(SableTrainSpawner.TrackHit hit, BlockPos seat) {
        ServerSubLevel host = RailSeams.serverHostOf((TrackNodeLocation)hit.location().edge.getFirst());
        return host == null ? new Vector3d(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5) : SubLevelData.worldOfBlock(host, seat);
    }

    static Vec3 bogeyFacing(ServerLevel level, ServerSubLevel sub, SableTrain.Bogey bogey, Vec3 fallback) {
        SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
        if (train != null && level == train.level()) {
            Vec3 plate = GhostWheelBodies.plateFacing(train, bogey.localPos());
            return plate == null ? fallback : plate;
        } else {
            return fallback;
        }
    }

    private static boolean isFinite(Vector3d v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    private static boolean isFinite(Quaterniond q) {
        return Double.isFinite(q.x) && Double.isFinite(q.y) && Double.isFinite(q.z) && Double.isFinite(q.w);
    }

    private static boolean hasBogey(ServerLevel level, BoundingBox3i bounds, int railY) {
        int minY = Math.min(railY, bounds.minY());
        MutableBlockPos pos = new MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = minY; y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (level.getBlockState(pos.set(x, y, z)).getBlock() instanceof AbstractBogeyBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    static SableTrainSpawner.TrackHit findRailBelow(ServerLevel level, BlockPos origin, Vec3 look) {
        return findRailBelow(level, origin, look, false);
    }

    static SableTrainSpawner.TrackHit findRailBelow(ServerLevel level, BlockPos origin, Vec3 look, boolean hanging) {
        Vec3 world = Vec3.atCenterOf(origin);
        double reach = searchReach();
        SableTrainSpawner.Hit best = new SableTrainSpawner.Hit();
        scanRailBelow(level, null, origin, look, best, hanging);
        BoundingBox3d box = new BoundingBox3d(world.x - reach, world.y - reach, world.z - reach, world.x + reach, world.y + reach, world.z + reach);
        List<ServerSubLevel> hosts = new ArrayList<>();

        for (SubLevel sl : Sable.HELPER.getAllIntersecting(level, box)) {
            if (sl instanceof ServerSubLevel host && !host.isRemoved()) {
                hosts.add(host);
                Vec3 localPos = SablePlotFrame.toLocalPos(host, world);
                scanRailBelow(level, host, BlockPos.containing(localPos), SablePlotFrame.toLocalDir(host, look), best, hanging);
            }
        }

        if (best.hit == null) {
            edgeRailBelow(level, null, world, look, best, hanging);

            for (ServerSubLevel host : hosts) {
                edgeRailBelow(level, host, SablePlotFrame.toLocalPos(host, world), SablePlotFrame.toLocalDir(host, look), best, hanging);
            }
        }

        return best.hit;
    }

    private static void edgeRailBelow(ServerLevel level, ServerSubLevel host, Vec3 origin, Vec3 look, SableTrainSpawner.Hit best, boolean hanging) {
        RailEdgeSearch.Found found = RailEdgeSearch.nearest(
            level.dimension(),
            host,
            origin,
            look,
            ((Integer)LoconauticsConfig.RAIL_SEARCH_RADIUS.get()).intValue() + 0.5,
            ((Integer)LoconauticsConfig.RAIL_SEARCH_VERTICAL_RANGE.get()).intValue(),
            hanging
        );
        if (found != null && found.distSqr() < best.distSqr) {
            best.hit = new SableTrainSpawner.TrackHit(found.location(), found.upNormal(), Mth.floor(found.railY()));
            best.distSqr = found.distSqr();
        }
    }

    private static double searchReach() {
        return (Integer)LoconauticsConfig.RAIL_SEARCH_RADIUS.get() + (Integer)LoconauticsConfig.RAIL_SEARCH_VERTICAL_RANGE.get() + 1.0;
    }

    private static void scanRailBelow(ServerLevel level, ServerSubLevel host, BlockPos origin, Vec3 look, SableTrainSpawner.Hit best, boolean hanging) {
        MutableBlockPos pos = new MutableBlockPos();
        int down = (Integer)LoconauticsConfig.RAIL_SEARCH_VERTICAL_RANGE.get();
        int radius = (Integer)LoconauticsConfig.RAIL_SEARCH_RADIUS.get();

        for (int dy = hanging ? -down : 0; dy <= (hanging ? 0 : down); dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (!(distSqr >= best.distSqr)) {
                        pos.set(origin.getX() + dx, origin.getY() - dy, origin.getZ() + dz);
                        BlockState state = level.getBlockState(pos);
                        if (state.getBlock() instanceof ITrackBlock track) {
                            Pair<Vec3, AxisDirection> axis = track.getNearestTrackAxis(level, pos, state, look);
                            TrackGraphLocation loc = TrackGraphHelper.getGraphLocationAt(
                                level, pos.immutable(), (AxisDirection)axis.getSecond(), (Vec3)axis.getFirst()
                            );
                            if (loc != null && loc.graph != null) {
                                best.hit = new SableTrainSpawner.TrackHit(loc, track.getUpNormal(level, pos, state), pos.getY());
                                best.distSqr = distSqr;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void noteRestored(UUID id) {
        lastTrainId = id;
    }

    private static void msg(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[sabletrain] " + text));
    }

    record BogeySeat(SableTrain.Bogey bogey, SableTrainSpawner.TrackHit hit, RailCarriage rail, Vector3d world) {
    }

    public record ChainSpots(List<TrackGraphLocation> spots, List<Vec3> heads) {
    }

    private static final class Hit {
        SableTrainSpawner.TrackHit hit;
        double distSqr = Double.MAX_VALUE;
    }

    public record RailSeat(TrackGraphLocation location, Vec3 upNormal, Vec3 lookAngle) {
    }

    private record RelocationPlan(List<SableTrain> cars, SableTrainSpawner.ChainSpots chain, boolean[] reversed, Vec3 upNormal) {
    }

    public record SeatPose(Vector3d origin, Quaterniond orient, RailCarriage carriage, List<SableTrain.Bogey> bogeys) {
    }

    record TrackHit(TrackGraphLocation location, Vec3 upNormal, int railY) {
    }
}
