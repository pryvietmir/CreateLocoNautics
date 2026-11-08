package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BearingAxleMenu extends BindFrequencyMenu<BearingAxleBlockEntity> {
    public static final String LEFT_KEY = "gui.loconautics.bind_frequency.left";
    public static final String RIGHT_KEY = "gui.loconautics.bind_frequency.right";

    public BearingAxleMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(
            type,
            id,
            inv,
            extraData,
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData)
        );
    }

    public BearingAxleMenu(MenuType<?> type, int id, Inventory inv, BearingAxleBlockEntity be) {
        super(type, id, inv, be, Component.translatable("gui.loconautics.bind_frequency.left"), Component.translatable("gui.loconautics.bind_frequency.right"));
    }

    @Override
    protected int rowCount() {
        return 2;
    }

    protected BearingAxleBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        if (Minecraft.getInstance().level.getBlockEntity(extraData.readBlockPos()) instanceof BearingAxleBlockEntity axle) {
            axle.readClient(extraData.readNbt(), extraData.registryAccess());
            return axle;
        } else {
            return null;
        }
    }

    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(4);
        if (this.contentHolder != null) {
            handler.setStackInSlot(0, ((BearingAxleBlockEntity)this.contentHolder).getLeftFreqFirst().copy());
            handler.setStackInSlot(1, ((BearingAxleBlockEntity)this.contentHolder).getLeftFreqSecond().copy());
            handler.setStackInSlot(2, ((BearingAxleBlockEntity)this.contentHolder).getRightFreqFirst().copy());
            handler.setStackInSlot(3, ((BearingAxleBlockEntity)this.contentHolder).getRightFreqSecond().copy());
        }

        return handler;
    }

    protected void saveData(BearingAxleBlockEntity be) {
        if (be != null) {
            be.setLeftFrequency(this.ghostInventory.getStackInSlot(0), this.ghostInventory.getStackInSlot(1));
            be.setRightFrequency(this.ghostInventory.getStackInSlot(2), this.ghostInventory.getStackInSlot(3));
        }
    }
}
