package com.lycoris.loconautics.content.hose.nozzle;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.common.grab.GrabConstraint;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.common.strand.StrandAnchors;
import com.lycoris.loconautics.common.strand.StrandAttachments;
import com.lycoris.loconautics.content.hatch.NozzleDockService;
import com.lycoris.loconautics.content.hose.HoseHolderBehaviour;
import com.lycoris.loconautics.content.hose.HoseStrand;
import com.lycoris.loconautics.content.hose.HoseStrandHolder;
import com.lycoris.loconautics.content.hose.flow.PressuredSink;
import com.lycoris.loconautics.content.hose.link.HoseLinkEndpoint;
import com.lycoris.loconautics.content.hose.rope.HoseRope;
import com.lycoris.loconautics.content.hose.rope.HoseRopeManager;
import com.lycoris.loconautics.content.hose.spray.HoseSprayColors;
import com.lycoris.loconautics.content.hose.spray.HoseSprayOptions;
import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntity;
import com.lycoris.loconautics.content.hose.spray.interaction.HoseSprayInteractions;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRope;
import com.lycoris.loconautics.content.steelcable.rope.SteelCableRopeManager;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.mojang.blaze3d.platform.NativeImage;
import com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler;
import com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour;
import com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class HoseNozzleBlockEntity extends SmartBlockEntity implements HoseStrandHolder, HoseLinkEndpoint, BlockEntitySubLevelActor, GrabbableActor {
    private static final int UNDOCK_SAFETY_TICKS = 10;
    private static final int REDOCK_COOLDOWN_TICKS = 10;
    private static final Set<HoseNozzleBlockEntity> CLIENT_NOZZLES = new HashSet<>();
    private static final int LINK_SPRAY_FLOW = 64;
    private static final int LINK_FLOW_LINGER_TICKS = 3;
    private static final float LINK_SPRAY_STRENGTH = 0.5F;
    private static final int SUCTION_TANK_CAPACITY = 1500;
    private static final double SUCTION_ROOT_SLACK_SQ = 4.0;
    private static final double SUCTION_BOTTOM_INSET = 0.0625;
    private static final double[] TRACE_PROBE_INSETS = new double[]{0.0625, -0.1, -0.25, -0.5, -1.0, -1.5};
    private HoseHolderBehaviour holder;
    private FluidDrainingBehaviour drainer;
    private FluidFillingBehaviour filler;
    private final SmartFluidTank suctionTank = new SmartFluidTank(1500, stack -> this.setChanged());
    private final IFluidHandler suctionHandler;
    @Nullable
    private BlockPos suctionRoot;
    private final Map<UUID, GrabConstraint> grabbers = new HashMap<>();
    @Nullable
    private IDockable dockedTarget;
    @Nullable
    private UUID hatchSubLevelId;
    private final Vector3d dockPoint = new Vector3d();
    private final Quaterniond dockOrientation = new Quaterniond();
    private final Vector3d dockInitialRelPos = new Vector3d();
    private final Quaterniond dockInitialRelOrient = new Quaterniond();
    private final Quaterniond dockTargetOrient = new Quaterniond();
    private boolean dockCaptured;
    private double dockLerp;
    private static final double DOCK_LERP_STEP = 0.04;
    @Nullable
    private PhysicsConstraintHandle dockConstraint;
    private int undockSafety;
    private long redockReadyTick;
    private final IFluidHandler linkSinkHandler = new HoseNozzleBlockEntity.LinkSink();
    private FluidStack linkFluid = FluidStack.EMPTY;
    private long linkFlowTick;
    private float linkStrength;
    private boolean linkSprayActive;
    private float sprayReaction;
    private long sprayReactionTick;
    private float sprayThrow;
    private FluidStack sprayFluid = FluidStack.EMPTY;
    private float lastSyncedSprayThrow = -1.0F;
    private FluidStack lastSyncedSprayFluid = FluidStack.EMPTY;
    private float clientSprayThrow;
    private FluidStack clientSprayFluid = FluidStack.EMPTY;
    private final List<UUID> sprayEntityIds = new ArrayList<>();
    private static final int SUCK_LINGER_TICKS = 3;
    private static final double SUCK_MIN_RANGE = 0.5;
    private static final double SUCK_MAX_RANGE = 3.0;
    private static final double SUCK_TIP_RADIUS = 0.12;
    private FluidStack suckFluid = FluidStack.EMPTY;
    private long suckTick;
    private float suckPower;
    private FluidStack lastSyncedSuckFluid = FluidStack.EMPTY;
    private float lastSyncedSuckStrength;
    private FluidStack clientSuckFluid = FluidStack.EMPTY;
    private float clientSuckStrength;
    private static final int[] WATER_COLORS = new int[]{-6697729, -7815435, -5317633, -8406800};
    private static final Map<ResourceLocation, int[]> FLUID_COLOR_CACHE = new HashMap<>();
    private static final double RECOIL_DELTA_V = 0.5;
    private static final int SPRAY_INTERVAL_TICKS = 5;

    public HoseNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.suctionHandler = new HosePulleyFluidHandler(this.suctionTank, this.filler, this.drainer, this::suctionRoot, () -> !this.isDocked());
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        this.holder = new HoseHolderBehaviour(this, false);
        behaviours.add(this.holder);
        behaviours.add(new SteelCableHolderBehaviour(this, this.strandCapacity()));
        this.drainer = new FluidDrainingBehaviour(this);
        this.filler = new FluidFillingBehaviour(this);
        behaviours.add(this.drainer);
        behaviours.add(this.filler);
    }

    @Nullable
    public IFluidHandler suctionHandler() {
        if (!this.isDocked() && this.level != null && !this.level.isClientSide) {
            return this.fluidHit() == null ? null : this.suctionHandler;
        } else {
            return null;
        }
    }

    @Nullable
    private HoseNozzleBlockEntity.FluidHit fluidHit() {
        return this.fluidHit(0.0625);
    }

    @Nullable
    private HoseNozzleBlockEntity.FluidHit fluidHit(double inset) {
        BlockPos lp = this.getBlockPos();
        SubLevel own = Sable.HELPER.getContaining(this.level, lp);
        Vec3 probe = new Vec3(lp.getX() + 0.5, lp.getY() + inset, lp.getZ() + 0.5);
        return Sable.HELPER
            .runIncludingSubLevels(
                this.level,
                probe,
                own == null,
                own,
                (sub, pos) -> this.level.getFluidState(pos).isEmpty() ? null : new HoseNozzleBlockEntity.FluidHit(sub, pos)
            );
    }

    private BlockPos suctionRoot() {
        HoseNozzleBlockEntity.FluidHit hit = this.fluidHit();
        if (hit == null) {
            return this.suctionRoot != null ? this.suctionRoot : this.getBlockPos();
        }

        if (this.suctionRoot != null && !this.level.getFluidState(this.suctionRoot).isEmpty() && this.suctionRoot.distSqr(hit.pos()) <= 4.0) {
            return this.suctionRoot;
        }

        this.suctionRoot = hit.pos();
        return this.suctionRoot;
    }

    public void tick() {
        super.tick();
        if (this.level instanceof ServerLevel) {
            this.tickLinkSpray(this.level.getGameTime());
            this.tickSuction(this.level.getGameTime());
        } else if (this.level != null && this.level.isClientSide) {
            SubLevel containing = Sable.HELPER.getContaining(this.level, this.getBlockPos());
            if (containing instanceof ClientSubLevel sub) {
                this.emitSprayClient(sub);
            }

            this.emitSuctionClient(containing instanceof ClientSubLevel sub ? sub : null);
        }
    }

    public void setSuckPower(float power) {
        this.suckPower = power;
    }

    private void tickSuction(long gameTime) {
        boolean sucking = !this.isDocked() && !this.suckFluid.isEmpty() && gameTime - this.suckTick <= 3L;
        FluidStack shown = sucking ? this.suckFluid : FluidStack.EMPTY;
        float strength = sucking ? Math.max(this.suckPower, 0.15F) : 0.0F;
        if (!FluidStack.isSameFluidSameComponents(shown, this.lastSyncedSuckFluid) || Math.abs(strength - this.lastSyncedSuckStrength) > 0.05F) {
            this.lastSyncedSuckFluid = shown.copy();
            this.lastSyncedSuckStrength = strength;
            if (!sucking) {
                this.suckFluid = FluidStack.EMPTY;
            }

            this.sendData();
        }
    }

    private void tickLinkSpray(long gameTime) {
        boolean flowing = !this.isDocked() && !this.linkFluid.isEmpty() && gameTime - this.linkFlowTick <= 3L;
        if (flowing) {
            this.linkSprayActive = true;
            this.driveSprayServer(this.linkFluid, this.linkStrength, true);
        } else if (this.linkSprayActive) {
            this.linkSprayActive = false;
            this.linkFluid = FluidStack.EMPTY;
            this.driveSprayServer(FluidStack.EMPTY, 0.0F, false);
        }
    }

    @Override
    public IFluidHandler linkSink() {
        return this.linkSinkHandler;
    }

    public int receive(FluidStack resource, float strength, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }

        if (this.isDocked()) {
            IFluidHandler docked = this.dockedFluidHandler();
            return docked == null ? 0 : docked.fill(resource, action);
        }

        if (strength <= 0.0F) {
            return 0;
        }

        int moved = Math.min(resource.getAmount(), 64);
        if (moved > 0 && action.execute() && this.level != null) {
            this.linkFluid = resource.copyWithAmount(1);
            this.linkStrength = strength;
            this.linkFlowTick = this.level.getGameTime();
        }

        return moved;
    }

    @Override
    public void updateGrab(UUID player, float range, boolean rotating, Quaterniondc orientation) {
        if (!this.isDocked()) {
            GrabConstraint c = this.grabbers.get(player);
            if (c == null) {
                c = new GrabConstraint(player, range);
                this.grabbers.put(player, c);
            } else {
                c.setScrollDistance(range);
            }

            c.setRotation(rotating, orientation);
        }
    }

    @Override
    public boolean canGrab() {
        return !this.isDocked();
    }

    @Override
    public Map<UUID, GrabConstraint> grabbers() {
        return this.grabbers;
    }

    public boolean isDocked() {
        return this.dockedTarget != null;
    }

    @Nullable
    public IFluidHandler dockedFluidHandler() {
        return this.dockedTarget == null ? null : this.dockedTarget.getDockedFluidHandler();
    }

    @Nullable
    public UUID dockHatchSubLevelId() {
        return this.hatchSubLevelId;
    }

    public boolean canUndock() {
        return this.isDocked() && this.undockSafety <= 0;
    }

    public boolean canRedock(long gameTime) {
        return gameTime >= this.redockReadyTick;
    }

    public void dock(IDockable hatch, @Nullable SubLevel hatchSub, Vec3 anchor, Quaterniondc orientation) {
        if (!this.isDocked()) {
            this.clearGrabs();
            this.dockedTarget = hatch;
            this.hatchSubLevelId = hatchSub == null ? null : hatchSub.getUniqueId();
            this.dockPoint.set(anchor.x, anchor.y, anchor.z);
            this.dockOrientation.set(orientation);
            this.dockCaptured = false;
            this.undockSafety = 10;
            if (this.level != null) {
                NozzleDockSounds.docked(this.level, this.worldPosition);
            }

            this.setChanged();
        }
    }

    public void forceUndock() {
        this.undockSafety = 0;
        this.undock(this.level == null ? 0L : this.level.getGameTime());
    }

    public void undock(long gameTime) {
        if (this.canUndock()) {
            this.releaseDockConstraint();
            if (this.dockedTarget != null) {
                this.dockedTarget.onUndocked();
                this.dockedTarget = null;
            }

            this.hatchSubLevelId = null;
            if (this.level != null) {
                NozzleDockSounds.undocked(this.level, this.worldPosition);
            }

            this.redockReadyTick = gameTime + 10L;
            this.setChanged();
        }
    }

    public void invalidate() {
        super.invalidate();
        NozzleDockService.unregister(this);
        CLIENT_NOZZLES.remove(this);
    }

    public void initialize() {
        super.initialize();
        if (this.level != null && this.level.isClientSide) {
            CLIENT_NOZZLES.add(this);
        }
    }

    public static boolean clientNozzleAt(Vector3d world) {
        for (HoseNozzleBlockEntity nozzle : CLIENT_NOZZLES) {
            if (nozzle.level != null) {
                Vec3 anchor = nozzle.getAttachmentPoint();
                Vec3 at = Sable.HELPER.projectOutOfSubLevel(nozzle.level, anchor);
                if (at.distanceToSqr(world.x, world.y, world.z) < 0.09) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (subLevel.getLevel() instanceof ServerLevel level) {
            NozzleDockService.register(this);
            if (this.dockedTarget != null && !this.dockedTarget.isDockPresent()) {
                this.forceUndock();
            }

            this.setPowered(subLevel, this.isDocked());
            this.traceFloat(subLevel, handle);
            if (this.isDocked()) {
                if (this.undockSafety > 0) {
                    this.undockSafety--;
                }

                this.maintainDockConstraint(subLevel);
            } else {
                this.applySprayRecoil(subLevel, handle);
                this.driveGrabs(subLevel, level);
            }
        }
    }

    private void traceFloat(ServerSubLevel subLevel, RigidBodyHandle handle) {
        if (subLevel.getLevel().getGameTime() % 20L == 0L && handle != null && handle.isValid()) {
            HoseNozzleBlockEntity.FluidHit hit = null;
            double probeInset = 0.0;

            for (double inset : TRACE_PROBE_INSETS) {
                hit = this.fluidHit(inset);
                if (hit != null) {
                    probeInset = inset;
                    break;
                }
            }

            MassData mass = subLevel.getMassTracker();
            Vector3d velocity = handle.getLinearVelocity(new Vector3d());
            String column = "none";
            if (hit != null) {
                BlockPos top = hit.pos();

                while (!this.level.getFluidState(top.above()).isEmpty()) {
                    top = top.above();
                }

                BlockPos bottom = hit.pos();

                while (!this.level.getFluidState(bottom.below()).isEmpty()) {
                    bottom = bottom.below();
                }

                double surfaceLocal = top.getY() + this.level.getFluidState(top).getHeight(this.level, top);
                Vector3d surface = new Vector3d(top.getX() + 0.5, surfaceLocal, top.getZ() + 0.5);
                if (hit.sub() != null) {
                    hit.sub().logicalPose().transformPosition(surface);
                }

                BlockPos lp = this.getBlockPos();
                double bottomWorldY = subLevel.logicalPose().transformPosition(new Vector3d(lp.getX() + 0.5, lp.getY(), lp.getZ() + 0.5)).y;
                column = "probe="
                    + probeInset
                    + " depth="
                    + (top.getY() - bottom.getY() + 1)
                    + " floor="
                    + this.level.getBlockState(bottom.below())
                    + " surfaceWorldY="
                    + surface.y
                    + " bottomWorldY="
                    + bottomWorldY
                    + " gap="
                    + (bottomWorldY - surface.y);
            }

            LoconauticsConstants.LOGGER
                .debug(
                    "[hosefloat] vy={} speed={} mass={} volume={} hitSub={} hitPos={} column=[{}] ropes=[{}] docked={}",
                    new Object[]{
                        velocity.y,
                        velocity.length(),
                        mass == null ? null : mass.getMass(),
                        PhysicsBlockPropertyHelper.getVolume(this.getBlockState()),
                        hit == null ? null : (hit.sub() == null ? "world" : hit.sub().getUniqueId()),
                        hit == null ? null : hit.pos(),
                        column,
                        this.ropeTrace(),
                        this.isDocked()
                    }
                );
        }
    }

    private String ropeTrace() {
        StringBuilder out = new StringBuilder();
        HoseRopeManager hoses = HoseRopeManager.get(this.level);
        if (this.holder != null && this.holder.getAttachedStrandId() != null && hoses != null) {
            HoseRope rope = hoses.get(this.holder.getAttachedStrandId());
            out.append("hose stretch=").append(rope == null ? "none" : rope.getStretchRatio()).append(' ');
        }

        SteelCableRopeManager cables = SteelCableRopeManager.get(this.level);
        SteelCableHolderBehaviour cableHolder = (SteelCableHolderBehaviour)this.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (cableHolder != null && cables != null) {
            for (UUID id : cableHolder.attachedStrandIds()) {
                SteelCableRope rope = cables.get(id);
                out.append("link taut=")
                    .append(rope == null ? "none" : rope.isTaut())
                    .append(" ext=")
                    .append(rope == null ? "none" : rope.getCurrentExtension())
                    .append(" goal=")
                    .append(rope == null ? "none" : rope.getExtension())
                    .append(' ');
            }
        }

        return out.toString().trim();
    }

    public void setSprayReaction(float reaction) {
        this.sprayReaction = reaction;
        if (this.level != null) {
            this.sprayReactionTick = this.level.getGameTime();
        }
    }

    private void applySprayRecoil(ServerSubLevel subLevel, RigidBodyHandle handle) {
        if (!(this.sprayReaction <= 0.0F) && handle != null && handle.isValid()) {
            if (subLevel.getLevel().getGameTime() - this.sprayReactionTick > 2L) {
                this.sprayReaction = 0.0F;
            } else {
                MassData mass = subLevel.getMassTracker();
                if (mass != null && !mass.isInvalid()) {
                    Vector3dc com = mass.getCenterOfMass();
                    if (com != null) {
                        double phase = subLevel.getLevel().getGameTime() * 0.9;
                        double pulse = 0.75 + 0.25 * Math.sin(phase);
                        double impulse = mass.getMass() * 0.5 * this.sprayReaction * pulse;
                        handle.applyImpulseAtPoint(new Vector3d(com), new Vector3d(0.0, impulse, 0.0));
                    }
                }
            }
        }
    }

    private void releaseDockConstraint() {
        if (this.dockConstraint != null) {
            if (this.dockConstraint.isValid()) {
                this.dockConstraint.remove();
            }

            this.dockConstraint = null;
        }
    }

    private void maintainDockConstraint(ServerSubLevel subLevel) {
        if (SubLevelContainer.getContainer(subLevel.getLevel()) instanceof ServerSubLevelContainer container) {
            PhysicsPipelineBody var8 = null;
            if (this.hatchSubLevelId != null) {
                if (!(container.getSubLevel(this.hatchSubLevelId) instanceof PhysicsPipelineBody body) || body.isRemoved()) {
                    this.releaseDockConstraint();
                    return;
                }

                var8 = body;
            }

            if (!this.dockCaptured) {
                this.captureDockPose(subLevel, var8);
                this.dockCaptured = true;
            }

            if (!(this.dockLerp >= 1.0) || this.dockConstraint == null || !this.dockConstraint.isValid()) {
                this.dockLerp = Math.min(1.0, this.dockLerp + 0.04);
                this.releaseDockConstraint();
                Vector3d anchorB = this.getGrabCenter().fma(1.0 - this.dockLerp, this.dockInitialRelPos);
                double rotLerp = Math.min(1.0, this.dockLerp * 2.0);
                Quaterniond orient = new Quaterniond(this.dockInitialRelOrient).slerp(this.dockTargetOrient, rotLerp);
                this.dockConstraint = container.physicsSystem()
                    .getPipeline()
                    .addConstraint(var8, subLevel, new FixedConstraintConfiguration(new Vector3d(this.dockPoint), anchorB, orient));
            }
        }
    }

    private void captureDockPose(ServerSubLevel nozzleSub, @Nullable PhysicsPipelineBody hatchBody) {
        Vector3d worldA = new Vector3d(this.dockPoint);
        Quaterniond orientA = new Quaterniond();
        if (hatchBody instanceof SubLevel hatchSub) {
            hatchSub.logicalPose().transformPosition(new Vector3d(this.dockPoint), worldA);
            orientA.set(hatchSub.logicalPose().orientation());
        }

        Vector3d worldB = nozzleSub.logicalPose().transformPosition(this.getGrabCenter(), new Vector3d());
        Quaterniond orientB = new Quaterniond(nozzleSub.logicalPose().orientation());
        orientB.transformInverse(worldA.sub(worldB, new Vector3d()), this.dockInitialRelPos);
        this.dockInitialRelOrient.set(new Quaterniond(orientA).conjugate().mul(orientB));
        this.dockTargetOrient.set(this.dockOrientation);
        this.dockLerp = 0.0;
    }

    private void setPowered(ServerSubLevel subLevel, boolean powered) {
        BlockState st = subLevel.getLevel().getBlockState(this.getBlockPos());
        if (st.hasProperty(HoseNozzleBlock.POWERED) && (Boolean)st.getValue(HoseNozzleBlock.POWERED) != powered) {
            subLevel.getLevel().setBlock(this.getBlockPos(), (BlockState)st.setValue(HoseNozzleBlock.POWERED, powered), 3);
        }
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return List.of();
    }

    public void driveSprayServer(FluidStack fluid, float throwStrength, boolean leaking) {
        this.sprayFluid = fluid == null ? FluidStack.EMPTY : fluid;
        this.sprayThrow = leaking ? throwStrength : 0.0F;
        this.setSprayReaction(leaking ? throwStrength : 0.0F);
        if (leaking && throwStrength > 0.0F) {
            this.manageSpray(throwStrength);
        } else {
            this.killSpray();
        }

        if (Math.abs(this.sprayThrow - this.lastSyncedSprayThrow) > 0.02F
            || Math.abs(this.sprayFluid.getAmount() - this.lastSyncedSprayFluid.getAmount()) > 50
            || !FluidStack.isSameFluidSameComponents(this.sprayFluid, this.lastSyncedSprayFluid)) {
            this.lastSyncedSprayThrow = this.sprayThrow;
            this.lastSyncedSprayFluid = this.sprayFluid.copy();
            this.setChanged();
            this.sendData();
        }
    }

    private void manageSpray(float power) {
        if (this.level instanceof ServerLevel slevel) {
            if (slevel.getGameTime() % 5L == 0L && HoseSprayInteractions.get(this.sprayFluid) != null) {
                this.spawnSprayDroplet(slevel, power);
            }
        }
    }

    private void spawnSprayDroplet(ServerLevel slevel, float power) {
        BlockPos lp = this.getBlockPos();
        double cx = lp.getX() + 0.5;
        double cz = lp.getZ() + 0.5;
        Vector3d o = this.projectOut(cx, lp.getY() - 0.0625, cz);
        Vector3d b = this.projectOut(cx, lp.getY() - 0.5, cz);
        Vec3 origin = new Vec3(o.x, o.y, o.z);
        Vec3 dir = new Vec3(b.x - o.x, b.y - o.y, b.z - o.z);
        this.pruneDeadDroplets(slevel);
        UUID attacker = this.grabbers.keySet().stream().findFirst().orElse(null);
        this.sprayEntityIds.add(HoseSprayEntity.launch(slevel, origin, dir, this.sprayFluid, power, attacker).getUUID());
    }

    private void killSpray() {
        if (!this.sprayEntityIds.isEmpty()) {
            if (this.level instanceof ServerLevel slevel) {
                for (UUID id : this.sprayEntityIds) {
                    if (slevel.getEntity(id) instanceof HoseSprayEntity e) {
                        e.discard();
                    }
                }
            }

            this.sprayEntityIds.clear();
        }
    }

    private void pruneDeadDroplets(ServerLevel slevel) {
        this.sprayEntityIds.removeIf(id -> !(slevel.getEntity(id) instanceof HoseSprayEntity e && e.isAlive()));
    }

    private Vector3d projectOut(double x, double y, double z) {
        Vec3 w = Sable.HELPER.projectOutOfSubLevel(this.level, new Vec3(x, y, z));
        return new Vector3d(w.x, w.y, w.z);
    }

    public void emitSprayClient(ClientSubLevel sub) {
        if (this.level != null && this.level.isClientSide && sub != null) {
            if (!(this.clientSprayThrow <= 0.0F) && !this.clientSprayFluid.isEmpty()) {
                BlockState st = this.getBlockState();
                if (!st.hasProperty(HoseNozzleBlock.POWERED) || !(Boolean)st.getValue(HoseNozzleBlock.POWERED)) {
                    BlockPos lp = this.getBlockPos();
                    double cx = lp.getX() + 0.5;
                    double cz = lp.getZ() + 0.5;
                    double yBase = lp.getY();
                    Vector3d spawn = sub.renderPose().transformPosition(new Vector3d(cx, yBase - 0.0625, cz), new Vector3d());
                    Vector3d belowMore = sub.renderPose().transformPosition(new Vector3d(cx, yBase - 0.5, cz), new Vector3d());
                    Vector3d down = belowMore.sub(spawn, new Vector3d());
                    if (down.lengthSquared() > 1.0E-9) {
                        down.normalize();
                    }

                    this.emitFluidJet(spawn, down, this.jetColors());
                }
            }
        }
    }

    private void emitSuctionClient(@Nullable ClientSubLevel sub) {
        if (this.level != null && !this.clientSuckFluid.isEmpty() && !this.isDocked()) {
            BlockPos lp = this.getBlockPos();
            Vector3d tip = new Vector3d(lp.getX() + 0.5, lp.getY() - 0.0625, lp.getZ() + 0.5);
            Vector3d below = new Vector3d(lp.getX() + 0.5, lp.getY() - 0.5, lp.getZ() + 0.5);
            if (sub != null) {
                sub.renderPose().transformPosition(tip);
                sub.renderPose().transformPosition(below);
            }

            Vector3d axis = below.sub(tip, new Vector3d());
            if (!(axis.lengthSquared() < 1.0E-9)) {
                axis.normalize();
                Vector3d side = new Vector3d(axis.z, axis.x, axis.y).cross(axis);
                if (side.lengthSquared() < 1.0E-6) {
                    side.set(1.0, 0.0, 0.0);
                }

                side.normalize();
                Vector3d up = new Vector3d(axis).cross(side).normalize();
                int[] colors = this.jetColors(this.clientSuckFluid);
                double range = 0.5 + 2.5 * this.clientSuckStrength;
                int count = 2 + (int)(this.clientSuckStrength * 4.0F);

                for (int i = 0; i < count; i++) {
                    double distance = 0.35 + this.level.random.nextDouble() * (range - 0.35);
                    double spawnAngle = this.level.random.nextDouble() * Math.PI * 2.0;
                    double spawnRadius = Math.sqrt(this.level.random.nextDouble()) * (0.15 + 0.3 * distance);
                    Vector3d start = new Vector3d(tip)
                        .fma(distance, axis)
                        .fma(Math.cos(spawnAngle) * spawnRadius, side)
                        .fma(Math.sin(spawnAngle) * spawnRadius, up);
                    double endAngle = this.level.random.nextDouble() * Math.PI * 2.0;
                    double endRadius = Math.sqrt(this.level.random.nextDouble()) * 0.12;
                    Vector3d end = new Vector3d(tip).fma(Math.cos(endAngle) * endRadius, side).fma(Math.sin(endAngle) * endRadius, up);
                    Vector3d toEnd = end.sub(start, new Vector3d());
                    this.level
                        .addParticle(
                            new HoseSprayOptions(colors[this.level.random.nextInt(colors.length)], true), start.x, start.y, start.z, toEnd.x, toEnd.y, toEnd.z
                        );
                }
            }
        }
    }

    private void emitFluidJet(Vector3d spawn, Vector3d down, int[] colors) {
        Vector3d axis = new Vector3d(down);
        Vector3d side = new Vector3d(axis.z, axis.x, axis.y).cross(axis);
        if (side.lengthSquared() < 1.0E-6) {
            side.set(1.0, 0.0, 0.0);
        }

        side.normalize();
        Vector3d up = new Vector3d(axis).cross(side).normalize();
        float t = Mth.clamp(this.clientSprayThrow, 0.0F, 1.0F);
        double coreSpeed = 0.3 + 0.3 * t;
        int coreCount = 1 + (int)(t * 4.0F);
        int mistCount = (int)(t * 5.0F);

        for (int i = 0; i < coreCount; i++) {
            double spread = 0.04;
            Vector3d v = new Vector3d(axis)
                .mul(coreSpeed * (0.85 + this.level.random.nextFloat() * 0.3))
                .fma((this.level.random.nextFloat() - 0.5) * spread, side)
                .fma((this.level.random.nextFloat() - 0.5) * spread, up);
            ParticleWorldLock.addParticle(this.level, this.sprayOption(colors), spawn.x, spawn.y, spawn.z, v.x, v.y, v.z);
        }

        for (int i = 0; i < mistCount; i++) {
            double spread = 0.14;
            double mistSpeed = 0.06 + this.level.random.nextFloat() * 0.08;
            Vector3d v = new Vector3d(axis)
                .mul(mistSpeed)
                .fma((this.level.random.nextFloat() - 0.5) * spread, side)
                .fma((this.level.random.nextFloat() - 0.5) * spread, up);
            ParticleWorldLock.addParticle(this.level, this.sprayOption(colors), spawn.x, spawn.y, spawn.z, v.x, v.y, v.z);
        }
    }

    private HoseSprayOptions sprayOption(int[] colors) {
        return new HoseSprayOptions(colors[this.level.random.nextInt(colors.length)]);
    }

    private int[] jetColors() {
        return this.jetColors(this.clientSprayFluid);
    }

    private int[] jetColors(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        int[] override = HoseSprayColors.get(fluid);
        if (override != null) {
            return override;
        }

        if (fluid.builtInRegistryHolder().is(FluidTags.WATER)) {
            return WATER_COLORS;
        }

        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation tex = ext.getStillTexture(stack);
        return tex == null ? WATER_COLORS : FLUID_COLOR_CACHE.computeIfAbsent(tex, t -> sampleTexture(t, ext.getTintColor(stack)));
    }

    private static int[] sampleTexture(ResourceLocation sprite, int tint) {
        int[] out = new int[]{-1, -1, -1, -1};
        ResourceLocation png = ResourceLocation.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Optional<Resource> resource = rm.getResource(png);
        if (resource.isEmpty()) {
            return out;
        }

        try (InputStream in = resource.get().open()) {
            NativeImage img = NativeImage.read(in);

            try {
                int w = img.getWidth();
                int frame = Math.min(w, img.getHeight());
                float tr = (tint >> 16 & 0xFF) / 255.0F;
                float tg = (tint >> 8 & 0xFF) / 255.0F;
                float tb = (tint & 0xFF) / 255.0F;
                Random rnd = new Random();

                for (int i = 0; i < 4; i++) {
                    for (int tryN = 0; tryN < 16; tryN++) {
                        int px = img.getPixelRGBA(rnd.nextInt(w), rnd.nextInt(frame));
                        if ((px >> 24 & 0xFF) >= 20) {
                            int r = (int)((px & 0xFF) * tr);
                            int g = (int)((px >> 8 & 0xFF) * tg);
                            int b = (int)((px >> 16 & 0xFF) * tb);
                            out[i] = 0xFF000000 | r << 16 | g << 8 | b;
                            break;
                        }
                    }
                }
            } catch (Throwable var22) {
                if (img != null) {
                    try {
                        img.close();
                    } catch (Throwable var21) {
                        var22.addSuppressed(var21);
                    }
                }

                throw var22;
            }

            if (img != null) {
                img.close();
            }
        } catch (Exception e) {
            LoconauticsConstants.LOGGER.debug("Nozzle sprite tint sample failed, using white fallback", e);
        }

        return out;
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("SuctionTank", this.suctionTank.writeToNBT(registries, new CompoundTag()));
        if (clientPacket) {
            tag.putFloat("SprayThrow", this.sprayThrow);
            tag.put("SprayFluid", this.sprayFluid.saveOptional(registries));
            tag.put("SuckFluid", this.lastSyncedSuckFluid.saveOptional(registries));
            tag.putFloat("SuckStrength", this.lastSyncedSuckStrength);
        }
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.suctionTank.readFromNBT(registries, tag.getCompound("SuctionTank"));
        if (clientPacket) {
            this.clientSprayThrow = tag.getFloat("SprayThrow");
            this.clientSprayFluid = tag.contains("SprayFluid") ? FluidStack.parseOptional(registries, tag.getCompound("SprayFluid")) : FluidStack.EMPTY;
            this.clientSuckFluid = tag.contains("SuckFluid") ? FluidStack.parseOptional(registries, tag.getCompound("SuckFluid")) : FluidStack.EMPTY;
            this.clientSuckStrength = tag.getFloat("SuckStrength");
        }
    }

    @Override
    public Vec3 getAttachmentPoint() {
        return StrandAnchors.hoseNozzle(this.getBlockPos());
    }

    @Override
    public boolean canAttachStrand() {
        return this.holder != null && this.holder.getAttachedStrandId() == null && StrandAttachments.isFree(this, SteelCableHolderBehaviour.TYPE);
    }

    @Override
    public void onStrandAttached(HoseStrand strand) {
        this.holder.setAttachedStrandId(strand.id);
        this.setChanged();
    }

    @Override
    public void onStrandDetached() {
        if (this.holder != null && this.holder.getAttachedStrandId() != null) {
            this.holder.setAttachedStrandId(null);
            this.setChanged();
        }
    }

    @Nullable
    @Override
    public HoseStrand getAttachedStrand() {
        return this.holder == null ? null : this.holder.getAttachedStrand();
    }

    private record FluidHit(@Nullable SubLevel sub, BlockPos pos) {
    }

    private final class LinkSink implements PressuredSink {
        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int tank) {
            IFluidHandler docked = HoseNozzleBlockEntity.this.dockedFluidHandler();
            return docked == null ? FluidStack.EMPTY : docked.getFluidInTank(0);
        }

        public int getTankCapacity(int tank) {
            IFluidHandler docked = HoseNozzleBlockEntity.this.dockedFluidHandler();
            return docked == null ? 64 : docked.getTankCapacity(0);
        }

        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler docked = HoseNozzleBlockEntity.this.dockedFluidHandler();
            return docked == null || docked.isFluidValid(0, stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            return this.fill(resource, 0.5F, action);
        }

        @Override
        public int fill(FluidStack resource, float strength, FluidAction action) {
            return HoseNozzleBlockEntity.this.receive(resource, strength, action);
        }

        @Override
        public void pull(float strength) {
            HoseNozzleBlockEntity.this.setSuckPower(strength);
        }

        @Nullable
        @Override
        public IFluidHandler endTank() {
            return HoseNozzleBlockEntity.this.dockedFluidHandler();
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler source = this.drainSource();
            return source == null ? FluidStack.EMPTY : this.noteSuction(source.drain(resource, action), action);
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler source = this.drainSource();
            return source == null ? FluidStack.EMPTY : this.noteSuction(source.drain(maxDrain, action), action);
        }

        private FluidStack noteSuction(FluidStack drained, FluidAction action) {
            if (!drained.isEmpty() && action.execute() && !HoseNozzleBlockEntity.this.isDocked() && HoseNozzleBlockEntity.this.level != null) {
                HoseNozzleBlockEntity.this.suckFluid = drained.copyWithAmount(1);
                HoseNozzleBlockEntity.this.suckTick = HoseNozzleBlockEntity.this.level.getGameTime();
            }

            return drained;
        }

        @Nullable
        private IFluidHandler drainSource() {
            IFluidHandler docked = HoseNozzleBlockEntity.this.dockedFluidHandler();
            return docked != null ? docked : HoseNozzleBlockEntity.this.suctionHandler();
        }
    }
}
