package com.lycoris.loconautics.common.turntable;

import com.simibubi.create.content.trains.signal.SignalBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public final class TurntableSignalHold {
    private static final int CALM_TICKS = 20;
    private static final String KEY = "BoundSignals";
    private final List<BlockPos> bound = new ArrayList<>();
    private int calmTicks;

    public boolean isEmpty() {
        return this.bound.isEmpty();
    }

    public List<BlockPos> positions() {
        return List.copyOf(this.bound);
    }

    public boolean bind(BlockPos signal) {
        if (this.bound.contains(signal)) {
            return false;
        }

        this.bound.add(signal.immutable());
        return true;
    }

    public void copyFrom(TurntableSignalHold other) {
        this.bound.clear();
        this.bound.addAll(other.bound);
        this.calmTicks = other.calmTicks;
    }

    public boolean tick(Level level, boolean busy) {
        if (this.bound.isEmpty()) {
            return false;
        }

        this.calmTicks = busy ? 0 : Math.min(this.calmTicks + 1, 20);
        boolean red = this.calmTicks < 20;
        boolean dropped = this.bound.removeIf(posx -> {
            boolean gone = level.isLoaded(posx) && !(level.getBlockEntity(posx) instanceof SignalBlockEntity);
            if (gone) {
                TurntableSignals.hold(level, posx, false);
            }

            return gone;
        });

        for (BlockPos pos : this.bound) {
            TurntableSignals.hold(level, pos, red);
        }

        return dropped;
    }

    public void release(Level level) {
        if (level != null && !level.isClientSide) {
            for (BlockPos pos : this.bound) {
                TurntableSignals.hold(level, pos, false);
            }
        }
    }

    public void write(CompoundTag tag) {
        if (!this.bound.isEmpty()) {
            tag.putLongArray("BoundSignals", this.bound.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }

    public void read(CompoundTag tag) {
        this.bound.clear();

        for (long packed : tag.getLongArray("BoundSignals")) {
            this.bound.add(BlockPos.of(packed));
        }
    }
}
