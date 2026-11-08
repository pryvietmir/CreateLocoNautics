package com.lycoris.loconautics.content.buffer;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;

public final class BufferContactManager {
    private static final long ENGAGED_MILLIS = 500L;
    private static final Map<SubLevelContacts.Pair, Long> ENGAGED = new ConcurrentHashMap<>();

    private BufferContactManager() {
    }

    public static boolean engaged(UUID a, UUID b) {
        Long seen = ENGAGED.get(SubLevelContacts.Pair.of(a, b));
        return seen != null && System.currentTimeMillis() - seen < 500L;
    }

    public static void watch(ServerSubLevelContainer container) {
        container.addObserver(new BufferContactManager.Watcher());
    }

    private record Head(BufferBlockEntity be, ServerSubLevel sub, Vector3d tip, Vector3d outward) {
    }

    private static final class Watcher implements SubLevelObserver {
        private int ticks;

        @Override
        public void tick(SubLevelContainer subLevels) {
            if (subLevels instanceof ServerSubLevelContainer container) {
                List<BufferContactManager.Head> heads = collect(container);

                for (BufferContactManager.Head head : heads) {
                    head.be().syncCompression();
                }

                if (this.ticks++ % 2 == 0) {
                    for (BufferContactManager.Head head : heads) {
                        pair(head, heads);
                    }
                }
            }
        }

        private static List<BufferContactManager.Head> collect(ServerSubLevelContainer container) {
            List<BufferContactManager.Head> heads = new ArrayList<>();

            for (ServerSubLevel sub : container.getAllSubLevels()) {
                if (!sub.isRemoved()) {
                    for (BufferBlockEntity be : SubLevelBlocks.findAll(sub, BufferBlockEntity.class)) {
                        heads.add(new BufferContactManager.Head(be, sub, BufferBlockEntity.worldTip(sub, be), BufferBlockEntity.worldOutward(sub, be)));
                    }
                }
            }

            return heads;
        }

        private static void pair(BufferContactManager.Head own, List<BufferContactManager.Head> heads) {
            BufferContactManager.Head nearest = null;
            double nearestGap = Double.MAX_VALUE;

            for (BufferContactManager.Head other : heads) {
                if (other.sub() != own.sub() && !(own.outward().dot(other.outward()) > -0.7)) {
                    Vector3d toOther = other.tip().sub(own.tip(), new Vector3d());
                    double along = toOther.dot(own.outward());
                    double lateral = toOther.fma(-along, own.outward()).length();
                    if (!(lateral > 0.6) && !(along > 1.5) && !(along < -1.0) && !(along >= nearestGap)) {
                        nearest = other;
                        nearestGap = along;
                    }
                }
            }

            if (nearest == null) {
                own.be().setContact(null, null);
            } else {
                own.be().setContact(nearest.sub().getUniqueId(), nearest.be().getBlockPos());
                BufferContactManager.ENGAGED.put(SubLevelContacts.Pair.of(own.sub().getUniqueId(), nearest.sub().getUniqueId()), System.currentTimeMillis());
            }
        }
    }
}
