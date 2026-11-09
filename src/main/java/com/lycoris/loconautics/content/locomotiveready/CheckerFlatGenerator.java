package com.lycoris.loconautics.content.locomotiveready;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.GenerationStep.Carving;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

public final class CheckerFlatGenerator extends ChunkGenerator {
    public static final int SURFACE_Y = 63;
    public static final MapCodec<CheckerFlatGenerator> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(Biome.CODEC.fieldOf("biome").forGetter(g -> g.biome), Codec.intRange(1, 64).fieldOf("tile_size").forGetter(g -> g.tileSize))
            .apply(i, CheckerFlatGenerator::new)
    );
    private static final ResourceKey<StructureSet> RAIL_GRID_SET = ResourceKey.create(
        Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath("loconautics", "rail_grid")
    );
    private static final BlockState LIGHT = Blocks.SNOW_BLOCK.defaultBlockState();
    private static final BlockState DARK = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private final Holder<Biome> biome;
    private final int tileSize;

    public CheckerFlatGenerator(Holder<Biome> biome, int tileSize) {
        super(new FixedBiomeSource(biome), b -> BiomeGenerationSettings.EMPTY);
        this.biome = biome;
        this.tileSize = tileSize;
    }

    private BlockState tile(int x, int z) {
        int lx = Math.floorMod(x, this.tileSize);
        int lz = Math.floorMod(z, this.tileSize);
        int ring = Math.min(Math.min(lx, this.tileSize - 1 - lx), Math.min(lz, this.tileSize - 1 - lz));
        int parity = Math.floorDiv(x, this.tileSize) + Math.floorDiv(z, this.tileSize) + ring;
        return (parity & 1) == 0 ? LIGHT : DARK;
    }

    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        Stream<Holder<StructureSet>> railGrid = structureSets.get(RAIL_GRID_SET).<Stream<Holder<StructureSet>>>map(ref -> Stream.of((Holder<StructureSet>)ref)).orElseGet(Stream::empty);
        return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, railGrid);
    }

    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        MutableBlockPos pos = new MutableBlockPos();
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Types.WORLD_SURFACE_WG);
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                BlockState fill = this.tile(minX + dx, minZ + dz);

                for (int y = 0; y <= 63; y++) {
                    BlockState state = y == 0 ? BEDROCK : fill;
                    chunk.setBlockState(pos.set(dx, y, dz), state, false);
                    floor.update(dx, y, dz, state);
                    surface.update(dx, y, dz, state);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    public void applyCarvers(
        WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, Carving step
    ) {
    }

    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    public int getSpawnHeight(LevelHeightAccessor level) {
        return 64;
    }

    public int getGenDepth() {
        return 256;
    }

    public int getSeaLevel() {
        return 63;
    }

    public int getMinY() {
        return 0;
    }

    public int getBaseHeight(int x, int z, Types type, LevelHeightAccessor level, RandomState random) {
        return 64;
    }

    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState fill = this.tile(x, z);
        BlockState[] column = new BlockState[64];

        for (int y = 0; y < column.length; y++) {
            column[y] = y == 0 ? BEDROCK : fill;
        }

        return new NoiseColumn(0, column);
    }

    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
    }
}
