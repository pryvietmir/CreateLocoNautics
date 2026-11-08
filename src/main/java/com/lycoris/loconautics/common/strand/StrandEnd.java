package com.lycoris.loconautics.common.strand;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public record StrandEnd(@Nullable UUID subLevelId, BlockPos local) {
    public boolean matches(@Nullable UUID sub, @Nullable BlockPos pos) {
        return Objects.equals(this.subLevelId, sub) && this.local.equals(pos);
    }
}
