package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

public class FireboxStockerBlockEntity extends SmartBlockEntity implements IDockable, IHaveGoggleInformation {
    public static final int SLOTS = 9;
    private static final int FEED_TICKS = 10;
    private static final int MAX_FIREBOX_SEARCH = 64;
    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        protected void onContentsChanged(int slot) {
            FireboxStockerBlockEntity.this.setChanged();
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return FireboxBlockEntity.isFuel(stack);
        }
    };
    private final IItemHandler insertOnly = new IItemHandler() {
        public int getSlots() {
            return FireboxStockerBlockEntity.this.inventory.getSlots();
        }

        public ItemStack getStackInSlot(int slot) {
            return FireboxStockerBlockEntity.this.inventory.getStackInSlot(slot);
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return FireboxStockerBlockEntity.this.inventory.insertItem(slot, stack, simulate);
        }

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        public int getSlotLimit(int slot) {
            return FireboxStockerBlockEntity.this.inventory.getSlotLimit(slot);
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return FireboxStockerBlockEntity.this.inventory.isItemValid(slot, stack);
        }
    };
    private boolean docked;
    private boolean primary;
    @Nullable
    private BlockPos controllerPos;
    private int feedCooldown = 10;

    public FireboxStockerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void tick() {
        super.tick();
        if (this.level != null && !this.level.isClientSide) {
            this.rescan();
            if (--this.feedCooldown <= 0) {
                this.feedCooldown = 10;
                if (this.primary) {
                    this.feedFirebox();
                }
            }
        }
    }

    private void rescan() {
        BlockPos found = null;
        List<BlockPos> group = this.fireboxGroup();

        for (BlockPos firebox : group) {
            BlockState state = this.level.getBlockState(firebox);
            if (!(Boolean)state.getValue(FireboxBlock.EXTENSION) && this.level.getBlockEntity(firebox) instanceof FireboxBlockEntity) {
                found = firebox;
                break;
            }
        }

        boolean wasPrimary = this.primary;
        BlockPos wasController = this.controllerPos;
        this.controllerPos = found;
        this.primary = found != null && this.isPrimaryStocker(group);
        if (wasPrimary != this.primary || !Objects.equals(wasController, this.controllerPos)) {
            this.setChanged();
            this.sendData();
        }
    }

    private boolean isPrimaryStocker(List<BlockPos> group) {
        long best = Long.MAX_VALUE;

        for (BlockPos firebox : group) {
            BlockPos below = firebox.below();
            if (this.level.getBlockEntity(below) instanceof FireboxStockerBlockEntity) {
                best = Math.min(best, below.asLong());
            }
        }

        return best == this.worldPosition.asLong();
    }

    private List<BlockPos> fireboxGroup() {
        List<BlockPos> found = new ArrayList<>();
        BlockPos start = this.worldPosition.above();
        if (!this.level.getBlockState(start).is(BoilerTags.BOILER_FIREBOX)) {
            return found;
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(start);

        while (!frontier.isEmpty() && found.size() < 64) {
            BlockPos current = frontier.poll();
            found.add(current);

            for (Direction dir : Plane.HORIZONTAL) {
                BlockPos neighbor = current.relative(dir);
                if (visited.add(neighbor) && this.level.getBlockState(neighbor).is(BoilerTags.BOILER_FIREBOX)) {
                    frontier.add(neighbor);
                }
            }
        }

        return found;
    }

    @Nullable
    private FireboxBlockEntity controller() {
        if (this.controllerPos == null) {
            return null;
        } else {
            return this.level.getBlockEntity(this.controllerPos) instanceof FireboxBlockEntity be ? be : null;
        }
    }

    private void feedFirebox() {
        FireboxBlockEntity firebox = this.controller();
        if (firebox != null) {
            ItemStackHandler target = firebox.getInventory();

            for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                ItemStack stack = this.inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    ItemStack one = stack.copyWithCount(1);
                    if (!ItemHandlerHelper.insertItem(target, one, true).isEmpty()) {
                        return;
                    }

                    ItemHandlerHelper.insertItem(target, one, false);
                    this.inventory.extractItem(slot, 1, false);
                    firebox.setChanged();
                    return;
                }
            }
        }
    }

    private Direction dockFace() {
        return (Direction)this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    private Vec3 dockAnchorLocal() {
        return HorizontalDockPoint.anchorLocal(this, this.dockFace(), this.dockSeatOffset());
    }

    @Override
    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(this, this.dockAnchorLocal());
    }

    @Override
    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(this.dockFace());
    }

    @Override
    public double dockRadius() {
        return 0.75;
    }

    @Override
    public double dockSeatOffset() {
        return 1.0;
    }

    @Override
    public boolean canDock() {
        return this.primary && !this.docked;
    }

    @Override
    public void onDocked() {
        this.docked = true;
        this.setChanged();
    }

    @Override
    public void onUndocked() {
        this.docked = false;
        this.setChanged();
    }

    @Nullable
    @Override
    public IFluidHandler getDockedFluidHandler() {
        return null;
    }

    @Nullable
    @Override
    public IItemHandler getDockedItemHandler() {
        return this.inventory;
    }

    @Override
    public boolean allowsItem(ItemStack stack) {
        return FireboxBlockEntity.isFuel(stack);
    }

    public IItemHandler inputHandler() {
        return this.insertOnly;
    }

    public boolean isPrimary() {
        return this.primary;
    }

    public SimpleContainer dropContents() {
        SimpleContainer container = new SimpleContainer(this.inventory.getSlots());

        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            container.setItem(slot, this.inventory.getStackInSlot(slot));
        }

        return container;
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.stocker_stats").forGoggles(tooltip);
        if (this.controllerPos == null) {
            LoconauticsLang.translate("gui.goggles.stocker_no_firebox").style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        }

        if (!this.primary) {
            LoconauticsLang.translate("gui.goggles.stocker_duplicate").style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        }

        int held = 0;

        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            held += this.inventory.getStackInSlot(slot).getCount();
        }

        LoconauticsLang.translate("gui.goggles.stocker_fuel").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder().text(String.valueOf(held)).style(ChatFormatting.WHITE).forGoggles(tooltip, 1);
        return true;
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putBoolean("Docked", this.docked);
        tag.putBoolean("Primary", this.primary);
        if (this.controllerPos != null) {
            tag.putLong("Controller", this.controllerPos.asLong());
        }
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        this.docked = tag.getBoolean("Docked");
        this.primary = tag.getBoolean("Primary");
        this.controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
