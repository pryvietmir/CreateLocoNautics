package com.lycoris.loconautics.foundation.smoke;

import com.lycoris.loconautics.foundation.particle.ShaderPacks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SmokeEmitter {
    private static final double MAX_MOTION = 1.5;
    private static final int PACK_THINNING = 2;
    private Vec3 at = Vec3.ZERO;
    private Vec3 motion = Vec3.ZERO;
    private boolean tracking;
    private float trailCharge;

    public static Vec3 toWorld(Vec3 local) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(BlockPos.containing(local));
        return sub == null ? local : sub.renderPose().transformPosition(local);
    }

    public void track(Vec3 local) {
        Vec3 world = toWorld(local);
        Vec3 delta = this.tracking ? world.subtract(this.at) : Vec3.ZERO;
        this.motion = delta.lengthSqr() > 2.25 ? Vec3.ZERO : delta;
        this.at = world;
        this.tracking = true;
    }

    public void stop() {
        this.tracking = false;
        this.trailCharge = 0.0F;
        this.motion = Vec3.ZERO;
    }

    public Vec3 position() {
        return this.at;
    }

    public static float thinning() {
        return (ShaderPacks.inUse() ? 2 : 1) / SmokeGovernor.scale();
    }

    public boolean trail(Level level, SmokeOptions options, float perTick) {
        this.trailCharge = this.trailCharge + perTick / thinning();
        if (this.trailCharge < 1.0F) {
            return false;
        }

        this.trailCharge--;
        this.spawn(level, options, 0.0);
        return true;
    }

    public void puff(Level level, SmokeOptions options, int count, double radius) {
        int thinned = Math.max(1, Math.round(count / thinning()));

        for (int i = 0; i < thinned; i++) {
            this.spawn(level, options, radius);
        }
    }

    private void spawn(Level level, SmokeOptions options, double radius) {
        double dx = 0.0;
        double dz = 0.0;
        if (radius > 0.0) {
            double angle = level.random.nextDouble() * (float) (Math.PI * 2);
            double reach = radius * Math.sqrt(level.random.nextDouble());
            dx = Math.cos(angle) * reach;
            dz = Math.sin(angle) * reach;
        }

        level.addAlwaysVisibleParticle(options, true, this.at.x + dx, this.at.y, this.at.z + dz, this.motion.x, this.motion.y, this.motion.z);
    }
}
