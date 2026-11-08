package com.lycoris.loconautics.mixin.create.train;

import com.lycoris.loconautics.trainphysics.state.CreateTrainShadow;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RailwaySavedData.class, remap = false)
public abstract class RailwaySavedDataShadowMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void loconautics$hideShadowTrains(CompoundTag nbt, Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        CreateTrainShadow.hideForSave();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void loconautics$restoreShadowTrains(CompoundTag nbt, Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        CreateTrainShadow.restoreAfterSave();
    }
}
