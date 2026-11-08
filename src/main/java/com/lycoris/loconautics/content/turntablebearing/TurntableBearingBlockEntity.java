package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.common.sable.TrackHostFrames;
import com.lycoris.loconautics.common.turntable.GhostTurntable;
import com.lycoris.loconautics.common.turntable.TurntableGhosts;
import com.lycoris.loconautics.common.turntable.TurntableMotion;
import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import com.lycoris.loconautics.common.turntable.TurntableSignalHold;
import com.lycoris.loconautics.common.turntable.sequence.TurntableInstruction;
import com.lycoris.loconautics.common.turntable.sequence.TurntableTriggerMode;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.foundation.debug.TurntableTrace;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimLevelUtil;
import dev.simulated_team.simulated.util.SimAssemblyHelper.AssemblyResult;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraBlockPos;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics.ExtraKineticsBlockEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class TurntableBearingBlockEntity
    extends KineticBlockEntity
    implements ExtraKinetics,
    IDisplayAssemblyExceptions,
    BlockEntitySubLevelActor,
    TurntableMotion.Host,
    TurntableGhosts.Source {
    private static final double SERVO_STIFFNESS = 1600.0;
    private static final double SERVO_DAMPING = 40.0;
    private static final double MIN_INERTIA = 10.0;
    private static final float MAX_COG_RPM = 96.0F;
    private static final float MAX_PLATE_RPM = 256.0F;
    private static final float PLATE_ANCHOR_SEPARATION_BIAS = 0.001F;
    private final TurntableBearingBlockEntity.TurntableCogwheelBlockEntity cogwheel;
    public boolean assembleNextTick;
    protected AssemblyException lastException;
    private final TurntableMotion motion = new TurntableMotion();
    @Nullable
    private UUID subLevelID;
    @Nullable
    private BlockPos platePos;
    @Nullable
    private RotaryConstraintHandle handle;
    private boolean assembling;
    private boolean removedForGood;
    private float speedOverride;
    private final TurntableSignalHold signals = new TurntableSignalHold();

    public TurntableBearingBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.cogwheel = new TurntableBearingBlockEntity.TurntableCogwheelBlockEntity(typeIn, pos, state, this);
    }

    public void tick() {
        super.tick();
        this.cogwheel.tick();
        if (!this.level.isClientSide) {
            if (this.assembleNextTick) {
                this.assembleNextTick = false;
                if (this.isAssembled()) {
                    this.disassemble();
                } else {
                    this.assemble();
                }
            }

            if (this.subLevelID != null) {
                this.checkPersistence(this.subLevelID);
                this.adoptGhost();
            }

            this.tickSignals();
            this.motion.beginTick();
            if (this.isAssembled()) {
                TurntableGhosts.trackLive(this);
                TurntableRegistry.markPlate(this.subLevelID);
                this.recordFrame();
                this.traceDetached();
                SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(this.level);
                if (physicsSystem != null && !physicsSystem.getPaused()) {
                    if (this.turntableScheduleDriven()) {
                        TurntableRegistry.register(this);
                        this.motion.step(this, true);
                    } else {
                        TurntableRegistry.unregister(this.subLevelID);
                        this.motion.step(this, false);
                    }
                }
            }
        }
    }

    private void traceDetached() {
        if (this.subLevelID != null && this.getAttachedSubLevel() == null && TurntableTrace.due("bearing" + this.subLevelID, this.level.getGameTime(), 20)) {
            TrackHostFrames.Frame frame = TrackHostFrames.frameOf(this.subLevelID);
            SubLevelPhysicsSystem physics = SubLevelPhysicsSystem.get(this.level);
            TurntableTrace.log(
                "bearing without plate "
                    + LogFormat.shortId(this.subLevelID)
                    + " at "
                    + this.getBlockPos().toShortString()
                    + " powered="
                    + this.turntablePowered()
                    + " sched="
                    + this.turntableScheduleDriven()
                    + " perTick="
                    + this.angularPerTick()
                    + " cog="
                    + this.cogwheel.getSpeed()
                    + " handle="
                    + (this.handle != null)
                    + " target="
                    + String.format("%.2f", this.motion.targetAngleDegrees())
                    + " alignTarget="
                    + (this.motion.alignTarget() != null)
                    + " aligned="
                    + this.motion.aligned()
                    + " reserved="
                    + TurntableRegistry.reserved(this.subLevelID)
                    + " registered="
                    + (TurntableRegistry.byPlate(this.subLevelID) == this)
                    + " frameLoaded="
                    + (frame != null && frame.loaded())
                    + " physicsPaused="
                    + (physics == null || physics.getPaused())
            );
        }
    }

    private void adoptGhost() {
        GhostTurntable ghost = TurntableGhosts.take(this.subLevelID);
        if (ghost != null) {
            this.motion.copyFrom(ghost.motion());
            this.signals.copyFrom(ghost.signals());
            this.setChanged();
            this.sendData();
        }
    }

    private void recordFrame() {
        if (this.getAttachedSubLevel() instanceof ServerSubLevel plate && !plate.isRemoved()) {
            TrackHostFrames.record(
                this.subLevelID, this.level.dimension(), plate, this.motion.targetAngleDegrees(), this.turntableAxisWorld(), this.turntableAxisNormal()
            );
        } else {
            TrackHostFrames.setAngle(this.subLevelID, this.motion.targetAngleDegrees());
        }
    }

    @Nullable
    @Override
    public GhostTurntable toGhost() {
        if (this.level != null && !this.level.isClientSide && this.isAssembled() && this.subLevelID != null && TrackHostFrames.frameOf(this.subLevelID) != null
            )
         {
            GhostTurntable ghost = new GhostTurntable(this.subLevelID, this.level.dimension(), this.getBlockPos());
            ghost.motion().copyFrom(this.motion);
            ghost.signals().copyFrom(this.signals);
            ghost.snapshot(
                this.turntablePowered(),
                this.angularPerTick(),
                this.facingNegative(),
                this.speedOverride,
                (Boolean)this.getBlockState().getValue(BlockStateProperties.POWERED)
            );
            return ghost;
        } else {
            return null;
        }
    }

    public boolean bindSignal(BlockPos signal) {
        if (!this.signals.bind(signal)) {
            return false;
        }

        this.setChanged();
        return true;
    }

    private void tickSignals() {
        if (this.signals.tick(this.level, this.plateBusy())) {
            this.setChanged();
        }
    }

    private boolean plateBusy() {
        return !this.isAssembled() || this.motion.busy(this.subLevelID);
    }

    private void releaseSignals() {
        this.signals.release(this.level);
    }

    public void turnBy(double degrees) {
        this.motion.turnBy(degrees);
    }

    public void turnTo(double absoluteDegrees) {
        this.motion.turnTo(absoluteDegrees);
    }

    public void stopTurning() {
        this.motion.stopTurning(this);
    }

    public boolean isTurning() {
        return this.motion.isTurning();
    }

    public double remainingManualDegrees() {
        return this.motion.manualRemainingDegrees();
    }

    public int getCurrentInstruction() {
        return this.motion.currentInstruction();
    }

    public void runInstruction(int index) {
        this.motion.runInstruction(this, index);
    }

    public float getPlateSpeed() {
        return this.plateSpeed();
    }

    public float getCogSpeed() {
        return this.limitCogSpeed(this.cogwheel.getSpeed());
    }

    public double degreesPerTick() {
        return Math.abs(this.angularPerTick());
    }

    @Override
    public float angularPerTick() {
        return convertToAngular(this.plateSpeed());
    }

    @Override
    public boolean facingNegative() {
        return ((Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING)).getAxisDirection() == AxisDirection.NEGATIVE;
    }

    @Override
    public float speedOverride() {
        return this.speedOverride;
    }

    @Override
    public boolean neighborSignal() {
        return this.level.hasNeighborSignal(this.worldPosition);
    }

    @Override
    public void motionChanged() {
        this.setChanged();
        this.sendData();
    }

    @Override
    public void bodiesMoved() {
        this.wakeBodies();
    }

    @Override
    public boolean turntableSettled() {
        return this.motion.settled();
    }

    @Nullable
    @Override
    public UUID plateSubLevelId() {
        return this.subLevelID;
    }

    @Override
    public Vec3 turntableAxisWorld() {
        Direction facing = (Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING);
        return this.toWorld(this.getBlockPos().relative(facing).getCenter(), true);
    }

    @Override
    public Vec3 turntableAxisNormal() {
        Vec3i normal = ((Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING)).getNormal();
        return this.toWorld(new Vec3(normal.getX(), normal.getY(), normal.getZ()), false).normalize();
    }

    private Vec3 toWorld(Vec3 local, boolean asPosition) {
        SubLevel containing = this.getContainingSubLevel();
        if (containing == null) {
            return local;
        }

        Vector3d in = new Vector3d(local.x, local.y, local.z);
        Vector3d out = asPosition
            ? containing.logicalPose().transformPosition(in, new Vector3d())
            : containing.logicalPose().transformNormal(in, new Vector3d());
        return new Vec3(out.x, out.y, out.z);
    }

    @Override
    public boolean turntablePowered() {
        return this.isAssembled() && this.cogwheel.getSpeed() != 0.0F;
    }

    @Override
    public boolean turntableScheduleDriven() {
        return this.isAssembled() && this.subLevelID != null && this.motion.scheduleDriven();
    }

    @Override
    public void turntableAlignTo(TrackNodeLocation movingHead, TrackNodeLocation target) {
        this.motion.alignTo(movingHead, target);
    }

    @Override
    public void turntableClearAlignment() {
        this.motion.clearAlignment();
    }

    @Nullable
    @Override
    public TrackNodeLocation turntableAlignTarget() {
        return this.motion.alignTarget();
    }

    @Override
    public boolean turntableAligned() {
        return this.motion.aligned();
    }

    private void wakeBodies() {
        SubLevel attached = this.getAttachedSubLevel();
        if (attached != null && this.handle != null) {
            PhysicsPipeline pipeline = ((ServerSubLevelContainer)SubLevelContainer.getContainer(this.level)).physicsSystem().getPipeline();
            if (this.getContainingSubLevel() instanceof ServerSubLevel containing) {
                pipeline.wakeUp(containing);
            }

            if (attached instanceof ServerSubLevel attachedServer) {
                pipeline.wakeUp(attachedServer);
            }
        }
    }

    public void onRedstoneUpdate(boolean isPowered) {
        this.motion.redstoneEdge(this, isPowered, this.turntableScheduleDriven());
        if ((Boolean)this.getBlockState().getValue(BlockStateProperties.POWERED) != isPowered) {
            this.level.setBlock(this.worldPosition, (BlockState)this.getBlockState().setValue(BlockStateProperties.POWERED, isPowered), 2);
        }
    }

    public void applyConfiguration(List<TurntableInstruction> newInstructions, TurntableTriggerMode newTrigger) {
        this.motion.configure(this, newInstructions, newTrigger);
    }

    private float limitCogSpeed(float speed) {
        return Mth.clamp(speed, -96.0F, 96.0F);
    }

    public float plateSpeed() {
        float cog = this.limitCogSpeed(this.cogwheel.getSpeed());
        return cog != 0.0F && this.speedOverride != 0.0F ? this.speedOverride : cog;
    }

    public float getSpeedOverride() {
        return this.speedOverride;
    }

    public void setSpeedOverride(float rpm) {
        float clamped = Mth.clamp(rpm, -256.0F, 256.0F);
        if (clamped != this.speedOverride) {
            this.speedOverride = clamped;
            this.pushStress();
            this.setChanged();
            this.sendData();
        }
    }

    public void pushStress() {
        if (this.level != null && !this.level.isClientSide && this.cogwheel.hasNetwork()) {
            this.cogwheel.getOrCreateNetwork().updateStressFor(this.cogwheel, this.cogwheel.calculateStressApplied());
        }
    }

    public float billedStress() {
        return this.plateStressImpact(this.cogwheel.getTheoreticalSpeed()) * Math.abs(this.cogwheel.getTheoreticalSpeed());
    }

    float plateStressImpact(float networkSpeed) {
        if (!this.isAssembled()) {
            return 0.0F;
        }

        float impact = ((Double)LoconauticsConfig.TURNTABLE_SU_IMPACT.get()).floatValue();
        float fed = Math.abs(networkSpeed);
        return fed == 0.0F ? impact : impact * Math.abs(this.plateSpeed()) / fed;
    }

    public void updateServoCoefficients() {
        this.validateConstraintHandle();
        if (this.isAssembled() && this.handle != null) {
            SubLevel subLevelA = this.getContainingSubLevel();
            SubLevel subLevelB = this.getAttachedSubLevel();
            Vec3i facingVec3I = ((Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING)).getNormal();
            Vector3d facingVec = new Vector3d(facingVec3I.getX(), facingVec3I.getY(), facingVec3I.getZ());
            Vector3d temp = new Vector3d();
            double inertiaA = Double.MAX_VALUE;
            double inertiaB = Double.MAX_VALUE;
            if (subLevelA instanceof ServerSubLevel serverA) {
                inertiaA = serverA.getMassTracker().getInertiaTensor().transform(facingVec, temp).dot(facingVec);
            }

            if (subLevelB instanceof ServerSubLevel serverB) {
                inertiaB = serverB.getMassTracker().getInertiaTensor().transform(facingVec, temp).dot(facingVec);
            }

            double totalInertia = Math.max(10.0, subLevelA != null && subLevelB != null ? Math.max(inertiaA, inertiaB) : Math.min(inertiaA, inertiaB));
            SubLevelPhysicsSystem physicsSystem = ((ServerSubLevelContainer)SubLevelContainer.getContainer(this.level)).physicsSystem();
            double kP = 1600.0 * totalInertia;
            double kD = 40.0 * totalInertia;
            float goalRadians = AngleHelper.rad(
                AngleHelper.angleLerp(physicsSystem.getPartialPhysicsTick(), this.motion.lastTargetAngleDegrees(), this.motion.targetAngleDegrees())
            );
            this.handle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, goalRadians, kP, kD, false, 0.0);
            this.handle.setContactsEnabled(false);
        }
    }

    private void validateConstraintHandle() {
        if (this.handle != null && !this.handle.isValid()) {
            this.handle = null;
        }
    }

    public void assemble() {
        BlockPos pos = this.getBlockPos();
        Direction facing = (Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING);
        BlockPos toAssemble = pos.relative(facing);

        AssemblyResult result;
        try {
            result = SimAssemblyHelper.assembleFromSingleBlock(this.level, pos, toAssemble, false, false);
            this.lastException = null;
        } catch (AssemblyException e) {
            this.lastException = e;
            this.sendData();
            return;
        }

        this.sendData();
        BlockState plate = (BlockState)((TurntablePlateBlock)LoconauticsRegistries.TURNTABLE_PLATE.get())
            .defaultBlockState()
            .setValue(DirectionalKineticBlock.FACING, facing);
        ServerSubLevel assembledSubLevel;
        BlockPos assembleOffset;
        if (result != null) {
            assembledSubLevel = (ServerSubLevel)result.subLevel();
            assembleOffset = result.offset();
        } else {
            ServerSubLevelContainer container = (ServerSubLevelContainer)SubLevelContainer.getContainer(this.level);
            Pose3d pose = new Pose3d();
            pose.position().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            assembledSubLevel = (ServerSubLevel)container.allocateNewSubLevel(pose);
            ServerLevelPlot plot = assembledSubLevel.getPlot();
            ChunkPos center = plot.getCenterChunk();
            plot.newEmptyChunk(center);
            plot.getEmbeddedLevelAccessor().setBlock(BlockPos.ZERO, plate, 3);
            BlockPos plotAnchor = plot.getCenterBlock();
            Vector3dc centerOfMass = assembledSubLevel.getMassTracker().getCenterOfMass();
            Vector3d subLevelCenter = JOMLConversion.atLowerCornerOf(pos);
            if (centerOfMass != null) {
                subLevelCenter.add(centerOfMass.x() - plotAnchor.getX(), centerOfMass.y() - plotAnchor.getY(), centerOfMass.z() - plotAnchor.getZ());
            } else {
                assembledSubLevel.logicalPose().rotationPoint().set(plotAnchor.getX() + 0.5, plotAnchor.getY() + 0.5, plotAnchor.getZ() + 0.5);
            }

            assembledSubLevel.logicalPose().position().set(subLevelCenter.x, subLevelCenter.y, subLevelCenter.z);
            assembleOffset = plotAnchor.subtract(pos);
            SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
            PhysicsPipeline pipeline = physicsSystem.getPipeline();
            SubLevel containingSubLevel = this.getContainingSubLevel();
            if (containingSubLevel != null) {
                SubLevelAssemblyHelper.kickFromContainingSubLevel((ServerLevel)this.level, physicsSystem, pipeline, assembledSubLevel, containingSubLevel);
                assembledSubLevel.logicalPose().orientation().set(containingSubLevel.logicalPose().orientation());
            }

            pipeline.teleport(assembledSubLevel, assembledSubLevel.logicalPose().position(), assembledSubLevel.logicalPose().orientation());
            assembledSubLevel.updateLastPose();
            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(this.level, pos);
        }

        this.level.setBlockAndUpdate(pos, (BlockState)this.getBlockState().setValue(TurntableBearingBlock.ASSEMBLED, true));
        BlockPos plotPos = pos.offset(assembleOffset);
        this.setSubLevelID(assembledSubLevel.getUniqueId());
        if (result != null) {
            this.level.setBlockAndUpdate(plotPos, plate);
        }

        if (this.level.getBlockEntity(plotPos) instanceof TurntablePlateBlockEntity plateBE) {
            plateBE.setParent(this);
            this.setPlatePos(plotPos);
        }

        this.attachConstraints(assembledSubLevel, JOMLConversion.toJOML(plotPos.relative(facing).getCenter()));
        this.pushStress();
    }

    public void disassemble() {
        if (!this.isRemoved()) {
            this.removeHandle();
            TurntableRegistry.unregister(this.subLevelID);
            TurntableRegistry.forgetPlate(this.subLevelID);
            TrackHostFrames.forget(this.subLevelID);
            TurntableGhosts.untrackLive(this);
            this.turntableClearAlignment();
            SubLevel subLevel = SubLevelContainer.getContainer(this.level).getSubLevel(this.subLevelID);
            BlockPos platePos = this.getPlatePos();
            if (platePos != null) {
                this.destroyPlate();
                if (Objects.equals(subLevel, Sable.HELPER.getContaining(this.level, this.getBlockPos()))) {
                    this.lastException = AssemblyException.unmovableBlock(this.getBlockPos(), this.getBlockState());
                    AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(this.level, platePos);
                } else if (subLevel != null && !subLevel.isRemoved()) {
                    SimAssemblyHelper.disassembleSubLevel(this.level, subLevel, platePos, this.getBlockPos(), Rotation.NONE, true);
                } else if (subLevel != null) {
                    AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(this.level, platePos);
                }
            }

            this.level.setBlockAndUpdate(this.getBlockPos(), (BlockState)this.getBlockState().setValue(TurntableBearingBlock.ASSEMBLED, false));
            this.pushStress();
            this.setSubLevelID(null);
            this.setPlatePos(null);
            this.motion.reset();
            this.sendData();
        }
    }

    private void checkPersistence(UUID id) {
        BlockPos platePos = this.getPlatePos();
        if (platePos == null
            || !SimLevelUtil.isAreaActuallyLoaded(this.level, platePos, 1)
            || this.level.getBlockState(platePos).is((Block)LoconauticsRegistries.TURNTABLE_PLATE.get())) {
            this.validateConstraintHandle();
            if (this.handle == null) {
                this.reattachConstraint((ServerSubLevel)SubLevelContainer.getContainer(this.level).getSubLevel(id), true);
            }
        }
    }

    public void reattachConstraint(@Nullable ServerSubLevel plateSubLevel, boolean updatePlate) {
        BlockPos platePos = this.getPlatePos();
        if (platePos != null) {
            this.removeHandle();
            if (updatePlate) {
                this.associatePlateWithParent();
            }

            BlockState plateState = this.level.getBlockState(platePos);
            if (plateState.is((Block)LoconauticsRegistries.TURNTABLE_PLATE.get())) {
                Direction plateFacing = (Direction)plateState.getValue(DirectionalKineticBlock.FACING);
                this.attachConstraints(plateSubLevel, JOMLConversion.toJOML(platePos.relative(plateFacing).getCenter()));
            }
        }
    }

    public void associatePlateWithParent() {
        BlockPos platePos = this.getPlatePos();
        if (platePos != null && this.level.getBlockEntity(platePos) instanceof TurntablePlateBlockEntity plate) {
            plate.setParent(this);
        }
    }

    private void attachConstraints(@Nullable ServerSubLevel plateSubLevel, Vector3d attachPos) {
        BlockPos platePos = this.getPlatePos();
        if (platePos != null) {
            BlockState plateState = this.level.getBlockState(platePos);
            if (plateState.is((Block)LoconauticsRegistries.TURNTABLE_PLATE.get())) {
                Direction facing = (Direction)this.getBlockState().getValue(DirectionalKineticBlock.FACING);
                Vector3d anchorPos = JOMLConversion.toJOML(this.getBlockPos().relative(facing).getCenter());
                Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
                Vec3 plateFacingVec = Vec3.atLowerCornerOf(((Direction)plateState.getValue(DirectionalKineticBlock.FACING)).getNormal());
                RotaryConstraintConfiguration constraint = new RotaryConstraintConfiguration(
                    anchorPos,
                    attachPos.sub(JOMLConversion.toJOML(plateFacingVec.scale(0.001F))),
                    JOMLConversion.toJOML(facingVec),
                    JOMLConversion.toJOML(plateFacingVec)
                );
                ServerSubLevelContainer container = SubLevelContainer.getContainer((ServerLevel)this.level);
                ServerSubLevel containingSubLevel = (ServerSubLevel)Sable.HELPER.getContaining(this);
                if (containingSubLevel != plateSubLevel) {
                    this.handle = (RotaryConstraintHandle)container.physicsSystem().getPipeline().addConstraint(containingSubLevel, plateSubLevel, constraint);
                }
            }
        }
    }

    protected void write(CompoundTag compound, Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        this.motion.write(compound);
        compound.putFloat("SpeedOverride", this.speedOverride);
        if (!clientPacket) {
            this.signals.write(compound);
        }

        if (this.subLevelID != null) {
            compound.putUUID("SubLevelID", this.subLevelID);
        }

        if (this.platePos != null) {
            compound.put("TurntablePlate", NbtUtils.writeBlockPos(this.platePos));
        }

        AssemblyException.write(compound, registries, this.lastException);
    }

    protected void read(CompoundTag compound, Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        this.motion.read(compound);
        this.speedOverride = compound.getFloat("SpeedOverride");
        if (!clientPacket) {
            this.signals.read(compound);
        }

        if (compound.hasUUID("SubLevelID")) {
            this.setSubLevelID(compound.getUUID("SubLevelID"));
        }

        if (compound.contains("TurntablePlate")) {
            this.setPlatePos((BlockPos)NbtUtils.readBlockPos(compound, "TurntablePlate").orElse(null));
        }

        this.lastException = AssemblyException.read(compound, registries);
    }

    public void invalidate() {
        super.invalidate();
        this.removeHandle();
        if (this.level == null || !this.level.isClientSide) {
            if (this.level != null) {
                TurntableGhosts.untrackLive(this);
                if (!this.removedForGood && this.isChunkUnloaded()) {
                    TurntableGhosts.ghostify(this);
                    return;
                }
            }

            this.releaseSignals();
            TurntableRegistry.unregister(this.subLevelID);
            TurntableRegistry.forgetPlate(this.subLevelID);
            TrackHostFrames.forget(this.subLevelID);
        }
    }

    public void beforeAssembly() {
        this.assembling = true;
    }

    public void remove() {
        this.removedForGood = true;
        if (!this.level.isClientSide && !this.assembling) {
            this.destroyPlate();
        }

        super.remove();
    }

    public boolean isAssembled() {
        return (Boolean)this.getBlockState().getValue(TurntableBearingBlock.ASSEMBLED);
    }

    @Nullable
    private SubLevel getAttachedSubLevel() {
        return SubLevelContainer.getContainer(this.level).getSubLevel(this.subLevelID);
    }

    @Nullable
    private SubLevel getContainingSubLevel() {
        return Sable.HELPER.getContaining(this);
    }

    private void destroyPlate() {
        BlockPos platePos = this.getPlatePos();
        if (platePos != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(this.level);
            if (container != null) {
                if (this.subLevelID == null || container.getSubLevel(this.subLevelID) != null) {
                    if (this.level.getBlockState(platePos).is((Block)LoconauticsRegistries.TURNTABLE_PLATE.get())) {
                        ((TurntablePlateBlock)LoconauticsRegistries.TURNTABLE_PLATE.get())
                            .withBlockEntityDo(this.level, platePos, TurntablePlateBlockEntity::beforeAssembly);
                        this.level.setBlock(platePos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private void removeHandle() {
        if (this.handle != null) {
            this.handle.remove();
            this.handle = null;
        }
    }

    public double getTargetAngleDegrees() {
        return this.motion.targetAngleDegrees();
    }

    public List<TurntableInstruction> getInstructions() {
        return this.motion.instructions();
    }

    public TurntableTriggerMode getTrigger() {
        return this.motion.trigger();
    }

    @NotNull
    @Override
    public KineticBlockEntity getExtraKinetics() {
        return this.cogwheel;
    }

    @Override
    public boolean shouldConnectExtraKinetics() {
        return false;
    }

    @Override
    public String getExtraKineticsSaveName() {
        return "TurntableCog";
    }

    public float propagateRotationTo(
        KineticBlockEntity target, BlockState stateFrom, BlockState stateTo, BlockPos diff, boolean connectedViaAxes, boolean connectedViaCogs
    ) {
        return this.getPlatePos() != null && stateTo.getBlock() instanceof TurntablePlateBlock
            ? 1.0F
            : super.propagateRotationTo(target, stateFrom, stateTo, diff, connectedViaAxes, connectedViaCogs);
    }

    public boolean isCustomConnection(KineticBlockEntity other, BlockState state, BlockState otherState) {
        return this.getPlatePos() != null && otherState.getBlock() instanceof TurntablePlateBlock;
    }

    public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
        if (this.getPlatePos() != null) {
            neighbours.add(this.getPlatePos());
        }

        return super.addPropagationLocations(block, state, neighbours);
    }

    @Nullable
    public BlockPos getPlatePos() {
        return this.platePos;
    }

    public void setPlatePos(@Nullable BlockPos platePos) {
        this.platePos = platePos;
    }

    @Nullable
    public UUID getSubLevelID() {
        return this.subLevelID;
    }

    public void setSubLevelID(@Nullable UUID subLevelID) {
        this.subLevelID = subLevelID;
    }

    public float calculateStressApplied() {
        return 0.0F;
    }

    public AssemblyException getLastAssemblyException() {
        return this.lastException;
    }

    @Nullable
    @Override
    public Iterable<@NotNull SubLevel> sable$getConnectionDependencies() {
        SubLevel attached = this.getAttachedSubLevel();
        return attached == null ? null : List.of(attached);
    }

    public static class TurntableCogwheelBlockEntity extends KineticBlockEntity implements ExtraKineticsBlockEntity {
        public static final ICogWheel EXTRA_COGWHEEL_CONFIG = new ICogWheel() {
            public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
                return false;
            }

            public Axis getRotationAxis(BlockState state) {
                return ((Direction)state.getValue(DirectionalKineticBlock.FACING)).getAxis();
            }
        };
        private final TurntableBearingBlockEntity parent;

        public TurntableCogwheelBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state, TurntableBearingBlockEntity parent) {
            super(typeIn, new ExtraBlockPos(pos), state);
            this.parent = parent;
        }

        public void onSpeedChanged(float previousSpeed) {
            super.onSpeedChanged(previousSpeed);
            if (this.speed != 0.0F && !this.parent.isAssembled()) {
                this.parent.assembleNextTick = true;
            }
        }

        public float calculateStressApplied() {
            this.lastStressApplied = this.parent.plateStressImpact(this.getTheoreticalSpeed());
            return this.lastStressApplied;
        }

        @Override
        public KineticBlockEntity getParentBlockEntity() {
            return this.parent;
        }

        protected boolean canPropagateDiagonally(IRotate block, BlockState state) {
            return true;
        }

        @Override
        public Component getKey() {
            return Component.translatable("loconautics.extra_kinetics.turntable_cog");
        }
    }
}
