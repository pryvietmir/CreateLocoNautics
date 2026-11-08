package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class BodyColorSelectorScreen extends PartSelectorScreen {
    private static final int BODY = 0;
    private static final int COLOR = 1;
    private final BodyColorModelViewer viewer;
    private final int initialBody;
    private final int initialColor;
    private final int initialStyle;
    private final int initialOptions;

    protected BodyColorSelectorScreen(
        PanelFrequencyMenu menu, Inventory inventory, String titleKey, BodyColorModelViewer viewer, int body, int color, int style, int options
    ) {
        super(menu, inventory, titleKey);
        this.viewer = viewer;
        this.initialBody = body;
        this.initialColor = color;
        this.initialStyle = style;
        this.initialOptions = options;
    }

    protected abstract String colorTooltipKey();

    protected abstract void drawIcon(GuiGraphics var1, int var2, int var3, int var4, int var5, int var6, int var7, int var8);

    protected abstract CustomPacketPayload applyPacket(int var1, int var2, int var3, int var4);

    @Override
    protected boolean supportsInvert() {
        return true;
    }

    @Override
    protected PartModelViewer viewer() {
        return this.viewer;
    }

    @Override
    protected List<PartSelectorScreen.ColumnSpec> columnSpecs() {
        return List.of(
            new PartSelectorScreen.ColumnSpec(
                185,
                4,
                "gui.loconautics.selector.body",
                (g, i, cx, cy, box) -> this.drawIcon(g, i, this.selected(1), this.selectedStyle(), this.selectedOptions(), cx, cy, box)
            ),
            new PartSelectorScreen.ColumnSpec(
                249,
                35,
                this.colorTooltipKey(),
                (g, i, cx, cy, box) -> this.drawIcon(g, this.selected(0), i, this.selectedStyle(), this.selectedOptions(), cx, cy, box)
            )
        );
    }

    @Override
    protected int initialStyle() {
        return this.initialStyle;
    }

    @Override
    protected int initialOptions() {
        return this.initialOptions;
    }

    @Override
    protected int initialSelection(int column) {
        return column == 0 ? this.initialBody : this.initialColor;
    }

    @Override
    protected void pushToViewer() {
        this.viewer.setBody(this.selected(0));
        this.viewer.setColor(this.selected(1));
        this.viewer.setStyle(this.selectedStyle());
        this.viewer.setOptions(this.selectedOptions());
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(
            this.applyPacket(this.selected(0), this.selected(1), this.selectedStyle(), this.rawOptions()), new CustomPacketPayload[0]
        );
    }
}
