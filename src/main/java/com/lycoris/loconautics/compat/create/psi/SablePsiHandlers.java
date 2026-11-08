package com.lycoris.loconautics.compat.create.psi;

import com.lycoris.loconautics.trainphysics.storage.CarriageStorage;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

public final class SablePsiHandlers {
    private static final int PASS_THROUGH_CAPACITY = 8000;

    private SablePsiHandlers() {
    }

    public static IItemHandlerModifiable carriageItems(ServerSubLevel sub, Runnable onTransfer) {
        return new SablePsiHandlers.NotifyingItems(new SablePsiHandlers.TickCached<>(sub, () -> {
            List<IItemHandlerModifiable> found = CarriageStorage.exposedItems(sub);
            return (IItemHandlerModifiable)(found.isEmpty() ? new ItemStackHandler(0) : new CombinedInvWrapper(found.toArray(new IItemHandlerModifiable[0])));
        }), onTransfer);
    }

    public static IFluidHandler carriageFluids(ServerSubLevel sub, Runnable onTransfer) {
        return new SablePsiHandlers.NotifyingFluids(
            new SablePsiHandlers.TickCached<>(sub, () -> new CombinedTankWrapper(CarriageStorage.exposedFluids(sub).toArray(new IFluidHandler[0]))), onTransfer
        );
    }

    public static IItemHandlerModifiable passThroughItems(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
        return new SablePsiHandlers.PassThroughItems(level, farPos, onTransfer);
    }

    public static IFluidHandler passThroughFluids(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
        return new SablePsiHandlers.PassThroughFluids(level, farPos, onTransfer);
    }

    static List<IItemHandler> itemNeighbors(ServerLevel level, BlockPos pos) {
        List<IItemHandler> out = new ArrayList<>();

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            if (!(level.getBlockEntity(neighbor) instanceof PortableStorageInterfaceBlockEntity)) {
                IItemHandler handler = (IItemHandler)level.getCapability(ItemHandler.BLOCK, neighbor, side.getOpposite());
                if (handler != null) {
                    out.add(handler);
                }
            }
        }

