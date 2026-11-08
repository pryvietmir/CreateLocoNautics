package com.lycoris.loconautics.content.industrial;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.ComparatorUtil;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

public class IndustrialTankBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int SYNC_RATE = 8;
    private final SmartFluidTank tank;
    private final IFluidHandler handler = new IndustrialTankBlockEntity.HandlerView();
    private BlockPos controller;
    private int size = 1;
    private int minY;
    private int[] layerCounts = new int[]{1};
    private int luminosity;
    private int syncCooldown;
    private boolean queuedSync;
    private boolean forceFluidLevelUpdate = true;
    private LerpedFloat fluidLevel;
    private int lastLight = -1;
    private int lastLit = -1;
    private int lastComparator = -1;
    private boolean lastReversed;

    public IndustrialTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tank = new SmartFluidTank(cellCapacity(), this::onFluidChanged);
    }

    public static int cellCapacity() {
        return (int)(FluidTankBlockEntity.getCapacityMultiplier() * 1.5F);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IFluidHandler getFluidHandler() {
        return this.handler;
    }

    public boolean isController() {
        return this.controller == null || this.controller.equals(this.worldPosition);
    }

    public BlockPos getControllerPos() {
        return this.isController() ? this.worldPosition : this.controller;
    }

    public IndustrialTankBlockEntity getControllerBE() {
        if (!this.isController() && this.hasLevel()) {
            return this.level.getBlockEntity(this.controller) instanceof IndustrialTankBlockEntity tankBE ? tankBE : null;
        } else {
            return this;
        }
    }

    public FluidStack getFluid() {
        return this.tank.getFluid();
    }

    public int getSize() {
        return this.size;
    }

    public int[] getLayerCounts() {
        return this.layerCounts;
    }

    public int getMinY() {
        return this.minY;
    }

    public int getLuminosity() {
        return this.luminosity;
    }

    public float fillState() {
        return (float)this.tank.getFluidAmount() / this.tank.getCapacity();
    }

    public float cellFill(int cellY, float partialTicks) {
        if (this.fluidLevel == null) {
            return 0.0F;
        }

        int index = cellY - this.minY;
        if (index >= 0 && index < this.layerCounts.length) {
            float volume = this.fluidLevel.getValue(partialTicks) * this.size;
            boolean reversed = this.tank.getFluid().getFluid().getFluidType().isLighterThanAir();
            float before = 0.0F;

            for (int i = 0; i < this.layerCounts.length; i++) {
                if (reversed ? i > index : i < index) {
                    before += this.layerCounts[i];
                }
            }

            return Mth.clamp((volume - before) / this.layerCounts[index], 0.0F, 1.0F);
        } else {
            return 0.0F;
        }
    }

    public void configureController(int newSize, int newMinY, int[] newLayers, FluidStack content) {
        this.controller = null;
        this.size = newSize;
        this.minY = newMinY;
        this.layerCounts = newLayers;
        int capacity = newSize * cellCapacity();
        FluidStack limited = content.copy();
        if (limited.getAmount() > capacity) {
            limited.setAmount(capacity);
        }

        this.tank.setCapacity(capacity);
        this.tank.setFluid(limited);
        this.forceFluidLevelUpdate = true;
        this.refreshSummary(true);
        this.setChanged();
        this.sendDataNow();
    }

    public void becomeMember(BlockPos controllerPos) {
        this.controller = controllerPos;
        this.size = 1;
        this.minY = this.worldPosition.getY();
        this.layerCounts = new int[]{1};
        this.tank.setCapacity(cellCapacity());
        this.tank.setFluid(FluidStack.EMPTY);
        this.fluidLevel = null;
        this.setChanged();
        this.sendDataNow();
    }

    public void setLuminosity(int value) {
        if (this.luminosity != value) {
            this.luminosity = value;
            this.setChanged();
            this.sendData();
        }
    }

    private void onFluidChanged(FluidStack stack) {
        if (this.hasLevel() && !this.level.isClientSide && this.isController()) {
            this.refreshSummary(false);
            this.setChanged();
            this.sendData();
        }
    }

    private void refreshSummary(boolean force) {
        if (this.hasLevel() && !this.level.isClientSide) {
            FluidStack fluid = this.tank.getFluid();
            FluidType type = fluid.getFluid().getFluidType();
            int light = (int)(type.getLightLevel(fluid) / 1.2F);
            boolean reversed = type.isLighterThanAir();
            float fill = this.fillState();
            int lit = Math.min(this.layerCounts.length, (int)(fill * this.layerCounts.length) + 1);
            int comparator = ComparatorUtil.fractionToRedstoneLevel(fill);
            boolean lightChanged = light != this.lastLight || lit != this.lastLit || reversed != this.lastReversed;
            boolean comparatorChanged = comparator != this.lastComparator;
            if (force || lightChanged || comparatorChanged) {
                this.lastLight = light;
                this.lastLit = lit;
                this.lastReversed = reversed;
                this.lastComparator = comparator;
                TankMultiblock.Group group = TankMultiblock.collect(this.level, List.of(this.worldPosition), Set.of());

                for (BlockPos pos : group.cells()) {
                    if (this.level.getBlockEntity(pos) instanceof IndustrialTankBlockEntity cell && cell.getControllerPos().equals(this.worldPosition)) {
                        int index = Mth.clamp(pos.getY() - this.minY, 0, this.layerCounts.length - 1);
                        boolean bright = reversed ? this.layerCounts.length - index <= lit : index < lit;
                        cell.setLuminosity(bright ? light : (light > 0 ? 1 : 0));
                        this.level.updateNeighbourForOutputSignal(pos, cell.getBlockState().getBlock());
                    }
                }
            }
        }
    }

    public void tick() {
        super.tick();
        if (this.syncCooldown > 0) {
            this.syncCooldown--;
            if (this.syncCooldown == 0 && this.queuedSync) {
                this.sendData();
            }
        }

        if (this.fluidLevel != null) {
            this.fluidLevel.tickChaser();
        }
    }

    public void lazyTick() {
        super.lazyTick();
        if (this.level instanceof ServerLevel serverLevel && !this.isController()) {
            if (serverLevel.isLoaded(this.controller)) {
                if (!(serverLevel.getBlockEntity(this.controller) instanceof IndustrialTankBlockEntity head && head.isController())) {
                    TankMultiblock.reform(serverLevel, this.worldPosition);
                }
            }
        }
    }

    public void initialize() {
        super.initialize();
        this.sendData();
    }

    public void sendData() {
        if (this.syncCooldown > 0) {
            this.queuedSync = true;
        } else {
            super.sendData();
            this.queuedSync = false;
            this.syncCooldown = 8;
        }
    }

    public void sendDataNow() {
        this.syncCooldown = 0;
        this.queuedSync = false;
        this.sendData();
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        IndustrialTankBlockEntity head = this.getControllerBE();
        return head == null
            ? false
            : this.containedFluidTooltip(tooltip, isPlayerSneaking, (IFluidHandler)this.level.getCapability(FluidHandler.BLOCK, head.getBlockPos(), null));
    }

    protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        int lightBefore = this.luminosity;
        this.luminosity = compound.getInt("Luminosity");
        this.controller = compound.contains("Controller") ? NBTHelper.readBlockPos(compound, "Controller") : null;
        if (this.isController()) {
            this.size = Math.max(1, compound.getInt("Size"));
            this.minY = compound.getInt("MinY");
            int[] layers = compound.getIntArray("Layers");
            this.layerCounts = layers.length == 0 ? new int[]{this.size} : layers;
            this.tank.setCapacity(this.size * cellCapacity());
            this.tank.readFromNBT(registries, compound.getCompound("TankContent"));
            if (this.tank.getSpace() < 0) {
                this.tank.drain(-this.tank.getSpace(), FluidAction.EXECUTE);
            }
        } else {
            this.size = 1;
        }

        if (compound.contains("ForceFluidLevel") || this.fluidLevel == null) {
            this.fluidLevel = LerpedFloat.linear().startWithValue(this.fillState());
        }

        if (clientPacket) {
            if (this.isController()) {
                this.fluidLevel.chase(this.fillState(), 0.5, Chaser.EXP);
            }

            if (this.luminosity != lightBefore && this.hasLevel()) {
                this.level.getChunkSource().getLightEngine().checkBlock(this.worldPosition);
            }

            if (compound.contains("LazySync")) {
                this.fluidLevel.chase(this.fluidLevel.getChaseTarget(), 0.125, Chaser.EXP);
            }
        }
    }

    protected void write(CompoundTag compound, Provider registries, boolean clientPacket) {
        compound.putInt("Luminosity", this.luminosity);
        if (!this.isController()) {
            compound.put("Controller", NbtUtils.writeBlockPos(this.controller));
        } else {
            compound.put("TankContent", this.tank.writeToNBT(registries, new CompoundTag()));
            compound.putInt("Size", this.size);
            compound.putInt("MinY", this.minY);
            compound.putIntArray("Layers", this.layerCounts);
        }

        super.write(compound, registries, clientPacket);
        if (clientPacket) {
            if (this.forceFluidLevelUpdate) {
                compound.putBoolean("ForceFluidLevel", true);
            }

            if (this.queuedSync) {
                compound.putBoolean("LazySync", true);
            }

            this.forceFluidLevelUpdate = false;
        }
    }

    private final class HandlerView implements IFluidHandler {
        private SmartFluidTank target() {
            IndustrialTankBlockEntity head = IndustrialTankBlockEntity.this.getControllerBE();
            return head == null ? null : head.tank;
        }

        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int index) {
            SmartFluidTank target = this.target();
            return target == null ? FluidStack.EMPTY : target.getFluid();
        }

        public int getTankCapacity(int index) {
            SmartFluidTank target = this.target();
            return target == null ? 0 : target.getCapacity();
        }

        public boolean isFluidValid(int index, FluidStack stack) {
            SmartFluidTank target = this.target();
            return target != null && target.isFluidValid(stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            SmartFluidTank target = this.target();
            return target == null ? 0 : target.fill(resource, action);
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            SmartFluidTank target = this.target();
            return target == null ? FluidStack.EMPTY : target.drain(resource, action);
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            SmartFluidTank target = this.target();
            return target == null ? FluidStack.EMPTY : target.drain(maxDrain, action);
        }
    }
}
