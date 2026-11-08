package com.lycoris.loconautics.content.frequency;

import com.simibubi.create.foundation.gui.menu.GhostItemMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public abstract class BindFrequencyMenu<T> extends GhostItemMenu<T> {
    public boolean slotsActive;
    public final Component[] rowLabels;

    protected BindFrequencyMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData, Component... rowLabels) {
        super(type, id, inv, extraData);
        this.rowLabels = rowLabels;
    }

    protected BindFrequencyMenu(MenuType<?> type, int id, Inventory inv, T be, Component... rowLabels) {
        super(type, id, inv, be);
        this.rowLabels = rowLabels;
    }

    protected abstract int rowCount();

    protected void addSlots() {
        this.addPlayerSlots(27, 60 + 29 * this.rowCount());

        for (int row = 0; row < this.rowCount(); row++) {
            this.addGhostPair(row * 2, 94, 112, 32 + 29 * row);
        }
    }

    protected final void addGhostPair(int firstIndex, int firstX, int secondX, int y) {
        this.addSlot(new BindFrequencyMenu.GhostSlot(this.ghostInventory, firstIndex, firstX, y));
        this.addSlot(new BindFrequencyMenu.GhostSlot(this.ghostInventory, firstIndex + 1, secondX, y));
    }

    protected void addPlayerSlots(int x, int y) {
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            this.addSlot(new BindFrequencyMenu.PlayerSlot(this.playerInventory, hotbarSlot, x + hotbarSlot * 18, y + 58));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new BindFrequencyMenu.PlayerSlot(this.playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
    }

    protected boolean allowRepeats() {
        return true;
    }

    private static class GhostSlot extends SlotItemHandler {
        public GhostSlot(IItemHandler itemHandler, int index, int x, int y) {
            super(itemHandler, index, x, y);
        }

        public boolean isFake() {
            return true;
        }

        public boolean isActive() {
            return true;
        }
    }

    private class PlayerSlot extends Slot {
        public PlayerSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        public boolean isActive() {
            return BindFrequencyMenu.this.slotsActive;
        }
    }
}
