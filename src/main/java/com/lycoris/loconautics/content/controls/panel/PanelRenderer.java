package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPartRenderer;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugePartRenderer;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panellever.LeverPartRenderer;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPartRenderer;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class PanelRenderer<T extends PanelBlockEntity> implements BlockEntityRenderer<T> {
    public PanelRenderer(Context context) {
    }

    public void render(T panel, float partialTick, PoseStack ms, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = panel.getBlockState();

        for (PanelSlot slot : PanelSlot.values()) {
            PanelPart part = panel.getPart(slot);
            if (part != null) {
                float animation = panel.animation(slot).getValue(partialTick);
                ms.pushPose();
                this.applyPartTransform(ms, state);
                float offX = panel.isCentered() ? 4.0F : slot.modelX;
                float offZ = panel.isCentered() ? 4.0F : slot.modelZ;
                ms.translate(offX / 16.0F, 0.0F, offZ / 16.0F);
                switch (part) {
                    case GaugeBlockEntity.Gauge gauge:
                        GaugePartRenderer.render(gauge, panel, slot, animation, partialTick, ms, buffers, packedLight, state);
                        break;
                    case ValvePart valve:
                        ValvePartRenderer.render(animation, ms, buffers, packedLight, state, valve.wheelColor());
                        break;
                    case ButtonPart button:
                        ButtonPartRenderer.render(
                            animation,
                            ms,
                            buffers,
                            packedLight,
                            state,
                            button.body(),
                            button.capColor(),
                            button.style(),
                            button.shownOptions(),
                            panel.bulbGlow(slot, partialTick)
                        );
                        break;
                    case LeverPart lever:
                        LeverPartRenderer.render(
                            animation,
                            ms,
                            buffers,
                            packedLight,
                            state,
                            lever.body(),
                            lever.knobColor(),
                            lever.style(),
                            lever.shownOptions(),
                            panel.bulbGlow(slot, partialTick)
                        );
                        break;
                    case SwitchPart flip:
                        SwitchPartRenderer.render(
                            animation,
                            ms,
                            buffers,
                            packedLight,
                            state,
                            flip.body(),
                            flip.knobColor(),
                            flip.style(),
                            flip.shownOptions(),
                            panel.bulbGlow(slot, partialTick)
                        );
                        break;
                    default:
                }

                PanelGlass glass = part.glass();
                if (glass != null) {
                    PanelGlassRenderer.render(glass, panel.glassAngle(slot), ms, buffers, packedLight, state);
                }

                ms.popPose();
            }
        }
    }

    protected void applyPartTransform(PoseStack ms, BlockState state) {
        placePanel(ms, state);
    }

    public static void placePanel(PoseStack ms, BlockState state) {
        AttachFace face = (AttachFace)state.getValue(PanelBlock.FACE);
        Direction facing = (Direction)state.getValue(PanelBlock.FACING);
        int x = face == AttachFace.WALL ? 90 : (face == AttachFace.CEILING ? 180 : 0);
        int yaw = (facing.get2DDataValue() + 2) % 4 * 90 + (face == AttachFace.CEILING ? 180 : 0);
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(yaw)));
        ms.mulPose(Axis.XP.rotationDegrees(-x));
        ms.translate(-0.5, -0.5, -0.5);
    }
}
