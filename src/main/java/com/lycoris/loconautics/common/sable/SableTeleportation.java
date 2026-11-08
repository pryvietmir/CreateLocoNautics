package com.lycoris.loconautics.common.sable;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.LinkHolder;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import dev.ryanhcode.sable.util.SableNBTUtils;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SableTeleportation {
    private static final Set<UUID> IN_FLIGHT = new HashSet<>();

    private SableTeleportation() {
    }

    public static boolean isInFlight(UUID subLevelId) {
        return IN_FLIGHT.contains(subLevelId);
    }

    public static void writePose(ServerLevel level, ServerSubLevel sub, Vector3dc position, Quaterniondc orient) {
        Pose3d pose = sub.logicalPose();
        pose.position().set(position);
        pose.orientation().set(orient);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
            pipeline.teleport(sub, pose.position(), pose.orientation());
            pipeline.resetVelocity(sub);
        }

        sub.updateLastPose();
    }

    @Nullable
    public static SableTeleportation.PlotSlot plotSlot(ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(sub.getLevel());
        return container == null
            ? null
            : new SableTeleportation.PlotSlot(sub.getPlot().plotPos.x - container.getOrigin().x, sub.getPlot().plotPos.z - container.getOrigin().y);
    }

    public static double baseOffsetY(ServerSubLevel sub) {
        return sub.logicalPose().position().y() - sub.boundingBox().minY();
    }

    public static boolean plotSlotFree(ServerLevel destLevel, SableTeleportation.PlotSlot slot) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(destLevel);
        return container != null && !occupied(container, slot.x(), slot.z());
    }

    private static boolean occupied(ServerSubLevelContainer container, int x, int z) {
        return container.getSubLevel(x, z) != null || container.getOccupancy().get(container.getIndex(x, z));
    }

    @Nullable
    public static SableTeleportation.PlotSlot firstFreeSlot(ServerLevel destLevel, Set<SableTeleportation.PlotSlot> claimed) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(destLevel);
        if (container == null) {
            return null;
        }

        int side = 1 << container.getLogSideLength();

        for (int x = 0; x < side; x++) {
            for (int z = 0; z < side; z++) {
                SableTeleportation.PlotSlot slot = new SableTeleportation.PlotSlot(x, z);
                if (!occupied(container, x, z) && !claimed.contains(slot)) {
                    return slot;
                }
            }
        }

        return null;
    }

    @Nullable
    public static String repot(CompoundTag tag, ServerLevel srcLevel, ServerLevel destLevel, SableTeleportation.PlotSlot from, SableTeleportation.PlotSlot to) {
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        return srcContainer != null && destContainer != null
            ? repotPlot(tag, srcContainer, destContainer, from, to)
            : "one of the two levels has no sub level container";
    }

    public static Vector3d plotShift(ServerLevel srcLevel, ServerLevel destLevel, SableTeleportation.PlotSlot from, SableTeleportation.PlotSlot to) {
        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        if (srcContainer != null && destContainer != null) {
            int logBlockSize = destContainer.getLogPlotSize() + 4;
            double dx = (long)(to.x() + destContainer.getOrigin().x) - (from.x() + srcContainer.getOrigin().x) << logBlockSize;
            double dz = (long)(to.z() + destContainer.getOrigin().y) - (from.z() + srcContainer.getOrigin().y) << logBlockSize;
            return new Vector3d(dx, 0.0, dz);
        } else {
            return new Vector3d();
        }
    }

    @Nullable
    private static String repotPlot(
        CompoundTag tag,
        ServerSubLevelContainer srcContainer,
        ServerSubLevelContainer destContainer,
        SableTeleportation.PlotSlot from,
        SableTeleportation.PlotSlot to
    ) {
        if (srcContainer.getLogPlotSize() != destContainer.getLogPlotSize()) {
            return "the two dimensions lay their plot grids out at different sizes";
        }

        CompoundTag plot = tag.getCompound("plot");
        plot.putInt("plot_x", to.x());
        plot.putInt("plot_z", to.z());
        int fromX = from.x() + srcContainer.getOrigin().x;
        int fromZ = from.z() + srcContainer.getOrigin().y;
        int toX = to.x() + destContainer.getOrigin().x;
        int toZ = to.z() + destContainer.getOrigin().y;
        if (fromX == toX && fromZ == toZ) {
            return null;
        }

        int logBlockSize = destContainer.getLogPlotSize() + 4;
        int dx = toX - fromX << logBlockSize;
        int dz = toZ - fromZ << logBlockSize;
        CompoundTag chunks = plot.getCompound("chunks");

        for (String chunkKey : chunks.getAllKeys()) {
            ListTag blockEntities = chunks.getCompound(chunkKey).getList("block_entities", 10);

            for (int i = 0; i < blockEntities.size(); i++) {
                CompoundTag be = blockEntities.getCompound(i);
                be.putInt("x", be.getInt("x") + dx);
                be.putInt("z", be.getInt("z") + dz);
                shiftKinetics(be, dx, dz);
            }
        }

        return null;
    }

    private static void shiftKinetics(CompoundTag be, int dx, int dz) {
        if (be.contains("Source", 11)) {
            int[] source = be.getIntArray("Source");
            if (source.length == 3) {
                source[0] += dx;
                source[2] += dz;
                be.putIntArray("Source", source);
            }
        }

        if (be.contains("Network", 10)) {
            CompoundTag network = be.getCompound("Network");
            network.putLong("Id", BlockPos.of(network.getLong("Id")).offset(dx, 0, dz).asLong());
        }
    }

    public static SableTeleportation.Result teleport(ServerSubLevel sub, ServerLevel destLevel, Vector3dc position, Quaterniondc orient) {
        return teleport(sub, destLevel, position, orient, null);
    }

    public static SableTeleportation.Result teleport(
        ServerSubLevel sub, ServerLevel destLevel, Vector3dc position, Quaterniondc orient, @Nullable SableTeleportation.PlotSlot targetSlot
    ) {
        ServerLevel srcLevel = sub.getLevel();
        if (sub.isRemoved()) {
            return new SableTeleportation.Result(null, "body " + sub.getUniqueId() + " is not live");
        }

        ServerSubLevelContainer srcContainer = SubLevelContainer.getContainer(srcLevel);
        ServerSubLevelContainer destContainer = SubLevelContainer.getContainer(destLevel);
        if (srcContainer != null && destContainer != null) {
            SableTeleportation.PlotSlot fromSlot = plotSlot(sub);
            if (destLevel != srcLevel) {
                if (fromSlot == null) {
                    return new SableTeleportation.Result(null, "body " + sub.getUniqueId() + " has no plot slot");
                }

                if (targetSlot == null) {
                    targetSlot = plotSlotFree(destLevel, fromSlot) ? fromSlot : firstFreeSlot(destLevel, Set.of());
                }

                if (targetSlot == null) {
                    return new SableTeleportation.Result(null, "no free plot left in " + destLevel.dimension().location());
                }

                if (!plotSlotFree(destLevel, targetSlot)) {
                    return new SableTeleportation.Result(
                        null, "plot " + targetSlot.x() + ", " + targetSlot.z() + " is taken in " + destLevel.dimension().location()
                    );
                }
            } else {
                targetSlot = null;
            }

            destLevel.getChunk(BlockPos.containing(position.x(), position.y(), position.z()));
            UUID subLevelId = sub.getUniqueId();
            boolean staffLocked = PhysicsStaffServerHandler.get(srcLevel).isLocked(sub);
            SubLevelPassengers.Snapshot riders = SubLevelPassengers.capture(srcLevel, sub);
            List<SubLevelGlue.Strip> glue = SubLevelGlue.take(sub);
            dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData data = SubLevelSerializer.toData(sub, List.of());
            CompoundTag tag = data.fullTag();
            CompoundTag keepPose = tag.getCompound("pose").copy();
            CompoundTag keepPlot = tag.getCompound("plot").copy();
            String tooTall = rebaseSections(tag, srcLevel, destLevel);
            if (tooTall != null) {
                SubLevelGlue.put(sub, glue);
                return new SableTeleportation.Result(null, tooTall);
            }

            if (targetSlot != null) {
                String badPlot = repotPlot(tag, srcContainer, destContainer, fromSlot, targetSlot);
                if (badPlot != null) {
                    SubLevelGlue.put(sub, glue);
                    return new SableTeleportation.Result(null, badPlot);
                }
            }

            Pose3d target = new Pose3d(sub.logicalPose());
            target.position().set(position);
            target.orientation().set(orient);
            if (targetSlot != null) {
                target.rotationPoint().add(plotShift(srcLevel, destLevel, fromSlot, targetSlot));
            }

            tag.put("pose", SableNBTUtils.writePose3d(target));
            tag.remove("linear_velocity");
            tag.remove("angular_velocity");
            IN_FLIGHT.add(subLevelId);

            ServerSubLevel moved;
            try {
                List<BlockEntity> oldBlockEntities = SubLevelBlocks.blockEntities(sub);

                for (BlockEntity be : oldBlockEntities) {
                    if (be instanceof LinkHolder holder) {
                        holder.releaseLinks();
                    }
                }

                srcContainer.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);

                for (BlockEntity be : oldBlockEntities) {
                    if (!be.isRemoved()) {
                        be.setRemoved();
                    }
                }

                moved = SubLevelSerializer.fullyLoad(destLevel, data);
                if (moved == null) {
                    tag.put("pose", keepPose);
                    tag.put("plot", keepPlot);
                    moved = SubLevelSerializer.fullyLoad(srcLevel, data);
                    if (moved == null) {
                        LoconauticsConstants.LOGGER
                            .error(
                                "[teleport] lost body {}, it loaded into neither {} nor {}",
                                new Object[]{subLevelId, destLevel.dimension().location(), srcLevel.dimension().location()}
                            );
                        return new SableTeleportation.Result(
                            null, "body " + subLevelId + " would not load into " + destLevel.dimension().location() + " and could not be put back"
                        );
                    }

                    SubLevelGlue.put(moved, glue);
                    SubLevelPassengers.restore(srcLevel, moved, riders);
                    relockStaff(srcLevel, subLevelId, staffLocked);
                    return new SableTeleportation.Result(
                        null,
                        "body "
                            + subLevelId
                            + " would not load into "
                            + destLevel.dimension().location()
                            + ", it is back in "
                            + srcLevel.dimension().location()
                    );
                }
            } finally {
                IN_FLIGHT.remove(subLevelId);
            }

            LoconauticsConstants.LOGGER
                .debug(
                    "[twinload] teleport body={} slot={},{} to {},{} pose={},{} rotationPoint={},{} worldBounds x {}..{} z {}..{}",
                    new Object[]{
                        subLevelId,
                        fromSlot == null ? -1 : fromSlot.x(),
                        fromSlot == null ? -1 : fromSlot.z(),
                        targetSlot == null ? -1 : targetSlot.x(),
                        targetSlot == null ? -1 : targetSlot.z(),
                        (int)moved.logicalPose().position().x(),
                        (int)moved.logicalPose().position().z(),
                        (int)moved.logicalPose().rotationPoint().x(),
                        (int)moved.logicalPose().rotationPoint().z(),
                        (int)moved.boundingBox().minX(),
                        (int)moved.boundingBox().maxX(),
                        (int)moved.boundingBox().minZ(),
                        (int)moved.boundingBox().maxZ()
                    }
                );
            SubLevelGlue.put(moved, glue);
            SubLevelPassengers.restore(destLevel, moved, riders);
            relockStaff(destLevel, subLevelId, staffLocked);
            return new SableTeleportation.Result(moved, null);
        } else {
            return new SableTeleportation.Result(null, "one of the two levels has no sub level container");
        }
    }

    private static void relockStaff(ServerLevel level, UUID subLevelId, boolean staffLocked) {
        if (staffLocked) {
            PhysicsStaffServerHandler.get(level).toggleLock(subLevelId);
        }
    }

    @Nullable
    public static String rebaseSections(CompoundTag tag, ServerLevel srcLevel, ServerLevel destLevel) {
        int delta = srcLevel.getMinSection() - destLevel.getMinSection();
        if (delta == 0) {
            return null;
        }

        int limit = destLevel.getSectionsCount();
        CompoundTag chunks = tag.getCompound("plot").getCompound("chunks");

        for (String chunkKey : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(chunkKey);
            CompoundTag sections = chunkTag.getCompound("sections");
            CompoundTag shifted = new CompoundTag();

            for (String sectionKey : sections.getAllKeys()) {
                int moved = Integer.parseInt(sectionKey) + delta;
                if (moved < 0 || moved >= limit) {
                    return "the body does not fit the build height of " + destLevel.dimension().location();
                }

                shifted.put(String.valueOf(moved), sections.getCompound(sectionKey));
            }

            chunkTag.put("sections", shifted);
            chunkTag.remove("heightmaps");
        }

        return null;
    }

    public record PlotSlot(int x, int z) {
    }

    public record Result(@Nullable ServerSubLevel moved, @Nullable String failure) {
        public boolean ok() {
            return this.failure == null;
        }
    }
}
