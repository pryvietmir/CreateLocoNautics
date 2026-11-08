package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BearingAxleValidity {
    private static final Direction[] BOGEY_DIRS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
    private static final Map<UUID, BearingAxleValidity.Classified> CACHE = new HashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    private BearingAxleValidity() {
    }

    @Nullable
    public static BlockPos driverBogey(BlockGetter level, BlockPos axlePos) {
        for (Direction dir : BOGEY_DIRS) {
            BlockPos pos = axlePos.relative(dir);
            if (level.getBlockState(pos).getBlock() instanceof AbstractBogeyBlock) {
                return pos.immutable();
            }
        }

        return null;
    }

    public static List<BearingAxleBlockEntity> valid(ServerSubLevel sub) {
        if (sub != null && !sub.isRemoved()) {
            long now = sub.getLevel().getGameTime();
            BearingAxleValidity.Classified cached = CACHE.get(sub.getUniqueId());
            if (cached != null && cached.tick() == now) {
                return cached.valid();
            }

            List<BearingAxleBlockEntity> valid = classify(sub.getLevel(), SubLevelBlocks.findAll(sub, BearingAxleBlockEntity.class));
            CACHE.put(sub.getUniqueId(), new BearingAxleValidity.Classified(now, valid));
            return valid;
        } else {
            return List.of();
        }
    }

    public static List<BearingAxleBlockEntity> classify(BlockGetter level, List<BearingAxleBlockEntity> axles) {
        List<BearingAxleBlockEntity> valid = new ArrayList<>();

        for (BearingAxleBlockEntity axle : axles) {
            BlockPos bogey = driverBogey(level, axle.getBlockPos());
            if (bogey == null) {
                axle.setValidity(BearingAxleValidity.Status.NO_BOGEY, null);
            } else if (!BogeyFacing.onAxis(level.getBlockState(bogey), axleFrontLocal(axle))) {
                axle.setValidity(BearingAxleValidity.Status.WRONG_AXIS, bogey);
            } else {
                axle.setValidity(BearingAxleValidity.Status.VALID, bogey);
                valid.add(axle);
            }
        }

        return valid;
    }

    @Nullable
    public static BearingAxleBlockEntity resolve(ServerSubLevel sub) {
        return pick(valid(sub));
    }

    @Nullable
    public static BearingAxleBlockEntity resolve(BlockGetter level, List<BearingAxleBlockEntity> axles) {
        return pick(classify(level, axles));
    }

    @Nullable
    private static BearingAxleBlockEntity pick(List<BearingAxleBlockEntity> valid) {
        BearingAxleBlockEntity best = null;
        double bestStrength = -1.0;

        for (BearingAxleBlockEntity axle : valid) {
            double strength = Math.abs(axle.getSpeed());
            if (best == null || strength > bestStrength || strength == bestStrength && olderThan(axle, best)) {
                best = axle;
                bestStrength = strength;
            }
        }

        return best;
    }

    public static Direction axleFrontLocal(BearingAxleBlockEntity axle) {
        BlockState state = axle.getBlockState();
        return !(state.getBlock() instanceof BearingAxleBlock) ? Direction.NORTH : BearingAxleBlock.front(state);
    }

    private static boolean olderThan(BearingAxleBlockEntity a, BearingAxleBlockEntity b) {
        return a.getPlacedTime() != b.getPlacedTime() ? a.getPlacedTime() < b.getPlacedTime() : a.getBlockPos().asLong() < b.getBlockPos().asLong();
    }

    private record Classified(long tick, List<BearingAxleBlockEntity> valid) {
    }

    public enum Status {
        VALID,
        NO_BOGEY,
        BOGEY_TAKEN,
        WRONG_AXIS;
    }
}
