package com.lycoris.loconautics.trainphysics.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class PortalCutLookup {
    private PortalCutLookup() {
    }

    public static boolean hidden(Level level, BlockPos pos) {
        return level.isClientSide()
            ? PortalCutsClient.hiddenAtPlot(pos.getX(), pos.getY(), pos.getZ())
            : level instanceof ServerLevel server && PortalColliders.hiddenAt(server, pos);
    }
}
