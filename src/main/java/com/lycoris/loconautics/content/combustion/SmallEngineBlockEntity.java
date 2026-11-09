package com.lycoris.loconautics.content.combustion;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.dock.DockPolling;
import com.lycoris.loconautics.common.dock.HorizontalDockPoint;
import com.lycoris.loconautics.common.dock.IDockable;
import com.lycoris.loconautics.common.grab.GrabbableActor;
import com.lycoris.loconautics.compat.heat.PropulsionCompat;
import com.lycoris.loconautics.compat.heat.PropulsionEngineHeat;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.content.hatch.NozzleDockService;
import com.lycoris.loconautics.content.hose.nozzle.HoseNozzleBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeEmitter;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3f;

public class SmallEngineBlockEntity extends GeneratingKineticBlockEntity implements IDockable {
    private static final double SMOKE_SPREAD = 0.25;
    private static final int SMOKE_INTERVAL = 4;
    private static final float MIN_SMOKE_SHARE = 0.12F;
    private static final int MAX_SMOKE_PUFFS = 4;
    private static final float MIN_SMOKE_SCALE = 0.55F;
    private static final float MIN_SHARE = 0.001F;
    private static final float SPIN_UP = 0.014F;
    private static final float SPIN_DOWN = 0.01F;
    private static final int LEVEL_INTERVAL = 10;
    private static final int LEVEL_STEP = 100;
    private static final int NETWORK_INTERVAL = 10;
    private static final int SHOWN_STEP = 5;
    private static final int BURNS_FOR_SLACK = 40;
    private final FluidTank tank;
    private final IFluidHandler inlet;
    private final IFluidHandler anySide;
    private final IFluidHandler dockPort;
    private boolean docked;
    @Nullable
    private HoseNozzleBlockEntity dockedNozzle;
    private boolean head = true;
    private boolean refused;
    private boolean running;
    private int controlSignal;
    private float ownRpm;
    private float burnCarry;
    private int fuelOrdinal = -1;
    private int poolAmount;
    private int poolCapacity = 1000;
    private long burnsUntil = -1L;
    private float poolRate;
    private long poolStamp;
    private FuelNetwork.Reading networkReading = FuelNetwork.Reading.NONE;
    @Nullable
    private BlockEntityBehaviour heat;
    private int activeEngines;
    private float rowRpm;
    @Nullable
    private SmokeEmitter smoke;
    @Nullable
    private SmallEngineSound sound;
    private float pistonPhase;
    private float pistonPhasePrev;
    private float shownSpin;
    private boolean spinStarted;
    private boolean spinLoggedRunning;
    private float shownRpm;
    private float lastRpm;
    private float lastShare = 1.0F;

