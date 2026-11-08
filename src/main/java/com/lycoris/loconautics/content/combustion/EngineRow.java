package com.lycoris.loconautics.content.combustion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class EngineRow {
    private static final int SCAN_LIMIT = 5;

    private EngineRow() {
    }

    public static boolean isEngine(BlockGetter level, BlockPos pos, Direction facing) {
        if (level instanceof LevelReader reader && !reader.hasChunkAt(pos)) {
            return false;
        } else {
            BlockState state = level.getBlockState(pos);
            return state.getBlock() instanceof SmallEngineBlock && state.getValue(SmallEngineBlock.HORIZONTAL_FACING) == facing;
        }
    }

    public static boolean joinsToward(BlockState state, Direction facing, Direction dir) {
        EnginePart part = (EnginePart)state.getValue(SmallEngineBlock.PART);
        boolean both = part == EnginePart.MIDDLE;
        return dir == facing ? both || part == EnginePart.BACK : both || part == EnginePart.FRONT;
    }

    public static EnginePart partOf(boolean joinAhead, boolean joinBehind) {
        return joinAhead ? (joinBehind ? EnginePart.MIDDLE : EnginePart.BACK) : (joinBehind ? EnginePart.FRONT : EnginePart.SINGLE);
    }

    public static boolean linked(BlockGetter level, BlockPos pos, BlockState self, Direction facing, Direction dir) {
        BlockPos next = pos.relative(dir);
        return isEngine(level, next, facing) && joinsToward(self, facing, dir) && joinsToward(level.getBlockState(next), facing, dir.getOpposite());
    }

    public static boolean linked(BlockGetter level, BlockPos pos, Direction facing, Direction dir) {
        return linked(level, pos, level.getBlockState(pos), facing, dir);
    }

    public static int lengthAround(BlockGetter level, BlockPos pos, Direction facing) {
        return 1 + reach(level, pos, facing, facing) + reach(level, pos, facing.getOpposite(), facing);
    }

    public static BlockPos headOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos head = pos;

        for (int i = 0; i < 5 && linked(level, head, facing, facing); i++) {
            head = head.relative(facing);
        }

        return head;
    }

    @Nullable
    public static BlockPos survivorNear(BlockGetter level, BlockPos gone) {
        for (int distance = 1; distance < 3; distance++) {
            for (Direction side : Plane.HORIZONTAL) {
                BlockPos candidate = gone.relative(side, distance);
                if (level.getBlockState(candidate).getBlock() instanceof SmallEngineBlock) {
                    Direction facing = (Direction)level.getBlockState(candidate).getValue(SmallEngineBlock.HORIZONTAL_FACING);
                    if (facing.getAxis() == side.getAxis() && isEngine(level, candidate, facing)) {
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    public static BlockPos tailOf(BlockGetter level, BlockPos pos, Direction facing) {
        BlockPos tail = pos;

        for (int i = 0; i < 5 && linked(level, tail, facing, facing.getOpposite()); i++) {
            tail = tail.relative(facing.getOpposite());
        }

        return tail;
    }

    public static List<BlockPos> membersFrom(BlockGetter level, BlockPos head, Direction facing) {
        List<BlockPos> members = new ArrayList<>(3);
        BlockPos at = head;

        for (int i = 0; i < 3 && isEngine(level, at, facing); i++) {
            members.add(at);
            if (!linked(level, at, facing, facing.getOpposite())) {
                break;
            }

            at = at.relative(facing.getOpposite());
        }

        return members;
    }

    public static int freeEndLength(BlockGetter level, BlockPos pos, Direction facing, Direction towardUs) {
        return isEngine(level, pos, facing) && !joinsToward(level.getBlockState(pos), facing, towardUs)
            ? 1 + reach(level, pos, towardUs.getOpposite(), facing)
            : 0;
    }

    private static int reach(BlockGetter level, BlockPos pos, Direction dir, Direction facing) {
        int steps = 0;
        BlockPos at = pos;

        while (steps < 5 && linked(level, at, facing, dir)) {
            steps++;
            at = at.relative(dir);
        }

        return steps;
    }
}
