package com.lycoris.loconautics.content.hose;

import com.lycoris.loconautics.common.strand.StrandHolder;
import org.jetbrains.annotations.Nullable;

public interface HoseStrandHolder extends StrandHolder {
    boolean canAttachStrand();

    void onStrandAttached(HoseStrand var1);

    void onStrandDetached();

    @Nullable
    HoseStrand getAttachedStrand();
}
