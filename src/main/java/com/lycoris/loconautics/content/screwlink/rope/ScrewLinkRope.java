package com.lycoris.loconautics.content.screwlink.rope;

import com.lycoris.loconautics.common.strand.AttachedRopePhysicsObject;
import com.lycoris.loconautics.common.strand.RopeGeometry;
import com.lycoris.loconautics.content.screwlink.ScrewLinkHook;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScrewLinkRope extends AttachedRopePhysicsObject {
    public static final double COLLISION_RADIUS = 0.125;
    private static final Vector3dc HOOK_ANCHOR = new Vector3d();
    private final double restLength;
    @Nullable
    private ScrewLinkHook hook;
    private boolean attachmentsApplied;

    private ScrewLinkRope(UUID id, Collection<Vector3d> points, double restLength) {
        super(id, points, 0.125);
        this.restLength = restLength;
    }

    public static ScrewLinkRope build(UUID id, double restLength, @Nullable UUID subA, BlockPos localA, Vector3dc worldA, Vector3dc worldB) {
        ScrewLinkRope rope = new ScrewLinkRope(id, RopeGeometry.tautPolyline(worldA, worldB, restLength), restLength);
        rope.putAttachment(AttachmentPoint.START, subA, localA);
        return rope;
    }

    public static ScrewLinkRope restore(UUID id, double restLength, @Nullable UUID subA, BlockPos localA, List<Vector3d> points) {
        List<Vector3d> copy = new ArrayList<>(points.size());

        for (Vector3d p : points) {
            copy.add(new Vector3d(p));
        }

        ScrewLinkRope rope = new ScrewLinkRope(id, copy, restLength);
        rope.putAttachment(AttachmentPoint.START, subA, localA);
        return rope;
    }

    public double getRestLength() {
        return this.restLength;
    }

    @Nullable
    public ScrewLinkHook getHook() {
        return this.hook;
    }

    public void setHook(@Nullable ScrewLinkHook hook) {
        this.hook = hook;
    }

    public void coupleTo(@Nullable UUID subB, BlockPos localB) {
        this.putAttachment(AttachmentPoint.END, subB, localB);
        this.attachmentsApplied = false;
    }

    public void releaseHook() {
        if (this.hook != null) {
            List<Vector3d> points = this.getPoints();
            this.setAttachment(AttachmentPoint.END, (Vector3dc)points.get(points.size() - 1), null);
            this.hook = null;
        }
    }

    public void decouple() {
        this.attachments.remove(AttachmentPoint.END);
        this.removeConstraint();
        this.attachmentsApplied = false;
    }

    @Nullable
    @Override
    protected Vector3d pinnedEnd(ServerLevel level, AttachmentPoint point) {
        return point == AttachmentPoint.END && this.hook != null ? new Vector3d(this.hook.getPose().position()) : super.pinnedEnd(level, point);
    }

    @Override
    public void prePhysicsTick(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (!this.attachmentsApplied) {
            this.attachmentsApplied = this.applyAttachments(level);
        }

        if (this.hook == null) {
            if (this.constraintNeedsRebuild(level)) {
                this.reattachConstraint(system, level);
            }
        } else if (!this.hook.isActive()) {
            this.releaseHook();
        } else {
            this.hook.updatePose();
            this.attachBody(level, AttachmentPoint.END, this.hook, HOOK_ANCHOR);
            this.hook.driveGrabs(this.hook, level);
        }
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        this.attachmentsApplied = this.applyAttachments(physicsSystem.getLevel());
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        this.hook = null;
        super.onUnloaded(holdingChunkMap, chunkPos);
        this.attachmentsApplied = false;
    }
}
