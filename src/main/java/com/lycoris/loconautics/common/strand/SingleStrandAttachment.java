package com.lycoris.loconautics.common.strand;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public interface SingleStrandAttachment {
    @Nullable
    UUID rawAttachedStrandId();

    void rawAttachedStrandId(@Nullable UUID var1);

    void markChanged();

    default void setAttachedStrandId(@Nullable UUID id) {
        this.rawAttachedStrandId(id);
        this.markChanged();
    }

    default void writeAttachedStrand(CompoundTag nbt) {
        UUID id = this.rawAttachedStrandId();
        if (id != null) {
            nbt.putUUID("AttachedStrand", id);
        }
    }

    default void readAttachedStrand(CompoundTag nbt) {
        this.rawAttachedStrandId(nbt.hasUUID("AttachedStrand") ? nbt.getUUID("AttachedStrand") : null);
    }
}
