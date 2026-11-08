package com.lycoris.loconautics.common.grab;

import com.lycoris.loconautics.trainphysics.TrainConsist;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public final class GrabConstraint {
    private static final float ROTATE_ANGULAR_STIFFNESS = 10000.0F;
    private static final float ROTATE_ANGULAR_DAMPING = 850.0F;
    private final UUID playerId;
    private float scrollDistance;
    private boolean rotating;
    private final Quaterniond targetOrientation = new Quaterniond();
    @Nullable
    private PhysicsConstraintHandle handle;

    public GrabConstraint(UUID playerId, float scrollDistance) {
        this.playerId = playerId;
        this.scrollDistance = scrollDistance;
    }

    public void setScrollDistance(float range) {
        this.scrollDistance = Mth.clamp(range, 1.0F, 5.0F);
    }

    public void setRotation(boolean rotating, Quaterniondc orientation) {
        this.rotating = rotating;
        if (orientation != null) {
            this.targetOrientation.set(orientation);
        }
    }

    public Vector3d goalFor(Player player) {
        return JOMLConversion.toJOML(player.getEyePosition().add(player.getLookAngle().scale(Math.max(2.0, this.scrollDistance))));
    }

    public void physicsTick(PhysicsPipelineBody body, ServerLevel level, Vector3d grabCenter, Vector3d grabAnchor, float rotateMaxForce) {
        this.removeJoint();
        Player player = level.getPlayerByUUID(this.playerId);
        if (player != null) {
            if (player.onGround() || player.isInWater() || player.getAbilities().flying || player.onClimbable()) {
                if (!(body instanceof ServerSubLevel subLevel && TrainConsist.standingOnConsistOf(level, player, subLevel.getUniqueId()))) {
                    Vector3d goal = this.goalFor(player);
                    double validRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 2.0;
                    double distSq = Sable.HELPER.distanceSquaredWithSubLevels(level, goal, grabCenter);
                    if (!(distSq > validRange * validRange)) {
                        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                        if (container != null) {
                            SubLevelPhysicsSystem system = container.physicsSystem();
                            Quaterniond frame = this.rotating ? new Quaterniond(this.targetOrientation) : new Quaterniond();
                            this.handle = system.getPipeline().addConstraint(null, body, new FreeConstraintConfiguration(goal, grabAnchor, frame));
                            if (this.handle != null) {
                                for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
                                    this.handle.setMotor(axis, 0.0, 240.0, 30.0, true, 120.0);
                                }

                                for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
                                    if (this.rotating) {
                                        this.handle.setMotor(axis, 0.0, 10000.0, 850.0, true, rotateMaxForce);
                                    } else {
                                        this.handle.setMotor(axis, 0.0, 0.0, 4.5, true, 120.0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void removeJoint() {
        if (this.handle != null) {
            this.handle.remove();
            this.handle = null;
        }
    }
}
