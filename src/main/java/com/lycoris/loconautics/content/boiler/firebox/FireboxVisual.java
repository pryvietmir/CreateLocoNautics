package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual.Context;
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual.SectionCollector;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Vector3f;

public class FireboxVisual extends AbstractBlockEntityVisual<FireboxBlockEntity> implements SimpleDynamicVisual {
    private static final float LEVER_DEGREES = -42.5F;
    private static final float DOOR_DEGREES = 35.0F;
    private static final Vector3f LEVER_PIVOT = pivot(7.5F, 12.5F, -1.025F);
    private static final Vector3f DOOR_LEFT_PIVOT = pivot(8.0F, 13.0F, -0.5F);
    private static final Vector3f DOOR_RIGHT_PIVOT = pivot(8.0F, 13.0F, -0.5F);
    private final TransformedInstance leverArm = this.instance(LoconauticsPartialModels.FIREBOX_LEVER_ARM);
    private final TransformedInstance doorLeft = this.instance(LoconauticsPartialModels.FIREBOX_DOOR_LEFT);
    private final TransformedInstance doorRight = this.instance(LoconauticsPartialModels.FIREBOX_DOOR_RIGHT);
    private Direction facing;
    private float yaw;
    private BlockPos openAirLightPos;
    private boolean hidden;

    public FireboxVisual(VisualizationContext ctx, FireboxBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
        this.applyFacing((Direction)this.blockState.getValue(BlockStateProperties.HORIZONTAL_FACING));
        this.animate(partialTick);
    }

    private void applyFacing(Direction next) {
        this.facing = next;
        this.yaw = 180.0F - next.toYRot();
        this.openAirLightPos = this.pos.relative(next);
        if (this.lightSections != null) {
            this.collectSections();
        }
    }

    private void collectSections() {
        long here = SectionPos.asLong(this.pos);
        long front = SectionPos.asLong(this.openAirLightPos);
        this.lightSections.sections(here == front ? LongSet.of(here) : LongSet.of(here, front));
    }

    private static Vector3f pivot(float x, float y, float z) {
        return new Vector3f(x / 16.0F, y / 16.0F, z / 16.0F);
    }

    private TransformedInstance instance(PartialModel model) {
        return (TransformedInstance)this.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.partial(model)).createInstance();
    }

    public void beginFrame(Context ctx) {
        if (this.stale()) {
            this.delete();
        } else {
            this.animate(ctx.partialTick());
        }
    }

    private boolean stale() {
        return ((FireboxBlockEntity)this.blockEntity).isRemoved() || this.level.getBlockEntity(this.pos) != this.blockEntity;
    }

    private void animate(float partialTick) {
        BlockState state = ((FireboxBlockEntity)this.blockEntity).getBlockState();
        if ((Boolean)state.getValue(FireboxBlock.EXTENSION)) {
            this.hide();
        } else {
            this.hidden = false;
            Direction current = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            if (current != this.facing) {
                this.applyFacing(current);
            }

            float swing = ((FireboxBlockEntity)this.blockEntity).getDoorOpenTime(partialTick) / 10.0F;
            float progress = Mth.sin(swing * (float) (Math.PI / 2));
            int light = brightest(LevelRenderer.getLightColor(this.level, this.pos), LevelRenderer.getLightColor(this.level, this.openAirLightPos));
            this.swingPart(this.leverArm, LEVER_PIVOT, -42.5F * progress, light);
            this.swingPart(this.doorLeft, DOOR_LEFT_PIVOT, -35.0F * progress, light);
            this.swingPart(this.doorRight, DOOR_RIGHT_PIVOT, 35.0F * progress, light);
        }
    }

    private static int brightest(int a, int b) {
        return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    private void hide() {
        if (!this.hidden) {
            this.hidden = true;

            for (TransformedInstance instance : new TransformedInstance[]{this.leverArm, this.doorLeft, this.doorRight}) {
                instance.setZeroTransform();
                instance.setChanged();
            }
        }
    }

    private void swingPart(TransformedInstance instance, Vector3f hinge, float degrees, int light) {
        ((TransformedInstance)((TransformedInstance)instance.setIdentityTransform()
                    .translate(this.visualPos.getX(), this.visualPos.getY(), this.visualPos.getZ())
                    .translate(0.5F, 0.5F, 0.5F)
                    .rotateYDegrees(this.yaw))
                .translate(-0.5F, -0.5F, -0.5F)
                .translate(hinge.x, hinge.y, hinge.z)
                .rotateZDegrees(degrees))
            .translate(-hinge.x, -hinge.y, -hinge.z);
        instance.light(light);
        instance.setChanged();
    }

    public void setSectionCollector(SectionCollector collector) {
        super.setSectionCollector(collector);
        this.collectSections();
    }

    public void updateLight(float partialTick) {
        this.animate(partialTick);
    }

    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(this.leverArm);
        consumer.accept(this.doorLeft);
        consumer.accept(this.doorRight);
    }

    protected void _delete() {
        this.leverArm.delete();
        this.doorLeft.delete();
        this.doorRight.delete();
    }
}
