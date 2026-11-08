package com.lycoris.loconautics.trainphysics.coupling;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

public interface CouplingProvider {
    void collectCoupledSubLevels(ServerLevel var1, UUID var2, Consumer<UUID> var3);

    default void collectWeldedSubLevels(ServerLevel level, UUID subLevelId, Consumer<UUID> neighbours) {
    }

    default void collectTwistLinks(ServerLevel level, UUID subLevelId, Consumer<TwistLink> links) {
    }
}
