package com.lycoris.loconautics.client.gui.selector;

import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

public class SelectorSpriteButton extends IconButton {
    public SelectorSpriteButton(int x, int y, ScreenElement icon) {
        super(x, y, icon);
    }

    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            this.isHovered = mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
            SelectorPanel.blitButton(graphics, this.getX(), this.getY(), this.isHovered, this.green);
            this.icon.render(graphics, this.getX() + 1, this.getY() + 1);
        }
    }
}
