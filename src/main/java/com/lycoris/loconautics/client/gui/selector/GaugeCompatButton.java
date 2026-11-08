package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panelgauge.GaugeCompat;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

public class GaugeCompatButton extends SelectionScrollInput {
    private static final int BLINK_MS = 450;
    private static final int STEPS_TO_HIDE_HINT = 3;
    private static final int TOOLTIP_OFFSET = 12;
    private static final int TOOLTIP_PAD = 3;
    private static final int TOOLTIP_GAP = 8;
    private final List<GaugeCompat.Entry> entries = GaugeCompat.sorted();
    private final List<Component> modLines = new ArrayList<>();
    private final String hintId;
    private int scrolls;

    public GaugeCompatButton(int x, int y, String hintId) {
        super(x, y, 18, 18);
        this.hintId = hintId;
        List<Component> names = new ArrayList<>();

        for (GaugeCompat.Entry entry : this.entries) {
            names.add(Component.literal(entry.name()).withStyle(entry.installed() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }

        this.forOptions(names);
        this.titled(Component.translatable("gui.loconautics.selector.compat"));
        this.calling(index -> {
            if (++this.scrolls >= 3) {
                ClientSeenHints.markSeen(hintId);
            }
        });
    }

    public void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            this.isHovered = this.isMouseOver(mouseX, mouseY);
            SelectorPanel.blitButton(graphics, this.getX(), this.getY(), this.isHovered, false);
            AllIcons.I_SEND_AND_RECEIVE.render(graphics, this.getX() + 1, this.getY() + 1);
            if (!ClientSeenHints.seen(this.hintId) && System.currentTimeMillis() / 450L % 2L == 0L) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.drawString(Minecraft.getInstance().font, "!", this.getX() + this.width - 5, this.getY() - 1, -171, true);
                graphics.pose().popPose();
            }
        }
    }

    public void playDownSound(SoundManager soundManager) {
    }

    protected void updateTooltip() {
        this.toolTip.clear();
        if (this.title != null && this.modLines != null) {
            this.modLines.clear();
            this.modLines.add(this.title.plainCopy().withStyle(style -> style.withColor(HEADER_RGB.getRGB())));

            for (int i = 0; i < this.options.size(); i++) {
                this.modLines
                    .add(
                        Component.empty()
                            .append(i == this.state ? "-> " : "> ")
                            .append((Component)this.options.get(i))
                            .withStyle(i == this.state ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                    );
            }

            this.modLines
                .add(
                    Component.translatable("gui.loconautics.selector.compat.view")
                        .withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC})
                );
        }
    }

    private static int tooltipHeight(int lines) {
        return lines * 10 + (lines > 1 ? 2 : 0) + 6;
    }

    private void renderAt(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int top) {
        graphics.renderComponentTooltip(font, lines, mouseX, top + 3 + 12);
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (this.isMouseOver(mouseX, mouseY)) {
            GaugeCompat.Entry current = this.entries.get(this.getState());
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(current.name()).withStyle(style -> style.withColor(HEADER_RGB.getRGB())));
            if (!current.installed()) {
                lines.add(
                    Component.translatable("gui.loconautics.selector.compat.missing")
                        .withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC})
                );
            }

            for (String block : current.blocks()) {
                lines.add(Component.literal("> " + block).withStyle(current.installed() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
            }

            int tallest = 0;

            for (GaugeCompat.Entry entry : this.entries) {
                tallest = Math.max(tallest, entry.blocks().size() + (entry.installed() ? 1 : 2));
            }

            int firstHeight = tooltipHeight(this.modLines.size());
            int top = this.getY() + this.height / 2 - (firstHeight + 8 + tooltipHeight(tallest)) / 2;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 800.0F);
            this.renderAt(graphics, font, this.modLines, mouseX, top);
            this.renderAt(graphics, font, lines, mouseX, top + firstHeight + 8);
            graphics.pose().popPose();
        }
    }
}
