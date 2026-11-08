package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class SwitchPartVisual extends PanelPartVisual {
    private final int body;
    private final int knobColor;
    private final int style;
    private final int options;
    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    private final TransformedInstance paddle;
    @Nullable
    private final PanelBulbVisual bulb;
    @Nullable
    private final PanelGlassVisual glass;
    private float lastFraction = Float.NaN;

    public SwitchPartVisual(VisualizationContext context, SwitchPart flip) {
        super(context);
        this.body = flip.body();
        this.knobColor = flip.knobColor();
        this.style = flip.style();
        this.options = flip.shownOptions();
        PanelGlass glassKind = flip.glass();
        this.glass = glassKind == null ? null : new PanelGlassVisual(this::create, glassKind);
        this.plate = PanelStyles.SWITCH.isOn(this.options, PanelStyles.SWITCH_PLATE) ? this.create(PanelModels.slimPlate()) : null;
        this.shell = this.create(PanelModels.slimBody(this.body));
        this.paddle = this.create(
            ShiftedModels.shifted(
                LoconauticsPartialModels.SWITCH_PADDLE, LoconauticsSpriteShifts.GAUGE_SWITCH, 3 * (this.knobColor % 5), 5 * (this.knobColor / 5)
            )
        );
        this.bulb = PanelStyles.SWITCH.isOn(this.options, PanelStyles.SWITCH_LIGHT)
            ? new PanelBulbVisual(this::create, LoconauticsPartialModels.SWITCH_BULB)
            : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof SwitchPart flip
            && flip.body() == this.body
            && flip.knobColor() == this.knobColor
            && flip.style() == this.style
            && flip.shownOptions() == this.options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) {
            if (this.plate != null) {
                place(this.plate, ms, light);
            }

            place(this.shell, ms, light);
        }

        float fraction = panel.animation(slot).getValue(partialTick);
        if (full || fraction != this.lastFraction) {
            this.lastFraction = fraction;
            ms.pushPose();
            ms.translate(0.25F, 0.125F, 0.250205F);
            ms.mulPose(Axis.XP.rotationDegrees(22.5F * fraction));
            ms.translate(-0.25F, -0.125F, -0.250205F);
            place(this.paddle, ms, light);
            ms.popPose();
        }

        if (this.bulb != null) {
            this.bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        }

        if (this.glass != null) {
            this.glass.update(ms, light, panel.glassAngle(slot), full);
        }
    }
}
