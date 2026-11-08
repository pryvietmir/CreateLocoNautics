package com.lycoris.loconautics.common;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface ConnectorDetacher {
    int detachAll(ServerLevel var1, UUID var2);

    default boolean decoupleBetween(ServerLevel level, UUID subA, UUID subB) {
        return false;
    }
}
