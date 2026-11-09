package com.lycoris.loconautics.common.sable;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation.DiscoveredLocation;
import com.simibubi.create.content.trains.signal.SingleBlockEntityEdgePoint;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

@EventBusSubscriber(modid = "loconautics")
public final class StationTrackRebind {
    private static final String TARGET_TRACK_KEY = "TargetTrack";
    private static final String BEZIER_KEY = "Bezier";
    private static final String BEZIER_CURVE_KEY = "Key";
    private static final List<StationTrackRebind.Pending> PENDING = new ArrayList<>();
    private static final Map<BlockPos, BlockPos> MOVED = new HashMap<>();

    private StationTrackRebind() {
    }

    public static void capture(ServerLevel level, BlockPos oldTrack, BlockPos newTrack) {
        MOVED.put(oldTrack.immutable(), newTrack.immutable());
        BlockState state = level.getBlockState(oldTrack);
        if (state.getBlock() instanceof TrackBlock track) {
            HashSet var16 = new HashSet<>();

            for (DiscoveredLocation end : track.getConnected(level, oldTrack, state, false, null)) {
                for (TrackGraph graph : Create.RAILWAYS.getGraphs(level, end)) {
                    TrackNode node = graph.locateNode(end);

                    for (TrackEdge edge : graph.getConnectionsFrom(node).values()) {
                        for (TrackEdgePoint point : edge.getEdgeData().getPoints()) {
                            if (point instanceof SingleBlockEntityEdgePoint single
                                && level.dimension().equals(single.getBlockEntityDimension())
                                && var16.add(single.getBlockEntityPos())) {
                                PENDING.add(new StationTrackRebind.Pending(level, single.getBlockEntityPos().immutable()));
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        if (PENDING.isEmpty()) {
            MOVED.clear();
        } else {
            List<StationTrackRebind.Pending> work = List.copyOf(PENDING);
            PENDING.clear();

            for (StationTrackRebind.Pending pending : work) {
                rebind(pending);
            }

            MOVED.clear();
        }
    }

    private static void rebind(StationTrackRebind.Pending pending) {
        TrackTargetingBehaviour<?> target = (TrackTargetingBehaviour<?>)BlockEntityBehaviour.get(
            pending.level(), pending.station(), TrackTargetingBehaviour.TYPE
        );
        if (target != null) {
            BlockPos oldTrack = target.getGlobalPosition();
            BlockPos newTrack = MOVED.get(oldTrack);
            if (newTrack != null) {
                Provider registries = pending.level().registryAccess();
                CompoundTag tag = new CompoundTag();
                target.write(tag, registries, false);
                tag.put("TargetTrack", NbtUtils.writeBlockPos(newTrack.subtract(pending.station())));
                if (tag.contains("Bezier")) {
                    CompoundTag bezier = tag.getCompound("Bezier");
                    BlockPos curve = NBTHelper.readBlockPos(bezier, "Key");
                    bezier.put("Key", NbtUtils.writeBlockPos(curve.offset(newTrack.subtract(oldTrack))));
                }

                target.read(tag, registries, false);
                target.blockEntity.notifyUpdate();
            }
        }
    }

    private record Pending(ServerLevel level, BlockPos station) {
    }
}
