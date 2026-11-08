package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class ButtonPartVisual extends PanelPartVisual {
    private final int body;
    private final int capColor;
    private final int style;
    private final int options;
    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    private final TransformedInstance cap;
    @Nullable
    private final PanelBulbVisual bulb;
    @Nullable
    private final PanelGlassVisual glass;
    private float lastPressed = Float.NaN;

    public ButtonPartVisual(VisualizationContext context, ButtonPart button) {
        super(context);
        this.body = button.body();
        this.capColor = button.capColor();
        this.style = button.style();
        this.options = button.shownOptions();
        PanelGlass glassKind = button.glass();
        this.glass = glassKind == null ? null : new PanelGlassVisual(this::create, glassKind);
        this.plate = PanelStyles.BUTTON.isOn(this.options, PanelStyles.BUTTON_PLATE) ? this.create(PanelModels.plate()) : null;
        ButtonPartRenderer.CapStyle capStyle = ButtonPartRenderer.capStyle(this.style);
        this.shell = this.create(capStyle.big() ? PanelModels.bigBody(this.body) : PanelModels.body(this.body));
        this.cap = this.create(
            ShiftedModels.shifted(capStyle.cap(), capStyle.shift(), capStyle.strideX() * (this.capColor % 5), capStyle.strideY() * (this.capColor / 5))
        );
        this.bulb = PanelStyles.BUTTON.isOn(this.options, PanelStyles.BUTTON_LIGHT)
            ? new PanelBulbVisual(this::create, LoconauticsPartialModels.GAUGE_BULB)
            : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof ButtonPart button
            && button.body() == this.body
            && button.capColor() == this.capColor
            && button.style() == this.style
            && button.shownOptions() == this.options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) {
            if (this.plate != null) {
                place(this.plate, ms, light);
            }

            place(this.shell, ms, light);
        }

        if (this.bulb != null) {
            this.bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        }

        if (this.glass != null) {
            this.glass.update(ms, light, panel.glassAngle(slot), full);
        }

        float pressed = panel.animation(slot).getValue(partialTick);
        if (full || pressed != this.lastPressed) {
            this.lastPressed = pressed;
            ms.pushPose();
            ms.translate(0.0F, -pressed * 0.046875F, 0.0F);
            place(this.cap, ms, light);
            ms.popPose();
        }
    }
}
