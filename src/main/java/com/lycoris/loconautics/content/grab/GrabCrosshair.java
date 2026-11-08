package com.lycoris.loconautics.content.grab;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.resources.ResourceLocation;

public final class GrabCrosshair {
    public static final Layer OVERLAY = GrabCrosshair::render;
    private static final ResourceLocation ICON = LoconauticsConstants.id("textures/gui/grab/crosshair.png");
    private static final int ICON_W = 12;
    private static final int ICON_H = 9;

    private GrabCrosshair() {
    }

    public static boolean replacesCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        return GrabClientHandler.grabbing() && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (replacesCrosshair()) {
            int x = (graphics.guiWidth() - 12) / 2;
            int y = (graphics.guiHeight() - 9) / 2;
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ONE_MINUS_SRC_COLOR, SourceFactor.ONE, DestFactor.ZERO);
            graphics.blit(ICON, x, y, 0.0F, 0.0F, 12, 9, 12, 9);
            RenderSystem.defaultBlendFunc();
        }
    }
}
