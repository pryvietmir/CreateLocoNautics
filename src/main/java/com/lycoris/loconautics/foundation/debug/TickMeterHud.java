package com.lycoris.loconautics.foundation.debug;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;

public final class TickMeterHud {
    public static final Layer OVERLAY = TickMeterHud::render;
    private static final int MARGIN = 2;
    private static final int GREEN = -11141291;
    private static final int YELLOW = -171;
    private static final int RED = -43691;
    private static final double FRAME_SMOOTHING = 0.05;
    private static double smoothFrameMs;

    private TickMeterHud() {
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null && !mc.options.hideGui) {
            double gap = TickMeter.gapMs();
            int color = gap < 55.0 ? -11141291 : (gap < 100.0 ? -171 : -43691);
            double frameMs = mc.getFrameTimeNs() / 1000000.0;
            smoothFrameMs = smoothFrameMs + (frameMs - smoothFrameMs) * 0.05;
            String text = String.format(
                "TPS %.1f  gap %.1f ms  tick %.1f ms | FPS %d  frame %.1f ms", TickMeter.tps(), gap, TickMeter.workMs(), mc.getFps(), smoothFrameMs
            );
            g.drawString(mc.font, text, 2, 2, color, true);
        }
    }
}
