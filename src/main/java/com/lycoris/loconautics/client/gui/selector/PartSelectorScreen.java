package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.client.gui.gauge.GaugePartColumn;
import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlock;
import com.lycoris.loconautics.network.packets.controls.selector.PanelInvertPacket;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public abstract class PartSelectorScreen extends AbstractSimiContainerScreen<PanelFrequencyMenu> {
    protected final BlockPos panelPos;
    protected final PanelSlot slot;
    protected SelectorLabelBar labelBar;
    private final boolean bound;
    private final String titleKey;
    private List<PartSelectorScreen.ColumnSpec> specs;
    private GaugePartColumn[] columns;
    private boolean inverted;
    private int style;
    private IconButton invertButton;
    private final List<IconButton> optionButtons = new ArrayList<>();
    private int options;
    private Rect2i inventoryArea = new Rect2i(0, 0, 0, 0);
    protected int guiLeft;
    protected int guiTop;

    protected PartSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, String titleKey) {
        super(menu, inventory, Component.translatable("gui.loconautics." + titleKey));
        this.bound = menu.part() != null;
        this.panelPos = this.bound ? menu.panelPos() : BlockPos.ZERO;
        this.slot = this.bound ? menu.slot() : PanelSlot.values()[0];
        this.titleKey = titleKey;
    }

    protected abstract PartModelViewer viewer();

    protected BlockState state() {
        return ((GaugeBlock)BoilerBlocks.LOCOMOTIVE_GAUGE.get()).defaultBlockState();
    }

    protected abstract List<PartSelectorScreen.ColumnSpec> columnSpecs();

    protected abstract int initialSelection(int var1);

    protected abstract void pushToViewer();

    protected abstract void sendApply();

    protected boolean supportsInvert() {
        return false;
    }

    @Nullable
    protected PanelStyleSet styleSet() {
        return null;
    }

    protected int initialStyle() {
        return 0;
    }

    protected final int selectedStyle() {
        return this.style;
    }

    protected int initialOptions() {
        return 0;
    }

    protected final int selectedOptions() {
        PanelStyleSet set = this.styleSet();
        return set == null ? this.options : set.effectiveOptions(this.options, this.style);
    }

    protected final int rawOptions() {
        return this.options;
    }

    protected final int selected(int column) {
        return this.columns[column].selected();
    }

    protected void addExtraControls() {
    }

    protected int optionX(PanelStyleSet set) {
        return 27;
    }

    protected boolean hasMetricBar() {
        return false;
    }

    protected final void setSelected(int column, int value) {
        if (this.columns != null) {
            this.columns[column].setSelected(value);
        }
    }

    protected void init() {
        this.setWindowSize(339, 305);
        super.init();
        ((PanelFrequencyMenu)this.menu).slotsActive = true;
        this.optionButtons.clear();
        this.guiLeft = this.leftPos;
        this.guiTop = this.topPos;
        this.inventoryArea = new Rect2i(this.leftPos + 81, this.topPos + 197, 176, 108);
        this.viewer().setState(this.state());
        this.style = this.initialStyle();
        this.options = this.initialOptions();
        this.specs = this.columnSpecs();
        this.columns = new GaugePartColumn[this.specs.size()];

        for (int i = 0; i < this.specs.size(); i++) {
            PartSelectorScreen.ColumnSpec spec = this.specs.get(i);
            this.columns[i] = SelectorPanel.column(this.guiLeft, this.guiTop, spec.stripX(), spec.count(), spec.renderer());
            this.columns[i].setSelected(this.initialSelection(i));
        }

        IconButton confirm = new SelectorSpriteButton(this.guiLeft + 314, this.guiTop + 169, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        confirm.setToolTip(Component.translatable("gui.loconautics." + this.titleKey + ".done"));
        this.addRenderableWidget(confirm);
        PanelStyleSet set = this.styleSet();
        if (set != null) {
            this.addStyleControls(set);
        }

        this.labelBar = new SelectorLabelBar(this.font, this::setFocused, this.panelPos, this.slot, this.guiLeft, this.guiTop);
        this.addRenderableWidget(this.labelBar.createBox());
        this.addExtraControls();
        if (this.supportsInvert()) {
            this.inverted = Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.panelPos) instanceof PanelBlockEntity panel
                && panel.isInverted(this.slot);
            this.invertButton = new SelectorSpriteButton(this.guiLeft + 5, this.guiTop + 169, AllIcons.I_FLIP);
            this.invertButton.withCallback(this::toggleInvert);
            this.addRenderableWidget(this.invertButton);
            this.refreshInvertButton();
        }
    }

    private void addStyleControls(PanelStyleSet set) {
        if (set.count() > 1) {
            IconButton previous = new SelectorArrowButton(this.guiLeft + 20, this.guiTop + 84, false);
            previous.withCallback(() -> this.stepStyle(-1));
            previous.setToolTip(Component.translatable("gui.loconautics.selector.style.previous"));
            this.addRenderableWidget(previous);
            IconButton next = new SelectorArrowButton(this.guiLeft + 130, this.guiTop + 84, true);
            next.withCallback(() -> this.stepStyle(1));
            next.setToolTip(Component.translatable("gui.loconautics.selector.style.next"));
            this.addRenderableWidget(next);
        }

        SelectorOptionButtons.add(set, this.guiLeft + this.optionX(set), this.guiTop + 169, () -> this.options, nextx -> this.options = nextx, button -> {
            this.optionButtons.add(button);
            this.addRenderableWidget(button);
        });
    }

    private void stepStyle(int step) {
        this.style = Math.floorMod(this.style + step, this.styleSet().count());
    }

    private void toggleInvert() {
        this.inverted = !this.inverted;
        PacketDistributor.sendToServer(new PanelInvertPacket(this.panelPos, this.slot.ordinal(), this.inverted), new CustomPacketPayload[0]);
        this.refreshInvertButton();
    }

    private void refreshInvertButton() {
        this.invertButton.green = this.inverted;
    }

    private void renderStyleDots(GuiGraphics graphics, int mouseX, int mouseY) {
        PanelStyleSet set = this.styleSet();
        if (set != null && set.count() >= 2) {
            int hovered = SelectorPanel.styleDotAt(this.guiLeft, this.guiTop, set.count(), mouseX, mouseY);
            SelectorPanel.renderStyleDots(graphics, this.guiLeft, this.guiTop, set.count(), this.style, hovered);
            if (hovered >= 0) {
                graphics.renderComponentTooltip(
                    this.font,
                    List.of(
                        Component.translatable("gui.loconautics.selector.style"), Component.translatable(set.langKey(hovered)).withStyle(ChatFormatting.GRAY)
                    ),
                    mouseX,
                    mouseY
                );
            }
        }
    }

    private int styleDotUnder(double mouseX, double mouseY) {
        PanelStyleSet set = this.styleSet();
        return set != null && set.count() >= 2 ? SelectorPanel.styleDotAt(this.guiLeft, this.guiTop, set.count(), mouseX, mouseY) : -1;
    }

    private void renderInvertTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.invertButton != null && this.invertButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(
                this.font,
                List.of(
                    Component.translatable("gui.loconautics.selector.invert"),
                    Component.translatable(this.inverted ? "gui.loconautics.selector.invert.on" : "gui.loconautics.selector.invert.off")
                        .withStyle(this.inverted ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                ),
                mouseX,
                mouseY
            );
        }
    }

    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        SelectorPanel.blitPanel(graphics, this.guiLeft, this.guiTop);
        if (((PanelFrequencyMenu)this.menu).hasFrequency()) {
            SelectorPanel.blitSignalRow(graphics, this.guiLeft, this.guiTop);
            Component signal = ((PanelFrequencyMenu)this.menu).rowLabels[0];
            graphics.drawString(this.font, signal, this.guiLeft + 157 + (118 - this.font.width(signal)) / 2, this.guiTop + 34, -1, true);
        }

        if (this.hasMetricBar()) {
            SelectorPanel.blitMetricRow(graphics, this.guiLeft, this.guiTop);
        }

        for (PartSelectorScreen.ColumnSpec spec : this.specs) {
            SelectorPanel.blitStrip(graphics, this.guiLeft, this.guiTop, spec.stripX());
        }

        this.pushToViewer();
        SelectorPanel.renderViewer(graphics, this.viewer(), this.guiLeft, this.guiTop);

        for (GaugePartColumn column : this.columns) {
            column.render(graphics);
        }

        this.renderPlayerInventory(graphics, this.guiLeft + 81, this.guiTop + 197);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        PanelStyleSet set = this.styleSet();
        boolean optionsShown = set == null || set.hasOptions(this.style);
        this.optionButtons.forEach(button -> button.visible = optionsShown);
        super.render(graphics, mouseX, mouseY, partialTick);
        SelectorPanel.renderViewerOverlay(graphics, this.font, this.viewer(), this.guiLeft, this.guiTop);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 800.0F);
        this.renderStyleDots(graphics, mouseX, mouseY);

        for (int i = 0; i < this.columns.length; i++) {
            SelectorPanel.renderColumnTooltip(graphics, this.font, this.columns[i], this.specs.get(i).tooltipKey(), mouseX, mouseY);
        }

        this.renderInvertTooltip(graphics, mouseX, mouseY);
        this.labelBar.render(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    public List<Rect2i> getExtraAreas() {
        return List.of(this.inventoryArea);
    }

    public void onClose() {
        if (this.bound && this.columns != null) {
            this.sendApply();
        }

        if (this.labelBar != null) {
            this.labelBar.save();
        }

        super.onClose();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.labelBar.keyPressed(keyCode)) {
            return true;
        } else {
            return this.labelBar.typing(keyCode, scanCode, modifiers) ? true : super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.labelBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (SelectorPanel.clickedReset(this.viewer(), this.guiLeft, this.guiTop, mouseX, mouseY, button)) {
            return true;
        }

        int dot = this.styleDotUnder(mouseX, mouseY);
        if (dot >= 0 && button == 0) {
            this.style = dot;
            return true;
        }

        if (this.viewer().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (GaugePartColumn column : this.columns) {
            if (column.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.viewer().mouseDragged(mouseX, mouseY, button, dragX, dragY) ? true : super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.viewer().mouseReleased(mouseX, mouseY, button) ? true : super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isPauseScreen() {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.styleDotUnder(mouseX, mouseY) >= 0 && scrollY != 0.0) {
            this.style = Math.floorMod(this.style + (scrollY > 0.0 ? -1 : 1), this.styleSet().count());
            return true;
        }

        if (this.viewer().mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        for (GaugePartColumn column : this.columns) {
            if (column.mouseScrolled(mouseX, mouseY, scrollY)) {
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    public record ColumnSpec(int stripX, int count, String tooltipKey, GaugePartColumn.SlotRenderer renderer) {
    }
}
