package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FireboxRenderer extends SafeBlockEntityRenderer<FireboxBlockEntity> {
    private static final float LEVER_DEGREES = -42.5F;
    private static final float DOOR_DEGREES = 35.0F;
    private static final float[] LEVER_PIVOT = pivot(7.5F, 12.5F, -1.025F);
    private static final float[] DOOR_PIVOT = pivot(8.0F, 13.0F, -0.5F);

    public FireboxRenderer(Context context) {
    }

    private static float[] pivot(float x, float y, float z) {
        return new float[]{x / 16.0F, y / 16.0F, z / 16.0F};
    }

    protected void renderSafe(FireboxBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (!VisualizationManager.supportsVisualization(be.getLevel())) {
            BlockState state = be.getBlockState();
            if (!(Boolean)state.getValue(FireboxBlock.EXTENSION)) {
                Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
                float yaw = 180.0F - facing.toYRot();
                float swing = be.getDoorOpenTime(partialTicks) / 10.0F;
                float progress = Mth.sin(swing * (float) (Math.PI / 2));
                swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_LEVER_ARM, yaw, LEVER_PIVOT, -42.5F * progress);
                swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_DOOR_LEFT, yaw, DOOR_PIVOT, -35.0F * progress);
                swing(ms, buffer, state, light, LoconauticsPartialModels.FIREBOX_DOOR_RIGHT, yaw, DOOR_PIVOT, 35.0F * progress);
            }
        }
    }

    private static void swing(PoseStack ms, MultiBufferSource buffer, BlockState state, int light, PartialModel model, float yaw, float[] hinge, float degrees) {
        SuperByteBuffer part = CachedBuffers.partial(model, state);
        ((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)part.center()).rotateYDegrees(yaw)).uncenter())
                    .translate(hinge[0], hinge[1], hinge[2]))
                .rotateZDegrees(degrees))
            .translate(-hinge[0], -hinge[1], -hinge[2]);
        part.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }
}
