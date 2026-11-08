package com.lycoris.loconautics.content.frequency;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class BindFrequencyScreen<T, M extends BindFrequencyMenu<T>> extends AbstractSimiContainerScreen<M> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("loconautics", "textures/gui/frequency/frequency_bind.png");
    private static final int PANEL_U = 0;
    private static final int PANEL_V = 0;
    private static final int PANEL_W = 214;
    private static final int PANEL_H = 95;
    private static final int TEXTURE_W = 214;
    private static final int TEXTURE_H = 127;
    private static final int INV_X = 19;
    private static final int INV_Y = 100;
    private static final int BTN_Y = 60;
    private static final int BTN_CONFIRM_X = 157;
    private static final int BTN_TRASH_X = 179;
    private static final int LABEL_CENTER_X = 54;
    private static final int ROW_LABEL_Y0 = 40;
    private static final int ROW_LABEL_STEP = 29;
    private final ResourceLocation texture;
    private final int panelW;
    private final int panelH;
    private final int textureW;
    private final int textureH;
    private final int invX;
    private final int invY;
    private final int btnY;
    private final int btnConfirmX;
    private final int btnTrashX;
    private IconButton confirmButton;
    private IconButton trashButton;
    private Rect2i inventoryArea = new Rect2i(0, 0, 0, 0);

    public BindFrequencyScreen(M menu, Inventory inv, Component title) {
        this(menu, inv, title, TEXTURE, 214, 95, 214, 127, 19, 100, 60, 157, 179);
    }

    protected BindFrequencyScreen(
        M menu,
        Inventory inv,
        Component title,
        ResourceLocation texture,
        int panelW,
        int panelH,
        int textureW,
        int textureH,
        int invX,
        int invY,
        int btnY,
        int btnConfirmX,
        int btnTrashX
    ) {
        super(menu, inv, title);
        this.texture = texture;
        this.panelW = panelW;
        this.panelH = panelH;
        this.textureW = textureW;
        this.textureH = textureH;
        this.invX = invX;
        this.invY = invY;
        this.btnY = btnY;
        this.btnConfirmX = btnConfirmX;
        this.btnTrashX = btnTrashX;
    }

    protected void init() {
        this.setWindowSize(this.panelW, this.invY + 108);
        super.init();
        ((BindFrequencyMenu)this.menu).slotsActive = true;
        this.confirmButton = new IconButton(this.leftPos + this.btnConfirmX, this.topPos + this.btnY, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(() -> this.minecraft.player.closeContainer());
        this.addRenderableWidget(this.confirmButton);
        this.trashButton = new IconButton(this.leftPos + this.btnTrashX, this.topPos + this.btnY, AllIcons.I_TRASH);
        this.trashButton.withCallback(this::clearAllFrequencies);
        this.addRenderableWidget(this.trashButton);
        this.inventoryArea = new Rect2i(this.leftPos + this.invX, this.topPos + this.invY, 176, 108);
    }

    private void clearAllFrequencies() {
        for (int i = 0; i < ((BindFrequencyMenu)this.menu).rowLabels.length * 2; i++) {
            ((BindFrequencyMenu)this.menu).ghostInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(this.texture, this.leftPos, this.topPos, 0.0F, 0.0F, this.panelW, this.panelH, this.textureW, this.textureH);
        this.renderPlayerInventory(graphics, this.leftPos + this.invX, this.topPos + this.invY);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = this.minecraft.font;
        int color = -1;
        int textH = 9;

        for (int row = 0; row < ((BindFrequencyMenu)this.menu).rowLabels.length; row++) {
            Component label = ((BindFrequencyMenu)this.menu).rowLabels[row];
            int y = 40 + 29 * row;
            graphics.drawString(font, label, 54 - font.width(label) / 2, y - textH / 2, color, true);
        }
    }

    public List<Rect2i> getExtraAreas() {
        return List.of(this.inventoryArea);
    }
}
