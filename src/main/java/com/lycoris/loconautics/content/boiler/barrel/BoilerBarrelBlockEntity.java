package com.lycoris.loconautics.content.boiler.barrel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BoilerBarrelBlockEntity extends BlockEntity {
    private int storedWater;

    public BoilerBarrelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void storeWater(int millibuckets) {
        if (millibuckets > 0) {
            this.storedWater += millibuckets;
            this.setChanged();
        }
    }

    public int takeWater(int millibuckets) {
        int taken = Math.min(this.storedWater, Math.max(0, millibuckets));
        if (taken > 0) {
            this.storedWater -= taken;
            this.setChanged();
        }

        return taken;
    }

    protected void saveAdditional(CompoundTag tag, Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.storedWater > 0) {
            tag.putInt("StoredWater", this.storedWater);
        }
    }

    protected void loadAdditional(CompoundTag tag, Provider registries) {
        super.loadAdditional(tag, registries);
        this.storedWater = tag.getInt("StoredWater");
    }
}
