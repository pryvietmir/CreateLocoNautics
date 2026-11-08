package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlock;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;

public class GaugeBlock extends PanelBlock implements IBE<GaugeBlockEntity> {
    public static final MapCodec<GaugeBlock> CODEC = simpleCodec(GaugeBlock::new);
    private static final int FRONT_RED = 0;
    private static final int FRONT_LIGHT_GRAY = 1;
    private static final int FRONT_BRASS = 2;

    public GaugeBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    private static BlockState particleBlock(int front) {
        return switch (front) {
            case 0 -> Blocks.REDSTONE_BLOCK.defaultBlockState();
            case 1 -> Blocks.ANDESITE.defaultBlockState();
            case 2 -> lookup("create:brass_block");
            default -> lookup("create:industrial_iron_block");
        };
    }

    private static BlockState lookup(String id) {
        Block block = (Block)BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        return block != null && block != Blocks.AIR ? block.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
    }

    private static BlockState particleStateAt(BlockGetter level, BlockPos pos) {
        PanelBlockEntity panel = panelAt(level, pos);
        if (panel != null) {
            for (PanelSlot slot : PanelSlot.values()) {
                if (panel.getPart(slot) instanceof GaugeBlockEntity.Gauge gauge) {
                    return particleBlock(gauge.front());
                }
            }
        }

        return particleBlock(3);
    }

    private static void spawnBreakParticles(Level level, BlockPos pos, BlockState state, BlockState particle) {
        BlockParticleOption option = new BlockParticleOption(ParticleTypes.BLOCK, particle);
        state.getShape(level, pos)
            .forAllBoxes(
                (x1, y1, z1, x2, y2, z2) -> {
                    int nx = Math.max(1, (int)Math.ceil((x2 - x1) / 0.25));
                    int ny = Math.max(1, (int)Math.ceil((y2 - y1) / 0.25));
                    int nz = Math.max(1, (int)Math.ceil((z2 - z1) / 0.25));

                    for (int i = 0; i < nx; i++) {
                        for (int j = 0; j < ny; j++) {
                            for (int k = 0; k < nz; k++) {
                                double fx = (i + 0.5) / nx;
                                double fy = (j + 0.5) / ny;
                                double fz = (k + 0.5) / nz;
                                level.addParticle(
                                    option,
                                    pos.getX() + x1 + fx * (x2 - x1),
                                    pos.getY() + y1 + fy * (y2 - y1),
                                    pos.getZ() + z1 + fz * (z2 - z1),
                                    fx - 0.5,
                                    fy - 0.5,
                                    fz - 0.5
                                );
                            }
                        }
                    }
                }
            );
    }

    public static IClientBlockExtensions clientExtensions() {
        return new IClientBlockExtensions() {
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
                GaugeBlock.spawnBreakParticles(level, pos, state, GaugeBlock.particleStateAt(level, pos));
                return true;
            }
        };
    }

    public Class<GaugeBlockEntity> getBlockEntityClass() {
        return GaugeBlockEntity.class;
    }

    public BlockEntityType<? extends GaugeBlockEntity> getBlockEntityType() {
        return (BlockEntityType<? extends GaugeBlockEntity>)BoilerBlocks.LOCOMOTIVE_GAUGE_BE.get();
    }
}
