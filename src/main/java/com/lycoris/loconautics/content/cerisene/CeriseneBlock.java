package com.lycoris.loconautics.content.cerisene;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class CeriseneBlock extends HalfTransparentBlock {
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 4);
    public static final BooleanProperty PRIMED = BooleanProperty.create("primed");
    public static final MapCodec<CeriseneBlock> CODEC = simpleCodec(CeriseneBlock::new);

    public CeriseneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue(HEAT, 0)).setValue(PRIMED, false));
    }

    protected MapCodec<? extends HalfTransparentBlock> codec() {
        return CODEC;
    }

    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(new Property[]{HEAT, PRIMED});
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 20);
        }
    }

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if ((Boolean)state.getValue(PRIMED)) {
            CeriseneExplosion.chainTick(level, pos);
        } else {
            int heat = (Integer)state.getValue(HEAT);
            if (heat == 4 && random.nextFloat() < 0.25F) {
                CeriseneExplosion.explode(level, pos);
            } else {
                int next = CeriseneHeat.nextHeat(level, pos, heat);
                if (next != heat) {
                    level.setBlock(pos, (BlockState)state.setValue(HEAT, next), 3);
                }

                level.scheduleTick(pos, this, 20);
            }
        }
    }

    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int heat = (Integer)state.getValue(HEAT);
        if (heat >= 3) {
            int count = heat == 4 ? 3 : 1;

            for (int i = 0; i < count; i++) {
                if (!(random.nextFloat() > 0.5F)) {
                    Direction face = Direction.getRandom(random);
                    double x = pos.getX() + 0.5 + face.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
                    double y = pos.getY() + 0.5 + face.getStepY() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
                    double z = pos.getZ() + 0.5 + face.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.6;
                    level.addParticle(random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.LAVA, x, y, z, 0.0, 0.03, 0.0);
                }
            }
        }
    }

    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }
}
