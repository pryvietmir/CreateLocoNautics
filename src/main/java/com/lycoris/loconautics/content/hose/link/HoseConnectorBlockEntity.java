package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.content.hose.flow.HoseFlowSink;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.flow.HoseSourceProbe;
import com.lycoris.loconautics.content.hose.flow.PressureProbe;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;

public class HoseConnectorBlockEntity extends SmartBlockEntity implements HoseLinkEndpoint {
    private final IFluidHandler linkHandler = new HoseConnectorBlockEntity.LinkHandler();
    private final PressureProbe pressureProbe = new PressureProbe();
    private final HoseSourceProbe sourceProbe = new HoseSourceProbe();

    public HoseConnectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new SteelCableHolderBehaviour(this, this.strandCapacity()));
    }

    public Direction plateSide() {
        return (Direction)this.getBlockState().getValue(HoseConnectorBlock.FACING);
    }

    public IFluidHandler getLinkHandler() {
        return this.linkHandler;
    }

    @Nullable
    @Override
    public IFluidHandler linkSink() {
        if (this.level == null) {
            return null;
        }

        Direction plate = this.plateSide();
        return (IFluidHandler)this.level.getCapability(FluidHandler.BLOCK, this.getBlockPos().relative(plate), plate.getOpposite());
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Direction open = this.plateSide().getOpposite();
        return Vec3.atCenterOf(this.getBlockPos()).add(open.getStepX() * 0.1875, open.getStepY() * 0.1875, open.getStepZ() * 0.1875);
    }

    private HosePressure.Pressure platePressure() {
        return this.pressureProbe.read(this.level, this.getBlockPos(), this.plateSide());
    }

    private boolean pulls(@Nullable IFluidHandler sink) {
        float pull = Math.abs(this.platePressure().pull());
        return !(!(pull <= 0.0F) && sink instanceof HoseFlowSink flow) || flow.pull(pull);
    }

    @Nullable
    private IFluidHandler farSink() {
        IFluidHandler sink = HoseLinks.farSink(this);
        return sink != null && HoseFlowSink.unwrap(sink) != this.linkHandler && !this.sourceProbe.feeds(this.level, this.getBlockPos(), this.plateSide(), sink)
            ? sink
            : null;
    }

    private final class LinkHandler implements IFluidHandler {
        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int tank) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            return sink != null && sink.getTanks() != 0 ? sink.getFluidInTank(0) : FluidStack.EMPTY;
        }

        public int getTankCapacity(int tank) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            return sink != null && sink.getTanks() != 0 ? sink.getTankCapacity(0) : 0;
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            return sink != null && sink.getTanks() > 0 && sink.isFluidValid(0, stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            if (sink == null) {
                return 0;
            }

            float rpm = HoseConnectorBlockEntity.this.platePressure().push();
            return rpm > 0.0F && sink instanceof HoseFlowSink flow ? flow.fillPushed(resource, rpm, action) : sink.fill(resource, action);
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            return sink != null && HoseConnectorBlockEntity.this.pulls(sink) ? sink.drain(resource, action) : FluidStack.EMPTY;
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler sink = HoseConnectorBlockEntity.this.farSink();
            return sink != null && HoseConnectorBlockEntity.this.pulls(sink) ? sink.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }
}
