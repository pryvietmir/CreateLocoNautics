package com.lycoris.loconautics.common.copycat;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CopycatMaterialStore {
    private final BlockEntity owner;
    private BlockState material = AllBlocks.COPYCAT_BASE.getDefaultState();
    private ItemStack consumed = ItemStack.EMPTY;

    public CopycatMaterialStore(BlockEntity owner) {
        this.owner = owner;
    }

    public BlockState material() {
        return this.material;
    }

    public boolean hasCustomMaterial() {
        return !AllBlocks.COPYCAT_BASE.has(this.material);
    }

    public ItemStack consumed() {
        return this.consumed;
    }

    public void setMaterial(BlockState state) {
        this.material = state;
        this.push();
    }

    public void setConsumed(ItemStack stack) {
        this.consumed = stack.copyWithCount(1);
        this.push();
    }

    public void reset() {
        this.material = AllBlocks.COPYCAT_BASE.getDefaultState();
        this.consumed = ItemStack.EMPTY;
        this.push();
    }

    public boolean cycleMaterial() {
        if (this.material.hasProperty(TrapDoorBlock.HALF) && this.material.getOptionalValue(TrapDoorBlock.OPEN).orElse(false)) {
            this.setMaterial((BlockState)this.material.cycle(TrapDoorBlock.HALF));
        } else if (this.material.hasProperty(BlockStateProperties.FACING)) {
            this.setMaterial((BlockState)this.material.cycle(BlockStateProperties.FACING));
        } else if (this.material.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            this.setMaterial(
                (BlockState)this.material
                    .setValue(
                        BlockStateProperties.HORIZONTAL_FACING, ((Direction)this.material.getValue(BlockStateProperties.HORIZONTAL_FACING)).getClockWise()
                    )
            );
        } else if (this.material.hasProperty(BlockStateProperties.AXIS)) {
            this.setMaterial((BlockState)this.material.cycle(BlockStateProperties.AXIS));
        } else if (this.material.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            this.setMaterial((BlockState)this.material.cycle(BlockStateProperties.HORIZONTAL_AXIS));
        } else {
            if (!this.material.hasProperty(BlockStateProperties.LIT)) {
                return false;
            }

            this.setMaterial((BlockState)this.material.cycle(BlockStateProperties.LIT));
        }

        return true;
    }

    private void push() {
        this.owner.setChanged();
        if (this.owner instanceof SmartBlockEntity smart) {
            smart.sendData();
        }

        Level level = this.owner.getLevel();
        if (level != null) {
            BlockState own = this.owner.getBlockState();
            level.sendBlockUpdated(this.owner.getBlockPos(), own, own, 3);
        }
    }

    public void write(CompoundTag tag, Provider registries) {
        tag.put("Material", NbtUtils.writeBlockState(this.material));
        if (!this.consumed.isEmpty()) {
            tag.put("Consumed", this.consumed.save(registries));
        }
    }

    public void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        BlockState previous = this.material;
        this.material = tag.contains("Material")
            ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Material"))
            : AllBlocks.COPYCAT_BASE.getDefaultState();
        this.consumed = tag.contains("Consumed") ? ItemStack.parseOptional(registries, tag.getCompound("Consumed")) : ItemStack.EMPTY;
        if (clientPacket && previous != this.material) {
            this.redraw();
        }
    }

    private void redraw() {
        this.owner.requestModelDataUpdate();
        Level level = this.owner.getLevel();
        if (level != null) {
            BlockState own = this.owner.getBlockState();
            level.sendBlockUpdated(this.owner.getBlockPos(), own, own, 16);
        }
    }
}
