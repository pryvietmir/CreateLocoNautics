package com.lycoris.loconautics.content.combustion;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class SmallEngineVisual extends KineticBlockEntityVisual<SmallEngineBlockEntity> implements SimpleDynamicVisual {
    private final TransformedInstance leftPiston = this.piston();
    private final TransformedInstance rightPiston = this.piston();
    @Nullable
    private RotatingInstance shaft;

    public SmallEngineVisual(VisualizationContext context, SmallEngineBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        this.syncShaft();
        this.animate(partialTick);
        this.relight(new FlatLit[]{this.leftPiston, this.rightPiston});
    }

    private TransformedInstance piston() {
        return (TransformedInstance)this.instancerProvider()
            .instancer(InstanceTypes.TRANSFORMED, Models.partial(EnginePistons.model(this.blockState)))
            .createInstance();
    }

    private void syncShaft() {
        if (!((SmallEngineBlockEntity)this.blockEntity).isHead()) {
            if (this.shaft != null) {
                this.shaft.delete();
                this.shaft = null;
            }
        } else if (this.shaft == null) {
            this.shaft = (RotatingInstance)this.instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                .createInstance();
            this.shaft
                .setup((KineticBlockEntity)this.blockEntity)
                .setPosition(this.getVisualPosition())
                .rotateToFace(Direction.SOUTH, SmallEngineBlock.output(this.blockState))
                .setChanged();
            this.relight(new FlatLit[]{this.shaft});
        } else {
            this.shaft.setup((KineticBlockEntity)this.blockEntity).setChanged();
        }
    }

    public void beginFrame(Context ctx) {
        this.animate(ctx.partialTick());
    }

    private void animate(float partialTick) {
        float yaw = EnginePistons.yaw(this.blockState);
        this.pose(this.leftPiston, yaw, ((SmallEngineBlockEntity)this.blockEntity).pistonShake(partialTick, 0.0F));
        this.pose(this.rightPiston, yaw + 180.0F, ((SmallEngineBlockEntity)this.blockEntity).pistonShake(partialTick, (float) Math.PI));
    }

    private void pose(TransformedInstance piston, float yaw, Vector3f shake) {
        Vec3i at = this.getVisualPosition();
        ((TransformedInstance)piston.setIdentityTransform().translate(at.getX(), at.getY(), at.getZ()).translate(0.5F, 0.5F, 0.5F).rotateYDegrees(yaw))
            .translate(-0.5F, -0.5F, -0.5F)
            .translate(shake.x, shake.y, shake.z)
            .setChanged();
    }

    public void update(float partialTick) {
        this.syncShaft();
    }

    public void updateLight(float partialTick) {
        if (this.shaft != null) {
            this.relight(new FlatLit[]{this.shaft});
        }

        this.relight(new FlatLit[]{this.leftPiston, this.rightPiston});
    }

    protected void _delete() {
        if (this.shaft != null) {
            this.shaft.delete();
        }

        this.leftPiston.delete();
        this.rightPiston.delete();
    }

    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(this.shaft);
        consumer.accept(this.leftPiston);
        consumer.accept(this.rightPiston);
    }
}
