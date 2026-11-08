package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class TurntableBearingVisual extends OrientedRotatingVisual<TurntableBearingBlockEntity> {
    private final RotatingInstance topShaft;
    private final RotatingInstance cogInstance;

    public TurntableBearingVisual(VisualizationContext context, TurntableBearingBlockEntity blockEntity, float partialTick) {
        super(
            context,
            blockEntity,
            partialTick,
            Direction.SOUTH,
            ((Direction)blockEntity.getBlockState().getValue(BlockStateProperties.FACING)).getOpposite(),
            Models.partial(LoconauticsPartialModels.TURNTABLE_SHAFT)
        );
        Direction facing = (Direction)blockEntity.getBlockState().getValue(BlockStateProperties.FACING);
        this.topShaft = ((RotatingInstance)this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(LoconauticsPartialModels.TURNTABLE_SHAFT))
                .createInstance())
            .rotateToFace(Direction.SOUTH, facing)
            .setup(blockEntity)
            .setPosition(this.getVisualPosition());
        this.cogInstance = ((RotatingInstance)this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(LoconauticsPartialModels.TURNTABLE_COG))
                .createInstance())
            .rotateToFace(Direction.UP, facing.getOpposite())
            .setup(blockEntity.getExtraKinetics())
            .setPosition(this.getVisualPosition());
        this.topShaft.setVisible(!blockEntity.isAssembled());
        this.topShaft.setChanged();
        this.cogInstance.setChanged();
    }

    public void update(float partialTick) {
        super.update(partialTick);
        this.topShaft.setVisible(!((TurntableBearingBlockEntity)this.blockEntity).isAssembled());
        this.topShaft.setup((KineticBlockEntity)this.blockEntity).setChanged();
        this.cogInstance.setup(((TurntableBearingBlockEntity)this.blockEntity).getExtraKinetics()).setChanged();
    }

    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        this.relight(new FlatLit[]{this.topShaft});
        this.relight(new FlatLit[]{this.cogInstance});
    }

    protected void _delete() {
        super._delete();
        this.topShaft.delete();
        this.cogInstance.delete();
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(this.topShaft);
        consumer.accept(this.cogInstance);
    }
}
