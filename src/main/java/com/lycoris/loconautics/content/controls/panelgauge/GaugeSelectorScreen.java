package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.client.gui.gauge.GaugeModelViewer;
import com.lycoris.loconautics.client.gui.selector.GaugeCompatButton;
import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.client.gui.selector.PartSelectorScreen;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyleSet;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.network.packets.controls.LocomotiveGaugeSelectPacket;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class GaugeSelectorScreen extends PartSelectorScreen {
    private static final int BACKGROUND = 0;
    private static final int FRONT = 1;
    private static final int NEEDLE = 2;
    private static final int BACKGROUND_VARIANTS = 36;
    private static final int FRONT_VARIANTS = 4;
    private GaugeMetric metric;
    private final boolean bound;
    private GaugeCompatButton compatButton;
    private final GaugeModelViewer viewer = new GaugeModelViewer();
    private final int initialBackground;
    private final int initialFront;
    private final int initialNeedleColor;
    private final int initialOptions;

    public GaugeSelectorScreen(
        PanelFrequencyMenu menu, Inventory inventory, GaugeMetric metric, boolean bound, int background, int front, int needleColor, int options
    ) {
        super(menu, inventory, "gauge_selector");
        this.metric = metric;
        this.bound = bound;
        this.initialBackground = background;
        this.initialFront = front;
        this.initialNeedleColor = needleColor;
        this.initialOptions = options;
    }

    @Override
    protected PartModelViewer viewer() {
        return this.viewer;
    }

    @Override
    protected List<PartSelectorScreen.ColumnSpec> columnSpecs() {
        return List.of(
            new PartSelectorScreen.ColumnSpec(
                165,
                36,
                "gui.loconautics.selector.background",
                (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                    g, this.state(), i, this.selected(1), this.selected(2), this.selectedOptions(), cx, cy, box
                )
            ),
            new PartSelectorScreen.ColumnSpec(
                213,
                4,
                "gui.loconautics.selector.front",
                (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                    g, this.state(), this.selected(0), i, this.selected(2), this.selectedOptions(), cx, cy, box
                )
            ),
            new PartSelectorScreen.ColumnSpec(
                261,
                8,
                "gui.loconautics.selector.needle",
                (g, i, cx, cy, box) -> GaugeModelViewer.renderGaugeIcon(
                    g, this.state(), this.selected(0), this.selected(1), i, this.selectedOptions(), cx, cy, box
                )
            )
        );
    }

    @Override
    protected boolean hasMetricBar() {
        return !this.bound || GaugeMetric.valuesOf(this.metric.group()).length > 1;
    }

    @Override
    protected void addExtraControls() {
        this.compatButton = new GaugeCompatButton(this.guiLeft + 314 - 28, this.guiTop + 169, this.hintId());
        this.addRenderableWidget(this.compatButton);
        if (!this.bound) {
            int labelX = this.guiLeft + 157 + 4;
            Label unbound = new Label(labelX, this.guiTop + 29 + 5, Component.empty()).withShadow();
            unbound.text = Component.translatable("gui.loconautics.selector.bind");
            this.addRenderableWidget(unbound);
        } else {
            GaugeMetric[] choices = GaugeMetric.valuesOf(this.metric.group());
            if (choices.length >= 2) {
                List<Component> names = new ArrayList<>();

                for (GaugeMetric choice : choices) {
                    names.add(Component.translatable("loconautics." + choice.langKey()));
                }

                int x = this.guiLeft + 157;
                int y = this.guiTop + 29;
                Label label = new Label(x + 4, y + 5, Component.empty()).withShadow();
                SelectionScrollInput input = new SelectionScrollInput(x, y, 160, 18);
                input.forOptions(names).titled(Component.translatable("gui.loconautics.selector.metric")).writingTo(label).calling(index -> {
                    this.metric = choices[Math.max(0, Math.min(choices.length - 1, index))];
                    this.setSelected(0, this.metric.defaultBackground());
                }).setState(indexOf(choices, this.metric));
                this.addRenderableWidget(input);
                this.addRenderableWidget(label);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.compatButton.renderTooltips(graphics, this.font, mouseX, mouseY);
    }

    private String hintId() {
        return "gauge_compat:" + Minecraft.getInstance().level.dimension().location() + ":" + this.panelPos.asLong() + ":" + this.slot.ordinal();
    }

    private static int indexOf(GaugeMetric[] choices, GaugeMetric metric) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == metric) {
                return i;
            }
        }

        return 0;
    }

    @Override
    protected PanelStyleSet styleSet() {
        return PanelStyles.GAUGE;
    }

    @Override
    protected int optionX(PanelStyleSet set) {
        return 7;
    }

    @Override
    protected int initialOptions() {
        return this.initialOptions;
    }

    @Override
    protected int initialSelection(int column) {
        return switch (column) {
            case 0 -> this.initialBackground;
            case 1 -> this.initialFront;
            default -> this.initialNeedleColor;
        };
    }

    @Override
    protected void pushToViewer() {
        this.viewer.setBackground(this.selected(0));
        this.viewer.setFront(this.selected(1));
        this.viewer.setNeedleColor(this.selected(2));
        this.viewer.setOptions(this.selectedOptions());
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(
            new LocomotiveGaugeSelectPacket(
                this.panelPos, this.slot.ordinal(), this.metric, this.selected(0), this.selected(1), this.selected(2), this.selectedOptions()
            ),
            new CustomPacketPayload[0]
        );
    }
}
