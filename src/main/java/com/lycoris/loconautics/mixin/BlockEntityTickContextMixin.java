package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity", remap = false)
public abstract class BlockEntityTickContextMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    // Fix: this hook runs for every block entity in every loaded chunk, but it only matters for
    // Create's processing block entities (sequenced assembly happens in SmartBlockEntity
    // subclasses: deployers, presses, saws, spouts). A cheap instanceof check skips chests,
    // furnaces, hoppers and every other vanilla/modded block entity.
    @Inject(method = "tick", at = @At("HEAD"))
    private void loconautics$enter(CallbackInfo ci) {
        if (this.blockEntity instanceof SmartBlockEntity && this.blockEntity.getLevel() != null && !this.blockEntity.getLevel().isClientSide) {
            TrainAdvancements.blockEntityTicking(this.blockEntity);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void loconautics$exit(CallbackInfo ci) {
        if (this.blockEntity instanceof SmartBlockEntity) {
            TrainAdvancements.blockEntityTicking(null);
        }
    }
}
