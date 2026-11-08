package com.lycoris.loconautics.trainphysics.fx;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class SableTrainCollisionParticles {
    private static final double MARGIN = 0.75;

    private SableTrainCollisionParticles() {
    }

    public static boolean withinTrain(ServerLevel level, double x, double y, double z) {
        if (SableTrainRegistry.isEmpty()) {
            return false;
        }

        if (SubLevelContainer.getContainer(level) instanceof ServerSubLevelContainer container) {
            for (SableTrain train : SableTrainRegistry.all()) {
                if (train.level() == level) {
                    UUID id = train.car().subLevelId();
                    if (id != null && container.getSubLevel(id) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                        BoundingBox3dc b = sub.boundingBox();
                        if (b != null
                            && x >= b.minX() - 0.75
                            && x <= b.maxX() + 0.75
                            && y >= b.minY() - 0.75
                            && y <= b.maxY() + 0.75
                            && z >= b.minZ() - 0.75
                            && z <= b.maxZ() + 0.75) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }
}
