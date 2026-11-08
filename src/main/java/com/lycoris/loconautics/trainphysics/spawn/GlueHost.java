package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.Rotation;

public final class GlueHost {
    private GlueHost() {
    }

    public static ServerSubLevel trainOf(ServerSubLevel a, ServerSubLevel b) {
        if (a != null && b != null) {
            boolean trainA = SableTrainRegistry.bySubLevel(a.getUniqueId()) != null;
            boolean trainB = SableTrainRegistry.bySubLevel(b.getUniqueId()) != null;
            if (trainA == trainB) {
                return null;
            } else {
                return trainA ? a : b;
            }
        } else {
            return null;
        }
    }

    public static boolean secondGoes(ServerSubLevel first, ServerSubLevel second) {
        ServerSubLevel train = trainOf(first, second);
        return train != null ? train == first : second.getMassTracker().getMass() < first.getMassTracker().getMass();
    }

    public static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }
}
