package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.common.BogeyGroundDrop;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateBase.class)
public abstract class BogeyGroundOffsetMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
    private void loconautics$groundedBogey(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        if (this.getBlock() instanceof AbstractBogeyBlock) {
            float drop = BogeyGroundDrop.of(level.getBlockEntity(pos));
            if (drop > 0.0F) {
                cir.setReturnValue(new Vec3(0.0, -drop, 0.0));
            }
        }
    }
}
