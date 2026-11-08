package com.lycoris.loconautics.common.grab;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerLevel;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public interface GrabTarget {
    float LINEAR_STIFFNESS = 240.0F;
    float LINEAR_DAMPING = 30.0F;
    float ANGULAR_DAMPING = 4.5F;
    float MAX_FORCE = 120.0F;
    float MIN_GRAB_DISTANCE = 1.0F;
    float MAX_GRAB_DISTANCE = 5.0F;

    void updateGrab(UUID var1, float var2, boolean var3, Quaterniondc var4);

    Map<UUID, GrabConstraint> grabbers();

    Vector3d getGrabCenter();

    default Vector3d getGrabAnchor() {
        return this.getGrabCenter();
    }

    default float rotateMaxForce() {
        return 120.0F;
    }

    default boolean isGrabbed() {
        return !this.grabbers().isEmpty();
    }

    default boolean canGrab() {
        return true;
    }

    default void stopGrab(UUID player) {
        GrabConstraint c = this.grabbers().remove(player);
        if (c != null) {
            c.removeJoint();
        }
    }

    default void clearGrabs() {
        this.grabbers().values().forEach(GrabConstraint::removeJoint);
        this.grabbers().clear();
    }

    default void driveGrabs(PhysicsPipelineBody body, ServerLevel level) {
        if (!this.grabbers().isEmpty()) {
            Vector3d grabCenter = this.getGrabCenter();
            Vector3d grabAnchor = this.getGrabAnchor();
            Iterator<Entry<UUID, GrabConstraint>> it = this.grabbers().entrySet().iterator();

            while (it.hasNext()) {
                Entry<UUID, GrabConstraint> e = it.next();
                if (level.getPlayerByUUID(e.getKey()) == null) {
                    e.getValue().removeJoint();
                    it.remove();
                } else {
                    e.getValue().physicsTick(body, level, grabCenter, grabAnchor, this.rotateMaxForce());
                }
            }
        }
    }
}
