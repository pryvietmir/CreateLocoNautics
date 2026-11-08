package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

public final class SubLevelBlocks {
    private static final Map<SubLevel, SubLevelBlocks.Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private SubLevelBlocks() {
    }

    public static void forEachBlock(SubLevel sub, SubLevelBlocks.BlockVisitor visitor) {
        if (sub != null && !sub.isRemoved()) {
            BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            MutableBlockPos cursor = new MutableBlockPos();

            for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                ChunkPos cp = chunk.getPos();
                int minX = Math.max(bounds.minX(), cp.getMinBlockX());
                int maxX = Math.min(bounds.maxX(), cp.getMaxBlockX());
                int minZ = Math.max(bounds.minZ(), cp.getMinBlockZ());
                int maxZ = Math.min(bounds.maxZ(), cp.getMaxBlockZ());
                int firstSection = Math.max(0, chunk.getSectionIndex(bounds.minY()));
                int lastSection = Math.min(chunk.getSectionsCount() - 1, chunk.getSectionIndex(bounds.maxY()));

                for (int si = firstSection; si <= lastSection; si++) {
                    LevelChunkSection section = chunk.getSection(si);
                    if (!section.hasOnlyAir()) {
                        int sectionMinY = chunk.getMinSection() * 16 + si * 16;
                        int minY = Math.max(bounds.minY(), sectionMinY);
                        int maxY = Math.min(bounds.maxY(), sectionMinY + 15);

                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    cursor.set(x, y, z);
                                    if (!visitor.visit(cursor, section.getBlockState(x & 15, y & 15, z & 15))) {
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean hasBlock(SubLevel sub, Class<? extends Block> type) {
        boolean[] found = new boolean[]{false};
        forEachBlock(sub, (pos, state) -> {
            if (type.isInstance(state.getBlock())) {
                found[0] = true;
                return false;
            } else {
                return true;
            }
        });
        return found[0];
    }

    public static int countBlocks(SubLevel sub) {
        int[] count = new int[]{0};
        forEachBlock(sub, (pos, state) -> {
            if (!state.isAir()) {
                count[0]++;
            }

            return true;
        });
        return count[0];
    }

    public static List<BlockEntity> blockEntities(SubLevel sub) {
        if (sub != null && !sub.isRemoved()) {
            int count = 0;

            for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
                count += holder.getChunk().getBlockEntities().size();
            }

            SubLevelBlocks.Cached cached = CACHE.get(sub);
            if (cached != null && cached.count() == count && cached.entities().stream().noneMatch(BlockEntity::isRemoved)) {
                return cached.entities();
            }

            List<BlockEntity> out = new ArrayList<>(count);

            for (PlotChunkHolder holder : sub.getPlot().getLoadedChunks()) {
                out.addAll(holder.getChunk().getBlockEntities().values());
            }

            List<BlockEntity> fresh = Collections.unmodifiableList(out);
            CACHE.put(sub, new SubLevelBlocks.Cached(count, fresh));
            return fresh;
        } else {
            return List.of();
        }
    }

    @Nullable
    public static <T extends BlockEntity> T find(SubLevel sub, Class<T> type) {
        for (BlockEntity be : blockEntities(sub)) {
            if (type.isInstance(be)) {
                return type.cast(be);
            }
        }

        return null;
    }

    public static <T extends BlockEntity> List<T> findAll(SubLevel sub, Class<T> type) {
        List<T> out = new ArrayList<>();

        for (BlockEntity be : blockEntities(sub)) {
            if (type.isInstance(be)) {
                out.add(type.cast(be));
            }
        }

        return out;
    }

    @FunctionalInterface
    public interface BlockVisitor {
        boolean visit(BlockPos var1, BlockState var2);
    }

    private record Cached(int count, List<BlockEntity> entities) {
    }
}
