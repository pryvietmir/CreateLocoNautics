package com.lycoris.loconautics.content.dashboard;

import com.lycoris.loconautics.content.controls.panel.PanelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class DashboardRenderer extends PanelRenderer<DashboardBlockEntity> {
    public DashboardRenderer(Context context) {
        super(context);
    }

    @Override
    protected void applyPartTransform(PoseStack ms, BlockState state) {
        placeBoard(ms, state);
    }

    public static void placeBoard(PoseStack ms, BlockState state) {
        Direction facing = (Direction)state.getValue(DashboardBlock.FACING);
        int yaw = (facing.get2DDataValue() + 2) % 4 * 90;
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(yaw)));
        ms.translate(-0.5, -0.5, -0.5);
        ms.translate(0.0F, 0.875F, 1.0F);
        ms.mulPose(Axis.XP.rotationDegrees(-22.5F));
        ms.translate(0.0F, 0.0F, -1.0625F);
    }
}
