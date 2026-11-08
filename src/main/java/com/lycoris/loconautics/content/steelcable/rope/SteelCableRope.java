package com.lycoris.loconautics.content.steelcable.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.common.strand.StrandLength;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.steelcable.SteelCableType;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SteelCableRope extends AttachedRopePhysicsObject {
    public static final double COLLISION_RADIUS = 0.125;
    public static final double MIN_EXTENSION = 0.1;
    private final SteelCableType type;
    private final double restLength;
    private boolean attachmentsApplied;
    private double extensionGoal = 1.0;
    private double lastExtensionGoal = 1.0;
    private double lastFirstSegmentExtension = 1.0;
    private int lastPointCount;

    public SteelCableRope(UUID id, Collection<Vector3d> points, SteelCableType type, double restLength) {
        super(id, points, 0.125);
        this.type = type;
        this.restLength = restLength;
    }

    public static SteelCableRope build(
        UUID id,
        SteelCableType type,
        double restLength,
        @Nullable UUID subA,
        BlockPos localA,
        Vector3dc worldA,
        @Nullable UUID subB,
        BlockPos localB,
        Vector3dc worldB
    ) {
        double length = Math.max(restLength, 0.1);
        int whole = (int)Math.floor(length);
        double first = length - whole;
        if (first < 0.1 && whole > 0) {
            whole--;
            first++;
        }

        List<Vector3d> points = new ArrayList<>();
        points.add(new Vector3d(worldA));

        for (int k = 0; k <= whole; k++) {
            points.add(worldA.lerp(worldB, (first + k) / length, new Vector3d()));
        }

        SteelCableRope rope = new SteelCableRope(id, points, type, restLength);
        rope.putAttachment(AttachmentPoint.START, subA, localA);
        rope.putAttachment(AttachmentPoint.END, subB, localB);
        rope.extensionGoal = first;
        rope.lastExtensionGoal = first;
        return rope;
    }

    public static SteelCableRope restore(
        UUID id,
        SteelCableType type,
        double restLength,
        @Nullable UUID subA,
        BlockPos localA,
        @Nullable UUID subB,
        BlockPos localB,
        List<Vector3d> points,
        double extensionGoal
    ) {
        List<Vector3d> copy = new ArrayList<>(points.size());

        for (Vector3d p : points) {
            copy.add(new Vector3d(p));
        }

        SteelCableRope rope = new SteelCableRope(id, copy, type, restLength);
        rope.extensionGoal = extensionGoal;
        rope.lastExtensionGoal = extensionGoal;
        rope.putAttachment(AttachmentPoint.START, subA, localA);
        rope.putAttachment(AttachmentPoint.END, subB, localB);
        return rope;
    }

    public StrandKind kind() {
        return this.type.kind();
    }

    public double getExtension() {
        return this.extensionGoal;
    }

    public boolean tickSnap() {
        return this.tickOverstretch(StrandLength.rest(this.extensionGoal, this.getPoints().size()), this.type.snapStretchPercent());
    }

    public boolean isTaut() {
        double desired = StrandLength.rest(this.extensionGoal, this.getPoints().size());
        return this.getCurrentExtension() > desired * (1.0 + this.type.maxStretchAllowed() / 100.0);
    }

    public void updateFirstSegmentExtension(double goal) {
        this.lastExtensionGoal = this.extensionGoal;
        this.extensionGoal = goal;
    }

    @Override
    public void addPoint(Vector3dc position) {
        super.addPoint(position);
        this.attachmentsApplied = false;
    }

    @Override
    public void removeFirstPoint() {
        super.removeFirstPoint();
        this.attachmentsApplied = false;
    }

    @Override
    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (!this.attachmentsApplied) {
            this.attachmentsApplied = this.applyAttachments(level);
        }

        if (this.type.carriesFluid() && this.anyAttachmentGrabbed(level, be -> be instanceof HoseNozzleBlockEntity nozzle && nozzle.isGrabbed())) {
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
    }

    @Override
    protected boolean requireBothBodiesResolved() {
        return true;
    }

    @Override
    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        dampAllAxes(handle);
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        this.setFirstSegmentLength(this.extensionGoal);
        this.attachmentsApplied = this.applyAttachments(physicsSystem.getLevel());
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        super.onUnloaded(holdingChunkMap, chunkPos);
        this.attachmentsApplied = false;
    }
}
