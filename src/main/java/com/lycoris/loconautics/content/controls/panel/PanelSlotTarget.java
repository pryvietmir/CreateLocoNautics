package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.foundation.utility.IInteractionChecker;
import net.minecraft.world.entity.player.Player;

public record PanelSlotTarget(PanelBlockEntity panel, PanelSlot slot) implements IInteractionChecker {
    public boolean hasFrequency() {
        return this.panel.getPart(this.slot) instanceof RedstoneLinkedPart;
    }

    public boolean canPlayerUse(Player player) {
        return !this.panel.isRemoved() && this.panel.hasPart(this.slot);
    }
}
