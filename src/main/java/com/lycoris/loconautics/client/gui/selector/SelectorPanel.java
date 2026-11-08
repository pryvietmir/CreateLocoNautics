package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.gui.gauge.GaugePartColumn;
import com.simibubi.create.foundation.gui.AllIcons;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SelectorPanel {
    public static final int PANEL_W = 339;
    public static final int PANEL_H = 193;
    public static final int LABEL_Y = 3;
    public static final int LABEL_W = 200;
    public static final int LABEL_BAR_H = 15;
    public static final int LABEL_TEXT_COLOR = -12566464;
    public static final int VIEW_X = 34;
    public static final int VIEW_Y = 43;
    public static final int VIEW_SIZE = 92;
    public static final int TOGGLE_Y = 169;
    public static final int INVERT_X = 5;
    public static final int INVERT_Y = 169;
    public static final int OPTION_X = 27;
    public static final int OPTION_Y = 169;
    public static final int OPTION_STEP = 22;
    public static final int CONFIRM_X = 314;
    public static final int CONFIRM_Y = 169;
    public static final int ARROW_SIZE = 10;
    public static final int STYLE_ARROW_Y = 84;
    public static final int STYLE_PREV_X = 20;
    public static final int STYLE_NEXT_X = 130;
    public static final int STYLE_DOTS_Y = 142;
    public static final int ROW_X = 151;
    public static final int ROW_Y = 23;
    public static final int SIGNAL_TEXT_X = 157;
    public static final int SIGNAL_TEXT_W = 118;
    public static final int SIGNAL_TEXT_Y = 34;
    public static final int SIGNAL_TEXT_COLOR = -1;
    public static final int METRIC_X = 157;
    public static final int METRIC_Y = 29;
    public static final int METRIC_W = 160;
    public static final int SOLO_STRIP_X = 217;
    public static final int LEFT_STRIP_X = 185;
    public static final int RIGHT_STRIP_X = 249;
    public static final int TRIPLE_LEFT_STRIP_X = 165;
    public static final int TRIPLE_MID_STRIP_X = 213;
    public static final int TRIPLE_RIGHT_STRIP_X = 261;
    public static final int BUTTON_SIZE = 18;
    private static final ResourceLocation TEXTURE = LoconauticsConstants.id("textures/gui/selector/panel.png");
    private static final int SHEET_W = 512;
    private static final int SHEET_H = 256;
    private static final int BUTTON_U = 352;
    private static final int BUTTON_HOVER_U = 376;
    private static final int BUTTON_V = 4;
    private static final int GREEN_BUTTON_U = 420;
    private static final int GREEN_BUTTON_V = 28;
    private static final int ARROW_U = 352;
    private static final int ARROW_V = 150;
    private static final int ARROW_HOVER_OFFSET = 12;
    private static final int ARROW_GLYPH_U = 376;
    private static final int ROW_W = 172;
    private static final int ROW_H = 30;
    private static final int SIGNAL_ROW_U = 340;
    private static final int SIGNAL_ROW_V = 166;
    private static final int GAUGE_ROW_U = 340;
    private static final int GAUGE_ROW_V = 198;
    private static final int RENAME_U = 442;
    private static final int RENAME_HOVER_U = 457;
    private static final int RENAME_V = 4;
    private static final int RENAME_SIZE = 9;
    private static final int STRIP_U = 352;
    private static final int STRIP_V = 64;
    private static final int STRIP_W = 40;
    private static final int STRIP_H = 82;
    private static final int STRIP_Y = 72;
    private static final int SLOT_CENTER_X = 20;
    private static final int TOP_SLOT_Y = 86;
    private static final int MID_SLOT_Y = 112;
    private static final int BOTTOM_SLOT_Y = 138;
    private static final int CORNER_OVERHANG = 1;
    private static final int DOT_SIZE = 4;
    private static final int DOT_STEP = 8;
    private static final int DOT_HIT_HEIGHT = 10;
    public static final int OVERLAY_Z_ABOVE_PREVIEW_MODEL = 800;

    private SelectorPanel() {
    }

    public static void blitPanel(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left, top, 0.0F, 0.0F, 339, 193, 512, 256);
    }

    public static void blitStrip(GuiGraphics g, int left, int top, int stripX) {
        g.blit(TEXTURE, left + stripX, top + 72, 352.0F, 64.0F, 40, 82, 512, 256);
    }

    public static void blitButton(GuiGraphics g, int x, int y, boolean hovered, boolean active) {
        if (active && !hovered) {
            g.blit(TEXTURE, x, y, 420.0F, 28.0F, 18, 18, 512, 256);
        } else {
            g.blit(TEXTURE, x, y, hovered ? 376.0F : 352.0F, 4.0F, 18, 18, 512, 256);
        }
    }

    public static void blitArrow(GuiGraphics g, int x, int y, boolean next, boolean hovered) {
        int offset = hovered ? 12 : 0;
        g.blit(TEXTURE, x, y, 352 + offset, 150.0F, 10, 10, 512, 256);
        int glyphU = 376 + offset;
        if (!next) {
            g.blit(TEXTURE, x, y, glyphU, 150.0F, 10, 10, 512, 256);
        } else {
            g.blit(TEXTURE, x, y, 10, 10, glyphU + 10, 150.0F, -10, 10, 512, 256);
        }
    }

    public static void blitSignalRow(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left + 151, top + 23, 340.0F, 166.0F, 172, 30, 512, 256);
    }

    public static void blitRename(GuiGraphics g, int x, int y, boolean hovered) {
        g.blit(TEXTURE, x, y, hovered ? 457.0F : 442.0F, 4.0F, 9, 9, 512, 256);
    }

    public static void blitMetricRow(GuiGraphics g, int left, int top) {
        g.blit(TEXTURE, left + 151, top + 23, 340.0F, 198.0F, 172, 30, 512, 256);
    }

    public static GaugePartColumn column(int left, int top, int stripX, int count, GaugePartColumn.SlotRenderer slotRenderer) {
        return new GaugePartColumn(left + stripX + 20, top + 86, top + 112, top + 138, count, 0, slotRenderer);
    }

    public static void renderViewer(GuiGraphics g, PartModelViewer viewer, int left, int top) {
        viewer.render(g, left + 34, top + 43, 92, 92);
    }

    public static void renderViewerOverlay(GuiGraphics g, Font font, PartModelViewer viewer, int left, int top) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 800.0F);
        AllIcons.I_REFRESH.render(g, resetIconX(left), resetIconY(top));
        int level = viewer.zoom();
        String zoom = level > 0 ? "+" + level : String.valueOf(level);
        g.drawCenteredString(font, zoom, left + 34 + 92 + 1 - 8, cornerBottom(top) - 6 - 9 / 2, -3092264);
        g.pose().popPose();
    }

    public static void renderColumnTooltip(GuiGraphics g, Font font, GaugePartColumn column, String labelKey, int mouseX, int mouseY) {
        if (column.hovered(mouseX, mouseY)) {
            g.renderComponentTooltip(
                font,
                List.of(Component.translatable(labelKey), Component.translatable("gui.loconautics.selector.scroll_hint").withStyle(ChatFormatting.DARK_GRAY)),
                mouseX,
                mouseY
            );
        }
    }

    public static void renderStyleDots(GuiGraphics g, int left, int top, int count, int selected, int hovered) {
        int x0 = dotsLeft(left, count) + 2;
        int y = top + 142;

        for (int i = 0; i < count; i++) {
            int color = i == selected ? -1 : (i == hovered ? -5197632 : -10855832);
            int x = x0 + i * 8;
            g.fill(x + 1, y, x + 4 - 1, y + 4, color);
            g.fill(x, y + 1, x + 4, y + 4 - 1, color);
        }
    }

    public static int styleDotAt(int left, int top, int count, double mouseX, double mouseY) {
        double y0 = top + 142 - 3.0;
        if (!(mouseY < y0) && !(mouseY >= y0 + 10.0)) {
            double relative = mouseX - dotsLeft(left, count);
            return !(relative < 0.0) && !(relative >= count * 8) ? (int)(relative / 8.0) : -1;
        } else {
            return -1;
        }
    }

    private static int dotsLeft(int left, int count) {
        return left + 34 + (92 - count * 8) / 2;
    }

    public static boolean clickedReset(PartModelViewer viewer, int left, int top, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        } else {
            int x = resetIconX(left);
            int y = resetIconY(top);
            if (!(mouseX < x) && !(mouseX >= x + 16) && !(mouseY < y) && !(mouseY >= y + 16)) {
                viewer.resetView();
                return true;
            } else {
                return false;
            }
        }
    }

    private static int resetIconX(int left) {
        return left + 34 - 1;
    }

    private static int cornerBottom(int top) {
        return top + 43 + 92 + 1;
    }

    private static int resetIconY(int top) {
        return cornerBottom(top) - 16;
    }
}
