package com.lycoris.loconautics.mixin.sable.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline", remap = false)
public abstract class SubLevelFluidNotSolidMixin {
    private static final int EMPTY_VOXEL = 0;
    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(
        method = "handleChunkSectionAddition",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;addChunk(JIII[IZI)V")
    )
    private void loconautics$dropPlotFluidFromChunk(
        long scene, int sectionX, int sectionY, int sectionZ, int[] voxels, boolean global, int bodyId, Operation<Void> original
    ) {
        if (!global) {
            for (int i = 0; i < voxels.length; i++) {
                if (voxels[i] != 0) {
                    BlockPos pos = new BlockPos((sectionX << 4) + (i & 15), (sectionY << 4) + (i >> 8), (sectionZ << 4) + (i >> 4 & 15));
                    if (VoxelNeighborhoodState.isLiquid(this.level.getBlockState(pos))) {
                        voxels[i] = 0;
                    }
                }
            }
        }

        original.call(new Object[]{scene, sectionX, sectionY, sectionZ, voxels, global, bodyId});
    }

    @WrapOperation(method = "handleBlockChange", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;changeBlock(JIIII)V"))
    private void loconautics$dropPlotFluidFromChange(long scene, int x, int y, int z, int voxel, Operation<Void> original) {
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        BlockPos pos = new BlockPos(x, y, z);
        boolean inPlot = container != null && container.inBounds(x >> 4, z >> 4);
        int value = inPlot && VoxelNeighborhoodState.isLiquid(this.level.getBlockState(pos)) ? 0 : voxel;
        original.call(new Object[]{scene, x, y, z, value});
    }
}
