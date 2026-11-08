package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface RedstoneLinkedPart extends PanelPart {
    int MAX_SIGNAL = 15;

    @Nullable
    BlockPos link();

    int signal(Level var1);

    @Override
    default float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
        return panel.outputSignal(slot, this.signal(panel.getLevel())) / 15.0F;
    }

    default boolean connected(PanelBlockEntity panel) {
        return this.link() != null || panel.hasLinkFrequency(this);
    }

    default int engineSignal(int signal) {
        return signal;
    }

    default void driveLink(PanelBlockEntity panel, PanelSlot slot, Level level, BlockPos link, PanelPart unlinked, int signal) {
        SmallEngineBlockEntity engine = SmallEngineBlockEntity.resolve(level, link);
        if (engine != null) {
            engine.driveFromPanel(panel.outputSignal(slot, this.engineSignal(signal)));
        } else if (!PanelLink.stillThere(level, link)) {
            PanelDriveBus.release(level, link);
            panel.replacePart(slot, unlinked);
        } else {
            float driven = PanelDriveBus.report(level, link, panel.outputSignal(slot, signal));
            if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) {
                receiver.setSignal(Math.round(driven));
            }

            PanelLink.set(level, link, true);
        }
    }

    @Override
    default void onRemoved(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        BlockPos link = this.link();
        if (link != null && level != null) {
            PanelDriveBus.release(level, link);
            if (level.getBlockEntity(link) instanceof RedstoneLinkBlockEntity receiver) {
                receiver.setSignal(0);
            }

            PanelLink.set(level, link, false);
        }
    }
}
