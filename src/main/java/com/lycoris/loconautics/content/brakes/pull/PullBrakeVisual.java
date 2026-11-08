package com.lycoris.loconautics.content.brakes.pull;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class PullBrakeVisual extends AbstractBlockEntityVisual<PullBrakeBlockEntity> implements SimpleDynamicVisual {
    private static final int LINK_COUNT = 4;
    private final TransformedInstance wheel;
    private final TransformedInstance[] links = new TransformedInstance[4];

    public PullBrakeVisual(VisualizationContext ctx, PullBrakeBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        String color = ((PullBrakeBlock)this.blockState.getBlock()).colorName();
        this.wheel = this.instance(LoconauticsPartialModels.PULL_BRAKE_WHEELS.get(color));

        for (int i = 0; i < 4; i++) {
            this.links[i] = this.instance(LoconauticsPartialModels.PULL_BRAKE_CHAINS.get(color));
        }

        this.animate(partialTick);
    }

    private TransformedInstance instance(PartialModel model) {
        return (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    public void beginFrame(Context ctx) {
        if (!((PullBrakeBlockEntity)this.blockEntity).isRemoved() && this.level.getBlockEntity(this.pos) == this.blockEntity) {
            this.animate(ctx.partialTick());
        } else {
            this.delete();
        }
    }

    private void animate(float partialTick) {
        float pull = ((PullBrakeBlockEntity)this.blockEntity).pull.getValue(partialTick);
        float yaw = 180.0F - ((Direction)this.blockState.getValue(HorizontalDirectionalBlock.FACING)).toYRot();
        int light = LevelRenderer.getLightColor(this.level, this.pos);
        ((TransformedInstance)this.place(this.wheel, yaw, light).translate(0.5F, 0.5F, 0.8125F).rotateZDegrees(PullBrakeMotion.wheelDegrees(pull)))
            .translate(-0.5F, -0.5F, -0.8125F);
        this.wheel.setChanged();

        for (int i = 0; i < 4; i++) {
            int link = -3 + i;
            TransformedInstance instance = this.links[i];
            if (!PullBrakeMotion.linkSpawned(link, pull)) {
                instance.setZeroTransform();
            } else {
                this.place(instance, yaw, light).translate(0.0F, PullBrakeMotion.linkOffsetPixels(link, pull) / 16.0F, 0.0F);
            }

            instance.setChanged();
        }
    }

    private TransformedInstance place(TransformedInstance instance, float yaw, int light) {
        ((TransformedInstance)instance.setIdentityTransform()
                .translate(this.visualPos.getX(), this.visualPos.getY(), this.visualPos.getZ())
                .translate(0.5F, 0.5F, 0.5F)
                .rotateYDegrees(yaw))
            .translate(-0.5F, -0.5F, -0.5F);
        instance.light(light);
        return instance;
    }

    public void updateLight(float partialTick) {
        this.animate(partialTick);
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.wheel);

        for (TransformedInstance link : this.links) {
            consumer.accept(link);
        }
    }

    protected void _delete() {
        this.wheel.delete();

        for (TransformedInstance link : this.links) {
            link.delete();
        }
    }
}
