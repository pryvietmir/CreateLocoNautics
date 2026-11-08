package com.lycoris.loconautics.client.copycat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface CopycatMaterialSource {
    @Nullable
    BlockState copycatMaterial(BlockAndTintGetter var1, BlockPos var2, BlockState var3);
}
