package com.lycoris.loconautics.content.hatch;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;

public class HatchRenderer extends SafeBlockEntityRenderer<FluidHatchBlockEntity> {
    private static final float OPEN_DEGREES = 105.0F;

    public HatchRenderer(Context context) {
    }

    protected PartialModel baseModel(FluidHatchBlockEntity be) {
        return LoconauticsPartialModels.HATCH_FLUID_BASE;
    }

    protected PartialModel lidModel(FluidHatchBlockEntity be) {
        return LoconauticsPartialModels.HATCH_FLUID_LID;
    }

    protected void renderSafe(FluidHatchBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        float angle = ease(be.lid.getValue(partialTicks)) * 105.0F;
        Direction facing = (Direction)be.getBlockState().getValue(FluidHatchBlock.FACING);
        ms.pushPose();
        if ((Boolean)be.getBlockState().getValue(FluidHatchBlock.RAISED)) {
            Direction dome = facing.getOpposite();
            ms.translate(dome.getStepX() * 0.5, dome.getStepY() * 0.5, dome.getStepZ() * 0.5);
        }

        SuperByteBuffer base = CachedBuffers.partial(this.baseModel(be), be.getBlockState());
        orient(base, facing);
        base.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        SuperByteBuffer lid = CachedBuffers.partial(this.lidModel(be), be.getBlockState());
        orient(lid, facing);
        int hinge = (Integer)be.getBlockState().getValue(FluidHatchBlock.LID_HINGE);
        float hingeDeg = hinge * 90.0F + (facing == Direction.SOUTH ? 180.0F : 0.0F);
        if (hingeDeg != 0.0F) {
            ((SuperByteBuffer)((SuperByteBuffer)lid.center()).rotateYDegrees(hingeDeg)).uncenter();
        }

        ((SuperByteBuffer)((SuperByteBuffer)lid.translate(0.0F, 0.25F, 0.8125F)).rotateXDegrees(angle)).translate(0.0F, -0.25F, -0.8125F);
        lid.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }

    private static void orient(SuperByteBuffer buf, Direction facing) {
        buf.center();
        switch (facing) {
            case UP:
                buf.rotateXDegrees(180.0F);
                break;
            case NORTH:
                buf.rotateXDegrees(90.0F);
                break;
            case SOUTH:
                buf.rotateXDegrees(270.0F);
                break;
            case WEST:
                ((SuperByteBuffer)buf.rotateYDegrees(90.0F)).rotateXDegrees(90.0F);
                break;
            case EAST:
                ((SuperByteBuffer)buf.rotateYDegrees(270.0F)).rotateXDegrees(90.0F);
        }

        buf.uncenter();
    }

    private static float ease(float t) {
        return t < 0.5F ? 2.0F * t * t : 1.0F - 2.0F * (1.0F - t) * (1.0F - t);
    }
}
