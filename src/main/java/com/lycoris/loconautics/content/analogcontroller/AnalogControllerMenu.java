package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.items.ItemStackHandler;

public class AnalogControllerMenu extends BindFrequencyMenu<AnalogControllerBlockEntity> {
    public static final String FORWARD_KEY = "gui.loconautics.bind_frequency.speed";
    public static final String BACKWARD_KEY = "gui.loconautics.bind_frequency.direction";
    public static final String LEFT_KEY = "gui.loconautics.bind_frequency.left";
    public static final String RIGHT_KEY = "gui.loconautics.bind_frequency.right";

    public AnalogControllerMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(
            type,
            id,
            inv,
            extraData,
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData),
            (Component)ComponentSerialization.TRUSTED_STREAM_CODEC.decode(extraData)
        );
    }

    public AnalogControllerMenu(MenuType<?> type, int id, Inventory inv, AnalogControllerBlockEntity be) {
        super(
            type,
            id,
            inv,
            be,
            Component.translatable("gui.loconautics.bind_frequency.speed"),
            Component.translatable("gui.loconautics.bind_frequency.direction"),
            Component.translatable("gui.loconautics.bind_frequency.left"),
            Component.translatable("gui.loconautics.bind_frequency.right")
        );
    }

    @Override
    protected int rowCount() {
        return 4;
    }

    protected AnalogControllerBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        if (Minecraft.getInstance().level.getBlockEntity(extraData.readBlockPos()) instanceof AnalogControllerBlockEntity ace) {
            ace.readClient(extraData.readNbt(), extraData.registryAccess());
            return ace;
        } else {
            return null;
        }
    }

    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(8);
        if (this.contentHolder != null) {
            handler.setStackInSlot(0, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyFirst().copy());
            handler.setStackInSlot(1, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencySecond().copy());
            handler.setStackInSlot(2, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyBackFirst().copy());
            handler.setStackInSlot(3, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyBackSecond().copy());
            handler.setStackInSlot(4, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyLeftFirst().copy());
            handler.setStackInSlot(5, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyLeftSecond().copy());
            handler.setStackInSlot(6, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyRightFirst().copy());
            handler.setStackInSlot(7, ((AnalogControllerBlockEntity)this.contentHolder).getFrequencyRightSecond().copy());
        }

        return handler;
    }

    protected void saveData(AnalogControllerBlockEntity be) {
        if (be != null) {
            be.setFrequency(this.ghostInventory.getStackInSlot(0), this.ghostInventory.getStackInSlot(1));
            be.setBackwardFrequency(this.ghostInventory.getStackInSlot(2), this.ghostInventory.getStackInSlot(3));
            be.setLeftFrequency(this.ghostInventory.getStackInSlot(4), this.ghostInventory.getStackInSlot(5));
            be.setRightFrequency(this.ghostInventory.getStackInSlot(6), this.ghostInventory.getStackInSlot(7));
        }
    }
}
