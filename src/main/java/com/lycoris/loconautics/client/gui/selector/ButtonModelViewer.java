package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelGlassRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

public class ButtonModelViewer extends BodyColorModelViewer {
    private static final ButtonModelViewer ICON = new ButtonModelViewer();
    private static final float PREVIEW_GLOW = 1.0F;

    public ButtonModelViewer() {
        this.options = PanelStyles.BUTTON.defaultOptions();
    }

    @Override
    protected float iconDrop() {
        return 0.09F;
    }

    @Override
    protected float iconScale() {
        return 0.92F;
    }

    @Override
    protected void draw(PoseStack pose, MultiBufferSource buffers) {
        ButtonPartRenderer.render(0.0F, pose, buffers, 15728880, this.state, this.body, this.color, this.style, this.options, 1.0F);
    }

    @Override
    protected void drawGlass(PoseStack pose, MultiBufferSource buffers) {
        PanelGlass glass = PanelStyles.BUTTON.isOn(this.options, PanelStyles.BUTTON_GLASS) ? PanelGlass.BODY : null;
        if (glass != null) {
            PanelGlassRenderer.render(glass, 0.0F, pose, buffers, 15728880, this.state);
        }
    }

    public static void renderButtonIcon(GuiGraphics g, BlockState state, int body, int capColor, int style, int options, int cx, int cy, int box) {
        ICON.renderIconWith(g, state, body, capColor, style, options, cx, cy, box);
    }
}
