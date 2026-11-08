package com.lycoris.loconautics.network.packets.controls;

import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.PacketTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PanelPartEdit {
    private PanelPartEdit() {
    }

    public static <T extends PanelPart> void run(IPayloadContext context, BlockPos panelPos, int slotIndex, Class<T> partType, PanelPartEdit.Action<T> action) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (slotIndex >= 0 && slotIndex < PanelSlot.values().length) {
                    PanelBlockEntity panel = PacketTargets.blockEntity(player.level(), panelPos, PanelBlockEntity.class);
                    if (panel != null && PacketTargets.withinReach(player, panel)) {
                        PanelSlot slot = PanelSlot.values()[slotIndex];
                        PanelPart part = panel.getPart(slot);
                        if (partType.isInstance(part)) {
                            action.apply(player, panel, slot, partType.cast(part));
                        }
                    }
                }
            }
        });
    }

    @FunctionalInterface
    public interface Action<T extends PanelPart> {
        void apply(ServerPlayer var1, PanelBlockEntity var2, PanelSlot var3, T var4);
    }
}
