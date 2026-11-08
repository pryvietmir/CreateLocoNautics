package com.lycoris.loconautics.common.strand;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ClientStrands {
    private static final int SNAPSHOT_HISTORY_TICKS = 6;
    private static final List<ClientStrands> ALL = new ArrayList<>();
    private final Map<UUID, ClientStrands.Strand> strands = new ConcurrentHashMap<>();

    public ClientStrands() {
        ALL.add(this);
    }

    public static void tickAll(double interpolationTick) {
        for (ClientStrands feature : ALL) {
            feature.tickInterpolation(interpolationTick);
        }
    }

    public static void clearAll() {
        for (ClientStrands feature : ALL) {
            feature.clear();
        }
    }

    public void accept(UUID id, int interpolationTick, boolean stopped, List<Vector3d> points, @Nullable StrandEnd start, @Nullable StrandEnd end) {
        if (points.isEmpty()) {
            this.strands.remove(id);
        } else {
            ClientStrands.Strand strand = this.strands.computeIfAbsent(id, key -> new ClientStrands.Strand());
            strand.stopped = stopped;
            if (start != null) {
                strand.start = start;
            }

            if (end != null) {
                strand.end = end;
            }

            strand.accept(interpolationTick, points);
        }
    }

    public void tickInterpolation(double interpolationTick) {
        for (ClientStrands.Strand strand : this.strands.values()) {
            strand.tickInterpolation(interpolationTick);
        }
    }

    public Collection<ClientStrands.Strand> all() {
        return this.strands.values();
    }

    public Collection<Entry<UUID, ClientStrands.Strand>> entries() {
        return this.strands.entrySet();
    }

    public ClientStrands.Strand get(UUID id) {
        return this.strands.get(id);
    }

    public void clear() {
        this.strands.clear();
    }

    public static final class Point {
        public final Vector3d position = new Vector3d();
        public final Vector3d previousPosition = new Vector3d();
        private final List<ClientStrands.Snapshot> snapshots = new ArrayList<>();

        Point(Vector3d initial) {
            this.position.set(initial);
            this.previousPosition.set(initial);
        }

        public Vector3d renderPos(float partialTicks, Vector3d dst) {
            return dst.set(this.previousPosition).lerp(this.position, partialTicks);
        }
    }

    private record Snapshot(double interpolationTick, Vector3d position) {
    }

    public static final class Strand {
        private final List<ClientStrands.Point> points = new ArrayList<>();
        private boolean stopped;
        @Nullable
        private StrandEnd start;
        @Nullable
        private StrandEnd end;

        @Nullable
        public StrandEnd start() {
            return this.start;
        }

        @Nullable
        public StrandEnd end() {
            return this.end;
        }

        public List<ClientStrands.Point> points() {
            return this.points;
        }

        public int size() {
            return this.points.size();
        }

        private void accept(double tick, List<Vector3d> incoming) {
            if (this.points.size() != incoming.size()) {
                this.points.clear();

                for (Vector3d p : incoming) {
                    this.points.add(new ClientStrands.Point(p));
                }
            }

            for (int i = 0; i < incoming.size(); i++) {
                this.points.get(i).snapshots.add(new ClientStrands.Snapshot(tick, new Vector3d((Vector3dc)incoming.get(i))));
            }
        }

        void tickInterpolation(double gameTick) {
            for (ClientStrands.Point point : this.points) {
                List<ClientStrands.Snapshot> buffer = point.snapshots;
                point.previousPosition.set(point.position);

                while (!buffer.isEmpty() && buffer.get(0).interpolationTick() < gameTick - 6.0) {
                    buffer.remove(0);
                }

                if (!buffer.isEmpty()) {
                    int beforeIndex = -1;
                    ClientStrands.Snapshot before = null;
                    ClientStrands.Snapshot after = null;

                    for (int i = 0; i < buffer.size(); i++) {
                        ClientStrands.Snapshot snapshot = buffer.get(i);
                        if (snapshot.interpolationTick() == gameTick) {
                            point.position.set(snapshot.position());
                        } else {
                            if (!(snapshot.interpolationTick() < gameTick)) {
                                after = snapshot;
                                break;
                            }

                            beforeIndex = i;
                            before = snapshot;
                        }
                    }

                    if (before != null && after != null) {
                        double factor = (gameTick - before.interpolationTick()) / (after.interpolationTick() - before.interpolationTick());
                        before.position().lerp(after.position(), factor, point.position);
                    } else if (before != null) {
                        point.position.set(before.position());
                        if (beforeIndex - 1 >= 0 && !this.stopped) {
                            ClientStrands.Snapshot beforeBefore = buffer.get(beforeIndex - 1);
                            double lead = Mth.clamp(gameTick - before.interpolationTick(), 0.0, 1.0);
                            double fraction = lead / (before.interpolationTick() - beforeBefore.interpolationTick());
                            point.position.set(beforeBefore.position()).lerp(before.position(), 1.0 + fraction);
                        }
                    } else if (after != null) {
                        point.position.set(after.position());
                    }
                }
            }
        }
    }
}
