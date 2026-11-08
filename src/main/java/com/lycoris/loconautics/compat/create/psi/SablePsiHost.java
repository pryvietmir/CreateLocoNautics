package com.lycoris.loconautics.compat.create.psi;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface SablePsiHost {
    void loconautics$link(@Nullable UUID var1, BlockPos var2);

    void loconautics$unlink();

    boolean loconautics$linked();

    @Nullable
    UUID loconautics$partnerSubLevel();

    @Nullable
    BlockPos loconautics$partnerPos();

    void loconautics$setDistance(float var1);

    float loconautics$distance();
}
