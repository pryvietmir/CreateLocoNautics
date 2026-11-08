package com.lycoris.loconautics.content.controls.panel;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class PanelLink {
    public static final BooleanProperty SKIN = BooleanProperty.create("loconautics_panel");

    public static boolean isPanel(BlockState state) {
        return state.hasProperty(SKIN) && (Boolean)state.getValue(SKIN);
    }

    public static boolean stillThere(Level level, BlockPos pos) {
        return !level.isLoaded(pos) || level.getBlockState(pos).hasProperty(SKIN);
    }

    public static void set(Level level, BlockPos pos, boolean panel) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(SKIN) && (Boolean)state.getValue(SKIN) != panel) {
            level.setBlock(pos, (BlockState)state.setValue(SKIN, panel), 2);
            refreshFrequencyNetwork(level, pos);
        }
    }

    private static void refreshFrequencyNetwork(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof RedstoneLinkBlockEntity link) {
                LinkBehaviour behaviour = (LinkBehaviour)link.getBehaviour(LinkBehaviour.TYPE);
                if (behaviour != null) {
                    behaviour.notifySignalChange();
                }
            }
        }
    }

    private PanelLink() {
    }
}
