package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.frequency.BindFrequencyMenu;
import com.lycoris.loconautics.network.PacketTargets;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PanelFrequencyMenu extends BindFrequencyMenu<PanelSlotTarget> {
    public static final String SIGNAL_KEY = "gui.loconautics.bind_frequency.signal";
    private BlockPos clientPos;
    private PanelSlot clientSlot;
    private PanelPart clientPart;

    public PanelFrequencyMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData, Component.translatable("gui.loconautics.bind_frequency.signal"));
    }

    public PanelFrequencyMenu(MenuType<?> type, int id, Inventory inv, PanelSlotTarget target) {
        super(type, id, inv, target, Component.translatable("gui.loconautics.bind_frequency.signal"));
    }

    public static void open(Player player, BlockPos panelPos, PanelSlot slot) {
        if (player instanceof ServerPlayer serverPlayer) {
            PanelBlockEntity panel = PacketTargets.blockEntity(serverPlayer.level(), panelPos, PanelBlockEntity.class);
            if (panel != null && panel.hasPart(slot)) {
                open(player, panel, slot);
            }
        }
    }

    public static void open(Player player, PanelBlockEntity panel, PanelSlot slot) {
        if (player instanceof ServerPlayer serverPlayer) {
            PanelSlotTarget target = new PanelSlotTarget(panel, slot);
            serverPlayer.openMenu(
                new SimpleMenuProvider(
                    (id, inv, p) -> new PanelFrequencyMenu((MenuType<?>)LoconauticsRegistries.PANEL_FREQUENCY_MENU.get(), id, inv, target),
                    panel.stackForSlot(slot).getHoverName()
                ),
                buf -> {
                    PanelPart part = panel.getPart(slot);
                    CompoundTag partTag = new CompoundTag();
                    part.write(partTag);
                    buf.writeBlockPos(panel.getBlockPos());
                    buf.writeVarInt(slot.ordinal());
                    buf.writeVarInt(part.type().ordinal());
                    buf.writeNbt(partTag);
                }
            );
        }
    }

    public BlockPos panelPos() {
        return this.contentHolder != null ? ((PanelSlotTarget)this.contentHolder).panel().getBlockPos() : this.clientPos;
    }

    public PanelSlot slot() {
        return this.contentHolder != null ? ((PanelSlotTarget)this.contentHolder).slot() : this.clientSlot;
    }

    public PanelPart part() {
        if (this.clientPart != null) {
            return this.clientPart;
        } else {
            return this.contentHolder == null ? null : ((PanelSlotTarget)this.contentHolder).panel().getPart(((PanelSlotTarget)this.contentHolder).slot());
        }
    }

    @Override
    protected int rowCount() {
        return 1;
    }

    public boolean hasFrequency() {
        return this.part() instanceof RedstoneLinkedPart;
    }

    @Override
    protected void addSlots() {
        this.addPlayerSlots(89, 215);
        if (this.hasFrequency()) {
            this.addGhostPair(0, 282, 300, 30);
        }
    }

    protected PanelSlotTarget createOnClient(RegistryFriendlyByteBuf extraData) {
        BlockPos pos = extraData.readBlockPos();
        int ordinal = extraData.readVarInt();
        PanelPartType[] types = PanelPartType.values();
        int typeOrdinal = extraData.readVarInt();
        CompoundTag partTag = extraData.readNbt();
        PanelSlot[] slots = PanelSlot.values();
        if (ordinal >= 0 && ordinal < slots.length) {
            this.clientPos = pos;
            this.clientSlot = slots[ordinal];
            if (typeOrdinal >= 0 && typeOrdinal < types.length && partTag != null) {
                this.clientPart = types[typeOrdinal].read(partTag);
            }

            PanelBlockEntity panel = PacketTargets.blockEntity(Minecraft.getInstance().level, pos, PanelBlockEntity.class);
            return panel == null ? null : new PanelSlotTarget(panel, this.clientSlot);
        } else {
            return null;
        }
    }

    protected ItemStackHandler createGhostInventory() {
        ItemStackHandler handler = new ItemStackHandler(2);
        PanelLinkTransmitter transmitter = this.contentHolder == null
            ? null
            : ((PanelSlotTarget)this.contentHolder).panel().transmitter(((PanelSlotTarget)this.contentHolder).slot());
        if (transmitter != null) {
            handler.setStackInSlot(0, transmitter.first().copy());
            handler.setStackInSlot(1, transmitter.second().copy());
        }

        return handler;
    }

    protected void saveData(PanelSlotTarget target) {
        if (target != null && target.canPlayerUse(this.player) && target.hasFrequency()) {
            ItemStack first = this.ghostInventory.getStackInSlot(0);
            ItemStack second = this.ghostInventory.getStackInSlot(1);
            target.panel().setLinkFrequency(target.slot(), first, second);
        }
    }
}
