package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class TurnMemory {
    private static final long LIFETIME_MILLIS = 150000L;
    private final Map<TurnMemory.Junction, TurnMemory.Pick> picks = new HashMap<>();
    private int total = 1;
    private long planTick = Long.MIN_VALUE;
    private Vec3 intent;
    private List<Couple<TrackNode>> route;

    public boolean planStale(long tick) {
        return this.planTick != tick;
    }

    public void setPlan(long tick, int total, @Nullable Vec3 intent, @Nullable List<Couple<TrackNode>> route) {
        this.planTick = tick;
        this.total = Math.max(1, total);
        this.intent = intent;
        this.route = route;
    }

    @Nullable
    public Vec3 intent() {
        return this.intent;
    }

    @Nullable
    public List<Couple<TrackNode>> route() {
        return this.route;
    }

    public void unwind(TrackEdge leaving, boolean forward, TurnMemory.BogeyId id) {
        if (!forward && leaving != null && !this.picks.isEmpty()) {
            TrackNodeLocation at = leaving.node1.getLocation();
            TrackNodeLocation exit = leaving.node2.getLocation();

            for (Entry<TurnMemory.Junction, TurnMemory.Pick> entry : this.picks.entrySet()) {
                if (entry.getKey().at().equals(at) && entry.getValue().exit.equals(exit)) {
                    entry.getValue().crossed.remove(id);
                }
            }
        }
    }

    public Entry<TrackNode, TrackEdge> settle(
        TrackEdge leaving,
        boolean forward,
        List<Entry<TrackNode, TrackEdge>> options,
        Entry<TrackNode, TrackEdge> own,
        @Nullable Entry<TrackNode, TrackEdge> planned,
        TurnMemory.BogeyId id
    ) {
        TurnMemory.Junction key = new TurnMemory.Junction(leaving.node1.getLocation(), leaving.node2.getLocation(), forward);
        long now = System.currentTimeMillis();
        TurnMemory.Pick pick = this.picks.get(key);
        if (pick != null && now - pick.stamp > 150000L) {
            this.picks.remove(key);
            pick = null;
        }

        if (pick != null && !pick.crossed.contains(id)) {
            for (Entry<TrackNode, TrackEdge> option : options) {
                if (option.getKey().getLocation().equals(pick.exit)) {
                    pick.crossed.add(id);
                    pick.stamp = now;
                    this.finish(key, pick);
                    return option;
                }
            }
        }

        Entry<TrackNode, TrackEdge> choice = planned != null ? planned : own;
        TurnMemory.Pick fresh = new TurnMemory.Pick(choice.getKey().getLocation(), now);
        fresh.crossed.add(id);
        this.picks.put(key, fresh);
        this.finish(key, fresh);
        return choice;
    }

    private void finish(TurnMemory.Junction key, TurnMemory.Pick pick) {
        if (pick.crossed.size() >= this.total) {
            this.picks.remove(key);
        }
    }

    public record BogeyId(UUID car, @Nullable BlockPos bogey) {
    }

    private record Junction(TrackNodeLocation from, TrackNodeLocation at, boolean forward) {
    }

    private static final class Pick {
        final TrackNodeLocation exit;
        final Set<TurnMemory.BogeyId> crossed = new HashSet<>();
        long stamp;

        Pick(TrackNodeLocation exit, long stamp) {
            this.exit = exit;
            this.stamp = stamp;
        }
    }
}
