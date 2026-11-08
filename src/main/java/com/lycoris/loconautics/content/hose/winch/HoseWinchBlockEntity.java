package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.grab.GrabConstraint;
import com.lycoris.loconautics.common.strand.RemovedSubLevels;
import com.lycoris.loconautics.common.strand.RopeGeometry;
import com.lycoris.loconautics.common.strand.RopePullTrace;
import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.common.strand.StrandHolder;
import com.lycoris.loconautics.common.strand.StrandSnap;
import com.lycoris.loconautics.content.hose.HoseFluidCarrier;
import com.lycoris.loconautics.content.hose.HoseHolderBehaviour;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandHolder;
import com.lycoris.loconautics.content.hose.HoseStrandManager;
import com.lycoris.loconautics.content.hose.flow.HosePressure;
import com.lycoris.loconautics.content.hose.flow.HoseSourceProbe;
import com.lycoris.loconautics.content.hose.flow.PressureProbe;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.content.hose.rope.HoseRope;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.portal.PortalTwins;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class HoseWinchBlockEntity extends GeneratingKineticBlockEntity implements HoseStrandHolder, HoseFluidCarrier {
    private static final float COIL_DEGREES_PER_RPM_TICK = 0.3F;
    private static final double TAUT_STRETCH = 1.02;
    private static final int FLOW_LINGER_TICKS = 3;
    private static final float FREE_SPIN_CAPACITY = 256.0F;
    private static final double FREE_SPIN_SLACK = 0.001;
    private static final double FREE_SPIN_GAIN = 30.0;
    private static final double FREE_SPIN_GRAB_GAIN = 0.5;
    private static final double FREE_SPIN_MAX_PAYOUT = 1.0;
    private static final double FREE_SPIN_PAYOUT_SMOOTH = 0.5;
    private static final int FREE_SPIN_APPLY_TICKS = 1;
    private static final float FREE_SPIN_APPLY_STEP = 0.5F;
    private static final float FREE_SPIN_LERP = 0.2F;
    private static final int FLOW_LOG_TICKS = 20;
    private HoseHolderBehaviour holder;
    private final IFluidHandler passThrough = new HoseWinchBlockEntity.PassThrough();
    private FluidStack flowFluid = FluidStack.EMPTY;
    private int flowTicks;
    private int flowRate = 1;
    private boolean pressureReaches;
    private boolean suctionReaches;
    private final PressureProbe pressureProbe = new PressureProbe();
    private final HoseSourceProbe sourceProbe = new HoseSourceProbe();
    private float sprayStrength;
    private float throwStrength;
    private float reach;
    private float lastSyncedThrow = -1.0F;
    private float lastSyncedReach = -1.0F;
    private FluidStack lastSyncedFluid = FluidStack.EMPTY;
    private float clientThrow;
    private float clientReach;
    private FluidStack clientFluid = FluidStack.EMPTY;
    private final LerpedFloat clientFill = LerpedFloat.linear().startWithValue(0.0);
    private float wavePhase;
    private float wavePhaseRate;
    private boolean clientHasStrand;
    private float clientLength;
    @Nullable
    private UUID clientStrandId;
    @Nullable
    private UUID clientNozzleSub;
    @Nullable
    private BlockPos clientNozzleLocal;
    @Nullable
    private float[] ropePtsIncoming;
    @Nullable
    private float[] ropePtsPrev;
    @Nullable
    private float[] ropePtsCurr;
    @Nullable
    private float[] pendingRopePoints;
    private float coilAngle;
    private float coilAnglePrev;
    private double smoothedPayout;
    private float freeSpinTarget;
    private float freeSpinRpm;
    private float appliedRpm;
    private long lastRpmApply;
    private static final double UNDOCK_STRETCH = 1.03;
    private static final double FREE_SPIN_UNDOCK_PAYOUT = 0.5;
    @Nullable
    private Vec3 lastNozzleLog;

    public HoseWinchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.holder = new HoseHolderBehaviour(this, true);
        behaviours.add(this.holder);
    }

    public void onWinchBroken() {
        if (this.level != null && !this.level.isClientSide && !this.isPortalTwin()) {
            HoseStrand strand = this.getAttachedStrand();
            if (strand != null) {
                this.snapAt(strand, false);
            }
        }
    }

    public void tick() {
        super.tick();
        if (this.level != null) {
            if (this.level.isClientSide) {
                HoseWinchKnotSource.track(this);
                this.coilAnglePrev = this.coilAngle;
                this.coilAngle = this.coilAngle + this.getSpeed() * 0.3F;
                float wrap = (int)(this.coilAngle / 360.0F) * 360.0F;
                this.coilAngle -= wrap;
                this.coilAnglePrev -= wrap;
                this.ropePtsPrev = this.ropePtsCurr;
                this.ropePtsCurr = this.ropePtsIncoming;
                if (this.clientReach <= 0.001F) {
                    this.clientFill.startWithValue(0.0);
                } else {
                    this.clientFill.chase(this.clientReach, 0.2, Chaser.LINEAR);
                    this.clientFill.tickChaser();
                }

                float period = 40.0F - 26.0F * Mth.clamp(this.clientThrow, 0.0F, 1.0F);
                this.wavePhaseRate = (float) (Math.PI * 2) / period;
                this.wavePhase = (float)((this.wavePhase + this.wavePhaseRate) % (Math.PI * 2));
            } else if (!this.isPortalTwin()) {
                HoseStrand strand = this.getAttachedStrand();
                if (strand == null) {
                    this.clearFlow();
                    this.freeSpinTarget = 0.0F;
                    this.syncFreeSpin();
                } else if (this.isNozzleGone(strand)) {
                    this.snapAt(strand, true);
                } else {
                    this.driveRope(strand);
                    HoseRopeManager ropeMgr = HoseRopeManager.get(this.level);
                    HoseRope driven = ropeMgr == null ? null : ropeMgr.get(strand.id);
                    if (driven != null && driven.isActive() && driven.tickSnap()) {
                        this.snapAt(strand, false);
                    } else {
                        this.syncFreeSpin();
                        this.updateFluidState(strand);
                        this.tryTensionUndock(strand);
                        HoseStrandManager mgr = HoseStrandManager.get(this.level);
                        if (mgr != null) {
                            mgr.touch();
                        }
                    }
                }
            }
        }
    }

    private boolean isPortalTwin() {
        return this.level instanceof ServerLevel server && PortalTwins.masterBlock(server, this.getBlockPos()) != null;
    }

    private void updateFluidState(HoseStrand strand) {
        float len = strand.currentLength;
        HosePressure.Pressure pressure = this.pressureProbe.read(this.level, this.getBlockPos(), this.fluidInputSide());
        float push = Math.abs(pressure.push());
        float pull = Math.abs(pressure.pull());
        float pump = Math.max(push, pull);
        this.reach = HosePressure.reach(pump);
        this.pressureReaches = HosePressure.reaches(push, len);
        this.suctionReaches = pull > 0.0F && HosePressure.reaches(pull, len);
        this.flowRate = Math.max(1, (int)(pump / 2.0F));
        HoseNozzleBlockEntity nozzle = this.resolveNozzle(strand);
        IFluidHandler linkSink = nozzle == null ? this.farLinkSink(strand) : null;
        boolean docked = nozzle != null ? nozzle.isDocked() : linkSink != null;
        if (this.flowTicks > 0) {
            this.flowTicks--;
        } else {
            this.flowFluid = FluidStack.EMPTY;
        }

        this.sprayStrength = 0.0F;
        if (docked) {
            this.reach = !this.pressureReaches && !this.suctionReaches ? 0.0F : len;
        } else if (this.pressureReaches) {
            this.sprayStrength = HosePressure.strength(pump, len);
        }

        this.throwStrength = this.flowFluid.isEmpty() ? 0.0F : this.sprayStrength;
        if (nozzle != null) {
            nozzle.setSuckPower(this.suctionReaches ? HosePressure.strength(pull, len) : 0.0F);
        }

        this.syncFluidState();
    }

    private void syncFluidState() {
        if (Math.abs(this.throwStrength - this.lastSyncedThrow) > 0.02F
            || Math.abs(this.reach - this.lastSyncedReach) > 0.1F
            || !FluidStack.isSameFluidSameComponents(this.flowFluid, this.lastSyncedFluid)) {
            this.lastSyncedThrow = this.throwStrength;
            this.lastSyncedReach = this.reach;
            this.lastSyncedFluid = this.flowFluid.copy();
            this.sendData();
        }
    }

    private void clearFlow() {
        this.pressureReaches = false;
        this.suctionReaches = false;
        this.sprayStrength = 0.0F;
        this.flowTicks = 0;
        this.flowFluid = FluidStack.EMPTY;
        this.reach = 0.0F;
        this.throwStrength = 0.0F;
        this.syncFluidState();
    }

    private void noteFlow(FluidStack moved) {
        this.flowFluid = moved.copyWithAmount(1);
        this.flowTicks = 3;
    }

    @Nullable
    private IFluidHandler heldSink() {
        HoseStrand strand = this.getAttachedStrand();
        if (strand == null) {
            return null;
        } else {
            HoseNozzleBlockEntity nozzle = this.resolveNozzle(strand);
            if (nozzle == null) {
                return this.farLinkSink(strand);
            } else {
                return nozzle.isDocked() ? nozzle.dockedFluidHandler() : nozzle.suctionHandler();
            }
        }
    }

    private boolean loopsBack(@Nullable IFluidHandler sink) {
        return this.level != null && this.sourceProbe.feeds(this.level, this.getBlockPos(), this.fluidInputSide(), sink);
    }

    private void logFillRefused(String why, FluidStack resource) {
        if (this.level != null && !this.level.isClientSide && this.level.getGameTime() % 20L == 0L) {
        }
    }

    private void tryTensionUndock(HoseStrand strand) {
        HoseNozzleBlockEntity nozzle = this.resolveNozzle(strand);
        if (nozzle != null && nozzle.isDocked() && this.level.getGameTime() % 5L == 0L) {
            HoseRopeManager dbg = HoseRopeManager.get(this.level);
            HoseRope dbgRope = dbg == null ? null : dbg.get(strand.id);
            LoconauticsConstants.LOGGER
                .debug(
                    "[undockdbg] winch tension canUndock={} stretch={} freeSpin={} payout={} ext={}",
                    new Object[]{
                        nozzle.canUndock(),
                        dbgRope == null ? "norope" : dbgRope.getStretchRatio(),
                        this.isFreeSpin(),
                        this.smoothedPayout,
                        dbgRope == null ? "norope" : dbgRope.getCurrentExtension()
                    }
                );
        }

        if (nozzle != null && nozzle.canUndock()) {
            HoseRopeManager mgr = HoseRopeManager.get(this.level);
            HoseRope rope = mgr == null ? null : mgr.get(strand.id);
            if (rope != null) {
                double stretch = rope.getStretchRatio();
                boolean freeSpinPull = this.isFreeSpin() && this.smoothedPayout >= 0.5 && stretch >= 1.0;
                if (stretch >= 1.03 || freeSpinPull) {
                    nozzle.undock(this.level.getGameTime());
                }
            }
        }
    }

    @Nullable
    private BlockEntity farEnd(HoseStrand strand) {
        return strand.nozzleLocalPos != null && this.level instanceof ServerLevel serverLevel
            ? StrandEndpoints.blockEntity(serverLevel, SubLevelContainer.getContainer(serverLevel), strand.nozzleSubLevelId, strand.nozzleLocalPos)
            : null;
    }

    @Nullable
    private IFluidHandler farLinkSink(HoseStrand strand) {
        BlockEntity far = this.farEnd(strand);
        return far == null ? null : HoseLinks.sinkFromWinchEnd(far);
    }

    @Nullable
    private HoseNozzleBlockEntity resolveNozzle(HoseStrand strand) {
        if (strand.nozzleSubLevelId != null && strand.nozzleLocalPos != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(this.level);
            return container != null
                    && container.getSubLevel(strand.nozzleSubLevelId) instanceof ServerSubLevel sub
                    && sub.getLevel().getBlockEntity(strand.nozzleLocalPos) instanceof HoseNozzleBlockEntity nozzle
                ? nozzle
                : null;
        } else {
            return null;
        }
    }

    public Direction fluidInputSide() {
        BlockState st = this.getBlockState();
        return st.hasProperty(BlockStateProperties.FACING) ? ((Direction)st.getValue(BlockStateProperties.FACING)).getOpposite() : Direction.UP;
    }

    @Override
    public IFluidHandler getHoseFluidHandler() {
        return this.passThrough;
    }

    private boolean isFreeSpin() {
        return this.getBlockState().hasProperty(HoseWinchBlock.POWERED) && (Boolean)this.getBlockState().getValue(HoseWinchBlock.POWERED);
    }

    public float getGeneratedSpeed() {
        return this.appliedRpm;
    }

    public float calculateAddedStressCapacity() {
        float capacity = this.appliedRpm == 0.0F ? 0.0F : 256.0F;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    private double freeSpinPayout(HoseRope rope) {
        double desired = rope.getExtension() + (rope.getPoints().size() - 2);
        double excess = rope.getCurrentExtension() - desired;
        return excess > 0.001 ? Math.min(1.0, (excess - 0.001) * 30.0) : 0.0;
    }

    private double grabPayout(HoseRope rope, @Nullable HoseNozzleBlockEntity nozzle) {
        if (nozzle != null && this.level instanceof ServerLevel serverLevel) {
            Vector3d var10 = this.winchWorld();
            double gap = 0.0;

            for (Entry<UUID, GrabConstraint> grab : nozzle.grabbers().entrySet()) {
                Player player = serverLevel.getPlayerByUUID(grab.getKey());
                if (player != null) {
                    gap = Math.max(gap, var10.distance(grab.getValue().goalFor(player)) - rope.getCurrentExtension());
                }
            }

            return gap > 0.001 ? Math.min(1.0, gap * 0.5) : 0.0;
        } else {
            return 0.0;
        }
    }

    private void syncFreeSpin() {
        this.freeSpinRpm = Mth.lerp(0.2F, this.freeSpinRpm, this.freeSpinTarget);
        if (this.freeSpinRpm < 1.0F) {
            this.freeSpinRpm = 0.0F;
        }

        boolean zeroFlip = this.freeSpinRpm == 0.0F != (this.appliedRpm == 0.0F);
        if ((zeroFlip || Math.abs(this.freeSpinRpm - this.appliedRpm) >= 0.5F) && this.level.getGameTime() - this.lastRpmApply >= 1L) {
            this.appliedRpm = this.freeSpinRpm;
            this.lastRpmApply = this.level.getGameTime();
            this.updateGeneratedRotation();
        }
    }

    private double getMovementSpeed(HoseStrand strand) {
        double linear = convertToLinear(this.getSpeed()) * strand.type.reelTierSpeedMultiplier();
        return Math.max(-0.49, Math.min(0.49, linear));
    }

    private void driveRope(HoseStrand strand) {
        HoseRopeManager mgr = HoseRopeManager.get(this.level);
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(this.level);
        if (mgr != null && system != null) {
            HoseRope rope = mgr.get(strand.id);
            if (rope == null) {
                UUID winchSub = this.winchSubLevelId();
                if (strand.points != null && strand.points.size() >= 2) {
                    rope = HoseRope.restore(
                        strand.id,
                        strand.type,
                        winchSub,
                        this.getBlockPos(),
                        strand.nozzleSubLevelId,
                        strand.nozzleLocalPos,
                        strand.points,
                        strand.extensionGoal
                    );
                } else {
                    rope = HoseRope.build(
                        strand.id,
                        strand.type,
                        winchSub,
                        this.getBlockPos(),
                        this.winchWorld(),
                        strand.nozzleSubLevelId,
                        strand.nozzleLocalPos,
                        this.nozzleWorld(strand),
                        Math.max(0.5, strand.currentLength)
                    );
                }

                mgr.add(rope);
            }

            if (rope.isActive()) {
                rope.updatePose();
                RopePullTrace.ends(rope, (ServerLevel)this.level);
            }

            boolean externalDrive = this.isFreeSpin() && this.appliedRpm == 0.0F && this.getSpeed() != 0.0F;
            boolean freeSpinning = this.isFreeSpin() && !externalDrive;
            double pull = freeSpinning ? Math.max(this.freeSpinPayout(rope), this.grabPayout(rope, this.resolveNozzle(strand))) : 0.0;
            this.smoothedPayout = Mth.lerp(0.5, this.smoothedPayout, pull);
            if (this.smoothedPayout < 0.002) {
                this.smoothedPayout = 0.0;
            }

            int maxRpm = (Integer)AllConfigs.server().kinetics.maxRotationSpeed.get();
            this.freeSpinTarget = Mth.clamp((float)(this.smoothedPayout / strand.type.reelTierSpeedMultiplier() * 512.0), 0.0F, maxRpm);
            double reelSpeed = freeSpinning ? this.smoothedPayout : this.getMovementSpeed(strand);
            if (reelSpeed < 0.0 && rope.getStretchRatio() > 1.02 && this.sharesBody(this.winchSubLevelId(), strand.nozzleSubLevelId)) {
                reelSpeed = 0.0;
            }

            int pointsBefore = rope.getPoints().size();
            rope.reel(reelSpeed, this.winchWorld());
            if (!freeSpinning && this.getSpeed() != 0.0F) {
                this.logReel(rope, strand, reelSpeed, pointsBefore);
            }

            if (!rope.isActive() && rope.areAttachmentsLoaded((ServerLevel)this.level) && system.getTicketManager().wouldBeLoaded(this.level, rope)) {
                system.addObject(rope);
            }

            if (rope.isActive()) {
                strand.currentLength = (float)Math.min(rope.getCurrentExtension(), strand.type.maxLength() + 1.0);
                strand.points = new ArrayList<>(rope.getPoints());
                strand.extensionGoal = rope.getExtension();
                if (rope.needsSync()) {
                    this.syncRopePoints(rope);
                    rope.justSynced();
                }
            }
        }
    }

    private boolean sharesBody(@Nullable UUID winchSub, @Nullable UUID nozzleSub) {
        return winchSub != null && nozzleSub != null && this.level instanceof ServerLevel serverLevel
            ? winchSub.equals(nozzleSub) || TrainConsist.connectedSubLevels(serverLevel, winchSub).contains(nozzleSub)
            : false;
    }

    private void logReel(HoseRope rope, HoseStrand strand, double reelSpeed, int pointsBefore) {
        ObjectList<Vector3d> pts = rope.getPoints();
        double maxSegment = 0.0;

        for (int i = 0; i < pts.size() - 1; i++) {
            maxSegment = Math.max(maxSegment, ((Vector3d)pts.get(i)).distance((Vector3dc)pts.get(i + 1)));
        }

        double firstSegment = pts.size() > 1 ? ((Vector3d)pts.get(0)).distance((Vector3dc)pts.get(1)) : 0.0;
        Vec3 nozzle = this.resolveNozzlePoint(strand);
        double nozzleStep = nozzle != null && this.lastNozzleLog != null ? nozzle.distanceTo(this.lastNozzleLog) : 0.0;
        this.lastNozzleLog = nozzle;
        LoconauticsConstants.LOGGER
            .debug(
                "[winchreel] t={} speed={} reel={} pts={}->{} goal={} desired={} cur={} stretch={} first={} maxSeg={} nozzleStep={} joint={} active={}",
                new Object[]{
                    this.level.getGameTime(),
                    this.getSpeed(),
                    reelSpeed,
                    pointsBefore,
                    pts.size(),
                    rope.getExtension(),
                    rope.getDesiredExtension(),
                    rope.getCurrentExtension(),
                    rope.getStretchRatio(),
                    firstSegment,
                    maxSegment,
                    nozzleStep,
                    rope.hasJoint(),
                    rope.isActive()
                }
            );
    }

    private Vector3d projectOut(double x, double y, double z) {
        Vec3 w = Sable.HELPER.projectOutOfSubLevel(this.level, new Vec3(x, y, z));
        return new Vector3d(w.x, w.y, w.z);
    }

    private Vector3d winchWorld() {
        Vec3 a = this.hoseAnchor();
        return this.projectOut(a.x, a.y, a.z);
    }

    @Nullable
    private UUID winchSubLevelId() {
        Vec3 a = this.hoseAnchor();
        SubLevel s = Sable.HELPER.getContaining(this.level, a);
        return s != null ? s.getUniqueId() : null;
    }

    private Vec3 hoseAnchor() {
        return Vec3.atCenterOf(this.getBlockPos());
    }

    public double spanTo(Vec3 farWorld) {
        Vec3 a = this.hoseAnchor();
        Vector3d world = this.projectOut(a.x, a.y, a.z);
        return world.distance(farWorld.x, farWorld.y, farWorld.z);
    }

    private Vector3d nozzleWorld(HoseStrand strand) {
        Vec3 p = this.resolveNozzlePoint(strand);
        return p != null ? new Vector3d(p.x, p.y, p.z) : this.winchWorld();
    }

    private void syncRopePoints(HoseRope rope) {
        ObjectList<Vector3d> pts = rope.getPoints();
        int n = pts.size();
        float[] rel = new float[n * 3];
        BlockPos base = this.getBlockPos();
        SubLevel own = Sable.HELPER.getContaining(this);
        Vector3d scratch = new Vector3d();

        for (int i = 0; i < n; i++) {
            scratch.set((Vector3dc)pts.get(i));
            if (own != null) {
                own.logicalPose().transformPositionInverse(scratch);
            }

            rel[i * 3] = (float)(scratch.x - base.getX());
            rel[i * 3 + 1] = (float)(scratch.y - base.getY());
            rel[i * 3 + 2] = (float)(scratch.z - base.getZ());
        }

        this.pendingRopePoints = rel;
        this.sendData();
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putFloat("Throw", this.throwStrength);
            tag.putFloat("Reach", this.reach);
            tag.put("FlowFluid", this.flowFluid.saveOptional(registries));
            HoseStrand strand = this.getAttachedStrand();
            tag.putBoolean("HasStrand", strand != null);
            if (strand != null) {
                tag.putFloat("Length", strand.currentLength);
                tag.putUUID("StrandId", strand.id);
                if (this.resolveNozzle(strand) != null) {
                    tag.putUUID("NozzleSub", strand.nozzleSubLevelId);
                    tag.putLong("NozzleLocal", strand.nozzleLocalPos.asLong());
                }
            }

            if (this.pendingRopePoints != null) {
                int[] packed = new int[this.pendingRopePoints.length];

                for (int i = 0; i < packed.length; i++) {
                    packed[i] = Float.floatToIntBits(this.pendingRopePoints[i]);
                }

                tag.putIntArray("RopePts", packed);
            }
        }
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            this.clientThrow = tag.getFloat("Throw");
            this.clientReach = tag.getFloat("Reach");
            this.clientFluid = tag.contains("FlowFluid") ? FluidStack.parseOptional(registries, tag.getCompound("FlowFluid")) : FluidStack.EMPTY;
            this.clientHasStrand = tag.getBoolean("HasStrand");
            this.clientLength = tag.getFloat("Length");
            this.clientStrandId = tag.hasUUID("StrandId") ? tag.getUUID("StrandId") : null;
            this.clientNozzleSub = tag.hasUUID("NozzleSub") ? tag.getUUID("NozzleSub") : null;
            this.clientNozzleLocal = tag.contains("NozzleLocal") ? BlockPos.of(tag.getLong("NozzleLocal")) : null;
            if (tag.contains("RopePts")) {
                int[] packed = tag.getIntArray("RopePts");
                float[] pts = new float[packed.length];

                for (int i = 0; i < packed.length; i++) {
                    pts[i] = Float.intBitsToFloat(packed[i]);
                }

                this.ropePtsIncoming = pts;
            }

            if (!this.clientHasStrand) {
                this.ropePtsIncoming = this.ropePtsCurr = this.ropePtsPrev = null;
            }
        }
    }

    public float coilAngleRadians(float partialTicks) {
        return Mth.lerp(partialTicks, this.coilAnglePrev, this.coilAngle) * (float) (Math.PI / 180.0);
    }

    public boolean clientHasStrand() {
        return this.clientHasStrand;
    }

    public float clientThrow() {
        return this.clientThrow;
    }

    public boolean clientHasFluid() {
        return !this.clientFluid.isEmpty();
    }

    public float clientReach() {
        return this.clientReach;
    }

    public float wavePhase(float partialTicks) {
        return this.wavePhase + this.wavePhaseRate * partialTicks;
    }

    public float clientFill(float partialTicks) {
        return this.clientFill.getValue(partialTicks);
    }

    public float clientLength() {
        return this.clientLength;
    }

    @Nullable
    public UUID clientStrandId() {
        return this.clientStrandId;
    }

    @Nullable
    public UUID clientNozzleSub() {
        return this.clientNozzleSub;
    }

    @Nullable
    public BlockPos clientNozzleLocal() {
        return this.clientNozzleLocal;
    }

    @Nullable
    public List<Vec3> clientRopePoints(float partialTicks) {
        return RopeGeometry.interpolateRopePoints(this.ropePtsCurr, this.ropePtsPrev, this.getBlockPos(), partialTicks);
    }

    public AABB getRenderBoundingBox() {
        return RopeGeometry.renderBounds(this.ropePtsCurr, this.getBlockPos(), 1.0);
    }

    private boolean isNozzleGone(HoseStrand strand) {
        if (strand.nozzleLocalPos != null && this.level instanceof ServerLevel serverLevel) {
            ServerSubLevelContainer var4 = SubLevelContainer.getContainer(serverLevel);
            if (RemovedSubLevels.contains(strand.nozzleSubLevelId)) {
                return true;
            } else {
                return !StrandEndpoints.isLoaded(serverLevel, var4, strand.nozzleSubLevelId, strand.nozzleLocalPos)
                    ? false
                    : !(this.farEnd(strand) instanceof StrandHolder);
            }
        } else {
            return false;
        }
    }

    private void snapAt(HoseStrand strand, boolean farEnd) {
        if (this.level instanceof ServerLevel slevel) {
            HoseRopeManager ropes = HoseRopeManager.get(this.level);
            HoseRope rope = ropes == null ? null : ropes.get(strand.id);
            List<Vector3d> pts = rope != null ? List.copyOf(rope.getPoints()) : strand.points;
            Vector3d at = StrandSnap.endOf(pts, !farEnd);
            if (at == null) {
                Vec3 fallback = farEnd ? this.resolveNozzlePoint(strand) : null;
                fallback = fallback != null ? fallback : Vec3.atCenterOf(this.getBlockPos());
                at = new Vector3d(fallback.x, fallback.y, fallback.z);
            }

            StrandSnap.snap(slevel, new ItemStack((ItemLike)LoconauticsRegistries.HOSE.get()), pts, at);
            HoseNozzleBlockEntity nozzle = this.resolveNozzle(strand);
            if (nozzle != null) {
                nozzle.onStrandDetached();
            }

            HoseStrandManager mgr = HoseStrandManager.get(this.level);
            if (mgr != null) {
                mgr.remove(strand.id);
            }

            this.removeRope(strand.id);
            this.onStrandDetached();
        }
    }

    private void removeRope(UUID id) {
        if (this.level != null && !this.level.isClientSide) {
            HoseRopeManager ropes = HoseRopeManager.get(this.level);
            if (ropes != null) {
                ropes.remove(SubLevelPhysicsSystem.get(this.level), id);
            }
        }
    }

    @Nullable
    private Vec3 resolveNozzlePoint(HoseStrand strand) {
        if (strand.nozzleLocalPos != null && this.level instanceof ServerLevel serverLevel) {
            Vector3d point = StrandEndpoints.worldPoint(
                serverLevel, SubLevelContainer.getContainer(serverLevel), strand.nozzleSubLevelId, strand.nozzleLocalPos
            );
            return point == null ? null : new Vec3(point.x, point.y, point.z);
        } else {
            return null;
        }
    }

    @Override
    public Vec3 getAttachmentPoint() {
        return this.hoseAnchor();
    }

    @Override
    public boolean canAttachStrand() {
        return this.holder != null && this.holder.isFree();
    }

    @Override
    public void onStrandAttached(HoseStrand strand) {
        this.holder.setAttachedStrandId(strand.id);
        this.setHasCoil(true);
        this.setChanged();
        this.sendData();
    }

    @Override
    public void onStrandDetached() {
        if (this.holder != null && this.holder.getAttachedStrandId() != null) {
            this.holder.setAttachedStrandId(null);
            this.setHasCoil(false);
            this.setChanged();
            this.sendData();
        }
    }

    private void setHasCoil(boolean hasCoil) {
        if (this.level != null && !this.level.isClientSide) {
            BlockState st = this.level.getBlockState(this.getBlockPos());
            if (st.getBlock() instanceof HoseWinchBlock && (Boolean)st.getValue(HoseWinchBlock.HAS_COIL) != hasCoil) {
                this.level.setBlock(this.getBlockPos(), (BlockState)st.setValue(HoseWinchBlock.HAS_COIL, hasCoil), 3);
            }
        }
    }

    @Nullable
    @Override
    public HoseStrand getAttachedStrand() {
        return this.holder == null ? null : this.holder.getAttachedStrand();
    }

    private class PassThrough implements IFluidHandler {
        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int tank) {
            return HoseWinchBlockEntity.this.flowFluid;
        }

        public int getTankCapacity(int tank) {
            IFluidHandler sink = HoseWinchBlockEntity.this.heldSink();
            return sink != null ? sink.getTankCapacity(0) : HoseWinchBlockEntity.this.flowRate;
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler sink = HoseWinchBlockEntity.this.heldSink();
            return sink == null || sink.isFluidValid(0, stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            if (HoseWinchBlockEntity.this.pressureReaches && !resource.isEmpty()) {
                HoseStrand strand = HoseWinchBlockEntity.this.getAttachedStrand();
                if (strand == null) {
                    HoseWinchBlockEntity.this.logFillRefused("no strand", resource);
                    return 0;
                }

                HoseNozzleBlockEntity nozzle = HoseWinchBlockEntity.this.resolveNozzle(strand);
                IFluidHandler sink = nozzle == null ? HoseWinchBlockEntity.this.farLinkSink(strand) : null;
                if (HoseWinchBlockEntity.this.loopsBack(nozzle != null ? nozzle.dockedFluidHandler() : sink)) {
                    return 0;
                }

                int moved = nozzle != null
                    ? nozzle.receive(resource, HoseWinchBlockEntity.this.sprayStrength, action)
                    : (sink == null ? 0 : sink.fill(resource, action));
                if (moved <= 0) {
                    HoseWinchBlockEntity.this.logFillRefused(
                        nozzle != null ? "nozzle took 0, spray=" + HoseWinchBlockEntity.this.sprayStrength : "no sink", resource
                    );
                }

                if (moved > 0 && action.execute()) {
                    HoseWinchBlockEntity.this.noteFlow(resource);
                }

                return moved;
            } else {
                HoseWinchBlockEntity.this.logFillRefused("pressure", resource);
                return 0;
            }
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler sink = HoseWinchBlockEntity.this.suctionReaches ? HoseWinchBlockEntity.this.heldSink() : null;
            if (sink != null && !HoseWinchBlockEntity.this.loopsBack(sink)) {
                FluidStack out = sink.drain(resource, action);
                if (!out.isEmpty() && action.execute()) {
                    HoseWinchBlockEntity.this.noteFlow(out);
                }

                return out;
            } else {
                return FluidStack.EMPTY;
            }
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler sink = HoseWinchBlockEntity.this.suctionReaches ? HoseWinchBlockEntity.this.heldSink() : null;
            if (sink != null && !HoseWinchBlockEntity.this.loopsBack(sink)) {
                FluidStack out = sink.drain(Math.min(maxDrain, HoseWinchBlockEntity.this.flowRate), action);
                if (!out.isEmpty() && action.execute()) {
                    HoseWinchBlockEntity.this.noteFlow(out);
                }

                return out;
            } else {
                return FluidStack.EMPTY;
            }
        }
    }
}
