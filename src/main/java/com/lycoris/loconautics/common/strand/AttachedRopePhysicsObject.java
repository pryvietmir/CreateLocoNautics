package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.BodyRef;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.rope.RapierRopeHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public abstract class AttachedRopePhysicsObject extends RopePhysicsObject {
    private static final int SNAP_TICKS = 5;
    protected final UUID id;
    protected final Map<AttachmentPoint, AttachedRopePhysicsObject.Attachment> attachments = new EnumMap<>(AttachmentPoint.class);
    @Nullable
    protected PhysicsConstraintHandle constraint;
    @Nullable
    private UUID constraintBodyA;
    @Nullable
    private UUID constraintBodyB;
    private final List<Vector3d> lastNetworkedPoints = new ArrayList<>();
    private final StrandKnotGrab knotGrab = new StrandKnotGrab();
    private final Map<AttachmentPoint, Vector3d> lastCenters = new EnumMap<>(AttachmentPoint.class);
    private boolean networkingStopped;
    private int overstretchTicks;
    private final Map<BlockPos, Vector3d> tracedAnchors = new HashMap<>();

    protected AttachedRopePhysicsObject(UUID id, Collection<Vector3d> points, double collisionRadius) {
        super(points, collisionRadius);
        this.id = id;
    }

    public UUID getUUID() {
        return this.id;
    }

    @Nullable
    public StrandEnd endOf(AttachmentPoint point) {
        AttachedRopePhysicsObject.Attachment att = this.attachments.get(point);
        return att == null ? null : new StrandEnd(att.subLevelId(), att.blockPos());
    }

    protected void putAttachment(AttachmentPoint point, @Nullable UUID subLevelId, BlockPos localPos) {
        this.attachments.put(point, new AttachedRopePhysicsObject.Attachment(subLevelId, localPos));
    }

    public double getCurrentExtension() {
        return StrandLength.current(this);
    }

    protected Vector3d resolveHolderPoint(@Nullable BlockEntity blockEntity, BlockPos blockPos, Vector3d blockCenter) {
        if (blockEntity instanceof StrandHolder holder) {
            Vec3 p = holder.getAttachmentPoint();
            this.traceAnchor(blockEntity, blockPos, new Vector3d(p.x, p.y, p.z), blockCenter);
            return new Vector3d(p.x, p.y, p.z);
        } else {
            this.traceAnchor(blockEntity, blockPos, blockCenter, blockCenter);
            return blockCenter;
        }
    }

    private void traceAnchor(@Nullable BlockEntity blockEntity, BlockPos blockPos, Vector3d point, Vector3d blockCenter) {
        Vector3d known = this.tracedAnchors.put(blockPos.immutable(), new Vector3d(point));
        if (known == null || !(known.distanceSquared(point) < 1.0E-6)) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[issue215] anchor rope={} {} holder={} pos={} state={} point=({}, {}, {}) offFromCenter=({}, {}, {})",
                    new Object[]{
                        this.id.toString().substring(0, 8),
                        this.getClass().getSimpleName(),
                        blockEntity == null ? "none" : blockEntity.getClass().getSimpleName(),
                        blockPos,
                        blockEntity == null ? "?" : blockEntity.getBlockState(),
                        point.x,
                        point.y,
                        point.z,
                        point.x - blockCenter.x,
                        point.y - blockCenter.y,
                        point.z - blockCenter.z
                    }
                );
        }
    }

    protected boolean requireBothBodiesResolved() {
        return false;
    }

    public StrandKnotGrab knotGrab() {
        return this.knotGrab;
    }

    @Override
    public void updatePose() {
        if (!this.knotGrab.engaged()) {
            super.updatePose();
        }
    }

    @Nullable
    protected Vector3d pinnedEnd(ServerLevel level, AttachmentPoint point) {
        AttachedRopePhysicsObject.Attachment att = this.attachments.get(point);
        return att == null ? null : StrandEndpoints.worldPoint(level, SubLevelContainer.getContainer(level), att.subLevelId(), att.blockPos());
    }

    public void driveKnotGrab(SubLevelPhysicsSystem system, ServerLevel level, double timeStep) {
        if (!this.knotGrab.idle()) {
            if (this.knotGrab
                .tickAndReportRelease(
                    level,
                    this.points,
                    this.pinnedEnd(level, AttachmentPoint.START),
                    this.pinnedEnd(level, AttachmentPoint.END),
                    timeStep,
                    this.getCollisionRadius()
                )) {
                system.removeObject(this);
            }
        }
    }

    public abstract void prePhysicsTick(SubLevelPhysicsSystem var1, ServerLevel var2, double var3);

    protected void configureConstraintMotors(PhysicsConstraintHandle handle) {
        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            handle.setMotor(axis, 0.0, 0.0, 0.25, false, 0.0);
        }
    }

    protected static void dampAllAxes(PhysicsConstraintHandle handle) {
        for (ConstraintJointAxis axis : ConstraintJointAxis.ANGULAR) {
            handle.setMotor(axis, 0.0, 0.0, 1.3, false, 0.0);
        }

        for (ConstraintJointAxis axis : ConstraintJointAxis.LINEAR) {
            handle.setMotor(axis, 0.0, 0.0, 0.25, false, 0.0);
        }
    }

    protected Vector3d holderPoint(ServerLevel level, AttachedRopePhysicsObject.Attachment att, @Nullable ServerSubLevel sub) {
        BlockPos bp = att.blockPos();
        BlockEntity be = (sub != null ? sub.getLevel() : level).getBlockEntity(bp);
        Vector3d blockCenter = new Vector3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
        return this.resolveHolderPoint(be, bp, blockCenter);
    }

    protected boolean applyAttachments(ServerLevel level) {
        boolean all = true;

        for (Entry<AttachmentPoint, AttachedRopePhysicsObject.Attachment> e : this.attachments.entrySet()) {
            AttachedRopePhysicsObject.Attachment att = e.getValue();
            BodyRef body = BodyRef.of(level, att.subLevelId());
            if (body.absent()) {
                all = false;
            } else {
                this.setAttachment(e.getKey(), this.holderPoint(level, att, body.sub()), body.sub());
            }
        }

        return all;
    }

    void checkAnchors(ServerLevel level) {
        if (this.getPoints().size() >= 2) {
            boolean centerMoved = false;

            for (Entry<AttachmentPoint, AttachedRopePhysicsObject.Attachment> e : this.attachments.entrySet()) {
                ServerSubLevel sub = BodyRef.of(level, e.getValue().subLevelId()).sub();
                if (sub != null && sub.getMassTracker().getCenterOfMass() != null) {
                    Vector3d center = new Vector3d(sub.getMassTracker().getCenterOfMass());
                    Vector3d known = this.lastCenters.put(e.getKey(), center);
                    if (known != null && known.distanceSquared(center) > 1.0E-8) {
                        centerMoved = true;
                    }
                }
            }

            if (centerMoved) {
                LoconauticsConstants.LOGGER
                    .debug(
                        "[ropeanchor] {} rope={} center of mass moved, attachments reapplied",
                        this.getClass().getSimpleName(),
                        this.id.toString().substring(0, 8)
                    );
                this.applyAttachments(level);
            }
        }
    }

    public boolean jointImpulses(Vector3d linear, Vector3d angular) {
        if (this.constraint != null && this.constraint.isValid()) {
            this.constraint.getJointImpulses(linear, angular);
            return true;
        } else {
            return false;
        }
    }

    protected void reattachConstraint(SubLevelPhysicsSystem system, ServerLevel level) {
        this.removeConstraint();
        AttachedRopePhysicsObject.Attachment start = this.attachments.get(AttachmentPoint.START);
        AttachedRopePhysicsObject.Attachment end = this.attachments.get(AttachmentPoint.END);
        if (start != null && end != null) {
            BodyRef refA = BodyRef.of(level, start.subLevelId());
            BodyRef refB = BodyRef.of(level, end.subLevelId());
            if (!refA.absent() && !refB.absent()) {
                ServerSubLevel bodyA = refA.sub();
                ServerSubLevel bodyB = refB.sub();
                if (bodyA != bodyB) {
                    if (!this.requireBothBodiesResolved() || bodyA != null && bodyB != null) {
                        Vector3d pos1 = this.holderPoint(level, start, bodyA);
                        Vector3d pos2 = this.holderPoint(level, end, bodyB);
                        PhysicsPipeline pipeline = system.getPipeline();
                        FreeConstraintConfiguration config = new FreeConstraintConfiguration(pos1, pos2, new Quaterniond());
                        this.constraint = pipeline.addConstraint(bodyA, bodyB, config);
                        if (this.constraint != null) {
                            this.constraintBodyA = start.subLevelId();
                            this.constraintBodyB = end.subLevelId();
                            this.configureConstraintMotors(this.constraint);
                        }
                    }
                }
            }
        }
    }

    protected boolean constraintBodiesLive(ServerLevel level) {
        return !BodyRef.of(level, this.constraintBodyA).absent() && !BodyRef.of(level, this.constraintBodyB).absent();
    }

    public boolean constraintLive() {
        return this.constraint != null && this.constraint.isValid();
    }

    protected boolean constraintNeedsRebuild(ServerLevel level) {
        return this.constraint == null || !this.constraint.isValid() || !this.constraintBodiesLive(level);
    }

    protected boolean anyAttachmentGrabbed(ServerLevel level, Predicate<BlockEntity> grabbedCheck) {
        for (AttachedRopePhysicsObject.Attachment att : this.attachments.values()) {
            if (this.isAttachmentGrabbed(level, att, grabbedCheck)) {
                return true;
            }
        }

        return false;
    }

    protected boolean isAttachmentGrabbed(ServerLevel level, AttachmentPoint point, Predicate<BlockEntity> grabbedCheck) {
        AttachedRopePhysicsObject.Attachment att = this.attachments.get(point);
        return att != null && this.isAttachmentGrabbed(level, att, grabbedCheck);
    }

    private boolean isAttachmentGrabbed(ServerLevel level, AttachedRopePhysicsObject.Attachment att, Predicate<BlockEntity> grabbedCheck) {
        if (att.subLevelId() == null) {
            return false;
        }

        ServerSubLevel sub = BodyRef.of(level, att.subLevelId()).sub();
        if (sub == null) {
            return false;
        }

        BlockEntity be = sub.getLevel().getBlockEntity(att.blockPos());
        return be != null && grabbedCheck.test(be);
    }

    protected void attachBody(ServerLevel level, AttachmentPoint point, PhysicsPipelineBody body, Vector3dc localPoint) {
        if (this.isActive() && this.handle instanceof RapierRopeHandle rapier) {
            Rapier3D.setRopeAttachment(
                Rapier3D.getSceneHandle(level),
                rapier.handle(),
                Rapier3D.getID(body),
                localPoint.x(),
                localPoint.y(),
                localPoint.z(),
                point == AttachmentPoint.END
            );
        }
    }

    protected void removeConstraint() {
        if (this.constraint != null) {
            if (this.constraint.isValid()) {
                this.constraint.remove();
            }

            this.constraint = null;
            this.constraintBodyA = null;
            this.constraintBodyB = null;
        }
    }

    @Override
    public void onAddition(SubLevelPhysicsSystem physicsSystem) {
        super.onAddition(physicsSystem);
        if (this.knotGrab.traceTicks > 0) {
            LoconauticsConstants.LOGGER.debug("[knotgrab] rope added to system id={}", this.id);
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        this.knotGrab.reset();
        if (this.knotGrab.traceTicks > 0) {
            LoconauticsConstants.LOGGER.debug("[knotgrab] rope removed from system id={}", this.id);
        }

        this.removeConstraint();
    }

    @Override
    public void onUnloaded(SubLevelHoldingChunkMap holdingChunkMap, ChunkPos chunkPos) {
        super.onUnloaded(holdingChunkMap, chunkPos);
        this.knotGrab.reset();
        this.removeConstraint();
    }

    public boolean areAttachmentsLoaded(ServerLevel level) {
        for (AttachedRopePhysicsObject.Attachment att : this.attachments.values()) {
            if (att.subLevelId() != null) {
                if (BodyRef.of(level, att.subLevelId()).absent()) {
                    return false;
                }
            } else if (!PhysicsChunkTicketManager.isChunkLoadedEnough(level, att.blockPos().getX() >> 4, att.blockPos().getZ() >> 4)) {
                return false;
            }
        }

        return true;
    }

    public boolean tickOverstretch(double desiredExtension, float snapStretchPercent) {
        if (!(snapStretchPercent <= 0.0F)
            && !(desiredExtension <= 1.0E-4)
            && !(this.getCurrentExtension() <= desiredExtension * (1.0 + snapStretchPercent / 100.0))) {
            return ++this.overstretchTicks >= 5;
        }

        this.overstretchTicks = 0;
        return false;
    }

    public boolean needsSync() {
        List<Vector3d> pts = this.getPoints();
        if (this.lastNetworkedPoints.size() != pts.size()) {
            return true;
        }

        double threshold = Mth.square(0.00625);

        for (int i = 0; i < pts.size(); i++) {
            if (pts.get(i).distanceSquared((Vector3dc)this.lastNetworkedPoints.get(i)) > threshold) {
                return true;
            }
        }

        return false;
    }

    public boolean isNetworkingStopped() {
        return this.networkingStopped;
    }

    public void setNetworkingStopped(boolean stopped) {
        this.networkingStopped = stopped;
    }

    public void justSynced() {
        this.lastNetworkedPoints.clear();
        ObjectListIterator var1 = this.getPoints().iterator();

        while (var1.hasNext()) {
            Vector3d p = (Vector3d)var1.next();
            this.lastNetworkedPoints.add(new Vector3d(p));
        }
    }

    public record Attachment(@Nullable UUID subLevelId, BlockPos blockPos) {
    }
}
