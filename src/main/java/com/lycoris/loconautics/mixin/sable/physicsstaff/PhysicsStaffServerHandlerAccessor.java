package com.lycoris.loconautics.mixin.sable.physicsstaff;

import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PhysicsStaffServerHandler.class, remap = false)
public interface PhysicsStaffServerHandlerAccessor {
    @Accessor("draggingSessions")
    Map<UUID, Object> loconautics$getDraggingSessions();

    @Accessor("locks")
    Map<UUID, Object> loconautics$getLocks();
}
