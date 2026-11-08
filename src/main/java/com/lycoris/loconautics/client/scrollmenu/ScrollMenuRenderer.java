package com.lycoris.loconautics.client.scrollmenu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.network.chat.Component;

public final class ScrollMenuRenderer {
    public static final Layer OVERLAY = ScrollMenuRenderer::render;
    private static final int SLOT = 22;
    private static final int GAP = 4;
    private static final int NAME_GAP = 16;
    private static final int DROP = 22;
    private static final int YELLOW_DROP = 6;

    private ScrollMenuRenderer() {
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ScrollMenu s = ScrollMenuHandler.current();
        if (s != null) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.options.hideGui && mc.level != null) {
                float pt = deltaTracker.getGameTimeDeltaPartialTick(true);
                float alpha = s.alpha.getValue(pt);
                if (!(alpha <= 0.01F)) {
                    float scale = s.scale.getValue(pt);
                    int w = graphics.guiWidth();
                    int h = graphics.guiHeight();
                    int total = 74;
                    int startX = (w - total) / 2;
                    int rowY = h - 59 - 16 - 22 + 22;
                    int[] slotX = new int[]{startX, startX + 22 + 4, startX + 52};
                    int cx = w / 2;
                    PoseStack ms = graphics.pose();
                    ms.pushPose();
                    ms.translate(cx, rowY + 11.0F, 0.0F);
                    ms.scale(scale, scale, 1.0F);
                    ms.translate(-cx, -(rowY + 11.0F), 0.0F);
                    Component sel = s.entries.get(s.selected).name();
                    graphics.drawString(mc.font, sel, cx - mc.font.width(sel) / 2, rowY - 16 - 9 + 6, withAlpha(16766720, alpha), true);
                    int[] slotIndex = new int[]{s.wrapped(-1), s.selected, s.wrapped(1)};

                    for (int i = 0; i < 3; i++) {
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                        AllGuiTextures.TOOLBELT_SLOT.render(graphics, slotX[i], rowY);
                        if (i == 1) {
                            AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, slotX[i] - 1, rowY - 1);
                        }

                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        graphics.renderItem(s.entries.get(slotIndex[i]).icon(), slotX[i] + 3, rowY + 3);
                    }

                    ms.popPose();
                }
            }
        }
    }

    private static int withAlpha(int rgb, float a) {
        int na = Math.max(0, Math.min(255, Math.round(255.0F * a)));
        return na << 24 | rgb & 16777215;
    }
}
