package com.lycoris.loconautics.common.strand;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public interface StrandTemplateKind {
    @Nullable
    CompoundTag snapshot(ServerLevel var1, BlockEntity var2, UUID var3);

    boolean restore(ServerLevel var1, BlockEntity var2, BlockEntity var3, CompoundTag var4);

    @Nullable
    StrandTemplateState stateOf(BlockEntity var1);

    default boolean farMayLackRecord() {
        return false;
    }
}
