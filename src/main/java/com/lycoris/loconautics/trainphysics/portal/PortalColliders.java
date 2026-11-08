package com.lycoris.loconautics.trainphysics.portal;

import com.lycoris.loconautics.common.sable.SubLevelData;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PortalColliders {
    private static final Map<UUID, PortalColliders.Applied> APPLIED = new HashMap<>();
    private static final Set<UUID> DIRTY = new HashSet<>();

    private PortalColliders() {
    }

    public static int apply(ServerLevel level, UUID subLevelId, PortalCut cut) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        BoundingBox3ic bounds = sub == null ? null : sub.getPlot().getBoundingBox();
        if (bounds == null) {
            APPLIED.remove(subLevelId);
            return 0;
        }

        PortalColliders.Applied previous = APPLIED.put(subLevelId, new PortalColliders.Applied(level, cut));
        PortalCut was = previous == null ? null : previous.cut();
        if (cut.equals(was) && !DIRTY.remove(subLevelId)) {
            return -1;
        }

        if (was != null) {
            restoreRange(level, bounds, was, cut);
        }

        long scene = Rapier3D.getSceneHandle(level);
        int hidden = 0;
        MutableBlockPos pos = new MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (cut.noCollider(x, y, z) && !level.getBlockState(pos.set(x, y, z)).isAir()) {
                        hidden++;
                        Rapier3D.changeBlock(scene, x, y, z, 0);
                    }
                }
            }
        }

        return hidden;
    }

    public static boolean hiddenAt(ServerLevel level, BlockPos pos) {
        if (APPLIED.isEmpty()) {
            return false;
        }

        for (Entry<UUID, PortalColliders.Applied> entry : APPLIED.entrySet()) {
            PortalColliders.Applied applied = entry.getValue();
            if (applied.level() == level) {
                ServerSubLevel sub = SubLevelData.resolve(level, entry.getKey());
                BoundingBox3ic bounds = sub == null ? null : sub.getPlot().getBoundingBox();
                if (bounds != null
                    && pos.getX() >= bounds.minX()
                    && pos.getX() <= bounds.maxX()
                    && pos.getY() >= bounds.minY()
                    && pos.getY() <= bounds.maxY()
                    && pos.getZ() >= bounds.minZ()
                    && pos.getZ() <= bounds.maxZ()) {
                    return applied.cut().hidden(pos.getX(), pos.getY(), pos.getZ());
                }
            }
        }

        return false;
    }

    public static void invalidate(UUID subLevelId) {
        if (APPLIED.containsKey(subLevelId)) {
            DIRTY.add(subLevelId);
        }
    }

    public static void clear(UUID subLevelId) {
        DIRTY.remove(subLevelId);
        PortalColliders.Applied applied = APPLIED.remove(subLevelId);
        if (applied != null) {
            ServerSubLevel sub = SubLevelData.resolve(applied.level(), subLevelId);
            BoundingBox3ic bounds = sub == null ? null : sub.getPlot().getBoundingBox();
            if (bounds != null) {
                restoreRange(applied.level(), bounds, applied.cut(), null);
            }
        }
    }

    private static void restoreRange(ServerLevel level, BoundingBox3ic bounds, PortalCut was, PortalCut now) {
        PhysicsPipeline pipeline = pipelineOf(level);
        if (pipeline != null) {
            MutableBlockPos pos = new MutableBlockPos();

            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        if (was.noCollider(x, y, z) && (now == null || !now.noCollider(x, y, z))) {
                            BlockState state = level.getBlockState(pos.set(x, y, z));
                            if (!state.isAir()) {
                                pipeline.handleBlockChange(SectionPos.of(pos), null, x & 15, y & 15, z & 15, Blocks.AIR.defaultBlockState(), state);
                            }
                        }
                    }
                }
            }
        }
    }

    private static PhysicsPipeline pipelineOf(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.physicsSystem().getPipeline();
    }

    private record Applied(ServerLevel level, PortalCut cut) {
    }
}
