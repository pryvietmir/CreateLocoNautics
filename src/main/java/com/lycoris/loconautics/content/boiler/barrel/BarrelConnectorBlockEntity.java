package com.lycoris.loconautics.content.boiler.barrel;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class BarrelConnectorBlockEntity extends BlockEntity {
    public static final int BUFFER_MB = 1000;
    private final FluidTank buffer = new FluidTank(1000, stack -> stack.getFluid() == Fluids.WATER) {
        protected void onContentsChanged() {
            BarrelConnectorBlockEntity.this.setChanged();
        }
    };
    private final IFluidHandler handler = new BarrelConnectorBlockEntity.Inlet();

    public BarrelConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public IFluidHandler getFluidHandler() {
        return this.handler;
    }

    public int drainBuffer(int millibuckets) {
        return millibuckets <= 0 ? 0 : this.buffer.drain(millibuckets, FluidAction.EXECUTE).getAmount();
    }

    protected void saveAdditional(CompoundTag tag, Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Buffer", this.buffer.writeToNBT(registries, new CompoundTag()));
    }

    protected void loadAdditional(CompoundTag tag, Provider registries) {
        super.loadAdditional(tag, registries);
        this.buffer.readFromNBT(registries, tag.getCompound("Buffer"));
    }

    private final class Inlet implements IFluidHandler {
        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int tank) {
            return BarrelConnectorBlockEntity.this.buffer.getFluid();
        }

        public int getTankCapacity(int tank) {
            return BarrelConnectorBlockEntity.this.buffer.getCapacity();
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid() == Fluids.WATER;
        }

        public int fill(FluidStack resource, FluidAction action) {
            return BarrelConnectorBlockEntity.this.buffer.fill(resource, action);
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
