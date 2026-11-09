package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.trainphysics.fx.SableTrainCollisionParticles;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerLevel.class, remap = false)
public abstract class ServerLevelCollisionParticleMixin {
    @Inject(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I", at = @At("HEAD"), cancellable = true)
    private void loconautics$suppressTrainCollisionParticles(
        ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed, CallbackInfoReturnable<Integer> cir
    ) {
        if (type instanceof BlockParticleOption block) {
            if (block.getState().getBlock() instanceof GhostWheelBlock) {
                cir.setReturnValue(0);
            } else if (SubLevelPhysicsSystem.currentlySteppingSystem != null) {
                if (SableTrainCollisionParticles.withinTrain((ServerLevel)(Object)this, x, y, z)) {
                    cir.setReturnValue(0);
                }
            }
        }
    }
}
