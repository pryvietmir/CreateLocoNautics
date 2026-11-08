package com.lycoris.loconautics.content.schedule.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;

public final class ChunkMapColorSampler {
    private ChunkMapColorSampler() {
    }

    public static byte[] sample(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = (LevelChunk)level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false);
        if (chunk == null) {
            return new byte[256];
        }

        int baseX = chunkPos.getMinBlockX();
        int baseZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        double[] heights = new double[272];
        int[] packed = new int[272];
        int[] depth = new int[272];
        boolean[] water = new boolean[272];
        MutableBlockPos pos = new MutableBlockPos();
        MutableBlockPos pos2 = new MutableBlockPos();

        for (int lz = -1; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int idx = (lz + 1) * 16 + lx;
                int y = chunk.getHeight(Types.WORLD_SURFACE, wx, wz) + 1;
                int waterDepth = 0;
                BlockState state;
                if (y <= minY + 1) {
                    state = Blocks.BEDROCK.defaultBlockState();
                } else {
                    do {
                        pos.set(wx, --y, wz);
                        state = chunk.getBlockState(pos);
                    } while (state.getMapColor(level, pos) == MapColor.NONE && y > minY);

                    if (y > minY && !state.getFluidState().isEmpty()) {
                        int yy = y - 1;
                        pos2.set(pos);

                        BlockState below;
                        do {
                            pos2.setY(yy--);
                            below = chunk.getBlockState(pos2);
                            waterDepth++;
                        } while (yy > minY && !below.getFluidState().isEmpty());

                        state = correctFluidState(level, state, pos);
                    }
                }

                MapColor color = state.getMapColor(level, pos);
                heights[idx] = y;
                packed[idx] = color.id;
                depth[idx] = waterDepth;
                water[idx] = color == MapColor.WATER;
            }
        }

        byte[] colors = new byte[256];

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                int wz = baseZ + lz;
                int idx = (lz + 1) * 16 + lx;
                int north = lz * 16 + lx;
                int id = packed[idx];
                if (id == MapColor.NONE.id) {
                    colors[lz * 16 + lx] = 0;
                } else {
                    Brightness b;
                    if (water[idx]) {
                        double d2 = depth[idx] * 0.1 + (wx + wz & 1) * 0.2;
                        b = d2 < 0.5 ? Brightness.HIGH : (d2 > 0.9 ? Brightness.LOW : Brightness.NORMAL);
                    } else {
                        double d3 = (heights[idx] - heights[north]) * 4.0 / 5.0 + ((wx + wz & 1) - 0.5) * 0.4;
                        b = d3 > 0.6 ? Brightness.HIGH : (d3 < -0.6 ? Brightness.LOW : Brightness.NORMAL);
                    }

                    colors[lz * 16 + lx] = MapColor.byId(id).getPackedId(b);
                }
            }
        }

        return colors;
    }

    private static BlockState correctFluidState(ServerLevel level, BlockState state, BlockPos pos) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
    }
}
