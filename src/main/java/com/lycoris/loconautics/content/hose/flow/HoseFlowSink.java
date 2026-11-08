package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.content.steelcable.SteelCableStrand;
import com.lycoris.loconautics.content.steelcable.SteelCableStrandManager;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public final class HoseFlowSink implements IFluidHandler {
    private final IFluidHandler delegate;
    private final List<HoseFlowSink.Hop> hops;
    private final Level level;

    public HoseFlowSink(IFluidHandler delegate, List<HoseFlowSink.Hop> hops, Level level) {
        this.delegate = delegate;
        this.hops = hops;
        this.level = level;
    }

    public static IFluidHandler unwrap(IFluidHandler handler) {
        return handler instanceof HoseFlowSink sink ? sink.delegate : handler;
    }

    @Nullable
    public static IFluidHandler tankOf(@Nullable IFluidHandler handler) {
        IFluidHandler plain = handler == null ? null : unwrap(handler);
        return plain instanceof PressuredSink pressured && pressured.endTank() != null ? pressured.endTank() : plain;
    }

    public int getTanks() {
        return this.delegate.getTanks();
    }

    public FluidStack getFluidInTank(int tank) {
        return this.delegate.getFluidInTank(tank);
    }

    public int getTankCapacity(int tank) {
        return this.delegate.getTankCapacity(tank);
    }

    public boolean isFluidValid(int tank, FluidStack stack) {
        return this.delegate.isFluidValid(tank, stack);
    }

    public int fill(FluidStack resource, FluidAction action) {
        return this.markFlow(this.delegate.fill(resource, action), action);
    }

    public int fill(FluidStack resource, float strength, FluidAction action) {
        int moved = this.delegate instanceof PressuredSink pressured ? pressured.fill(resource, strength, action) : this.delegate.fill(resource, action);
        return this.markFlow(moved, action);
    }

    public int fillPushed(FluidStack resource, float pushRpm, FluidAction action) {
        double length = this.hoseLength();
        return HosePressure.reaches(pushRpm, length) ? this.fill(resource, HosePressure.strength(pushRpm, length), action) : 0;
    }

    public boolean pull(float pullRpm) {
        double length = this.hoseLength();
        if (!HosePressure.reaches(pullRpm, length)) {
            return false;
        }

        if (this.delegate instanceof PressuredSink pressured) {
            pressured.pull(HosePressure.strength(pullRpm, length));
        }

        return true;
    }

    private double hoseLength() {
        if (this.level instanceof ServerLevel serverLevel) {
            SteelCableStrandManager var8 = SteelCableStrandManager.get(serverLevel);
            double total = 0.0;

            for (HoseFlowSink.Hop hop : this.hops) {
                SteelCableStrand strand = var8 == null ? null : var8.get(hop.strandId());
                total += strand == null ? 0.0 : strand.restLength;
            }

            return total;
        } else {
            return 0.0;
        }
    }

    private int markFlow(int moved, FluidAction action) {
        if (moved > 0 && action.execute()) {
            long now = this.level.getGameTime();

            for (HoseFlowSink.Hop hop : this.hops) {
                HoseFlowTracker.mark(hop.strandId(), hop.forward(), now);
            }
        }

        return moved;
    }

    public FluidStack drain(FluidStack resource, FluidAction action) {
        return this.delegate.drain(resource, action);
    }

    public FluidStack drain(int maxDrain, FluidAction action) {
        return this.delegate.drain(maxDrain, action);
    }

    public record Hop(UUID strandId, boolean forward) {
    }
}
