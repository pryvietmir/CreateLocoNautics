package com.lycoris.loconautics.mixin.create.train;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.trains.entity.Carriage$DimensionalCarriageEntity", remap = false)
public abstract class CarriageAnchorGuardMixin {
    @Shadow
    public Vec3 positionAnchor;

    @Inject(method = "alignEntity", at = @At("HEAD"), cancellable = true)
    private void loconautics$skipWithoutAnchor(CarriageContraptionEntity entity, CallbackInfo ci) {
        if (this.positionAnchor == null) {
            ci.cancel();
        }
    }
}
