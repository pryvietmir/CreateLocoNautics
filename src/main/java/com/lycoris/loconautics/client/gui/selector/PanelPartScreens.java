package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlotTarget;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelbutton.ButtonSelectorScreen;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeMetric;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeSelectorScreen;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panellever.LeverSelectorScreen;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchSelectorScreen;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import com.lycoris.loconautics.content.controls.panelvalve.ValveSelectorScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class PanelPartScreens {
    private PanelPartScreens() {
    }

    public static PartSelectorScreen create(PanelFrequencyMenu menu, Inventory inventory, Component title) {
        PanelPart part = menu.part();
        if (part instanceof ButtonPart button) {
            return new ButtonSelectorScreen(menu, inventory, button);
        } else if (part instanceof LeverPart lever) {
            return new LeverSelectorScreen(menu, inventory, lever);
        } else if (part instanceof SwitchPart switcher) {
            return new SwitchSelectorScreen(menu, inventory, switcher);
        } else if (!(part instanceof GaugeBlockEntity.Gauge gauge)) {
            int wheelColor = part instanceof ValvePart valve ? valve.wheelColor() : 0;
            return new ValveSelectorScreen(menu, inventory, wheelColor);
        } else {
            GaugeMetric metric = menu.contentHolder != null && ((PanelSlotTarget)menu.contentHolder).panel() instanceof GaugeBlockEntity gaugePanel
                ? gaugePanel.pickableMetric(gauge)
                : gauge.metric();
            return new GaugeSelectorScreen(menu, inventory, metric, gauge.bound(), gauge.background(), gauge.front(), gauge.needleColor(), gauge.options());
        }
    }
}
