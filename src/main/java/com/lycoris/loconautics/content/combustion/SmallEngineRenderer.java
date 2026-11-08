package com.lycoris.loconautics.content.combustion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
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
import org.joml.Vector3f;

public class SmallEngineRenderer extends KineticBlockEntityRenderer<SmallEngineBlockEntity> {
    public SmallEngineRenderer(Context context) {
        super(context);
    }

    protected void renderSafe(SmallEngineBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState state = be.getBlockState();
            float yaw = EnginePistons.yaw(state);
            piston(ms, buffer, state, light, yaw, be.pistonShake(partialTicks, 0.0F));
            piston(ms, buffer, state, light, yaw + 180.0F, be.pistonShake(partialTicks, (float) Math.PI));
            if (be.isHead()) {
                Direction output = SmallEngineBlock.output(state);
                Axis axis = output.getAxis();
                SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, output);
                kineticRotationTransform(shaft, be, axis, getAngleForBe(be, be.getBlockPos(), axis), light);
                shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
            }
        }
    }

    private static void piston(PoseStack ms, MultiBufferSource buffer, BlockState state, int light, float yaw, Vector3f shake) {
        SuperByteBuffer part = CachedBuffers.partial(EnginePistons.model(state), state);
        ((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)part.center()).rotateYDegrees(yaw)).uncenter()).translate(shake.x, shake.y, shake.z);
        part.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }
}
