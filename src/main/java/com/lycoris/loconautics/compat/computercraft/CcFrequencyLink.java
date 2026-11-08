package com.lycoris.loconautics.compat.computercraft;

import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class CcFrequencyLink implements IRedstoneLinkable {
    private final BearingAxleBlockEntity axle;
    private final ServerLevel level;
    private final Couple<Frequency> key;
    private final boolean listening;
    private int receivedStrength;
    private int transmitStrength;

    public CcFrequencyLink(BearingAxleBlockEntity axle, ServerLevel level, ItemStack first, ItemStack second, boolean listening) {
        this.axle = axle;
        this.level = level;
        this.key = Couple.create(Frequency.of(first), Frequency.of(second));
        this.listening = listening;
    }

    public void addToNetwork() {
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(this.level, this);
    }

    public void removeFromNetwork() {
        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(this.level, this);
    }

    public int getReceivedStrength() {
        return this.receivedStrength;
    }

    public void setTransmitStrength(int strength) {
        int clamped = Math.max(0, Math.min(15, strength));
        if (clamped != this.transmitStrength) {
            this.transmitStrength = clamped;
            Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(this.level, this);
        }
    }

    public int getTransmittedStrength() {
        return this.transmitStrength;
    }

    public boolean isListening() {
        return this.listening;
    }

    public boolean isAlive() {
        return !this.axle.isRemoved();
    }

    public Couple<Frequency> getNetworkKey() {
        return this.key;
    }

    public BlockPos getLocation() {
        return this.axle.getBlockPos();
    }

    public void setReceivedStrength(int strength) {
        this.receivedStrength = strength;
    }
}
