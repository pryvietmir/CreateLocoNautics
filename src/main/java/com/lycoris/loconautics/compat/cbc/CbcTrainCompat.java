package com.lycoris.loconautics.compat.cbc;

import com.lycoris.loconautics.common.impact.ImpactSpread;
import com.lycoris.loconautics.common.impact.TrainImpacts;
import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.ProjectileContext;

public final class CbcTrainCompat {
    private static final double MIN_SPEED = 1.0E-4;

    private CbcTrainCompat() {
    }

    public static void onShellImpact(Level level, HitResult hit, ProjectileContext ctx) {
        if (level instanceof ServerLevel serverLevel) {
            AbstractCannonProjectile projectile = ctx.projectile;
            Vec3 velocity = projectile.getDeltaMovement();
            double momentum = projectile.getProjectileMass() * velocity.length();
            Vec3 dir = direction(velocity);
            if ((Boolean)LoconauticsConfig.CBC_GLOBAL_KNOCKBACK_ENABLED.get()) {
                TrainKnockback.applyKnockbackToAllSubLevels(serverLevel, impactDeltaV(momentum, dir));
            }

            if ((Boolean)LoconauticsConfig.CBC_DERAIL_ENABLED.get()) {
                SubLevel sub = Sable.HELPER.getContaining(level, hit.getLocation());
                if (sub != null) {
                    SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
                    if (train != null && train.car() != null) {
                        ServerSubLevel struck = SubLevelData.resolve(serverLevel, train.car().subLevelId());
                        if (struck != null) {
                            Vector3d deltaV = impactDeltaV(momentum, dir);
                            TrainImpacts.throwOff(train, struck, deltaV);
                            ImpactSpread.spread(serverLevel, train, deltaV, true);
                        }
                    }
                }
            }
        }
    }

    public static void onShellFired(Level level, AbstractCannonProjectile projectile) {
        if (level instanceof ServerLevel && (Boolean)LoconauticsConfig.CBC_RECOIL_ENABLED.get()) {
            Vec3 velocity = projectile.getDeltaMovement();
            double speed = velocity.length();
            if (!(speed < 1.0E-4)) {
                Vec3 dir = direction(velocity);
                SubLevel sub = Sable.HELPER.getContaining(level, projectile.position().subtract(dir.scale(1.5)));
                if (sub != null) {
                    SableTrain train = SableTrainRegistry.bySubLevel(sub.getUniqueId());
                    if (train != null) {
                        double recoilSpeed = projectile.getProjectileMass() * speed * (Double)LoconauticsConfig.CBC_RECOIL_MOMENTUM_SCALE.get();
                        Vec3 recoil = dir.scale(-recoilSpeed);
                        TrainKnockback.applyRailRecoil(train, new Vector3d(recoil.x, recoil.y, recoil.z));
                    }
                }
            }
        }
    }

    private static Vec3 direction(Vec3 velocity) {
        double speed = velocity.length();
        return speed > 1.0E-4 ? velocity.scale(1.0 / speed) : new Vec3(0.0, 1.0, 0.0);
    }

    private static Vector3d impactDeltaV(double momentum, Vec3 dir) {
        double horizontal = Math.max(
            momentum * (Double)LoconauticsConfig.CBC_KNOCKBACK_MOMENTUM_SCALE.get(), (Double)LoconauticsConfig.CBC_KNOCKBACK_BASE_SPEED.get()
        );
        return new Vector3d(dir.x * horizontal, dir.y * horizontal + horizontal * (Double)LoconauticsConfig.CBC_KNOCKBACK_UPWARD.get(), dir.z * horizontal);
    }
}
