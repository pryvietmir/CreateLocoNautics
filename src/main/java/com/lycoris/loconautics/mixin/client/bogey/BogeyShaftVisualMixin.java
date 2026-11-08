package com.lycoris.loconautics.mixin.client.bogey;

import com.lycoris.loconautics.content.bearingaxle.BogeyShaft;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.bogey.BogeyBlockEntityVisual;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import java.util.function.Consumer;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BogeyBlockEntityVisual.class, remap = false)
public abstract class BogeyShaftVisualMixin extends AbstractBlockEntityVisual<AbstractBogeyBlockEntity> {
    @Unique
    private RotatingInstance loconautics$shaft;
    @Unique
    private Axis loconautics$shaftAxis;

    protected BogeyShaftVisualMixin(VisualizationContext ctx, AbstractBogeyBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
    }

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void loconautics$syncShaft(CallbackInfo ci) {
        BlockState state = ((AbstractBogeyBlockEntity)this.blockEntity).getBlockState();
        if (!BogeyShaft.isShafted(state)) {
            this.loconautics$dropShaft();
        } else {
            Axis axis = (Axis)state.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            if (this.loconautics$shaft != null && this.loconautics$shaftAxis != axis) {
                this.loconautics$dropShaft();
            }

            if (this.loconautics$shaft == null) {
                this.loconautics$shaft = (RotatingInstance)this.instancerProvider()
                    .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                    .createInstance();
                this.loconautics$shaftAxis = axis;
                this.loconautics$shaft.rotateToFace(axis).setPosition(this.getVisualPosition()).setChanged();
                this.relight(new FlatLit[]{this.loconautics$shaft});
            }

            KineticBlockEntity end = BogeyShaft.endpoint(((AbstractBogeyBlockEntity)this.blockEntity).getLevel(), this.pos, axis);
            if (end == null) {
                this.loconautics$shaft.setRotationAxis(axis).setRotationalSpeed(0.0F).setRotationOffset(0.0F).setChanged();
            } else {
                KineticBlockEntityVisual.applyOverstressEffect(end, new RotatingInstance[]{this.loconautics$shaft.setup(end, axis)});
            }
        }
    }

    @Inject(method = "updateLight", at = @At("TAIL"))
    private void loconautics$relightShaft(float partialTick, CallbackInfo ci) {
        if (this.loconautics$shaft != null) {
            this.relight(new FlatLit[]{this.loconautics$shaft});
        }
    }

    @Inject(method = "collectCrumblingInstances", at = @At("TAIL"))
    private void loconautics$crumbleShaft(Consumer<Instance> consumer, CallbackInfo ci) {
        if (this.loconautics$shaft != null) {
            consumer.accept(this.loconautics$shaft);
        }
    }

    @Inject(method = "_delete", at = @At("HEAD"))
    private void loconautics$deleteShaft(CallbackInfo ci) {
        this.loconautics$dropShaft();
    }

    @Unique
    private void loconautics$dropShaft() {
        if (this.loconautics$shaft != null) {
            this.loconautics$shaft.delete();
            this.loconautics$shaft = null;
        }
    }
}
