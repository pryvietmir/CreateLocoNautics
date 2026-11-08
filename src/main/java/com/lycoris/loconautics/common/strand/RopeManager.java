package com.lycoris.loconautics.common.strand;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public abstract class RopeManager<R extends AttachedRopePhysicsObject> {
    private static final List<Function<Level, RopeManager<?>>> REGISTERED = new ArrayList<>();
    private final Map<UUID, R> ropes = new HashMap<>();

    protected RopeManager() {
    }

    public static void register(Function<Level, RopeManager<?>> manager) {
        REGISTERED.add(manager);
    }

    @Nullable
    public static AttachedRopePhysicsObject find(Level level, UUID id) {
        for (Function<Level, RopeManager<?>> manager : REGISTERED) {
            RopeManager<?> ropes = manager.apply(level);
            AttachedRopePhysicsObject rope = ropes == null ? null : ropes.get(id);
            if (rope != null) {
                return rope;
            }
        }

        return null;
    }

    public void add(R rope) {
        this.ropes.put(rope.getUUID(), rope);
    }

    @Nullable
    public R get(UUID id) {
        return id == null ? null : this.ropes.get(id);
    }

    public Collection<R> all() {
        return Collections.unmodifiableCollection(this.ropes.values());
    }

    public void remove(SubLevelPhysicsSystem system, UUID id) {
        R rope = this.ropes.remove(id);
        if (rope != null) {
            if (system == null) {
                throw new IllegalStateException("null system on rope removal for " + id);
            }

            if (rope.isActive()) {
                system.removeObject(rope);
            }
        }
    }

    protected static <R extends AttachedRopePhysicsObject> void tick(SubLevelPhysicsSystem system, double timeStep, @Nullable RopeManager<R> mgr) {
        if (mgr != null) {
            ServerLevel level = system.getLevel();

            for (R rope : mgr.ropes.values()) {
                if (rope.isActive()) {
                    if (!rope.areAttachmentsLoaded(level)) {
                        system.removeObject(rope);
                    } else {
                        rope.driveKnotGrab(system, level, timeStep);
                        if (rope.isActive()) {
                            rope.prePhysicsTick(system, level, timeStep);
                            RopePullTrace.sample(rope, level);
                            rope.checkAnchors(level);
                        }
                    }
                }
            }
        }
    }
}
