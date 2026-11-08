package com.lycoris.loconautics.content.ghostwheel;

import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback.CollisionResult;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class GhostWheelCollisionCallback implements BlockSubLevelCollisionCallback {
    public static final GhostWheelCollisionCallback INSTANCE = new GhostWheelCollisionCallback();

    private GhostWheelCollisionCallback() {
    }

    private static boolean isOwnCar(ServerLevel level, BlockPos padPos, BlockPos otherPos) {
        UUID padSub = subLevelIdAt(level, padPos);
        UUID otherSub = subLevelIdAt(level, otherPos);
        if (padSub != null && otherSub != null) {
            UUID parent = GhostWheelBodies.parentCarOf(padSub);
            return otherSub.equals(parent);
        } else {
            return false;
        }
    }

    private static UUID subLevelIdAt(ServerLevel level, BlockPos pos) {
        return SableCompanion.INSTANCE.getContaining(level, pos.getX() >> 4, pos.getZ() >> 4) instanceof ServerSubLevel sub ? sub.getUniqueId() : null;
    }

    public CollisionResult sable$onCollision(BlockPos blockPos, @Nullable BlockPos otherBlockPos, Vector3d hitPos, double impactVelocity) {
        if (otherBlockPos == null) {
            return new CollisionResult(new Vector3d(), true);
        }

        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null) {
            return CollisionResult.NONE;
        }

        ServerLevel level = system.getLevel();
        if (level == null) {
            return CollisionResult.NONE;
        }

        if (isOwnCar(level, blockPos, otherBlockPos)) {
            return new CollisionResult(new Vector3d(), true);
        }

        if (subLevelIdAt(level, otherBlockPos) == null) {
            UUID padSub = subLevelIdAt(level, blockPos);
            if (padSub != null && GhostWheelBodies.isRailedPlate(padSub)) {
                return new CollisionResult(new Vector3d(), true);
            }
        }

        return CollisionResult.NONE;
    }
}
