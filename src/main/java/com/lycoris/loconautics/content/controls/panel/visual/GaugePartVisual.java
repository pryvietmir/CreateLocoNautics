package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeNeedle;
import com.lycoris.loconautics.content.controls.panelgauge.GaugePartRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.jetbrains.annotations.Nullable;

public final class GaugePartVisual extends PanelPartVisual {
    private static final int FACE_STRIDE = 8;
    private final int background;
    private final int front;
    private final int needleColor;
    private final int options;
    private final GaugeNeedle style;
    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance back;
    private final TransformedInstance face;
    private final TransformedInstance body;
    private final TransformedInstance needle;
    @Nullable
    private final PanelBulbVisual bulb;
    private float lastShown = Float.NaN;
    private float lastQuiver = Float.NaN;

    public GaugePartVisual(VisualizationContext context, GaugeBlockEntity.Gauge gauge) {
        super(context);
        this.background = gauge.background();
        this.front = gauge.front();
        this.needleColor = gauge.needleColor();
        this.options = gauge.options();
        this.style = gauge.needle();
        this.plate = PanelStyles.GAUGE.isOn(this.options, PanelStyles.GAUGE_PLATE) ? this.create(PanelModels.plate()) : null;
        this.back = this.create(ShiftedModels.plain(LoconauticsPartialModels.GAUGE_BACK));
        this.face = this.create(
            ShiftedModels.shifted(
                LoconauticsPartialModels.GAUGE_FACE, LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE, 8 * (this.background % 6), 8 * (this.background / 6)
            )
        );
        this.body = this.create(PanelModels.body(GaugePartRenderer.bodyForFront(this.front)));
        this.needle = this.create(
            ShiftedModels.shifted(
                GaugePartRenderer.partialFor(this.style),
                LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE,
                this.style.colorOffsetU(this.needleColor),
                this.style.colorOffsetV(this.needleColor)
            )
        );
        this.bulb = PanelStyles.GAUGE.isOn(this.options, PanelStyles.GAUGE_LIGHT)
            ? new PanelBulbVisual(this::create, LoconauticsPartialModels.GAUGE_BULB)
            : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof GaugeBlockEntity.Gauge gauge
            && gauge.background() == this.background
            && gauge.front() == this.front
            && gauge.needleColor() == this.needleColor
            && gauge.options() == this.options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) {
            if (this.plate != null) {
                place(this.plate, ms, light);
            }

            place(this.back, ms, light);
            place(this.face, ms, light);
            place(this.body, ms, light);
        }

        float shown = panel.animation(slot).getValue(partialTick);
        if (this.bulb != null) {
            this.bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        }

        float quiver = GaugePartRenderer.wobble(panel, slot, shown, partialTick);
        if (full || shown != this.lastShown || quiver != this.lastQuiver) {
            this.lastShown = shown;
            this.lastQuiver = quiver;
            ms.pushPose();
            GaugePartRenderer.applyNeedle(ms, this.style, shown, quiver);
            place(this.needle, ms, light);
            ms.popPose();
        }
    }
}
