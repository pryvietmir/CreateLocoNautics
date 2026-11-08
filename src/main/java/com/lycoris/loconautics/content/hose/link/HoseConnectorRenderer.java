package com.lycoris.loconautics.content.hose.link;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class HoseConnectorRenderer implements BlockEntityRenderer<HoseConnectorBlockEntity> {
    public HoseConnectorRenderer(Context context) {
    }

    public void render(HoseConnectorBlockEntity be, float partialTicks, PoseStack ps, MultiBufferSource buffer, int light, int overlay) {
        SteelCableHolderBehaviour holder = (SteelCableHolderBehaviour)be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder != null && !holder.attachedStrandIds().isEmpty()) {
            Direction open = be.plateSide().getOpposite();
            SuperByteBuffer knot = CachedBuffers.partialFacing(LoconauticsPartialModels.HOSE_KNOT, be.getBlockState(), be.plateSide());
            ((SuperByteBuffer)knot.translate(open.getStepX() * 0.1875, open.getStepY() * 0.1875, open.getStepZ() * 0.1875))
                .light(light)
                .renderInto(ps, buffer.getBuffer(RenderType.solid()));
        }
    }

    public AABB getRenderBoundingBox(HoseConnectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.0);
    }
}
