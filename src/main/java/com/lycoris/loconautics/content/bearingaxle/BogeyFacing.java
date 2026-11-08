package com.lycoris.loconautics.content.bearingaxle;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BogeyFacing {
    private BogeyFacing() {
    }

    @Nullable
    public static Axis axisOf(BlockState state) {
        return state.getBlock() instanceof AbstractBogeyBlock && state.hasProperty(AbstractBogeyBlock.AXIS)
            ? (Axis)state.getValue(AbstractBogeyBlock.AXIS)
            : null;
    }

    @Nullable
    public static Direction defaultFront(BlockState state) {
        Axis axis = axisOf(state);
        if (axis == null) {
            return null;
        } else {
            return axis == Axis.X ? Direction.EAST : Direction.SOUTH;
        }
    }

    @Nullable
    public static Direction front(BlockState state, @Nullable Direction override) {
        Axis axis = axisOf(state);
        if (axis == null) {
            return null;
        } else {
            return override != null && override.getAxis() == axis ? override : defaultFront(state);
        }
    }

    public static boolean onAxis(BlockState state, Direction dir) {
        Axis axis = axisOf(state);
        return axis != null && dir.getAxis() == axis;
    }
}
