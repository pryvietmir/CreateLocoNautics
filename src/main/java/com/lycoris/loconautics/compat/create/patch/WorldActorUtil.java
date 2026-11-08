package com.lycoris.loconautics.compat.create.patch;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

final class WorldActorUtil {
    private WorldActorUtil() {
    }

    static Direction snapToCardinal(Vec3 v) {
        double ax = Math.abs(v.x());
        double ay = Math.abs(v.y());
        double az = Math.abs(v.z());
        if (ay >= ax && ay >= az) {
            return v.y() > 0.0 ? Direction.UP : Direction.DOWN;
        } else if (ax >= az) {
            return v.x() > 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            return v.z() > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    static long posKey(UUID subLevelId, BlockPos local) {
        return subLevelId.getLeastSignificantBits() ^ local.asLong() * -7046029254386353131L;
    }

    @Nullable
    static WorldActorUtil.ForwardMotion advanced(ServerSubLevel subLevel, BlockPos localPos, Direction localFacing, Map<Long, Vec3> lastCenter) {
        Vec3 localCenter = Vec3.atCenterOf(localPos);
        Vec3 worldCenter = subLevel.logicalPose().transformPosition(localCenter);
        Vec3 worldAhead = subLevel.logicalPose().transformPosition(localCenter.add(localFacing.getStepX(), 0.0, localFacing.getStepZ()));
        Vec3 worldAbove = subLevel.logicalPose().transformPosition(localCenter.add(0.0, 1.0, 0.0));
        Direction worldFacing = snapToCardinal(worldAhead.subtract(worldCenter));
        Direction worldUp = snapToCardinal(worldAbove.subtract(worldCenter));
        Vec3 prev = lastCenter.put(posKey(subLevel.getUniqueId(), localPos), worldCenter);
        if (prev == null) {
            return null;
        }

        if (worldCenter.distanceToSqr(prev) < 1.0E-6) {
            return null;
        }

        Vec3 facingVec = new Vec3(worldFacing.getStepX(), worldFacing.getStepY(), worldFacing.getStepZ());
        return worldCenter.subtract(prev).dot(facingVec) <= 0.0 ? null : new WorldActorUtil.ForwardMotion(worldCenter, worldFacing, worldUp);
    }

    static void invalidate(Map<Long, Vec3> lastCenter, UUID subLevelId) {
        long prefix = subLevelId.getLeastSignificantBits();
        lastCenter.keySet().removeIf(k -> (k ^ prefix) < 4294967296L);
    }

    record ForwardMotion(Vec3 worldCenter, Direction worldFacing, Direction worldUp) {
    }
}
