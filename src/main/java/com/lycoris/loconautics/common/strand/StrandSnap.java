package com.lycoris.loconautics.common.strand;

import java.util.List;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class StrandSnap {
    private static final float SNAP_VOLUME = 0.8F;

    private StrandSnap() {
    }

    public static void snap(ServerLevel level, ItemStack item, @Nullable List<Vector3d> points, Vector3d at) {
        effects(level, item, points, at);
        Containers.dropItemStack(level, at.x, at.y, at.z, item.copy());
    }

    public static void effects(ServerLevel level, ItemStack item, @Nullable List<Vector3d> points, Vector3d at) {
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        if (points != null) {
            ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, item);

            for (Vector3d p : points) {
                level.sendParticles(particle, p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    @Nullable
    public static Vector3d endOf(@Nullable List<Vector3d> points, boolean first) {
        if (points != null && !points.isEmpty()) {
            Vector3d p = first ? points.get(0) : points.get(points.size() - 1);
            return new Vector3d(p);
        } else {
            return null;
        }
    }
}
