package com.lycoris.loconautics.mixin.sable.placement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.trainphysics.portal.PortalCutLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockPlaceContext.class, priority = 1500, remap = false)
public abstract class BlockPlaceCutMixin {
    @WrapOperation(
        method = "sable$intersectBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState loconautics$skipCutBlocks(Level level, BlockPos pos, Operation<BlockState> original) {
        return PortalCutLookup.hidden(level, pos) ? Blocks.AIR.defaultBlockState() : (BlockState)original.call(new Object[]{level, pos});
    }
}
