package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.copycat.CopycatBlockLogic;
import com.lycoris.loconautics.common.copycat.CopycatMaterialHolder;
import com.lycoris.loconautics.common.copycat.CopycatMaterialStore;
import com.lycoris.loconautics.content.steelcable.SteelCableHolder;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.trainphysics.coupling.TwistLink;
import com.lycoris.loconautics.trainphysics.sublevel.SubLevelContacts;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class KnuckleBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, SteelCableHolder, CopycatMaterialHolder {
    private static final long NOT_ENGAGED = -1L;
    private static final double TIP_OUTWARD = 0.04;
    private static final double TIP_HEIGHT = 0.53;
    @Nullable
    private UUID partnerSubLevelId = null;
    @Nullable
    private BlockPos partnerLocalPos = null;
    private boolean coupled;
    private transient long rotateLockUntil;
    private transient long coupleLockUntil;
    @Nullable
    private transient UUID holdSubLevelId;
    @Nullable
    private transient BlockPos holdLocalPos;
    private transient long engagedAt = -1L;
    private transient double engageFrom = 2.0;
    private transient int missingScans;
    private transient KnuckleJoint.Live joint;
    private transient float clip;
    private transient boolean clipLatched;
    private transient double clipClock = Double.NaN;
    private final CopycatMaterialStore copycat = new CopycatMaterialStore(this);

    public KnuckleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new SteelCableHolderBehaviour(this, this.strandCapacity()));
    }

    @Override
    public Vec3 getAttachmentPoint() {
        Vector3d tip = tipLocal(this.getBlockPos(), (Direction)this.getBlockState().getValue(KnuckleBlock.FACING));
        return new Vec3(tip.x, tip.y, tip.z);
    }

    public float animationClip(double hingeGap, float partialTicks) {
        float target = this.clipTarget(hingeGap);
        if (this.level == null) {
            return target;
        }

        double now = (float)this.level.getGameTime() + partialTicks;
        boolean firstFrame = Double.isNaN(this.clipClock);
        double dt = firstFrame ? 0.0 : Math.max(0.0, now - this.clipClock);
        this.clipClock = now;
        if (firstFrame) {
            this.clip = target;
            return this.clip;
        }

        this.clip = this.clip + (target - this.clip) * (float)(1.0 - Math.exp(-dt * KnuckleTuning.positionLerp));
        if (Math.abs(target - this.clip) < 0.001F) {
            this.clip = target;
        }

        return this.clip;
    }

    private float clipTarget(double hingeGap) {
        if (!this.isConnected()) {
            this.clipLatched = false;
            if (this.isReleasing()) {
                return KnuckleTuning.clipForGap(hingeGap);
            } else {
                return this.isRedstoneLocked() ? 2.0F : 0.0F;
            }
        } else {
            if (this.clipLatched) {
                return 2.0F;
            }

            float gapClip = KnuckleTuning.clipForGap(hingeGap);
            if (gapClip >= 2.0F) {
                this.clipLatched = true;
            }

            return gapClip;
        }
    }

    public boolean isRedstoneLocked() {
        return this.level != null && KnuckleBlock.redstoneLocked(this.level, this.getBlockPos(), (Direction)this.getBlockState().getValue(KnuckleBlock.FACING));
    }

    @Override
    public CopycatMaterialStore copycat() {
        return this.copycat;
    }

    public ModelData getModelData() {
        return CopycatBlockLogic.modelData(this.copycat);
    }

    public boolean isConnected() {
        return this.coupled && this.partnerSubLevelId != null;
    }

    public boolean isReleasing() {
        return !this.coupled && this.partnerSubLevelId != null;
    }

    public boolean isController(UUID ownSubLevelId) {
        return this.partnerSubLevelId != null && ownSubLevelId.compareTo(this.partnerSubLevelId) < 0;
    }

    @Nullable
    public UUID getPartnerSubLevelId() {
        return this.partnerSubLevelId;
    }

    @Nullable
    public BlockPos getPartnerLocalPos() {
        return this.partnerLocalPos;
    }

    public void movePartnerPos(BlockPos partnerLocalPos) {
        this.partnerLocalPos = partnerLocalPos;
        this.missingScans = 0;
        this.setChanged();
    }

    public void setConnection(UUID partnerSubLevel, BlockPos partnerLocalPos) {
        this.partnerSubLevelId = partnerSubLevel;
        this.partnerLocalPos = partnerLocalPos;
        this.coupled = true;
        this.missingScans = 0;
        this.traceTransition("couple");
        this.releaseCoupling();
        this.writeConnectedState(true);
        this.setChanged();
        this.sendData();
    }

    boolean partnerMissedTooLong() {
        return ++this.missingScans >= 4;
    }

    void partnerSeen() {
        this.missingScans = 0;
    }

    private void traceTransition(String what) {
        if (this.level != null && !this.level.isClientSide()) {
            LoconauticsConstants.LOGGER
                .debug("[knuckle] {} {} partner={} tick={}", new Object[]{what, this.getBlockPos(), this.partnerSubLevelId, this.level.getGameTime()});
        }
    }

    public void clearConnection() {
        this.traceTransition("uncouple");
        StackTraceElement[] frames = new Throwable().getStackTrace();
        LoconauticsConstants.LOGGER
            .debug(
                "[iss234k] clearConnection {} called from {} < {} < {}",
                new Object[]{this.getBlockPos(), frames.length > 1 ? frames[1] : "?", frames.length > 2 ? frames[2] : "?", frames.length > 3 ? frames[3] : "?"}
            );
        this.returnContacts();
        this.releaseCoupling();
        this.coupled = false;
        if (this.level != null) {
            this.rotateLockUntil = this.level.getGameTime() + 40L;
            this.coupleLockUntil = this.level.getGameTime() + 60L;
        }

        this.writeConnectedState(false);
        this.setChanged();
        this.sendData();
    }

    private void returnContacts() {
        if (this.partnerSubLevelId != null && this.level != null && !this.level.isClientSide() && Sable.HELPER.getContaining(this) instanceof SubLevel own) {
            SubLevelContacts.enableCoupled(own.getUniqueId(), this.partnerSubLevelId);
        }
    }

    public void clearPartner() {
        this.partnerSubLevelId = null;
        this.partnerLocalPos = null;
        this.setChanged();
        this.sendData();
    }

    public boolean isRotateLocked() {
        return this.level != null && this.level.getGameTime() < this.rotateLockUntil;
    }

    public boolean isCouplingLocked() {
        return this.level != null && this.level.getGameTime() < this.coupleLockUntil;
    }

    void holdApartFrom(UUID otherSubLevel, BlockPos otherLocalPos) {
        this.holdSubLevelId = otherSubLevel;
        this.holdLocalPos = otherLocalPos;
    }

    void releaseHold() {
        this.holdSubLevelId = null;
        this.holdLocalPos = null;
    }

    boolean isHeldApart() {
        return this.holdSubLevelId != null;
    }

    @Nullable
    UUID holdSubLevelId() {
        return this.holdSubLevelId;
    }

    @Nullable
    BlockPos holdLocalPos() {
        return this.holdLocalPos;
    }

    private void writeConnectedState(boolean connected) {
        if (this.level != null) {
            BlockState state = this.level.getBlockState(this.getBlockPos());
            if (state.hasProperty(KnuckleBlock.CONNECTED) && (Boolean)state.getValue(KnuckleBlock.CONNECTED) != connected) {
                this.level.setBlock(this.getBlockPos(), (BlockState)state.setValue(KnuckleBlock.CONNECTED, connected), 3);
            }
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (this.coupled && this.partnerSubLevelId != null && this.partnerLocalPos != null && this.level != null && this.isController(subLevel.getUniqueId())) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(subLevel.getLevel());
            if (container != null
                && container.getSubLevel(this.partnerSubLevelId) instanceof ServerSubLevel partnerSub
                && partnerSub != subLevel
                && !partnerSub.isRemoved()
                && partnerSub.getLevel().getBlockEntity(this.partnerLocalPos) instanceof KnuckleBlockEntity partnerBe
                && partnerBe.isConnected()) {
                Vector3d hingeA = hingeLocal(this.getBlockPos(), (Direction)this.getBlockState().getValue(KnuckleBlock.FACING));
                Vector3d hingeB = hingeLocal(partnerBe.getBlockPos(), (Direction)partnerBe.getBlockState().getValue(KnuckleBlock.FACING));
                if (this.engagedAt == -1L) {
                    Vector3d worldA = subLevel.logicalPose().transformPosition(new Vector3d(hingeA));
                    Vector3d worldB = partnerSub.logicalPose().transformPosition(new Vector3d(hingeB));
                    this.engagedAt = this.level.getGameTime();
                    this.engageFrom = worldA.distance(worldB);
                }

                boolean bar308 = KnuckleRailCoupler.rigid(subLevel) && KnuckleRailCoupler.rigid(partnerSub);
                KnuckleRailCoupler.trace308(this, subLevel, hingeA, partnerSub, hingeB, bar308);
                if (bar308) {
                    KnuckleJoint.release(this.joint);
                    this.joint = null;
                    double length = this.engageFrom + (2.0 - this.engageFrom) * this.engageGain();
                    KnuckleRailCoupler.put(
                        this, new KnuckleRailCoupler.Pair(subLevel.getUniqueId(), hingeA, partnerSub.getUniqueId(), hingeB, length, this.level.getGameTime())
                    );
                } else {
                    KnuckleRailCoupler.remove(this);
                    this.joint = KnuckleJoint.hold(this.joint, subLevel, hingeA, partnerSub, hingeB, this.engageFrom, this.engageGain());
                }
            } else {
                this.releaseCoupling();
            }
        } else {
            this.releaseCoupling();
        }
    }

    public void invalidate() {
        KnuckleRailCoupler.remove(this);
        KnuckleJoint.release(this.joint);
        this.joint = null;
        super.invalidate();
    }

    boolean engageDone() {
        return this.level != null && this.engagedAt != -1L && this.level.getGameTime() - this.engagedAt >= 8L;
    }

    private double engageGain() {
        long elapsed = this.level.getGameTime() - this.engagedAt;
        return Math.max(0.0, Math.min(1.0, elapsed / 8.0));
    }

    public void releaseCoupling() {
        KnuckleRailCoupler.remove(this);
        KnuckleJoint.release(this.joint);
        this.joint = null;
        this.engagedAt = -1L;
        this.engageFrom = 2.0;
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        if (this.coupled && this.partnerSubLevelId != null && this.level != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(this.level);
            if (container != null) {
                SubLevel partner = container.getSubLevel(this.partnerSubLevelId);
                if (partner != null) {
                    return List.of(partner);
                }
            }
        }

        return List.of();
    }

    static Vector3d tipLocal(BlockPos p, Direction facing) {
        return new Vector3d(p.getX() + 0.5 + facing.getStepX() * 0.04, p.getY() + 0.53, p.getZ() + 0.5 + facing.getStepZ() * 0.04);
    }

    public Vector3d hingePlot() {
        return hingeLocal(this.getBlockPos(), (Direction)this.getBlockState().getValue(KnuckleBlock.FACING));
    }

    @Nullable
    public TwistLink twistLink(ServerSubLevel own) {
        return this.isConnected()
                && this.level != null
                && this.partnerLocalPos != null
                && this.isController(own.getUniqueId())
                && this.level.getBlockEntity(this.partnerLocalPos) instanceof KnuckleBlockEntity partner
                && partner.isConnected()
            ? new TwistLink(own.getUniqueId(), this.hingePlot(), this.partnerSubLevelId, partner.hingePlot(), TwistLink.Kind.KNUCKLE, 1.0, null)
            : null;
    }

    static Vector3d hingeLocal(BlockPos p, Direction facing) {
        return new Vector3d(p.getX() + 0.5 - 0.5 * facing.getStepX(), p.getY() + 0.4375, p.getZ() + 0.5 - 0.5 * facing.getStepZ());
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.partnerSubLevelId != null) {
            tag.putString("PartnerSubLevel", this.partnerSubLevelId.toString());
        }

        if (this.partnerLocalPos != null) {
            tag.putLong("PartnerLocalPos", this.partnerLocalPos.asLong());
        }

        tag.putBoolean("Coupled", this.coupled);
        this.copycat.write(tag, registries);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.partnerSubLevelId = tag.contains("PartnerSubLevel") ? UUID.fromString(tag.getString("PartnerSubLevel")) : null;
        this.partnerLocalPos = tag.contains("PartnerLocalPos") ? BlockPos.of(tag.getLong("PartnerLocalPos")) : null;
        this.coupled = tag.getBoolean("Coupled");
        this.copycat.read(tag, registries, clientPacket);
    }
}
