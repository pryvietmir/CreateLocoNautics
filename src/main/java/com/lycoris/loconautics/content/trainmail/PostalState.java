package com.lycoris.loconautics.content.trainmail;

import java.util.Set;
import java.util.UUID;

public interface PostalState {
    boolean loconautics$tripped();

    void loconautics$setTripped(boolean var1);

    void loconautics$setRepeat(boolean var1);

    boolean loconautics$repeat();

    boolean loconautics$deposit();

    void loconautics$setDeposit(boolean var1);

    Set<UUID> loconautics$warned();
}
