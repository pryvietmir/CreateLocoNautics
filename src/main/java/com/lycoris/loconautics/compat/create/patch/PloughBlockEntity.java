package com.lycoris.loconautics.compat.create.patch;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PloughBlockEntity extends SmartBlockEntity {
    ActorLockScroll lockScroll;

    public PloughBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.lockScroll = new ActorLockScroll(this);
        behaviours.add(this.lockScroll);
    }

    public boolean isLocked() {
        return this.level == null ? false : this.lockScroll.getMode().isLocked(this.level, this.worldPosition);
    }
}
