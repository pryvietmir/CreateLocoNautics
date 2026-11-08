package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.LoconauticsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

public final class NozzleDockSounds {
    private static final ResourceLocation DOCK = ResourceLocation.fromNamespaceAndPath("simulated", "block.docking_connector.dock");
    private static final ResourceLocation UNDOCK = ResourceLocation.fromNamespaceAndPath("simulated", "block.docking_connector.retract");
    private static final float DOCK_BODY_VOLUME = 0.32F;
    private static final float DOCK_BODY_PITCH = 0.62F;
    private static final float DOCK_GRIT_VOLUME = 0.2F;
    private static final float DOCK_GRIT_PITCH = 0.66F;
    private static final float DOCK_RING_VOLUME = 0.07F;
    private static final float DOCK_RING_PITCH = 1.7F;
    private static final float UNDOCK_BODY_VOLUME = 0.3F;
    private static final float UNDOCK_BODY_PITCH = 0.78F;
    private static final float UNDOCK_GRIT_VOLUME = 0.18F;
    private static final float UNDOCK_GRIT_PITCH = 0.83F;
    private static final float UNDOCK_RING_VOLUME = 0.06F;
    private static final float UNDOCK_RING_PITCH = 1.55F;

    private NozzleDockSounds() {
    }

    public static void docked(Level level, BlockPos pos) {
        layer(level, pos, DOCK, 0.32F, 0.62F);
        layer(level, pos, DOCK, 0.2F, 0.66F);
        layer(level, pos, DOCK, 0.07F, 1.7F);
    }

    public static void undocked(Level level, BlockPos pos) {
        layer(level, pos, UNDOCK, 0.3F, 0.78F);
        layer(level, pos, UNDOCK, 0.18F, 0.83F);
        layer(level, pos, UNDOCK, 0.06F, 1.55F);
    }

    private static void layer(Level level, BlockPos pos, ResourceLocation id, float volume, float pitch) {
        SoundEvent event = (SoundEvent)BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        if (event == null) {
            LoconauticsConstants.LOGGER.warn("[nozzlesound] missing sound event {}", id);
        } else {
            level.playSound(null, pos, event, SoundSource.BLOCKS, volume, pitch);
        }
    }
}
