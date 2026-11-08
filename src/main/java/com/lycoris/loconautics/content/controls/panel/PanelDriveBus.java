package com.lycoris.loconautics.content.controls.panel;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class PanelDriveBus {
    private static final Map<PanelDriveBus.Target, PanelDriveBus.Bid> BIDS = new HashMap<>();

    public static float report(Level level, BlockPos pos, float value) {
        PanelDriveBus.Target target = new PanelDriveBus.Target(level.dimension(), pos);
        long now = level.getGameTime();
        PanelDriveBus.Bid bid = BIDS.computeIfAbsent(target, key -> new PanelDriveBus.Bid());
        if (bid.tick != now) {
            bid.previous = bid.current;
            bid.current = 0.0F;
            bid.tick = now;
        }

        bid.current = Math.max(bid.current, value);
        return Math.max(bid.current, bid.previous);
    }

    public static void release(Level level, BlockPos pos) {
        BIDS.remove(new PanelDriveBus.Target(level.dimension(), pos));
    }

    public static void clear() {
        BIDS.clear();
    }

    private PanelDriveBus() {
    }

    private static final class Bid {
        long tick;
        float current;
        float previous;
    }

    private record Target(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
