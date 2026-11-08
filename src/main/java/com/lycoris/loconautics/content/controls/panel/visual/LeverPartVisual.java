package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class LeverPartVisual extends PanelPartVisual {
    private final int body;
    private final int knobColor;
    private final int style;
    private final int options;
    @Nullable
    private final TransformedInstance plate;
    private final TransformedInstance shell;
    @Nullable
    private final TransformedInstance base;
    private final TransformedInstance handle;
    private final TransformedInstance knob;
    @Nullable
    private final PanelBulbVisual bulb;
    private float lastFraction = Float.NaN;

    public LeverPartVisual(VisualizationContext context, LeverPart lever) {
        super(context);
        this.body = lever.body();
        this.knobColor = lever.knobColor();
        this.style = lever.style();
        this.options = lever.shownOptions();
        PanelStyle.ModelKit kit = PanelStyles.LEVER.get(this.style).kit();
        LoconauticsPartialModels.StyleKit partials = kit == null ? null : LoconauticsPartialModels.LEVER_STYLE_KITS.get(kit.name());
        int knobStrideX = kit == null ? 5 : kit.knobStrideX();
        int knobStrideY = kit == null ? 5 : kit.knobStrideY();
        this.plate = PanelStyles.LEVER.isOn(this.options, PanelStyles.LEVER_PLATE) ? this.create(PanelModels.slimPlate()) : null;
        this.shell = this.create(PanelModels.slimBody(this.body));
        this.base = partials != null && partials.base() != null ? this.create(ShiftedModels.plain(partials.base())) : null;
        this.handle = this.create(ShiftedModels.plain(partials == null ? LoconauticsPartialModels.LEVER_HANDLE : partials.handle()));
        this.knob = this.create(
            ShiftedModels.shifted(
                partials == null ? LoconauticsPartialModels.LEVER_KNOB : partials.knob(),
                LoconauticsSpriteShifts.GAUGE_LEVER,
                knobStrideX * (this.knobColor % 5),
                knobStrideY * (this.knobColor / 5)
            )
        );
        this.bulb = PanelStyles.LEVER.isOn(this.options, PanelStyles.LEVER_LIGHT)
            ? new PanelBulbVisual(this::create, LoconauticsPartialModels.LEVER_BULB)
            : null;
    }

    @Override
    public boolean matches(PanelPart part) {
        return part instanceof LeverPart lever
            && lever.body() == this.body
            && lever.knobColor() == this.knobColor
            && lever.style() == this.style
            && lever.shownOptions() == this.options;
    }

    @Override
    public void update(PanelBlockEntity panel, PanelSlot slot, PoseStack ms, float partialTick, int light, boolean full) {
        if (full) {
            if (this.plate != null) {
                place(this.plate, ms, light);
            }

            place(this.shell, ms, light);
            if (this.base != null) {
                place(this.base, ms, light);
            }
        }

        float fraction = panel.animation(slot).getValue(partialTick);
        if (full || fraction != this.lastFraction) {
            this.lastFraction = fraction;
            ms.pushPose();
            ms.translate(0.25F, 0.09375F, 0.25F);
            ms.mulPose(Axis.XP.rotationDegrees(Mth.lerp(fraction, -55.0F, 55.0F)));
            ms.translate(-0.25F, -0.09375F, -0.25F);
            place(this.handle, ms, light);
            place(this.knob, ms, light);
            ms.popPose();
        }

        if (this.bulb != null) {
            this.bulb.update(ms, light, panel.bulbGlow(slot, partialTick), full);
        }
    }
}
