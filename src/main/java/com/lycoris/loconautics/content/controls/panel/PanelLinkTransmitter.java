package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PanelLinkTransmitter implements IRedstoneLinkable {
    private final BlockEntity owner;
    private final ItemStack first;
    private final ItemStack second;
    private final Couple<Frequency> key;
    private int strength;
    private boolean registered;

    public PanelLinkTransmitter(BlockEntity owner, ItemStack first, ItemStack second) {
        this.owner = owner;
        this.first = first.copy();
        this.second = second.copy();
        this.key = Couple.create(Frequency.of(first), Frequency.of(second));
    }

    public static boolean isEmpty(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty();
    }

    public static PanelLinkTransmitter read(BlockEntity owner, CompoundTag tag, Provider registries) {
        return new PanelLinkTransmitter(
            owner, ItemStack.parseOptional(registries, tag.getCompound("First")), ItemStack.parseOptional(registries, tag.getCompound("Second"))
        );
    }

    public CompoundTag write(Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("First", this.first.saveOptional(registries));
        tag.put("Second", this.second.saveOptional(registries));
        return tag;
    }

    public ItemStack first() {
        return this.first;
    }

    public ItemStack second() {
        return this.second;
    }

    public void transmit(Level level, int signal) {
        if (!this.registered) {
            this.strength = signal;
            this.registered = true;
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this);
        } else if (signal != this.strength) {
            this.strength = signal;
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(level, this);
        }
    }

    public void release(Level level) {
        if (this.registered) {
            this.registered = false;
            this.strength = 0;
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, this);
        }
    }

    public int getTransmittedStrength() {
        return this.strength;
    }

    public void setReceivedStrength(int power) {
    }

    public boolean isListening() {
        return false;
    }

    public boolean isAlive() {
        return this.registered && !this.owner.isRemoved();
    }

    public Couple<Frequency> getNetworkKey() {
        return this.key;
    }

    public BlockPos getLocation() {
        return this.owner.getBlockPos();
    }
}
