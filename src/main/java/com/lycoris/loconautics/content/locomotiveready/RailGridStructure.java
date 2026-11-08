package com.lycoris.loconautics.content.locomotiveready;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationContext;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationStub;
import net.minecraft.world.level.levelgen.structure.Structure.StructureSettings;

public final class RailGridStructure extends Structure {
    public static final MapCodec<RailGridStructure> CODEC = simpleCodec(RailGridStructure::new);

    public RailGridStructure(StructureSettings settings) {
        super(settings);
    }

    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!(context.chunkGenerator() instanceof CheckerFlatGenerator)) {
            return Optional.empty();
        }

        ChunkPos chunk = context.chunkPos();
        int ox = chunk.getMiddleBlockX();
        int oz = chunk.getMiddleBlockZ();
        int oy = 64;
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(new BlockPos(ox, oy, oz), builder -> builder.addPiece(new RailGridPiece(ox, oy, oz, seed))));
    }

    public StructureType<?> type() {
        return (StructureType<?>)LocomotiveReadyWorld.RAIL_GRID.get();
    }
}
