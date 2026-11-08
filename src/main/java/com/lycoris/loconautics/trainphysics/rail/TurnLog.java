package com.lycoris.loconautics.trainphysics.rail;

import com.simibubi.create.content.trains.graph.TrackNode;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class TurnLog {
    private final Map<Long, TrackNode> crumbs = new HashMap<>();
    private long head;

    public long record(TrackNode node) {
        this.crumbs.put(this.head, node);
        return this.head++;
    }

    public TrackNode at(long seq) {
        return this.crumbs.get(seq);
    }

    public long head() {
        return this.head;
    }

    public void pruneBelow(long seq) {
        this.crumbs.keySet().removeIf(key -> key < seq);
    }

    public void remap(UnaryOperator<TrackNode> live) {
        this.crumbs.replaceAll((seq, node) -> live.apply(node));
    }

    public void clear() {
        this.crumbs.clear();
        this.head = 0L;
    }
}
