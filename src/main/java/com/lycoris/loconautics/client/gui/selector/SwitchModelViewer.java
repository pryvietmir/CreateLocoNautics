package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelGlassRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class SwitchModelViewer extends BodyColorModelViewer {
    private static final SwitchModelViewer ICON = new SwitchModelViewer();
    private static final float PREVIEW_FRACTION = 0.5F;
    private static final float PREVIEW_GLOW = 1.0F;

    @Override
    protected float iconDrop() {
        return 0.05F;
    }

    @Override
    protected void draw(PoseStack pose, MultiBufferSource buffers) {
        SwitchPartRenderer.render(0.5F, pose, buffers, 15728880, this.state, this.body, this.color, this.style, this.options, 1.0F);
    }

    @Override
    protected void drawGlass(PoseStack pose, MultiBufferSource buffers) {
        PanelGlass glass = PanelStyles.SWITCH.isOn(this.options, PanelStyles.SWITCH_GLASS) ? PanelGlass.SLIM : null;
        if (glass != null) {
            PanelGlassRenderer.render(glass, 0.0F, pose, buffers, 15728880, this.state);
        }
    }

    public static void renderSwitchIcon(GuiGraphics g, BlockState state, int body, int knobColor, int style, int options, int cx, int cy, int box) {
        ICON.renderIconWith(g, state, body, knobColor, style, options, cx, cy, box);
    }
}
