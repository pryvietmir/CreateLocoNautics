package com.lycoris.loconautics.client.gui.selector;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;

public class SelectorArrowButton extends IconButton {
    private final boolean next;

    public SelectorArrowButton(int x, int y, boolean next) {
        super(x, y, 10, 10, AllIcons.I_CONFIG_PREV);
        this.next = next;
    }

    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
            SelectorPanel.blitArrow(graphics, this.getX(), this.getY(), this.next, this.isHovered);
        }
    }
}
