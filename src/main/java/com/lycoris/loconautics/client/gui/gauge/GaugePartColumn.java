package com.lycoris.loconautics.client.gui.gauge;

import com.lycoris.loconautics.foundation.ui.PoseScissor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class GaugePartColumn {
    private static final int TOP_BOTTOM_SIZE = 22;
    private static final int CENTER_SIZE = 24;
    private static final float SCROLL_MILLIS = 140.0F;
    private final int centerX;
    private final int topY;
    private final int midY;
    private final int bottomY;
    private final int count;
    private final int slotInset;
    private final GaugePartColumn.SlotRenderer slotRenderer;
    private int selected;
    private float scrollFrom;
    private long scrollStart;

    public GaugePartColumn(int centerX, int topY, int midY, int bottomY, int count, int slotInset, GaugePartColumn.SlotRenderer slotRenderer) {
        this.centerX = centerX;
        this.topY = topY;
        this.midY = midY;
        this.bottomY = bottomY;
        this.count = count;
        this.slotInset = slotInset;
        this.slotRenderer = slotRenderer;
    }

    public int selected() {
        return this.selected;
    }

    public void setSelected(int selected) {
        this.selected = (selected % this.count + this.count) % this.count;
    }

    private int wrapped(int offset) {
        return ((this.selected + offset) % this.count + this.count) % this.count;
    }

    private void step(int direction) {
        this.scrollFrom = Mth.clamp(this.scrollOffset() + direction, -1.5F, 1.5F);
        this.scrollStart = Util.getMillis();
        this.selected = this.wrapped(direction);
    }

    private float scrollOffset() {
        float t = Mth.clamp((float)(Util.getMillis() - this.scrollStart) / 140.0F, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        return this.scrollFrom * (1.0F - eased);
    }

    private int slotY(float position) {
        return Math.round(position < 0.0F ? this.midY + position * (this.midY - this.topY) : this.midY + position * (this.bottomY - this.midY));
    }

    public void render(GuiGraphics g) {
        float scroll = this.scrollOffset();
        if (scroll == 0.0F) {
            this.slotRenderer.render(g, this.wrapped(-1), this.centerX, this.topY, 14 - this.slotInset * 2);
            this.slotRenderer.render(g, this.selected, this.centerX, this.midY, 18 - this.slotInset * 2);
            this.slotRenderer.render(g, this.wrapped(1), this.centerX, this.bottomY, 14 - this.slotInset * 2);
        } else {
            int half = 12;
            PoseScissor.enable(g, this.centerX - half, this.topY - 11, this.centerX + half, this.bottomY + 11);

            for (int offset = -2; offset <= 2; offset++) {
                float position = offset + scroll;
                if (!(Math.abs(position) > 1.6F)) {
                    float size = Mth.lerp(Math.min(1.0F, Math.abs(position)), 18.0F, 14.0F) - this.slotInset * 2;
                    this.slotRenderer.render(g, this.wrapped(offset), this.centerX, this.slotY(position), Math.round(size));
                }
            }

            g.disableScissor();
        }
    }

    public boolean hovered(double mx, double my) {
        return this.insideColumn(mx, my);
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (this.insideColumn(mx, my) && sy != 0.0) {
            this.step(sy > 0.0 ? -1 : 1);
            return true;
        } else {
            return false;
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (this.hit(mx, my, this.topY, 22)) {
            this.step(-1);
            return true;
        } else if (this.hit(mx, my, this.bottomY, 22)) {
            this.step(1);
            return true;
        } else {
            return this.hit(mx, my, this.midY, 24);
        }
    }

    private boolean insideColumn(double mx, double my) {
        double half = 12.0;
        double top = this.topY - 11.0;
        double bottom = this.bottomY + 11.0;
        return mx >= this.centerX - half && mx < this.centerX + half && my >= top && my < bottom;
    }

    private boolean hit(double mx, double my, int cy, int size) {
        double half = size / 2.0;
        return mx >= this.centerX - half && mx < this.centerX + half && my >= cy - half && my < cy + half;
    }

    public interface SlotRenderer {
        void render(GuiGraphics var1, int var2, int var3, int var4, int var5);
    }
}
