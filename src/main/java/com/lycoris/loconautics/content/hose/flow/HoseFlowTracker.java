package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.network.packets.attachment.HoseFlowPacket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "loconautics")
public final class HoseFlowTracker {
    private static final long LINGER_TICKS = 4L;
    private static final long RESEND_TICKS = 40L;
    private static final Map<UUID, HoseFlowTracker.Mark> MARKS = new ConcurrentHashMap<>();
    private static HoseFlowPacket lastSent = new HoseFlowPacket(List.of(), List.of());
    private static long lastSentTick;

    private HoseFlowTracker() {
    }

    public static void mark(UUID strandId, boolean forward, long gameTime) {
        MARKS.put(strandId, new HoseFlowTracker.Mark(gameTime, forward));
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        ServerLevel level = event.getServer().overworld();
        long now = level.getGameTime();
        MARKS.entrySet().removeIf(ex -> now - ((HoseFlowTracker.Mark)ex.getValue()).tick() > 4L);
        List<UUID> forward = new ArrayList<>();
        List<UUID> backward = new ArrayList<>();

        for (Entry<UUID, HoseFlowTracker.Mark> e : MARKS.entrySet()) {
            (e.getValue().forward() ? forward : backward).add(e.getKey());
        }

        boolean changed = !sameIds(forward, lastSent.forward()) || !sameIds(backward, lastSent.backward());
        if (changed || now - lastSentTick >= 40L) {
            lastSent = new HoseFlowPacket(forward, backward);
            lastSentTick = now;
            PacketDistributor.sendToPlayersInDimension(level, lastSent, new CustomPacketPayload[0]);
        }
    }

    private static boolean sameIds(List<UUID> a, List<UUID> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }

    private record Mark(long tick, boolean forward) {
    }
}
