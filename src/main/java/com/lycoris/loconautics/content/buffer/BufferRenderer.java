package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

public class BufferRenderer extends SmartBlockEntityRenderer<BufferBlockEntity> {
    private static final float PX = 0.0625F;
    private static final float ROD_BACK = 16.0F;
    private static final float ROD_LENGTH = 6.0F;

    public BufferRenderer(Context context) {
        super(context);
    }

    protected void renderSafe(BufferBlockEntity be, float partialTicks, PoseStack ps, MultiBufferSource bufferSource, int light, int overlay) {
        BlockState state = be.getBlockState();
        DyeColor color = ((BufferBlock)state.getBlock()).getColor();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        float extension = be.shownExtensionPixels(partialTicks);
        float blockstateYaw = (be.facing().toYRot() + 180.0F) % 360.0F;
        ps.pushPose();
        ps.translate(0.5F, ((CopycatPlacement)state.getValue(CopycatPlacement.PLACEMENT)).blocks(), 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-blockstateYaw));
        ps.translate(-0.5F, 0.0F, -0.5F);
        ps.pushPose();
        ps.translate(0.0F, 0.0F, -Math.min(extension, be.size().sleeveOutPixels()) * 0.0625F);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_SLEEVES.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 1.0F);
        ps.scale(1.0F, 1.0F, (6.0F + extension) / 6.0F);
        ps.translate(0.0F, 0.0F, -1.0F);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_RODS.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();
        ps.translate(0.0F, 0.0F, -extension * 0.0625F);
        CachedBuffers.partial(LoconauticsPartialModels.BUFFER_HEADS.get(color), state).light(light).renderInto(ps, buffer);
        ps.popPose();
    }
}
