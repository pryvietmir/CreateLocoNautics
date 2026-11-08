package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.common.dock.DockPolling;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.boiler.BoilerWaterSource;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.function.Predicate;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

public class FluidHatchBlockEntity extends SmartBlockEntity implements IDockable, BoilerWaterSource {
    private boolean docked;
    @Nullable
    private HoseNozzleBlockEntity dockedNozzle;
    private final FluidTank tank;
    private final HatchWaterSocket socket;
    public final LerpedFloat lid = LerpedFloat.linear().startWithValue(0.0);

    public FluidHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.lid.startWithValue(state.getValue(FluidHatchBlock.OPEN) ? 1.0 : 0.0);
        this.tank = new FluidTank((Integer)LoconauticsConfig.INJECTOR_TANK_CAPACITY.get(), stack -> stack.getFluid() == Fluids.WATER) {
            protected void onContentsChanged() {
                FluidHatchBlockEntity.this.setChanged();
            }
        };
        this.socket = new HatchWaterSocket(
            this,
            () -> (Direction)this.getBlockState().getValue(FluidHatchBlock.FACING),
            () -> (Boolean)this.getBlockState().getValue(FluidHatchBlock.RAISED),
            this::dockSeatOffset,
            this.tank
        );
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean isOpen() {
        return (Boolean)this.getBlockState().getValue(FluidHatchBlock.OPEN);
    }

    public boolean isDocked() {
        return this.docked;
    }

    public void tick() {
        super.tick();
        this.lid.chase(this.isOpen() ? 1.0 : 0.0, 0.2F, Chaser.LINEAR);
        this.lid.tickChaser();
        if (this.level != null && !this.level.isClientSide) {
            DockPolling.maybeUndock(this.docked, this.dockedNozzle, n -> !n.isRemoved() && n.isDocked(), this::onUndocked);
            if (this.docked && this.dockedNozzle != null && this.level.hasNeighborSignal(this.worldPosition)) {
                this.dockedNozzle.forceUndock();
            }

            SubLevel hatchSub = Sable.HELPER.getContaining(this);
            Vec3 anchor = this.socket.dockAnchorLocal();
            DockPolling.poll(
                this,
                NozzleDockService.all(),
                nozzle -> !nozzle.isRemoved() && !nozzle.isDocked() && nozzle.canRedock(this.level.getGameTime()),
                nozzle -> Sable.HELPER.getContaining(nozzle),
                GrabbableActor::worldCenter,
                (nozzle, sub) -> {
                    nozzle.dock(this, hatchSub, anchor, this.getDockOrientation());
                    this.dockedNozzle = nozzle;
                    this.onDocked();
                }
            );
        }
    }

    @Override
    public Vec3 getDockPoint() {
        return this.socket.getDockPoint();
    }

    @Override
    public Quaterniond getDockOrientation() {
        return this.socket.getDockOrientation();
    }

    @Override
    public double dockRadius() {
        return 0.5;
    }

    @Override
    public double dockSeatOffset() {
        return 0.1875;
    }

    @Override
    public boolean canDock() {
        return this.isOpen() && !this.docked && (this.level == null || !this.level.hasNeighborSignal(this.worldPosition));
    }

    @Override
    public boolean isDockPresent() {
        return !this.isRemoved();
    }

    @Override
    public void onDocked() {
        this.docked = true;
        this.setChanged();
    }

    @Override
    public void onUndocked() {
        this.docked = false;
        this.dockedNozzle = null;
        this.setChanged();
    }

    @Nullable
    @Override
    public IFluidHandler getDockedFluidHandler() {
        return this.socket.getDockedFluidHandler();
    }

    @Override
    public int getWaterAmount() {
        return this.socket.getWaterAmount();
    }

    @Override
    public int getWaterCapacity() {
        return this.socket.getWaterCapacity();
    }

    @Override
    public int drawWater(int millibuckets) {
        return this.socket.drawWater(millibuckets);
    }

    @Override
    public void returnWater(int millibuckets) {
        this.socket.returnWater(millibuckets);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Docked", this.docked);
        tag.put("Tank", this.tank.writeToNBT(registries, new CompoundTag()));
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.docked = tag.getBoolean("Docked");
        this.tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