    public SmallEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.tank = new FluidTank(1000, CombustionFuel::accepts) {
            protected void onContentsChanged() {
                SmallEngineBlockEntity.this.setChanged();
                SmallEngineBlockEntity.this.sendData();
            }
        };
        this.inlet = new SmallEngineBlockEntity.FuelInlet(this, SmallEngineBlockEntity.Drain.NONE);
        this.anySide = new SmallEngineBlockEntity.FuelInlet(this, SmallEngineBlockEntity.Drain.OWN);
        this.dockPort = new SmallEngineBlockEntity.FuelInlet(this, SmallEngineBlockEntity.Drain.ROW);
    }

    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        if (PropulsionCompat.isLoaded()) {
            this.heat = PropulsionEngineHeat.create(this);
            behaviours.add(this.heat);
        }
    }

    public FluidTank getTank() {
        return this.tank;
    }

    @Nullable
    public IFluidHandler fluidHandler(@Nullable Direction side) {
        if (side == null) {
            return this.anySide;
        } else {
            return side == Direction.DOWN ? this.inlet : null;
        }
    }

    public boolean isHead() {
        return this.head;
    }

    public boolean isRunning() {
        return this.running;
    }

    public int getControlSignal() {
        return this.controlSignal;
    }

    public int getFuelAmount() {
        if (this.level != null && !(this.poolRate <= 0.0F)) {
            float left = this.poolAmount - this.poolRate * (float)Math.max(0L, this.level.getGameTime() - this.poolStamp);
            return Mth.clamp(Math.round(left), 0, this.poolCapacity);
        } else {
            return this.poolAmount;
        }
    }

    public int getFuelCapacity() {
        return this.poolCapacity;
    }

    public float getOutputRpm() {
        return this.head ? this.rowRpm : this.ownRpm;
    }

    public float getOutputSu() {
        return this.head ? 32.0F * this.activeEngines * this.rowRpm : 0.0F;
    }

    public int getBurnsForSeconds() {
        return this.burnsUntil >= 0L && this.level != null ? (int)Math.max(0L, (this.burnsUntil - this.level.getGameTime()) / 20L) : -1;
    }

    public SmallEngineBlockEntity.Status getStatus() {
        if (this.running) {
            return SmallEngineBlockEntity.Status.RUNNING;
        } else if (this.refused) {
            return SmallEngineBlockEntity.Status.ROW_FULL;
        } else if (SmallEngineTuning.isOff(this.controlSignal)) {
            return SmallEngineBlockEntity.Status.SWITCHED_OFF;
        } else {
            return this.poolAmount <= 0 ? SmallEngineBlockEntity.Status.NO_FUEL : SmallEngineBlockEntity.Status.IDLE;
        }
    }

    @Nullable
    public static SmallEngineBlockEntity resolve(@Nullable Level level, BlockPos bound) {
        if (level != null && level.isLoaded(bound)) {
            BlockEntity at = level.getBlockEntity(bound);
            if (at instanceof SmallEngineBlockEntity engine) {
                return engine;
            } else {
                if (at != null) {
                    return null;
                }

                BlockPos survivor = EngineRow.survivorNear(level, bound);
                return survivor != null && level.getBlockEntity(survivor) instanceof SmallEngineBlockEntity engine ? engine : null;
            }
        } else {
            return null;
        }
    }

    public void driveFromPanel(int signal) {
        if (this.level != null && !this.level.isClientSide) {
            Direction facing = (Direction)this.getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
            BlockPos owner = EngineRow.headOf(this.level, this.worldPosition, facing);
            PanelDriveBus.report(this.level, owner, Mth.clamp(signal, 0, 15));
        }
    }

    public void handOverFuel() {
        if (this.level != null) {
            Direction facing = (Direction)this.getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
            List<SmallEngineBlockEntity> survivors = new ArrayList<>(6);

            for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
                BlockPos at = this.worldPosition;
                BlockState from = this.getBlockState();

                for (int i = 0; i < 3 && EngineRow.linked(this.level, at, from, facing, side); i++) {
                    at = at.relative(side);
                    from = this.level.getBlockState(at);
                    if (this.level.getBlockEntity(at) instanceof SmallEngineBlockEntity near) {
                        survivors.add(near);
                    }
                }
            }

            FluidStack total = this.tank.getFluid().copy();
            boolean sameFuel = true;

            for (SmallEngineBlockEntity near : survivors) {
                FluidStack other = near.tank.getFluid();
                if (!other.isEmpty()) {
                    if (total.isEmpty()) {
                        total = other.copy();
                    } else if (FluidStack.isSameFluidSameComponents(total, other)) {
                        total.grow(other.getAmount());
                    } else {
                        sameFuel = false;
                    }
                }
            }

            if (!total.isEmpty()) {
                if (sameFuel && !survivors.isEmpty()) {
                    int share = total.getAmount() / survivors.size();
                    int extra = total.getAmount() - share * survivors.size();

                    for (SmallEngineBlockEntity near : survivors) {
                        int want = share + (extra-- > 0 ? 1 : 0);
                        int kept = Math.min(want, near.tank.getCapacity());
                        near.tank.setFluid(total.copyWithAmount(kept));
                        near.setChanged();
                        near.sendData();
                    }
                } else {
                    FluidStack held = this.tank.getFluid().copy();

                    for (SmallEngineBlockEntity near : survivors) {
                        held.shrink(near.tank.fill(held, FluidAction.EXECUTE));
                    }
                }
            }
        }
    }

    public void remove() {
        if (this.level != null && !this.level.isClientSide) {
            PanelDriveBus.release(this.level, this.worldPosition);
        }

        if (this.level != null && this.level.isClientSide && this.smoke != null) {
            this.smoke.stop();
            this.smoke = null;
        }

        super.remove();
    }

    public void tick() {
        super.tick();
        if (this.level != null) {
            if (this.level.isClientSide) {
                this.clientTick();
            } else {
                Direction facing = (Direction)this.getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
                BlockPos headPos = EngineRow.headOf(this.level, this.worldPosition, facing);
                List<BlockPos> members = EngineRow.membersFrom(this.level, headPos, facing);
                List<SmallEngineBlockEntity> engines = this.enginesAt(members);
                boolean nowHead = headPos.equals(this.worldPosition);
                boolean nowRefused = !members.contains(this.worldPosition);
                int nowSignal = this.rowSignal(members, headPos);
                FluidTank source = nowRefused ? this.tank : this.fuelSource(engines);
                CombustionFuel fuel = CombustionFuel.of(source.getFluid());
                boolean nowRunning = !nowRefused && this.burn(fuel, nowSignal, source, engines);
                float nowRpm = nowRunning ? fuel.topRpm() * SmallEngineTuning.speedFraction(nowSignal) : 0.0F;
                int nowPool = 0;
                int nowCapacity = this.tank.getCapacity();
                if (!nowRefused) {
                    nowCapacity = engines.size() * 1000;

                    for (SmallEngineBlockEntity engine : engines) {
                        nowPool += engine.tank.getFluidAmount();
                    }
                } else {
                    nowPool = this.tank.getFluidAmount();
                }

                if (!nowRefused && this.level.getGameTime() % 10L == 0L) {
                    this.networkReading = FuelNetwork.read(this.level, members, source.getFluid());
                } else if (nowRefused) {
                    this.networkReading = FuelNetwork.Reading.NONE;
                }

                long now = this.level.getGameTime();
                float nowBurn = 0.0F;
                if (fuel != null && !nowRefused) {
                    float each = fuel.burnPerTick() * SmallEngineTuning.speedFraction(nowSignal);

                    for (SmallEngineBlockEntity engine : engines) {
                        if (engine == this ? nowRunning : engine.running) {
                            nowBurn += each;
                        }
                    }
                }

                boolean rateMoved = Math.abs(nowBurn - this.poolRate) > 1.0E-4F;
                boolean poolDrifted = Math.abs(nowPool - this.getFuelAmount()) >= Math.max(5, nowCapacity / 200);
                boolean poolEdge = nowPool != this.poolAmount && (nowPool == 0 || nowPool == nowCapacity);
                boolean reanchor = rateMoved || poolDrifted || poolEdge || nowCapacity != this.poolCapacity;
                float nextRate = reanchor ? nowBurn : this.poolRate;
                int nextAmount = reanchor ? nowPool : this.poolAmount;
                long nextStamp = reanchor ? now : this.poolStamp;
                long nowBurnsUntil = nowBurn <= 0.0F ? -1L : this.burnsUntil;
                if (nowBurn > 0.0F) {
                    float stored = this.networkReading.amount();

                    for (SmallEngineBlockEntity engine : engines) {
                        stored += engine.tank.getFluidAmount();
                    }

                    long estimate = now + (long)(stored / nowBurn);
                    if (this.burnsUntil < 0L || rateMoved || Math.abs(estimate - this.burnsUntil) > 40L) {
                        nowBurnsUntil = estimate;
                    }
                }

                int nowFuelOrdinal = fuel == null ? -1 : fuel.ordinal();
                boolean lostHead = this.head && !nowHead;
                boolean changed = nowHead != this.head
                    || nowFuelOrdinal != this.fuelOrdinal
                    || nowRefused != this.refused
                    || nowRunning != this.running
                    || nowSignal != this.controlSignal
                    || nowRpm != this.ownRpm
                    || reanchor
                    || nowBurnsUntil != this.burnsUntil;
                if (nowRunning && (!this.running || nowFuelOrdinal != this.fuelOrdinal) && this.level instanceof ServerLevel serverLevel) {
                    TrainAdvancements.engineStarted(serverLevel, this.worldPosition, nowFuelOrdinal, members.size());
                }

                this.head = nowHead;
                this.refused = nowRefused;
                this.running = nowRunning;
                this.controlSignal = nowSignal;
                this.ownRpm = nowRpm;
                this.fuelOrdinal = nowFuelOrdinal;
                this.poolAmount = nextAmount;
                this.poolRate = nextRate;
                this.poolStamp = nextStamp;
                this.poolCapacity = nowCapacity;
                this.burnsUntil = nowBurnsUntil;
                if (changed) {
                    this.setChanged();
                    this.sendData();
                }

                if (this.heat != null) {
                    float made = nowRunning ? PropulsionEngineHeat.perTick() * SmallEngineTuning.speedFraction(nowSignal) : 0.0F;
                    PropulsionEngineHeat.tick(this.heat, this.level, this.worldPosition, made);
                }

                if (lostHead) {
                    this.activeEngines = 0;
                    this.rowRpm = 0.0F;
                    this.updateGeneratedRotation();
                } else if (this.head) {
                    this.refreshOutput(engines);
                    if (this.level.getGameTime() % 10L == 0L) {
                        levelFuel(engines);
                    }
                }

                this.pollDock();
            }
        }
    }

    private Direction dockFace() {
        return ((Direction)this.getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING)).getOpposite();
    }

    private boolean atTail() {
        BlockState state = this.getBlockState();
        Direction facing = (Direction)state.getValue(SmallEngineBlock.HORIZONTAL_FACING);
        return !EngineRow.joinsToward(state, facing, facing.getOpposite());
    }

    private void pollDock() {
        DockPolling.maybeUndock(this.docked, this.dockedNozzle, n -> !n.isRemoved() && n.isDocked(), this::onUndocked);
        if (this.canDock()) {
            SubLevel engineSub = Sable.HELPER.getContaining(this);
            Vec3 anchor = HorizontalDockPoint.anchorLocal(this, this.dockFace(), this.dockSeatOffset());
            DockPolling.poll(
                this,
                NozzleDockService.all(),
                nozzle -> !nozzle.isRemoved() && !nozzle.isDocked() && nozzle.canRedock(this.level.getGameTime()),
                nozzle -> Sable.HELPER.getContaining(nozzle),
                GrabbableActor::worldCenter,
                (nozzle, sub) -> {
                    nozzle.dock(this, engineSub, anchor, this.getDockOrientation());
                    this.dockedNozzle = nozzle;
                    this.onDocked();
                }
            );
        }
    }

    @Override
    public Vec3 getDockPoint() {
        return HorizontalDockPoint.worldPoint(this, HorizontalDockPoint.anchorLocal(this, this.dockFace(), this.dockSeatOffset()));
    }

    @Override
    public Quaterniond getDockOrientation() {
        return HorizontalDockPoint.orientation(this.dockFace());
    }

    @Override
    public double dockRadius() {
        return 0.75;
    }

    @Override
    public double dockSeatOffset() {
        return 1.0;
    }

    @Override
    public boolean canDock() {
        return this.level != null && !this.level.isClientSide && !this.docked && this.atTail();
    }

    @Override
    public boolean isDockPresent() {
        return !this.isRemoved() && this.atTail();
    }

    @Override
    public void onDocked() {
        this.docked = true;
    }

    @Override
    public void onUndocked() {
        this.docked = false;
        this.dockedNozzle = null;
    }

    @Nullable
    @Override
    public IFluidHandler getDockedFluidHandler() {
        return this.dockPort;
    }

    public float getShownSpin() {
        return this.shownSpin;
    }

    public float getRunningShare() {
        return SmallEngineTuning.speedFraction(this.controlSignal);
    }

    public Vector3f pistonShake(float partialTick, float seed) {
        return EnginePistons.shake(
            Mth.lerp(partialTick, this.pistonPhasePrev, this.pistonPhase), seed, this.shownRpm, this.running ? this.getRunningShare() : this.lastShare
        );
    }

    private void clientTick() {
        if (!this.running || this.level instanceof PonderLevel || this.sound != null && !this.sound.isStopped()) {
            if (!this.running && this.sound != null && this.sound.isStopped()) {
                this.sound = null;
            }
        } else {
            // Started through SmallEngineSound so this class never passes a SmallEngineSound where a
            // client SoundInstance is expected: verifying that would load client classes on a server.
            this.sound = SmallEngineSound.start(this);
        }

        this.pistonPhasePrev = this.pistonPhase;
        if (this.running != this.spinLoggedRunning) {
            this.spinLoggedRunning = this.running;
            LoconauticsConstants.LOGGER
                .debug("[enginespin] running flipped to {} at {} tick={}", new Object[]{this.running, this.worldPosition, this.level.getGameTime()});
        }

        float spinTarget = this.running ? this.getRunningShare() : 0.0F;
        if (this.running) {
            this.lastShare = Math.max(spinTarget, 0.001F);
            this.lastRpm = this.ownRpm;
        }

        if (!this.spinStarted) {
            this.spinStarted = true;
            if (this.running) {
                this.shownSpin = spinTarget;
            }

            LoconauticsConstants.LOGGER
                .debug(
                    "[enginespin] new client engine at {} running={} rpm={} spin={}",
                    new Object[]{this.worldPosition, this.running, this.ownRpm, this.shownSpin}
                );
        }

        float spinStep = spinTarget > this.shownSpin ? 0.014F : 0.01F;
        this.shownSpin = this.shownSpin + Mth.clamp(spinTarget - this.shownSpin, -spinStep, spinStep);
        this.shownRpm = this.lastRpm * Mth.clamp(this.shownSpin / this.lastShare, 0.0F, 1.0F);
        if (this.shownRpm > 0.0F) {
            this.pistonPhase = this.pistonPhase + this.shownRpm / 60.0F / 20.0F * (float) (Math.PI * 2);
            if (this.pistonPhase > (float) (Math.PI * 2)) {
                this.pistonPhase -= (float) (Math.PI * 2);
                this.pistonPhasePrev -= (float) (Math.PI * 2);
            }
        }

        if (!this.running) {
            if (this.smoke != null) {
                this.smoke.stop();
            }
        } else {
            if (this.smoke == null) {
                this.smoke = new SmokeEmitter();
            }

            this.smoke.track(new Vec3(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5));
            if (this.level.getGameTime() % 4L == 0L) {
                float share = this.getRunningShare();
                if (!(this.level.random.nextFloat() > Math.max(0.12F, share))) {
                    SmokeOptions exhaust = SmokeOptions.exhaust(share);
                    SmokeOptions sized = new SmokeOptions(exhaust.color(), exhaust.style(), Mth.lerp(share, 0.55F, 1.0F));
                    this.smoke.puff(this.level, sized, 1 + Math.round(share * 3.0F), 0.25);
                }
            }
        }
    }

    private static void levelFuel(List<SmallEngineBlockEntity> engines) {
        SmallEngineBlockEntity fullest = null;
        SmallEngineBlockEntity emptiest = null;

        for (SmallEngineBlockEntity engine : engines) {
            if (fullest == null || engine.tank.getFluidAmount() > fullest.tank.getFluidAmount()) {
                fullest = engine;
            }

            if (emptiest == null || engine.tank.getFluidAmount() < emptiest.tank.getFluidAmount()) {
                emptiest = engine;
            }
        }

        if (fullest != null && emptiest != fullest) {
            int gap = fullest.tank.getFluidAmount() - emptiest.tank.getFluidAmount();
            if (gap >= 2) {
                FluidStack offer = fullest.tank.drain(Math.min(100, gap / 2), FluidAction.SIMULATE);
                int moved = emptiest.tank.fill(offer, FluidAction.EXECUTE);
                if (moved > 0) {
                    fullest.tank.drain(moved, FluidAction.EXECUTE);
                }
            }
        }
    }

    private List<SmallEngineBlockEntity> enginesAt(List<BlockPos> members) {
        List<SmallEngineBlockEntity> engines = new ArrayList<>(members.size());

        for (BlockPos pos : members) {
            BlockEntity be = (BlockEntity)(pos.equals(this.worldPosition) ? this : this.level.getBlockEntity(pos));
            if (be instanceof SmallEngineBlockEntity engine) {
                engines.add(engine);
            }
        }

        return engines;
    }

    List<SmallEngineBlockEntity> rowEngines() {
        if (this.level == null) {
            return List.of(this);
        }

        Direction facing = (Direction)this.getBlockState().getValue(SmallEngineBlock.HORIZONTAL_FACING);
        BlockPos headPos = EngineRow.headOf(this.level, this.worldPosition, facing);
        List<SmallEngineBlockEntity> engines = this.enginesAt(EngineRow.membersFrom(this.level, headPos, facing));
        return engines.contains(this) ? engines : List.of(this);
    }

    private int rowSignal(List<BlockPos> members, BlockPos owner) {
        int signal = Math.round(PanelDriveBus.report(this.level, owner, 0.0F));

        for (BlockPos pos : members) {
            signal = Math.max(signal, this.level.getBestNeighborSignal(pos));
        }

        return Mth.clamp(signal, 0, 15);
    }

    private FluidTank fuelSource(List<SmallEngineBlockEntity> engines) {
        if (CombustionFuel.of(this.tank.getFluid()) != null) {
            return this.tank;
        }

        for (SmallEngineBlockEntity engine : engines) {
            if (CombustionFuel.of(engine.tank.getFluid()) != null) {
                return engine.tank;
            }
        }

        return this.tank;
    }

    private boolean burn(@Nullable CombustionFuel fuel, int signal, FluidTank source, List<SmallEngineBlockEntity> engines) {
        if (fuel != null && !SmallEngineTuning.isOff(signal)) {
            this.burnCarry = this.burnCarry + fuel.burnPerTick() * SmallEngineTuning.speedFraction(signal);
            int whole = (int)this.burnCarry;
            if (whole <= 0) {
                return true;
            }

            this.burnCarry -= whole;
            FluidStack kind = source.getFluid().copyWithAmount(1);
            int left = whole - source.drain(whole, FluidAction.EXECUTE).getAmount();

            for (SmallEngineBlockEntity engine : engines) {
                if (left <= 0) {
                    break;
                }

                if (engine.tank != source && FluidStack.isSameFluidSameComponents(engine.tank.getFluid(), kind)) {
                    left -= engine.tank.drain(left, FluidAction.EXECUTE).getAmount();
                }
            }

            return left <= 0;
        } else {
            return false;
        }
    }

    private void refreshOutput(List<SmallEngineBlockEntity> engines) {
        int count = 0;
        float slowest = Float.MAX_VALUE;

        for (SmallEngineBlockEntity engine : engines) {
            if (engine.running) {
                count++;
                slowest = Math.min(slowest, engine.ownRpm);
            }
        }

        float nowRpm = count == 0 ? 0.0F : slowest;
        if (count != this.activeEngines || nowRpm != this.rowRpm) {
            this.activeEngines = count;
            this.rowRpm = nowRpm;
            this.updateGeneratedRotation();
        }
    }

    public float getGeneratedSpeed() {
        return this.head && this.activeEngines > 0 && !(this.rowRpm <= 0.0F)
            ? convertToDirection(this.rowRpm, SmallEngineBlock.output(this.getBlockState()))
            : 0.0F;
    }

    public float calculateAddedStressCapacity() {
        float capacity = this.head ? 32.0F * this.activeEngines : 0.0F;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.small_engine_stats").forGoggles(tooltip);
        SmallEngineBlockEntity.Status status = this.getStatus();
        Object[] args = status == SmallEngineBlockEntity.Status.RUNNING
            ? new Object[]{Math.round(SmallEngineTuning.speedFraction(this.controlSignal) * 100.0F)}
            : new Object[0];
        LoconauticsLang.translate("gui.goggles.small_engine_status_" + status.key, args).style(status.color).forGoggles(tooltip, 1);
        return true;
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Tank", this.tank.writeToNBT(registries, new CompoundTag()));
        tag.putBoolean("Head", this.head);
        tag.putBoolean("Refused", this.refused);
        tag.putBoolean("Running", this.running);
        tag.putInt("Signal", this.controlSignal);
        tag.putFloat("OwnRpm", this.ownRpm);
        tag.putFloat("BurnCarry", this.burnCarry);
        tag.putInt("Fuel", this.fuelOrdinal);
        tag.putInt("Pool", this.poolAmount);
        tag.putInt("PoolCapacity", this.poolCapacity);
        tag.putFloat("PoolRate", this.poolRate);
        tag.putLong("PoolStamp", this.poolStamp);
        tag.putLong("BurnsUntil", this.burnsUntil);
        tag.putInt("Active", this.activeEngines);
        tag.putFloat("RowRpm", this.rowRpm);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.tank.readFromNBT(registries, tag.getCompound("Tank"));
        if (this.tank.getFluidAmount() > this.tank.getCapacity()) {
            this.tank.getFluid().setAmount(this.tank.getCapacity());
        }

        this.head = !tag.contains("Head") || tag.getBoolean("Head");
        this.refused = tag.getBoolean("Refused");
        this.running = tag.getBoolean("Running");
        this.controlSignal = tag.getInt("Signal");
        this.ownRpm = tag.getFloat("OwnRpm");
        this.burnCarry = tag.getFloat("BurnCarry");
        this.fuelOrdinal = tag.contains("Fuel") ? tag.getInt("Fuel") : -1;
        this.poolAmount = tag.contains("Pool") ? tag.getInt("Pool") : this.tank.getFluidAmount();
        this.poolCapacity = tag.contains("PoolCapacity") ? tag.getInt("PoolCapacity") : this.tank.getCapacity();
        this.poolRate = tag.getFloat("PoolRate");
        this.poolStamp = tag.getLong("PoolStamp");
        this.burnsUntil = tag.contains("BurnsUntil") ? tag.getLong("BurnsUntil") : -1L;
        this.activeEngines = tag.getInt("Active");
        this.rowRpm = tag.getFloat("RowRpm");
    }

    private enum Drain {
        NONE,
        OWN,
        ROW;
    }

    private record FuelInlet(SmallEngineBlockEntity engine, SmallEngineBlockEntity.Drain drain) implements IFluidHandler {
        public int getTanks() {
            return 1;
        }

        public FluidStack getFluidInTank(int index) {
            FluidStack kind = FluidStack.EMPTY;
            int total = 0;

            for (SmallEngineBlockEntity member : this.engine.rowEngines()) {
                if (!member.tank.getFluid().isEmpty()) {
                    if (kind.isEmpty()) {
                        kind = member.tank.getFluid();
                    }

                    total += member.tank.getFluidAmount();
                }
            }

            return kind.isEmpty() ? FluidStack.EMPTY : kind.copyWithAmount(total);
        }

        public int getTankCapacity(int index) {
            return this.engine.rowEngines().size() * 1000;
        }

        public boolean isFluidValid(int index, FluidStack stack) {
            return CombustionFuel.accepts(stack);
        }

        public int fill(FluidStack resource, FluidAction action) {
            int filled = 0;

            for (SmallEngineBlockEntity member : this.engine.rowEngines()) {
                if (filled >= resource.getAmount()) {
                    break;
                }

                filled += member.tank.fill(resource.copyWithAmount(resource.getAmount() - filled), action);
            }

            return filled;
        }

        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (this.drain == SmallEngineBlockEntity.Drain.NONE || resource.isEmpty()) {
                return FluidStack.EMPTY;
            }

            if (this.drain == SmallEngineBlockEntity.Drain.OWN) {
                return this.engine.tank.drain(resource, action);
            }

            FluidStack taken = FluidStack.EMPTY;

            for (SmallEngineBlockEntity member : this.engine.rowEngines()) {
                int left = resource.getAmount() - taken.getAmount();
                if (left <= 0) {
                    break;
                }

                FluidStack got = member.tank.drain(resource.copyWithAmount(left), action);
                if (!got.isEmpty()) {
                    if (taken.isEmpty()) {
                        taken = got;
                    } else {
                        taken.grow(got.getAmount());
                    }
                }
            }

            return taken;
        }

        public FluidStack drain(int maxDrain, FluidAction action) {
            if (this.drain == SmallEngineBlockEntity.Drain.NONE) {
                return FluidStack.EMPTY;
            }

            if (this.drain == SmallEngineBlockEntity.Drain.OWN) {
                return this.engine.tank.drain(maxDrain, action);
            }

            for (SmallEngineBlockEntity member : this.engine.rowEngines()) {
                FluidStack kind = member.tank.getFluid();
                if (!kind.isEmpty()) {
                    return this.drain(kind.copyWithAmount(maxDrain), action);
                }
            }

            return FluidStack.EMPTY;
        }
    }

    public enum Status {
        RUNNING("running", ChatFormatting.GREEN),
        NO_FUEL("no_fuel", ChatFormatting.RED),
        SWITCHED_OFF("switched_off", ChatFormatting.GOLD),
        ROW_FULL("row_full", ChatFormatting.RED),
        IDLE("idle", ChatFormatting.GRAY);

        private final String key;
        private final ChatFormatting color;

        Status(String key, ChatFormatting color) {
            this.key = key;
            this.color = color;
        }
    }
}
