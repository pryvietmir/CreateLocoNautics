package com.lycoris.loconautics.common;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import java.util.function.IntConsumer;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class RedstoneLinkPair {
    private final BlockEntity owner;
    private final IntConsumer onFirstStrength;
    private final IntConsumer onSecondStrength;
    private ItemStack firstFreqA = ItemStack.EMPTY;
    private ItemStack firstFreqB = ItemStack.EMPTY;
    private ItemStack secondFreqA = ItemStack.EMPTY;
    private ItemStack secondFreqB = ItemStack.EMPTY;
    @Nullable
    private RedstoneLinkPair.Receiver firstReceiver;
    @Nullable
    private RedstoneLinkPair.Receiver secondReceiver;
    @Nullable
    private Level registeredIn;

    public RedstoneLinkPair(BlockEntity owner, IntConsumer onFirstStrength, IntConsumer onSecondStrength) {
        this.owner = owner;
        this.onFirstStrength = onFirstStrength;
        this.onSecondStrength = onSecondStrength;
    }

    public void register(Level level) {
        this.unregister(level);
        if (level instanceof ServerLevel server && PortalTwins.masterBlock(server, this.owner.getBlockPos()) != null) {
            LoconauticsConstants.LOGGER.debug("[linkreg] skipped twin block {} in {}", this.owner.getBlockPos().toShortString(), level.dimension().location());
        } else {
            Frequency a1 = Frequency.of(this.firstFreqA);
            Frequency a2 = Frequency.of(this.firstFreqB);
            Frequency b1 = Frequency.of(this.secondFreqA);
            Frequency b2 = Frequency.of(this.secondFreqB);
            boolean hasFirst = a1 != Frequency.EMPTY || a2 != Frequency.EMPTY;
            boolean hasSecond = b1 != Frequency.EMPTY || b2 != Frequency.EMPTY;
            if (hasFirst || hasSecond) {
                this.registeredIn = level;
                LoconauticsConstants.LOGGER
                    .debug(
                        "[linkreg] register {} in {} first={} second={} id={}",
                        new Object[]{
                            this.owner.getBlockPos().toShortString(), level.dimension().location(), hasFirst, hasSecond, System.identityHashCode(this.owner)
                        }
                    );
            }

            if (hasFirst) {
                this.firstReceiver = new RedstoneLinkPair.Receiver(this.owner.getBlockPos(), Couple.create(a1, a2), this.owner, this.onFirstStrength);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this.firstReceiver);
                deliverCurrent(level, this.firstReceiver);
            }

            if (hasSecond) {
                this.secondReceiver = new RedstoneLinkPair.Receiver(this.owner.getBlockPos(), Couple.create(b1, b2), this.owner, this.onSecondStrength);
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, this.secondReceiver);
                deliverCurrent(level, this.secondReceiver);
            }
        }
    }

    private static void deliverCurrent(Level level, RedstoneLinkPair.Receiver receiver) {
        int power = 0;

        for (IRedstoneLinkable other : Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(level, receiver)) {
            if (other != receiver && other.isAlive() && RedstoneLinkNetworkHandler.withinRange(receiver, other)) {
                power = Math.max(power, other.getTransmittedStrength());
            }
        }

        receiver.callback().accept(Math.min(power, 15));
    }

    public void unregister(Level level) {
        Level from = this.registeredIn != null ? this.registeredIn : level;
        if (this.firstReceiver != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(from, this.firstReceiver);
            this.firstReceiver = null;
        }

        if (this.secondReceiver != null) {
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(from, this.secondReceiver);
            this.secondReceiver = null;
        }

        if (this.registeredIn != null) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[linkreg] unregister {} from {} id={}",
                    new Object[]{this.owner.getBlockPos().toShortString(), this.registeredIn.dimension().location(), System.identityHashCode(this.owner)}
                );
            this.registeredIn = null;
        }
    }

    public void setFirstFrequency(ItemStack a, ItemStack b) {
        this.firstFreqA = a.copy();
        this.firstFreqB = b.copy();
    }

    public void setSecondFrequency(ItemStack a, ItemStack b) {
        this.secondFreqA = a.copy();
        this.secondFreqB = b.copy();
    }

    public ItemStack getFirstFreqA() {
        return this.firstFreqA;
    }

    public ItemStack getFirstFreqB() {
        return this.firstFreqB;
    }

    public ItemStack getSecondFreqA() {
        return this.secondFreqA;
    }

    public ItemStack getSecondFreqB() {
        return this.secondFreqB;
    }

    private record Receiver(BlockPos pos, Couple<Frequency> key, BlockEntity be, IntConsumer callback) implements IRedstoneLinkable {
        public int getTransmittedStrength() {
            return 0;
        }

        public boolean isListening() {
            return true;
        }

        public boolean isAlive() {
            return !this.be.isRemoved();
        }

        public Couple<Frequency> getNetworkKey() {
            return this.key;
        }

        public BlockPos getLocation() {
            return this.pos;
        }

        public void setReceivedStrength(int strength) {
            this.callback.accept(strength);
        }
    }
}
