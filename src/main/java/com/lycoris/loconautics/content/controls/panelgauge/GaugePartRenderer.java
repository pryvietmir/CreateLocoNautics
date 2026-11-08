package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelBodyRenderer;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public class GaugePartRenderer {
    private static final int[] BODY_FOR_FRONT = new int[]{0, 3, 2, 1};
    private static final float WOBBLE_REFERENCE_SWEEP = 160.0F;
    private static final float DIAL_START = 0.15F;
    private static final float DIAL_END = 0.85F;
    private static final float PEGGED_READING = 0.97F;
    private static final float TWITCH_DECAY = 0.22F;
    private static final float TWITCH_STRENGTH = 2.5F;

    public static void render(
        GaugeBlockEntity.Gauge gauge,
        PanelBlockEntity panel,
        PanelSlot slot,
        float shown,
        float partialTick,
        PoseStack ms,
        MultiBufferSource buffers,
        int light,
        BlockState state
    ) {
        TextureAtlasSprite sheet = LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE.getTarget();
        float pixelU = (sheet.getU1() - sheet.getU0()) / 64.0F;
        float pixelV = (sheet.getV1() - sheet.getV0()) / 64.0F;
        float step = pixelU * 8.0F;
        float stepV = pixelV * 8.0F;
        GaugeNeedle style = gauge.needle();
        float quiver = wobble(panel, slot, shown, partialTick);
        if (PanelStyles.GAUGE.isOn(gauge.options(), PanelStyles.GAUGE_PLATE)) {
            PanelBodyRenderer.renderPlate(ms, buffers.getBuffer(RenderType.cutout()), light, state);
        }

        CachedBuffers.partial(LoconauticsPartialModels.GAUGE_BACK, state).light(light).renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        CachedBuffers.partial(LoconauticsPartialModels.GAUGE_FACE, state)
            .light(light)
            .shiftUVScrolling(LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE, step * (gauge.background() % 6), stepV * (gauge.background() / 6))
            .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        PanelBodyRenderer.renderBody(ms, buffers.getBuffer(RenderType.cutout()), light, state, bodyForFront(gauge.front()));
        ms.pushPose();
        applyNeedle(ms, style, shown, quiver);
        CachedBuffers.partial(partialFor(style), state)
            .light(light)
            .shiftUVScrolling(
                LoconauticsSpriteShifts.LOCOMOTIVE_GAUGE, pixelU * style.colorOffsetU(gauge.needleColor()), pixelV * style.colorOffsetV(gauge.needleColor())
            )
            .renderInto(ms, buffers.getBuffer(RenderType.cutout()));
        ms.popPose();
        if (PanelStyles.GAUGE.isOn(gauge.options(), PanelStyles.GAUGE_LIGHT)) {
            PanelBulbRenderer.render(
                ms, buffers, light, state, PanelBulbRenderer.glowFor(panel.bulbGlow(slot, partialTick)), LoconauticsPartialModels.GAUGE_BULB
            );
        }
    }

    public static void applyNeedle(PoseStack ms, GaugeNeedle style, float shown, float quiver) {
        float travel = 0.15F + 0.70000005F * shown;
        if (style.slides()) {
            float slide = style.slidePixels() / 16.0F;
            ms.translate(0.0F, 0.0F, travel * slide + quiver / 160.0F * slide);
        } else {
            float angle = (travel - 0.5F) * style.sweepDegrees() + quiver;
            ms.translate(style.pivotX(), 0.0F, style.pivotZ());
            ms.mulPose(Axis.YP.rotationDegrees(angle));
            ms.translate(-style.pivotX(), 0.0F, -style.pivotZ());
        }
    }

    public static int bodyForFront(int front) {
        return BODY_FOR_FRONT[Mth.clamp(front, 0, BODY_FOR_FRONT.length - 1)];
    }

    public static PartialModel partialFor(GaugeNeedle style) {
        return switch (style) {
            case DEFAULT -> LoconauticsPartialModels.NEEDLE_DEFAULT;
            case CLASSIC -> LoconauticsPartialModels.NEEDLE_CLASSIC;
            case VERTICAL -> LoconauticsPartialModels.NEEDLE_VERTICAL;
            case LINEAR -> LoconauticsPartialModels.NEEDLE_LINEAR;
        };
    }

    public static float wobble(PanelBlockEntity panel, PanelSlot slot, float reading, float partialTick) {
        float amount = (float)((Double)LoconauticsConfig.DIAL_WOBBLE_DEGREES.get()).doubleValue();
        if (!(amount <= 0.0F) && !(reading <= 0.01F) && panel.getLevel() != null) {
            float time = ((float)panel.getLevel().getGameTime() + partialTick) * 0.16F;
            float phase = slot.ordinal() * 1.7F + panel.getBlockPos().hashCode() % 32 * 0.19F;
            float quiver = Mth.sin(time + phase) * 0.6F + Mth.sin(time * 2.7F + phase * 1.3F) * 0.4F;
            return quiver * amount * Math.min(1.0F, reading) + twitch(time, phase, reading, amount);
        } else {
            return 0.0F;
        }
    }

    private static float twitch(float time, float phase, float reading, float amount) {
        if (reading < 0.97F) {
            return 0.0F;
        }

        float cycle = Mth.frac(time * 0.45F + phase);
        if (cycle > 0.22F) {
            return 0.0F;
        }

        float kick = 1.0F - cycle / 0.22F;
        return -kick * kick * amount * 2.5F;
    }
}
