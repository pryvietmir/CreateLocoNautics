package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public class TurntableBearingRenderer extends KineticBlockEntityRenderer<TurntableBearingBlockEntity> {
    public TurntableBearingRenderer(Context context) {
        super(context);
    }

    protected void renderSafe(TurntableBearingBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
            BlockState state = be.getBlockState();
            Direction facing = (Direction)state.getValue(TurntableBearingBlock.FACING);
            Axis axis = ((IRotate)state.getBlock()).getRotationAxis(state);
            VertexConsumer vb = buffer.getBuffer(RenderType.solid());
            SuperByteBuffer cogwheel = kineticRotationTransform(
                CachedBuffers.partialFacingVertical(LoconauticsPartialModels.TURNTABLE_COG, state, facing.getOpposite()),
                be.getExtraKinetics(),
                axis,
                getAngleForBe(be.getExtraKinetics(), be.getBlockPos(), axis),
                light
            );
            cogwheel.renderInto(ms, vb);
            if (!be.isAssembled()) {
                renderRotatingBuffer(be, CachedBuffers.partialFacing(LoconauticsPartialModels.TURNTABLE_SHAFT, state, facing), ms, vb, light);
            }
        }
    }

    protected SuperByteBuffer getRotatedModel(TurntableBearingBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(
            LoconauticsPartialModels.TURNTABLE_SHAFT, state, ((Direction)state.getValue(TurntableBearingBlock.FACING)).getOpposite()
        );
    }
}
