package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public abstract class SafetyValveBlock extends Block implements IWrenchable, IBE<SafetyValveBlockEntity> {
    public SafetyValveBlock(Properties properties) {
        super(properties);
    }

    public Class<SafetyValveBlockEntity> getBlockEntityClass() {
        return SafetyValveBlockEntity.class;
    }
}
