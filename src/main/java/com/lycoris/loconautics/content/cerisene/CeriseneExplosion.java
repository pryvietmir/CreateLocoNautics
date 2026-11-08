package com.lycoris.loconautics.content.cerisene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CeriseneExplosion {
    private static ResourceKey<Level> capDimension;
    private static long capTime = -1L;
    private static int capCount;

    private CeriseneExplosion() {
    }

    public static void explode(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof CeriseneBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            effects(level, pos);

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                BlockState neighbor = level.getBlockState(next);
                if (neighbor.getBlock() instanceof CeriseneBlock && !(Boolean)neighbor.getValue(CeriseneBlock.PRIMED)) {
                    level.setBlock(next, (BlockState)neighbor.setValue(CeriseneBlock.PRIMED, true), 2);
                    level.scheduleTick(next, neighbor.getBlock(), 2);
                }
            }
        }
    }

    public static void chainTick(ServerLevel level, BlockPos pos) {
        long time = level.getGameTime();
        if (capDimension != level.dimension() || capTime != time) {
            capDimension = level.dimension();
            capTime = time;
            capCount = 0;
        }

        if (capCount >= 8) {
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1);
        } else {
            capCount++;
            explode(level, pos);
        }
    }

    private static void effects(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        level.playSound(null, pos, (SoundEvent)SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 20, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 8, 0.4, 0.4, 0.4, 0.02);
        igniteSurroundings(level, pos);
        pushEntities(level, center);
    }

    private static void igniteSurroundings(ServerLevel level, BlockPos pos) {
        int radius = 2;

        for (BlockPos probe : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            BlockPos at = probe.immutable();
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof TntBlock) {
                state.onCaughtFire(level, at, null, null);
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 11);
            } else if (state.isAir() && hasBurnableNeighbor(level, at)) {
                level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
            }
        }
    }

    private static boolean hasBurnableNeighbor(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos next = pos.relative(dir);
            BlockState state = level.getBlockState(next);
            if (state.isFlammable(level, next, dir.getOpposite()) || state.is(level.dimensionType().infiniburn())) {
                return true;
            }
        }

        return false;
    }

    private static void pushEntities(ServerLevel level, Vec3 center) {
        double reach = 3.0;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach))) {
            Vec3 away = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0).subtract(center);
            if (away.lengthSqr() < 1.0E-6) {
                away = new Vec3(0.0, 1.0, 0.0);
            }

            Vec3 push = away.normalize().scale(0.35);
            entity.push(push.x, push.y * 0.5 + 0.1, push.z);
            entity.hurtMarked = true;
            entity.igniteForSeconds(3.0F);
        }
    }
}
