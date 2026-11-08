package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

public final class HatchWaterSocket {
    private final BlockEntity physical;
    private final Supplier<Direction> facing;
    private final BooleanSupplier raised;
    private final DoubleSupplier dockSeatOffset;
    private final FluidTank tank;
    private boolean resolving;

    public HatchWaterSocket(BlockEntity physical, Supplier<Direction> facing, BooleanSupplier raised, DoubleSupplier dockSeatOffset, FluidTank tank) {
        this.physical = physical;
        this.facing = facing;
        this.raised = raised;
        this.dockSeatOffset = dockSeatOffset;
        this.tank = tank;
    }

    private Direction dockFace() {
        return this.facing.get().getOpposite();
    }

    public Vec3 dockAnchorLocal() {
        double out = this.dockSeatOffset.getAsDouble() + (this.raised.getAsBoolean() ? 0.5 : 0.0);
        return HorizontalDockPoint.anchorLocal(this.physical, this.dockFace(), out);
    }

    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(this.physical, this.dockAnchorLocal());
    }

    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(this.dockFace());
    }

    @Nullable
    public IFluidHandler getDockedFluidHandler() {
        Level level = this.physical.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }

        if (this.resolving) {
            return this.tank;
        }

        Direction into = this.facing.get();
        this.resolving = true;

        try {
            IFluidHandler downstream = (IFluidHandler)level.getCapability(FluidHandler.BLOCK, this.physical.getBlockPos().relative(into), into.getOpposite());
            return (IFluidHandler)(downstream != null ? downstream : this.tank);
        } finally {
            this.resolving = false;
        }
    }

    private IFluidHandler waterTarget() {
        IFluidHandler handler = this.getDockedFluidHandler();
        return (IFluidHandler)(handler != null ? handler : this.tank);
    }

    public int getWaterAmount() {
        IFluidHandler handler = this.waterTarget();
        int total = 0;

        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.getFluid() == Fluids.WATER) {
                total += stack.getAmount();
            }
        }

        return total;
    }

    public int getWaterCapacity() {
        IFluidHandler handler = this.waterTarget();
        int total = 0;

        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.getFluid() == Fluids.WATER) {
                total += handler.getTankCapacity(i);
            } else if (stack.isEmpty() && handler.isFluidValid(i, new FluidStack(Fluids.WATER, 1))) {
                total += handler.getTankCapacity(i);
            }
        }

        return total;
    }

    public int drawWater(int millibuckets) {
        return millibuckets <= 0 ? 0 : this.waterTarget().drain(new FluidStack(Fluids.WATER, millibuckets), FluidAction.EXECUTE).getAmount();
    }

    public void returnWater(int millibuckets) {
        if (millibuckets > 0) {
            this.waterTarget().fill(new FluidStack(Fluids.WATER, millibuckets), FluidAction.EXECUTE);
        }
    }
}
