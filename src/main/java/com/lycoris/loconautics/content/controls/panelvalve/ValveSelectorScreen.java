package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.client.gui.selector.PartModelViewer;
import com.lycoris.loconautics.client.gui.selector.PartSelectorScreen;
import com.lycoris.loconautics.client.gui.selector.ValveModelViewer;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.network.packets.controls.selector.ValveSelectorApplyPacket;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class ValveSelectorScreen extends PartSelectorScreen {
    private static final int WHEEL = 0;
    private final ValveModelViewer viewer = new ValveModelViewer();
    private final int initialWheelColor;

    public ValveSelectorScreen(PanelFrequencyMenu menu, Inventory inventory, int wheelColor) {
        super(menu, inventory, "valve_selector");
        this.initialWheelColor = wheelColor;
    }

    @Override
    protected PartModelViewer viewer() {
        return this.viewer;
    }

    @Override
    protected List<PartSelectorScreen.ColumnSpec> columnSpecs() {
        return List.of(
            new PartSelectorScreen.ColumnSpec(
                217, 35, "gui.loconautics.selector.wheel", (g, i, cx, cy, box) -> ValveModelViewer.renderValveIcon(g, this.state(), i, cx, cy, box)
            )
        );
    }

    @Override
    protected int initialSelection(int column) {
        return this.initialWheelColor;
    }

    @Override
    protected void pushToViewer() {
        this.viewer.setWheelColor(this.selected(0));
    }

    @Override
    protected void sendApply() {
        PacketDistributor.sendToServer(new ValveSelectorApplyPacket(this.panelPos, this.slot.ordinal(), this.selected(0)), new CustomPacketPayload[0]);
    }
}
