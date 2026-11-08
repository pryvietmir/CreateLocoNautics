package com.lycoris.loconautics.content.headstock;

import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class CopycatHeadstockBlockEntity extends SmartBlockEntity implements CopycatMaterialHolder {
    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);

    public CopycatHeadstockBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @Override
    public CopycatMaterialStore copycat() {
        return this.copycat;
    }

    public ModelData getModelData() {
        return CopycatBlockLogic.modelData(this.copycat);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        this.copycat.write(tag, registries);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.copycat.read(tag, registries, clientPacket);
    }
}
