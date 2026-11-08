package com.lycoris.loconautics.content.hose.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.content.hose.HoseType;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class HoseRope extends AttachedRopePhysicsObject {
    public static final double COLLISION_RADIUS = 0.125;
    private static final double MIN_EXTENSION = 1.0;
    private final HoseType type;
    private double extensionGoal = 1.0;
    private double lastExtensionGoal = 1.0;
    private double lastFirstSegmentExtension = 1.0;
    private int lastPointCount;
    private boolean attachmentsDirty;

    public HoseRope(UUID id, Collection<Vector3d> points, HoseType type) {
        super(id, points, 0.125);
        this.type = type;
    }

    public static HoseRope build(
        UUID id,
        HoseType type,
        @Nullable UUID winchSubLevelId,
        BlockPos winchPos,
        Vector3dc winchWorld,
        @Nullable UUID nozzleSubLevelId,
        BlockPos nozzleLocalPos,
        Vector3dc nozzleWorld,
        double length
    ) {
        double dist = Math.max(0.5, Math.min(length, type.maxLength() + 1.0));
        double straight = new Vector3d(winchWorld).sub(nozzleWorld).length();
        double span = Math.max(straight, 1.0);
        int whole = (int)Math.floor(span);
        double first = span - whole;
        if (first < 1.0 && whole > 0) {
            whole--;
            first++;
        }

        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(winchWorld));

        for (int k = 0; k <= whole; k++) {
            points.add(winchWorld.lerp(nozzleWorld, (first + k) / span, new Vector3d()));
        }

        HoseRope rope = new HoseRope(id, points, type);
        rope.putAttachment(AttachmentPoint.START, winchSubLevelId, winchPos);
        rope.putAttachment(AttachmentPoint.END, nozzleSubLevelId, nozzleLocalPos);

        double extension;
        for (extension = first + Math.max(0.0, dist - straight); extension > 1.0; extension--) {
            rope.addPoint(winchWorld);
        }

        rope.extensionGoal = extension;
        rope.lastExtensionGoal = extension;
        return rope;
    }

    public static HoseRope restore(
        UUID id,
        HoseType type,
        @Nullable UUID winchSubLevelId,
        BlockPos winchPos,
        @Nullable UUID nozzleSubLevelId,
        BlockPos nozzleLocalPos,
        List<Vector3d> points,
        double extensionGoal
    ) {
        List<Vector3d> copy = new ArrayList<>(points.size());

        for (Vector3d p : points) {
            copy.add(new Vector3d(p));
        }

        HoseRope rope = new HoseRope(id, copy, type);
        rope.extensionGoal = extensionGoal;
        rope.lastExtensionGoal = extensionGoal;
        rope.putAttachment(AttachmentPoint.START, winchSubLevelId, winchPos);
        rope.putAttachment(AttachmentPoint.END, nozzleSubLevelId, nozzleLocalPos);
        return rope;
    }

    public double getExtension() {
        return this.extensionGoal;
    }

    public double getStretchRatio() {
        double desired = this.extensionGoal + (this.getPoints().size() - 2) * 1.0;
        return desired <= 1.0E-4 ? 1.0 : this.getCurrentExtension() / desired;
    }

    public boolean tickSnap() {
        return this.tickOverstretch(this.extensionGoal + (this.getPoints().size() - 2) * 1.0, this.type.snapStretchPercent());
    }

    public void reel(double movementSpeed, Vector3dc winchWorld) {
        double desiredExtension = this.extensionGoal + (this.getPoints().size() - 2) * 1.0;
        double currentExtension = this.getCurrentExtension();
        boolean stretched = currentExtension > desiredExtension * (1.0 + this.type.maxRopeStretchAllowed() / 100.0);
        if (stretched) {
            movementSpeed = Math.max(0.0, movementSpeed);
        }

        if (currentExtension > this.type.maxLength()) {
            movementSpeed = Math.min(0.0, movementSpeed);
        }

        double extension = this.extensionGoal + movementSpeed;
        if (extension < 1.0 && this.getPoints().size() == 2) {
            extension = 1.0;
        } else {
            while (extension < 0.0) {
                this.removeFirstPoint();
                if (++extension < 1.0 && this.getPoints().size() == 2) {
                    extension = 1.0;
                    break;
                }
            }

            while (extension > 1.0) {
                this.addPoint(winchWorld);
                extension--;
            }

            if (extension < 1.0 && this.getPoints().size() <= 2) {
                extension = 1.0;
            }
        }

        this.lastExtensionGoal = this.extensionGoal;
        this.extensionGoal = extension;
    }

    @Override
    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (HoseRopeTrace.jointDisabled()
            || this.isAttachmentGrabbed(level, AttachmentPoint.END, be -> be instanceof HoseNozzleBlockEntity nozzle && nozzle.isGrabbed())) {
            this.removeConstraint();
        } else if (this.constraintNeedsRebuild(level)) {
            this.reattachConstraint(system, level);
        }

        if (this.getPoints().size() != this.lastPointCount) {
            this.lastExtensionGoal = this.extensionGoal;
            this.lastPointCount = this.getPoints().size();
        }

        double extension = Mth.lerp(system.getPartialPhysicsTick(), this.lastExtensionGoal, this.extensionGoal);
        if (!Mth.equal(extension, this.lastFirstSegmentExtension)) {
            this.setFirstSegmentLength(extension);
            this.lastFirstSegmentExtension = extension;
        }

        if (this.attachmentsDirty) {
            this.applyAttachments(level);
            this.attachmentsDirty = false;
        }

        HoseRopeTrace.sample(this, level);
    }

    @Override
    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        dampAllAxes(handle);
    }

    public boolean hasJoint() {
        return this.constraint != null && this.constraint.isValid();
    }

    public void readJointImpulses(Vector3d linear, Vector3d angular) {
        if (this.hasJoint()) {
            this.constraint.getJointImpulses(linear, angular);
        }
    }

    public double getFirstSegmentRest() {
        return this.lastFirstSegmentExtension;
    }

    public double getDesiredExtension() {
        return this.extensionGoal + (this.getPoints().size() - 2) * 1.0;
    }

    @Override
    public void removeFirstPoint() {
        super.removeFirstPoint();
        this.attachmentsDirty = true;
    }

    @Override
    public void addPoint(Vector3dc position) {
        super.addPoint(position);
        this.attachmentsDirty = true;
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        this.setFirstSegmentLength(this.extensionGoal);
        this.applyAttachments(physicsSystem.getLevel());
    }
}
