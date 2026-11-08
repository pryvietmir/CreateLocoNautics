package com.lycoris.loconautics.trainphysics.fx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;

public final class ParticleWorldLock {
    private static boolean active;

    private ParticleWorldLock() {
    }

    public static boolean active() {
        return active;
    }

    public static void addParticle(Level level, ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
        active = true;

        try {
            level.addParticle(options, x, y, z, vx, vy, vz);
        } finally {
            active = false;
        }
    }
}
