package com.lycoris.loconautics.content.transmission;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.items.ItemStackHandler;

public class TransmissionMenu extends BindFrequencyMenu<TransmissionBlockEntity> {
    public static final String SPEED_KEY = "gui.loconautics.bind_frequency.speed";
    public static final String DIRECTION_KEY = "gui.loconautics.bind_frequency.direction";

    public TransmissionMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(
            type,
            id,
            inv,
            extraData,
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData)
        );
    }

    public TransmissionMenu(MenuType<?> type, int id, Inventory inv, TransmissionBlockEntity be) {
        super(
            type,
            id,
            inv,
            be,
            Component.translatable("gui.loconautics.bind_frequency.speed"),
            Component.translatable("gui.loconautics.bind_frequency.direction")
        );
    }

    @Override
    protected int rowCount() {
        return 2;
    }

    protected TransmissionBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        if (Minecraft.getInstance().level.getBlockEntity(extraData.readBlockPos()) instanceof TransmissionBlockEntity tbe) {
            tbe.readClient(extraData.readNbt(), extraData.registryAccess());
            return tbe;
        } else {
            return null;
        }
    }

    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(4);
        if (this.contentHolder != null) {
            handler.setStackInSlot(0, ((TransmissionBlockEntity)this.contentHolder).getSpeedFreqFirst().copy());
            handler.setStackInSlot(1, ((TransmissionBlockEntity)this.contentHolder).getSpeedFreqSecond().copy());
            handler.setStackInSlot(2, ((TransmissionBlockEntity)this.contentHolder).getDirFreqFirst().copy());
            handler.setStackInSlot(3, ((TransmissionBlockEntity)this.contentHolder).getDirFreqSecond().copy());
        }

        return handler;
    }

    protected void saveData(TransmissionBlockEntity be) {
        if (be != null) {
            be.setSpeedFrequency(this.ghostInventory.getStackInSlot(0), this.ghostInventory.getStackInSlot(1));
            be.setDirectionFrequency(this.ghostInventory.getStackInSlot(2), this.ghostInventory.getStackInSlot(3));
        }
    }
}
