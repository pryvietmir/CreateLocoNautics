package com.lycoris.loconautics.content.screwlink.rope;

import com.lycoris.loconautics.common.strand.RopeManager;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.createmod.catnip.data.WorldAttached;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ScrewLinkRopeManager extends RopeManager<ScrewLinkRope> {
    private static final WorldAttached<ScrewLinkRopeManager> ATTACHED = new WorldAttached<>(l -> l instanceof ServerLevel ? new ScrewLinkRopeManager() : null);

    private ScrewLinkRopeManager() {
    }

    @Nullable
    public static ScrewLinkRopeManager get(Level level) {
        return (ScrewLinkRopeManager)ATTACHED.get(level);
    }

    @Override
    public void remove(SubLevelPhysicsSystem system, UUID id) {
        ScrewLinkRope rope = this.get(id);
        if (rope != null && rope.getHook() != null && rope.getHook().isActive()) {
            system.removeObject(rope.getHook());
        }

        super.remove(system, id);
    }

    public static void onPhysicsTick(SubLevelPhysicsSystem system, double timeStep) {
        tick(system, timeStep, get(system.getLevel()));
    }
}
