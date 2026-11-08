package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.content.boiler.barrel.BarrelPipeConnector;
import com.lycoris.loconautics.content.boiler.firebox.FireboxBlockEntity;
import com.lycoris.loconautics.content.boiler.firebox.FireboxGroups;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class BoilerBodyInlet implements IFluidHandler {
    private static final int CACHE_TICKS = 20;
    private final Level level;
    private final BlockPos pos;
    @Nullable
    private BlockPos cachedController;
    private long cachedAt = Long.MIN_VALUE;

    public BoilerBodyInlet(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos.immutable();
    }

    @Nullable
    private FireboxBlockEntity controller() {
        if (this.level.isClientSide) {
            return null;
        }

        long now = this.level.getGameTime();
        if (this.cachedController == null || now - this.cachedAt >= 20L || !(this.level.getBlockEntity(this.cachedController) instanceof FireboxBlockEntity)) {
            this.cachedController = this.findController();
            this.cachedAt = now;
        }

        return this.cachedController != null
                && this.level.getBlockEntity(this.cachedController) instanceof FireboxBlockEntity be
                && be.getWaterTank().getCapacity() > 0
            ? be
            : null;
    }

    @Nullable
    private BlockPos findController() {
        Set<BlockPos> seen = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        seen.add(this.pos);
        frontier.add(this.pos);

        while (!frontier.isEmpty() && seen.size() <= 256) {
            BlockPos current = frontier.poll();
            Axis axis = BarrelPipeConnector.bodyAxis(this.level.getBlockState(current));

            for (Direction dir : Direction.values()) {
                BlockPos next = current.relative(dir);
                BlockState state = this.level.getBlockState(next);
                if (state.is(BoilerTags.BOILER_FIREBOX)) {
                    BlockPos owner = FireboxGroups.controllerOf(this.level, next);
                    if (owner != null) {
                        return owner;
                    }
                } else {
                    if (axis != null && dir.getAxis() == axis && this.offsetFirebox(next, dir) != null) {
                        return this.offsetFirebox(next, dir);
                    }

                    if (state.is(BoilerTags.BOILER_BODY) && (axis == null || dir.getAxis() == axis) && seen.add(next)) {
                        frontier.add(next);
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos offsetFirebox(BlockPos end, Direction along) {
        for (Direction side : Direction.values()) {
            if (side.getAxis() != along.getAxis()) {
                BlockPos beside = end.relative(side);
                if (this.level.getBlockState(beside).is(BoilerTags.BOILER_FIREBOX)) {
                    BlockPos owner = FireboxGroups.controllerOf(this.level, beside);
                    if (owner != null) {
                        return owner;
                    }
                }
            }
        }

        return null;
    }

    public int getTanks() {
        return 1;
    }

    public FluidStack getFluidInTank(int tank) {
        FireboxBlockEntity firebox = this.controller();
        return firebox == null ? FluidStack.EMPTY : firebox.getWaterTank().getFluid();
    }

    public int getTankCapacity(int tank) {
        FireboxBlockEntity firebox = this.controller();
        return firebox == null ? 0 : firebox.getWaterTank().getCapacity();
    }

    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.getFluid() == Fluids.WATER && this.controller() != null;
    }

    public int fill(FluidStack resource, FluidAction action) {
        FireboxBlockEntity firebox = this.controller();
        return firebox == null ? 0 : firebox.fillFromHose(resource, action);
    }

    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
