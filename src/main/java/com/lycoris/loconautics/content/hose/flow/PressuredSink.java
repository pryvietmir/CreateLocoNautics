package com.lycoris.loconautics.content.hose.flow;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public interface PressuredSink extends IFluidHandler {
    int fill(FluidStack var1, float var2, FluidAction var3);

    void pull(float var1);

    @Nullable
    default IFluidHandler endTank() {
        return null;
    }
}
