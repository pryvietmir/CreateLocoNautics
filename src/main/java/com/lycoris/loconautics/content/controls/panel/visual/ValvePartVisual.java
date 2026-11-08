package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

public final class ValvePartVisual extends PanelPartVisual {
    private final int wheelColor;
    private final TransformedInstance base;
    private final TransformedInstance handle;
    private float lastDegrees = Float.NaN;

    public ValvePartVisual(VisualizationContext context, ValvePart valve) {
        super(context);
        this.wheelColor = valve.wheelColor();
        this.base = this.create(ShiftedModels.plain(LoconauticsPartialModels.VALVE_BASE));
        this.handle = this.create(
            ShiftedModels.shifted(LoconauticsPartialModels.VALVE_HANDLE, LoconauticsSpriteShifts.VALVE, 11 * (this.wheelColor % 5), 7 * (this.wheelColor / 5))
        );
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof ValvePart valve && valve.wheelColor() == this.wheelColor;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) {
            place(this.base, ms, light);
        }

        float degrees = panel.animation(slot).getValue(partialTick);
        if (full || degrees != this.lastDegrees) {
            this.lastDegrees = degrees;
            ms.pushPose();
            ms.translate(0.25F, 0.0F, 0.25F);
            ms.mulPose(Axis.YP.rotationDegrees(-degrees));
            ms.translate(-0.25F, 0.0F, -0.25F);
            place(this.handle, ms, light);
            ms.popPose();
        }
    }
}