        return out;
    }

    static List<IFluidHandler> fluidNeighbors(ServerLevel level, BlockPos pos) {
        List<IFluidHandler> out = new ArrayList<>();

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);
            if (!(level.getBlockEntity(neighbor) instanceof PortableStorageInterfaceBlockEntity)) {
                IFluidHandler handler = (IFluidHandler)level.getCapability(FluidHandler.BLOCK, neighbor, side.getOpposite());
                if (handler != null) {
                    out.add(handler);
                }
            }
        }

        return out;
    }

    static final class NotifyingFluids implements IFluidHandler {
        private final SablePsiHandlers.TickCached<IFluidHandler> wrapped;
        private final Runnable onTransfer;

        NotifyingFluids(SablePsiHandlers.TickCached<IFluidHandler> wrapped, Runnable onTransfer) {
            this.wrapped = wrapped;
            this.onTransfer = onTransfer;
        }

        public int getTanks() {
            return this.wrapped.get().getTanks();
        }

        public FluidStack getFluidInTank(int tank) {
            return this.wrapped.get().getFluidInTank(tank);
        }

        public int getTankCapacity(int tank) {
            return this.wrapped.get().getTankCapacity(tank);
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.wrapped.get().isFluidValid(tank, stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            int filled = this.wrapped.get().fill(resource, action);
            if (filled > 0 && action.execute()) {
                this.onTransfer.run();
            }

            return filled;
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = this.wrapped.get().drain(resource, action);
            if (!drained.isEmpty() && action.execute()) {
                this.onTransfer.run();
            }

            return drained;
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = this.wrapped.get().drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) {
                this.onTransfer.run();
            }

            return drained;
        }
    }

    static final class NotifyingItems implements IItemHandlerModifiable {
        private final SablePsiHandlers.TickCached<IItemHandlerModifiable> wrapped;
        private final Runnable onTransfer;

        NotifyingItems(SablePsiHandlers.TickCached<IItemHandlerModifiable> wrapped, Runnable onTransfer) {
            this.wrapped = wrapped;
            this.onTransfer = onTransfer;
        }

        public int getSlots() {
            return this.wrapped.get().getSlots();
        }

        public ItemStack getStackInSlot(int slot) {
            return this.wrapped.get().getStackInSlot(slot);
        }

        public void setStackInSlot(int slot, ItemStack stack) {
            this.wrapped.get().setStackInSlot(slot, stack);
        }

        public int getSlotLimit(int slot) {
            return this.wrapped.get().getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return this.wrapped.get().isItemValid(slot, stack);
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack left = this.wrapped.get().insertItem(slot, stack, simulate);
            if (!simulate && !ItemStack.matches(left, stack)) {
                this.onTransfer.run();
            }

            return left;
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack taken = this.wrapped.get().extractItem(slot, amount, simulate);
            if (!simulate && !taken.isEmpty()) {
                this.onTransfer.run();
            }

            return taken;
        }
    }

    static final class PassThroughFluids implements IFluidHandler {
        private final ServerLevel level;
        private final BlockPos farPos;
        private final Runnable onTransfer;

        PassThroughFluids(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
            this.level = level;
            this.farPos = farPos;
            this.onTransfer = onTransfer;
        }

        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        public int getTankCapacity(int tank) {
            return 8000;
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        public int fill(FluidStack resource, FluidAction action) {
            FluidStack left = resource.copy();
            int total = 0;

            for (IFluidHandler handler : SablePsiHandlers.fluidNeighbors(this.level, this.farPos)) {
                int moved = handler.fill(left, action);
                if (moved > 0) {
                    total += moved;
                    left.shrink(moved);
                    if (left.isEmpty()) {
                        break;
                    }
                }
            }

            if (total > 0 && action.execute()) {
                this.onTransfer.run();
            }

            return total;
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            for (IFluidHandler handler : SablePsiHandlers.fluidNeighbors(this.level, this.farPos)) {
                FluidStack drained = handler.drain(resource, action);
                if (!drained.isEmpty()) {
                    if (action.execute()) {
                        this.onTransfer.run();
                    }

                    return drained;
                }
            }

            return FluidStack.EMPTY;
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            for (IFluidHandler handler : SablePsiHandlers.fluidNeighbors(this.level, this.farPos)) {
                FluidStack drained = handler.drain(maxDrain, action);
                if (!drained.isEmpty()) {
                    if (action.execute()) {
                        this.onTransfer.run();
                    }

                    return drained;
                }
            }

            return FluidStack.EMPTY;
        }
    }

    static final class PassThroughItems implements IItemHandlerModifiable {
        private final ServerLevel level;
        private final BlockPos farPos;
        private final Runnable onTransfer;

        PassThroughItems(ServerLevel level, BlockPos farPos, Runnable onTransfer) {
            this.level = level;
            this.farPos = farPos;
            this.onTransfer = onTransfer;
        }

        public int getSlots() {
            return 1;
        }

        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        public void setStackInSlot(int slot, ItemStack stack) {
            throw new UnsupportedOperationException("PassThroughItems has no backing slot to set");
        }

        public int getSlotLimit(int slot) {
            return 64;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            ItemStack left = stack;

            for (IItemHandler handler : SablePsiHandlers.itemNeighbors(this.level, this.farPos)) {
                left = ItemHandlerHelper.insertItemStacked(handler, left, simulate);
                if (left.isEmpty()) {
                    break;
                }
            }

            if (!simulate && left.getCount() != stack.getCount()) {
                this.onTransfer.run();
            }

            return left;
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            for (IItemHandler handler : SablePsiHandlers.itemNeighbors(this.level, this.farPos)) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack taken = handler.extractItem(i, amount, simulate);
                    if (!taken.isEmpty()) {
                        if (!simulate) {
                            this.onTransfer.run();
                        }

                        return taken;
                    }
                }
            }

            return ItemStack.EMPTY;
        }
    }

    static final class TickCached<T> {
        private final ServerSubLevel sub;
        private final Supplier<T> builder;
        private T value;
        private long builtAt = Long.MIN_VALUE;

        TickCached(ServerSubLevel sub, Supplier<T> builder) {
            this.sub = sub;
            this.builder = builder;
        }

        T get() {
            long now = this.sub.getLevel().getGameTime();
            if (this.value == null || now != this.builtAt) {
                this.value = this.builder.get();
                this.builtAt = now;
            }

            return this.value;
        }
    }
}
