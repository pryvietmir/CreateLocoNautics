package com.lycoris.loconautics.foundation.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class FittedScreen extends NoBlurScreen {
    private static final float MARGIN = 4.0F;
    private static final float MIN_FIT = 0.3F;
    private boolean insideFit;

    protected FittedScreen(Component title) {
        super(title);
    }

    protected abstract int fitWidth();

    protected abstract int fitHeight();

    protected final float fit() {
        float byWidth = (this.width - 8.0F) / this.fitWidth();
        float byHeight = (this.height - 8.0F) / this.fitHeight();
        return Math.max(0.3F, Math.min(1.0F, Math.min(byWidth, byHeight)));
    }

    private double localX(double x) {
        return (x - this.width / 2.0) / this.fit() + this.width / 2.0;
    }

    private double localY(double y) {
        return (y - this.height / 2.0) / this.fit() + this.height / 2.0;
    }

    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float fit = this.fit();
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.insideFit = true;
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0F, this.height / 2.0F, 0.0F);
        graphics.pose().scale(fit, fit, 1.0F);
        graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F, 0.0F);
        this.renderFitted(graphics, (int)Math.round(this.localX(mouseX)), (int)Math.round(this.localY(mouseY)), partialTick);
        graphics.pose().popPose();
        this.insideFit = false;
    }

    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.insideFit) {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    protected void renderFitted(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    public final boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.fittedMouseClicked(this.localX(mouseX), this.localY(mouseY), button);
    }

    public final boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.fittedMouseReleased(this.localX(mouseX), this.localY(mouseY), button);
    }

    public final boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float fit = this.fit();
        return this.fittedMouseDragged(this.localX(mouseX), this.localY(mouseY), button, dragX / fit, dragY / fit);
    }

    public final boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.fittedMouseScrolled(this.localX(mouseX), this.localY(mouseY), scrollX, scrollY);
    }

    public final void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(this.localX(mouseX), this.localY(mouseY));
    }

    protected boolean fittedMouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean fittedMouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected boolean fittedMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    protected boolean fittedMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
