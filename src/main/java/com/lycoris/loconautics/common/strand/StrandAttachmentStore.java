package com.lycoris.loconautics.common.strand;

import java.util.UUID;

public interface StrandAttachmentStore {
    void attach(UUID var1);

    void detach(UUID var1);

    boolean holds(UUID var1);

    boolean isFree();
}
