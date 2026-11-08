package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.LabelText;
import com.lycoris.loconautics.network.packets.controls.selector.PanelLabelPacket;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SelectorLabelBar {
    private static final int ICON_SIZE = 9;
    private final Font font;
    private final Consumer<GuiEventListener> focus;
    private final BlockPos panelPos;
    private final PanelSlot slot;
    private final int left;
    private final int top;
    private EditBox box;
    private String initial = "";
    private boolean editing;

    public SelectorLabelBar(Font font, Consumer<GuiEventListener> focus, BlockPos panelPos, PanelSlot slot, int left, int top) {
        this.font = font;
        this.focus = focus;
        this.panelPos = panelPos;
        this.slot = slot;
        this.left = left;
        this.top = top;
    }

    public EditBox createBox() {
        Component current = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.panelPos) instanceof PanelBlockEntity panel
            ? panel.label(this.slot)
            : null;
        this.initial = current == null ? "" : LabelText.toRaw(current);
        this.editing = false;
        this.box = new EditBox(new NoShadowFontWrapper(this.font), this.left, this.top + 3, 200, 10, Component.empty());
        this.box.setBordered(false);
        this.box.setMaxLength(32);
        this.box.setTextColor(-12566464);
        this.box.setValue(this.initial);
        this.box.setResponder(raw -> this.center(this.font.width(raw)));
        this.box.setFocused(false);
        this.box.visible = false;
        this.center(this.font.width(this.initial));
        return this.box;
    }

    public boolean editing() {
        return this.editing;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean over = this.overBar(mouseX, mouseY);
        String raw = this.box.getValue();
        Component shown = (Component)(raw.isEmpty()
            ? Component.translatable("gui.loconautics.selector.label.empty").withStyle(ChatFormatting.DARK_GRAY)
            : LabelText.parse(Component.literal(raw)));
        int width = Math.min(this.font.width(shown), 200);
        int x = this.labelX(width);
        if (!this.editing) {
            graphics.drawString(this.font, shown, x, this.top + 3 + 1, -12566464, false);
        }

        SelectorPanel.blitRename(graphics, Math.min(x + width + 5, this.left + 339 - 9 - 4), this.top + 3, over);
        if (over && !this.editing) {
            graphics.renderComponentTooltip(
                this.font,
                List.of(
                    Component.translatable("gui.loconautics.selector.label"),
                    Component.translatable("gui.loconautics.selector.label.hint").withStyle(ChatFormatting.GRAY)
                ),
                mouseX,
                mouseY
            );
        }
    }

    private int labelX(int textWidth) {
        return this.left + 169 - (Math.min(textWidth, 200) + 9 + 5) / 2;
    }

    private void center(int textWidth) {
        this.box.setX(this.labelX(textWidth));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean onBar = this.overBar(mouseX, mouseY);
        if (onBar && !this.editing && button == 0) {
            this.start();
            return true;
        }

        if (!onBar && this.editing) {
            this.stop();
        }

        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == 258) {
            if (this.editing) {
                this.stop();
            } else {
                this.start();
            }

            return true;
        } else {
            if (!this.editing || keyCode != 257 && keyCode != 335) {
                return false;
            }

            this.stop();
            return true;
        }
    }

    public boolean typing(int keyCode, int scanCode, int modifiers) {
        if (this.editing && keyCode != 256) {
            this.box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        } else {
            return false;
        }
    }

    public void save() {
        if (this.box != null && !this.box.getValue().equals(this.initial)) {
            PacketDistributor.sendToServer(new PanelLabelPacket(this.panelPos, this.slot.ordinal(), this.box.getValue()), new CustomPacketPayload[0]);
        }
    }

    private boolean overBar(double mouseX, double mouseY) {
        return mouseX >= this.left && mouseX < this.left + 339 && mouseY >= this.top && mouseY < this.top + 15;
    }

    private void start() {
        this.editing = true;
        this.box.visible = true;
        this.box.setFocused(true);
        this.focus.accept(this.box);
        this.box.moveCursorToEnd(false);
        this.box.setHighlightPos(0);
    }

    private void stop() {
        this.editing = false;
        this.box.visible = false;
        this.box.setFocused(false);
        this.focus.accept(null);
    }
}
