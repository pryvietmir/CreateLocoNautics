package com.lycoris.loconautics.content.boiler.firebox;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.advancement.TrainAdvancements;
import com.lycoris.loconautics.common.strand.StrandTemplates;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.boiler.BoilerBlastEffects;
import com.lycoris.loconautics.content.boiler.BoilerContributions;
import com.lycoris.loconautics.content.boiler.BoilerMultiblockValidator;
import com.lycoris.loconautics.content.boiler.BoilerState;
import com.lycoris.loconautics.content.boiler.BoilerStructure;
import com.lycoris.loconautics.content.boiler.BoilerTags;
import com.lycoris.loconautics.content.boiler.BoilerWaterSource;
import com.lycoris.loconautics.content.boiler.barrel.BarrelConnectorBlockEntity;
import com.lycoris.loconautics.content.boiler.barrel.BoilerBarrelBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlockEntity;
import com.lycoris.loconautics.content.hatch.HullsteelSocketBridge;
import com.lycoris.loconautics.content.smokestack.SmokestackBlockEntity;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.foundation.smoke.SmokeOptions;
import com.lycoris.loconautics.foundation.smoke.SmokeStyle;
import com.lycoris.loconautics.trainphysics.fx.ParticleWorldLock;
import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class FireboxBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int FUEL_SLOTS = 1;
    public static final int RESCAN_TICKS = 20;
    public static final double NO_TRAIN = -1.0;
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        public boolean isItemValid(int slot, ItemStack stack) {
            return FireboxBlockEntity.isFuel(stack);
        }

        protected void onContentsChanged(int slot) {
            FireboxBlockEntity.this.setChanged();
            if (FireboxBlockEntity.this.level != null && !FireboxBlockEntity.this.level.isClientSide) {
                FireboxBlockEntity.this.refreshGroup();
            }
        }
    };
    private final FluidTank waterTank = new FluidTank(0, stack -> stack.getFluid() == Fluids.WATER) {
        protected void onContentsChanged() {
            FireboxBlockEntity.this.setChanged();
        }
    };
    private final BoilerWaterSource tankSource = new FireboxBlockEntity.TankSource();
    private final Map<BlockPos, Integer> trackedBarrels = new HashMap<>();
    private static final double BOILING_C = 100.0;
    private static final float COLD_FIRE_EFFICIENCY = 0.15F;
    private BoilerState boilerState = BoilerState.COLD;
    private int warmupTicks;
    private float burnTimeRemaining;
    private int rescanCooldown;
    private int inflowCooldown;
    private int dryTicks;
    private int wasteGas;
    private boolean lockedOut;
    private FireboxBlockEntity.FailureCause lastFailure;
    private static final float END_BLAST_SHARE = 0.75F;
    private static final float TAKEOFF_DRAW_MULTIPLIER = 2.5F;
    private static final double END_SHATTER_REACH = 3.0;
    private static final float BLOCKED_SMOKE_MIN_DENSITY = 0.5F;
    private static final float BLOCKED_SMOKE_SCALE = 0.35F;
    private static final int BLOCKED_SMOKE_COLOR = 2762274;
    private static final float LOW_WATER_RISE_BOOST = 1.0F;
    private static final float LOW_WATER_HEAT_SHARE = 0.25F;
    private BoilerStructure structure;
    @Nullable
    private String missingKey;
    public static final int DISPLAY_TICKS = 20;
    private int displayCooldown;
    private int jitterCooldown;
    private boolean dispValid;
    private float dispSu;
    private float dispRpm;
    private float dispEfficiency;
    private boolean dispOverfired;
    private int dispWater;
    private int dispWasteGas;
    private int dispRemainingSeconds;
    private float dispTemperatureJitter;
    private float dispWaterJitter;
    private float dispPressure;
    private float dispMaxSu;
    private int dispWaterCapacity;
    @Nullable
    private String dispStep;
    @Nullable
    private String dispOptionalStep;
    private float steamPressure;
    private boolean lastBurning;
    private float lastBurnRate;
    private float lastStressRatio;
    private double lastTrainMass = -1.0;
    private int lastWaterDrawn;
    private float lastSubSpeed;
    private boolean inSubLevel;
    public static final int DOOR_SWING_TICKS = 10;
    private static final float DOOR_SWING_SPEED = 1.35F;
    private static final double DOOR_REACH_PADDING = 0.7;
    private boolean manualDoorsOpen;
    private BlockPos controllerPos;
    private boolean doorsSwingingOpen;
    private float doorOpenTime;
    private float lastDoorOpenTime;

    public FireboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public boolean toggleDoors() {
        this.manualDoorsOpen = !this.manualDoorsOpen;
        this.setChanged();
        this.sendData();
        return this.manualDoorsOpen;
    }

    public boolean areDoorsManuallyOpen() {
        return this.manualDoorsOpen;
    }

    public void clientTick() {
        if (this.level != null) {
            if (--this.jitterCooldown <= 0) {
                this.jitterCooldown = 20;
                this.dispWaterJitter = this.rollWaterJitter();
            }

            this.lastDoorOpenTime = this.doorOpenTime;
            boolean open = this.manualDoorsOpen || this.stoker() != null;
            if (open != this.doorsSwingingOpen) {
                this.level
                    .playLocalSound(
                        this.worldPosition, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.1F, false
                    );
            }

            this.doorsSwingingOpen = open;
            this.spewSmoke();
            float dir = open ? 1.0F : -1.0F;
            this.doorOpenTime = Math.max(0.0F, Math.min(10.0F, this.doorOpenTime + dir * 1.35F));
        }
    }

    private void spewSmoke() {
        BlockState state = this.getBlockState();
        if ((Boolean)state.getValue(BlockStateProperties.LIT) && this.dispWasteGas > 0) {
            float choke = Math.min(1.0F, (float)this.dispWasteGas / ((Integer)LoconauticsConfig.DRAFT_CHOKE_TICKS.get()).intValue());
            float density = 0.5F + 0.5F * choke;
            Direction face = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            Vec3 mouth = Vec3.atCenterOf(this.worldPosition).add(face.getStepX() * 0.6, -0.1, face.getStepZ() * 0.6);
            if (!PortalCutsClient.hiddenAtPlot(mouth.x, mouth.y, mouth.z)) {
                Vec3 world = Sable.HELPER.projectOutOfSubLevel(this.level, mouth);
                Vec3 outward = Sable.HELPER.projectOutOfSubLevel(this.level, mouth.add(face.getStepX(), 0.0, face.getStepZ())).subtract(world);

                for (int i = 0; i < 2; i++) {
                    if (!(this.level.random.nextFloat() > density)) {
                        ParticleWorldLock.addParticle(
                            this.level,
                            new SmokeOptions(2762274, SmokeStyle.LEAK, 0.35F),
                            world.x + (this.level.random.nextDouble() - 0.5) * 0.3,
                            world.y + this.level.random.nextDouble() * 0.3,
                            world.z + (this.level.random.nextDouble() - 0.5) * 0.3,
                            outward.x * 0.03,
                            0.0,
                            outward.z * 0.03
                        );
                    }
                }
            }
        }
    }

    public float getDoorOpenTime(float partialTicks) {
        return Mth.lerp(partialTicks, this.lastDoorOpenTime, this.doorOpenTime);
    }

    @Nullable
    private Player stoker() {
        Vec3 center = this.worldPosition.getCenter();

        for (Player player : this.level.getEntitiesOfClass(Player.class, new AABB(this.worldPosition).inflate(8.0))) {
            if (isFuel(player.getMainHandItem()) || isFuel(player.getOffhandItem())) {
                double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 0.7;
                if (player.getEyePosition().distanceToSqr(center) < reach * reach) {
                    return player;
                }
            }
        }

        return null;
    }

    public void tick() {
        if (this.level != null && !this.level.isClientSide) {
            if (--this.rescanCooldown <= 0) {
                this.rescanCooldown = 20;
                this.rescan();
            }

            this.readSubLevelSpeed();
            if (!this.lockedOut && this.structure != null) {
                this.readAxle();
                this.drainInlets();
                boolean burning = this.consumeFuel();
                boolean watered = this.drawWater();
                this.tickStateMachine(burning, watered);
                if (!this.tickPressure(burning && watered)) {
                    this.tickWasteGas(burning);
                    if (!this.tickFailure(watered)) {
                        this.updateLit(burning);
                        this.tickDisplay();
                    }
                }
            } else {
                this.forceCold();
            }
        }
    }

    private void rescan() {
        BoilerMultiblockValidator.Scan result = BoilerMultiblockValidator.scan(this.level, this.worldPosition);
        boolean wasValid = this.structure != null;
        this.structure = result.structure();
        this.missingKey = result.missingKey();
        this.reconcileBarrels();
        if (this.structure == null) {
            this.boilerState = BoilerState.COLD;
            this.warmupTicks = 0;
        } else {
            this.tankSource.setWaterCapacity(this.structure.waterCapacity());
            this.reclaimParkedWater();
            this.resizeFuel(Math.max(1, this.structure.fireboxPositions().size()));
        }

        if (wasValid == (this.structure == null)) {
            this.setChanged();
            this.sendData();
        }
    }

    @Nullable
    private String computeNextStep() {
        if (this.lockedOut) {
            return null;
        } else if (this.structure == null) {
            return this.missingKey == null ? "add_boiler_body" : this.missingKey;
        } else if (!this.structure.hasWaterInlet()) {
            return "add_water_inlet";
        } else if (!this.structure.hasTakeoff()) {
            return "add_steam_takeoff";
        } else if (!this.structure.hasStack()) {
            return "add_smokestack";
        } else {
            BoilerWaterSource injector = this.water();
            if (injector == null || injector.getWaterAmount() <= 0) {
                return "add_water";
            } else {
                return this.burnTimeRemaining <= 0.0F && this.firstFuelSlot() < 0 ? "add_fuel" : null;
            }
        }
    }

    @Nullable
    public String nextStepKey() {
        return this.dispStep != null && !this.dispStep.isEmpty() ? this.dispStep : null;
    }

    @Nullable
    private String computeOptionalStep() {
        if (this.lockedOut || this.structure == null) {
            return null;
        } else if (this.boilerState != BoilerState.WARMING && this.boilerState != BoilerState.HOT) {
            return !this.structure.hasSafetyValve() && !this.hasBoundValve() ? "add_safety_valve" : null;
        } else {
            return null;
        }
    }

    @Nullable
    private SafetyValveBlockEntity safetyValve() {
        if (this.structure != null && this.structure.safetyValvePos() != null && this.level != null) {
            return this.level.getBlockEntity(this.structure.safetyValvePos()) instanceof SafetyValveBlockEntity valve ? valve : null;
        } else {
            return null;
        }
    }

    public boolean hasBoundValve() {
        return this.safetyValve() != null;
    }

    public float valveOpening() {
        SafetyValveBlockEntity valve = this.safetyValve();
        return valve == null ? 0.0F : valve.opening();
    }

    public float steamPressure() {
        return this.steamPressure;
    }

    private boolean tickPressure(boolean fed) {
        float burst = (float)((Double)LoconauticsConfig.BURST_PRESSURE.get()).doubleValue();
        SafetyValveBlockEntity valve = this.safetyValve();
        if (valve != null) {
            valve.setVenting(this.steamPressure > 0.0F);
        }

        if (valve instanceof SmartSafetyValveBlockEntity smart) {
            smart.updateFromBoiler(burst <= 0.0F ? 0.0F : this.steamPressure / burst);
        }

        float rise = fed
            ? (float)((Double)LoconauticsConfig.PRESSURE_RISE_PER_TICK.get()).doubleValue()
                * this.structure.temperatureCap()
                * this.draftChoke()
                * this.warmthEfficiency()
                * (1.0F + 1.0F * lowWater(this.waterTank.getFluidAmount(), this.waterTank.getCapacity()))
            : 0.0F;
        float vent = (float)((Double)LoconauticsConfig.PRESSURE_VENT_PER_TICK.get()).doubleValue() * this.valveOpening();
        float bleed = fed ? 0.0F : this.idleBleed();
        float draw = this.takeoffDraw();
        float before = this.steamPressure;
        this.steamPressure = Math.max(0.0F, this.steamPressure + rise - vent - bleed - draw);
        if (this.steamPressure < before && this.level.getGameTime() % 20L == 0L) {
            LoconauticsConstants.LOGGER
                .debug(
                    "[boilerfix] bleed pos={} fed={} before={} after={} rise={} vent={} bleed={} draw={} hasTakeoff={} hasValve={} boundValve={} valveOpening={}",
                    new Object[]{
                        this.worldPosition,
                        fed,
                        before,
                        this.steamPressure,
                        rise,
                        vent,
                        bleed,
                        draw,
                        this.structure.hasTakeoff(),
                        this.structure.hasSafetyValve(),
                        this.hasBoundValve(),
                        this.valveOpening()
                    }
                );
        }

        if (this.steamPressure >= (float)((Double)LoconauticsConfig.BURST_PRESSURE.get()).doubleValue()) {
            this.steamPressure = 0.0F;
            this.fail(this.worldPosition, FireboxBlockEntity.FailureCause.OVERPRESSURE);
            return true;
        } else {
            return false;
        }
    }

    private float takeoffDraw() {
        float maxRpm = (float)((Double)LoconauticsConfig.TAKEOFF_MAX_RPM.get()).doubleValue();
        if (maxRpm <= 0.0F) {
            return 0.0F;
        }

        float load = Math.max(0.0F, Math.min(1.0F, this.lastStressRatio)) * Math.min(1.0F, this.shaftRpm() / maxRpm);
        return (float)((Double)LoconauticsConfig.PRESSURE_RISE_PER_TICK.get()).doubleValue() * load * 2.5F;
    }

    private static float lowWater(int amount, int capacity) {
        return capacity <= 0 ? 0.0F : 1.0F - Math.min(1.0F, (float)Math.max(0, amount) / capacity);
    }

    private float idleBleed() {
        boolean canLose = this.structure != null && (this.structure.hasTakeoff() || this.structure.hasSafetyValve() || this.hasBoundValve());
        return canLose ? (float)((Double)LoconauticsConfig.PRESSURE_IDLE_BLEED_PER_TICK.get()).doubleValue() : 0.0F;
    }

    @Nullable
    public String optionalStepKey() {
        return this.dispOptionalStep != null && !this.dispOptionalStep.isEmpty() ? this.dispOptionalStep : null;
    }

    private void forceCold() {
        this.steamPressure = Math.max(0.0F, this.steamPressure - this.idleBleed());
        this.lastBurning = false;
        this.ventWasteGas();
        this.tickDisplay();
        if (this.boilerState != BoilerState.COLD || this.warmupTicks != 0) {
            this.boilerState = BoilerState.COLD;
            this.warmupTicks = 0;
            this.updateLit(false);
            this.setChanged();
            this.sendData();
        }
    }

    private float burnRate() {
        double mass = Math.max(0.0, this.lastTrainMass);
        float stress = Math.max(0.0F, Math.min(1.0F, this.lastStressRatio));
        float load = 1.0F
            + stress * (float)((Double)LoconauticsConfig.BURN_STRESS_FACTOR.get()).doubleValue()
            + (float)(mass / (Double)LoconauticsConfig.BURN_MASS_DIVISOR.get());
        return load * this.structure.burnMultiplier() * (float)((Double)LoconauticsConfig.BURN_RATE_SCALE.get()).doubleValue();
    }

    private boolean consumeFuel() {
        this.lastBurnRate = this.burnRate();
        this.lastBurning = true;
        if (this.burnTimeRemaining > 0.0F) {
            this.burnTimeRemaining = Math.max(0.0F, this.burnTimeRemaining - this.lastBurnRate);
            this.setChanged();
            return true;
        }

        int slot = this.firstFuelSlot();
        if (slot < 0) {
            this.lastBurning = false;
            return false;
        }

        int burnTime = this.inventory.getStackInSlot(slot).getBurnTime(RecipeType.SMELTING);
        if (burnTime <= 0) {
            burnTime = 1600;
        }

        this.burnTimeRemaining = burnTime - this.lastBurnRate;
        this.inventory.extractItem(slot, 1, false);
        this.setChanged();
        return true;
    }

    @Nullable
    private BoilerWaterSource socket() {
        if (this.structure != null && this.structure.injectorPos() != null) {
            BlockEntity be = this.level.getBlockEntity(this.structure.injectorPos());
            if (be instanceof BoilerWaterSource socket) {
                return socket;
            } else {
                return be == null ? null : HullsteelSocketBridge.waterSource(be);
            }
        } else {
            return null;
        }
    }

    @Nullable
    private BoilerWaterSource water() {
        return this.structure == null ? null : this.tankSource;
    }

    private void drainInlets() {
        if (this.inflowCooldown > 0) {
            this.inflowCooldown--;
        }

        if (this.structure != null) {
            int room = this.waterTank.getSpace();
            if (room > 0) {
                for (BlockPos connector : this.structure.connectorPositions()) {
                    if (room <= 0) {
                        break;
                    }

                    if (this.level.getBlockEntity(connector) instanceof BarrelConnectorBlockEntity be) {
                        room -= this.acceptWater(be.drainBuffer(room));
                    }
                }

                BoilerWaterSource socket = this.socket();
                if (socket != null && room > 0) {
                    this.acceptWater(socket.drawWater(room));
                }
            }
        }
    }

    private int acceptWater(int millibuckets) {
        if (millibuckets <= 0) {
            return 0;
        }

        this.inflowCooldown = 20;
        return this.waterTank.fill(new FluidStack(Fluids.WATER, millibuckets), FluidAction.EXECUTE);
    }

    public FluidTank getWaterTank() {
        return this.waterTank;
    }

    public int fillFromHose(FluidStack resource, FluidAction action) {
        if (this.structure != null && resource.getFluid() == Fluids.WATER) {
            int filled = this.waterTank.fill(resource, action);
            if (filled > 0 && action.execute()) {
                this.inflowCooldown = 20;
            }

            return filled;
        } else {
            return 0;
        }
    }

    private boolean drawWater() {
        BoilerWaterSource injector = this.water();
        if (injector == null) {
            this.lastWaterDrawn = 0;
            return false;
        }

        float perTick = (float)((Double)LoconauticsConfig.WATER_PER_BODY_PER_TICK.get()).doubleValue();
        int wanted = Math.round(this.structure.boilerCapacity() * perTick * this.outputFraction());
        if (wanted <= 0) {
            this.lastWaterDrawn = 0;
            return injector.getWaterAmount() > 0;
        }

        int drawn = injector.drawWater(wanted);
        this.lastWaterDrawn = drawn;
        if (drawn <= 0) {
            return false;
        }

        float recycle = (float)((Double)LoconauticsConfig.WATER_RECYCLE_FRACTION.get()).doubleValue();
        injector.returnWater((int)(drawn * recycle));
        return true;
    }

    private void tickStateMachine(boolean burning, boolean watered) {
        BoilerState prev = this.boilerState;
        int warmupTarget = (Integer)LoconauticsConfig.WARMUP_TICKS.get();
        boolean fed = burning && watered;
        switch (this.boilerState) {
            case COLD:
                if (fed) {
                    this.boilerState = BoilerState.WARMING;
                }
                break;
            case WARMING:
                if (fed) {
                    this.warmupTicks = Math.min(warmupTarget, this.warmupTicks + 1);
                    if (this.warmupTicks >= warmupTarget) {
                        this.boilerState = BoilerState.HOT;
                    }
                } else if (!burning && !watered) {
                    this.coolDown();
                } else {
                    this.boilerState = BoilerState.STARVED;
                }
                break;
            case HOT:
                if (!fed) {
                    this.boilerState = BoilerState.STARVED;
                }
                break;
            case STARVED:
                if (fed) {
                    this.boilerState = BoilerState.WARMING;
                } else if (burning) {
                    this.warmupTicks = Math.min(warmupTarget, this.warmupTicks + 1);
                } else {
                    this.coolDown();
                }
        }

        if (this.boilerState != prev) {
            this.setChanged();
            this.sendData();
        }
    }

    private void coolDown() {
        this.warmupTicks = Math.max(0, this.warmupTicks - 1);
        if (this.warmupTicks == 0) {
            this.boilerState = BoilerState.COLD;
        }
    }

    public float outputFraction() {
        if (this.structure != null && !this.lockedOut) {
            float raw = Math.min(1.0F, this.steamPressure);
            return Math.min(raw, this.structure.temperatureCap());
        } else {
            return 0.0F;
        }
    }

    private float draftChoke() {
        float full = ((Integer)LoconauticsConfig.DRAFT_CHOKE_TICKS.get()).intValue();
        return Math.max(0.0F, 1.0F - this.wasteGas / full);
    }

    public static float quantize4(float value) {
        return Math.round(value / 4.0F) * 4.0F;
    }

    public float shaftRpm() {
        if (this.structure != null && !this.lockedOut) {
            float fraction = this.outputFraction();
            if (fraction <= 0.0F) {
                return 0.0F;
            }

            float min = (float)((Double)LoconauticsConfig.TAKEOFF_MIN_RPM.get()).doubleValue();
            float max = (float)((Double)LoconauticsConfig.TAKEOFF_MAX_RPM.get()).doubleValue();
            return quantize4((min + (max - min) * fraction) * this.takeoffRegulator());
        } else {
            return 0.0F;
        }
    }

    private float takeoffRegulator() {
        if (this.structure != null && this.structure.takeoffPos() != null) {
            return this.level.getBlockEntity(this.structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff ? takeoff.regulator() : 1.0F;
        } else {
            return 1.0F;
        }
    }

    public int remainingSeconds() {
        if (this.structure != null && !(this.lastBurnRate <= 0.0F)) {
            float queued = 0.0F;

            for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                ItemStack fuel = this.inventory.getStackInSlot(slot);
                if (isFuel(fuel)) {
                    queued += (float)fuel.getBurnTime(RecipeType.SMELTING) * fuel.getCount();
                }
            }

            float ticks = (this.burnTimeRemaining + queued) / this.lastBurnRate;
            return Math.round(ticks / 20.0F);
        } else {
            return 0;
        }
    }

    public static String formatTime(int sec) {
        int min = sec / 60;
        int hour = min / 60;
        sec = Math.floorMod(sec, 60);
        min = Math.floorMod(min, 60);
        StringBuilder out = new StringBuilder();
        if (hour > 0) {
            out.append(hour).append("h ");
        }

        if (min < 10 && hour > 0) {
            out.append('0');
        }

        if (min > 0 || hour > 0) {
            out.append(min).append("m ");
        }

        if (sec < 10 && (min > 0 || hour > 0)) {
            out.append('0');
        }

        return out.append(sec).append('s').toString();
    }

    public float temperatureFraction() {
        int target = (Integer)LoconauticsConfig.WARMUP_TICKS.get();
        return target <= 0 ? 0.0F : (float)this.warmupTicks / target;
    }

    private static float boilingFraction() {
        double ambient = (Double)LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = (Double)LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        if (working <= 100.0) {
            return 0.0F;
        }

        double ratio = Math.max(0.0, (100.0 - ambient) / (working - ambient));
        return (float)(ratio * ratio);
    }

    private float warmthEfficiency() {
        float boiling = boilingFraction();
        float t = this.temperatureFraction();
        if (t < boiling) {
            return 0.0F;
        }

        if (boiling >= 1.0F) {
            return 1.0F;
        }

        float span = Math.min(1.0F, (t - boiling) / (1.0F - boiling));
        return 0.15F + 0.85F * span;
    }

    public float steamTemperatureCelsius() {
        double ambient = (Double)LoconauticsConfig.AMBIENT_TEMPERATURE_C.get();
        double working = (Double)LoconauticsConfig.WORKING_TEMPERATURE_C.get();
        double curve = Math.sqrt(Math.max(0.0F, this.temperatureFraction()));
        double lowWaterHeat = 1.0 + 0.25F * lowWater(this.dispWater, this.dispWaterCapacity);
        return (float)(ambient + (working - ambient) * curve * lowWaterHeat) + this.dispTemperatureJitter;
    }

    private float rollTemperatureJitter() {
        float amplitude = (float)((Double)LoconauticsConfig.TEMPERATURE_JITTER_C.get()).doubleValue();
        if (!(amplitude <= 0.0F) && this.level != null) {
            float triangular = this.level.random.nextFloat() - this.level.random.nextFloat();
            return triangular * amplitude * this.temperatureFraction();
        } else {
            return 0.0F;
        }
    }

    private float rollWaterJitter() {
        float amplitude = (float)((Double)LoconauticsConfig.WATER_JITTER_MB.get()).doubleValue();
        if (amplitude <= 0.0F || this.level == null) {
            return 0.0F;
        } else if (this.dispWater > 0 && this.inflowCooldown > 0) {
            float triangular = this.level.random.nextFloat() - this.level.random.nextFloat();
            return triangular * amplitude * this.temperatureFraction();
        } else {
            return 0.0F;
        }
    }

    public static String formattedTemperature(FireboxBlockEntity be) {
        float celsius = be.steamTemperatureCelsius();
        return format(celsius);
    }

    private static String format(float celsius) {
        return switch ((LoconauticsConfig.TemperatureUnit)LoconauticsConfig.TEMPERATURE_UNIT.get()) {
            case FAHRENHEIT -> String.format(Locale.ROOT, "%.0f F", celsius * 9.0F / 5.0F + 32.0F);
            case KELVIN -> String.format(Locale.ROOT, "%.0f K", celsius + 273.15F);
            case CELSIUS -> String.format(Locale.ROOT, "%.0f C", celsius);
        };
    }

    public String temperatureFeelKey() {
        float t = this.temperatureFraction();
        if (t < 0.33F) {
            return "cold";
        } else {
            return t < 0.66F ? "warm" : "hot";
        }
    }

    public float maxPossibleSu() {
        return this.structure == null
            ? 0.0F
            : this.structure.boilerCapacity()
                * (float)((Double)LoconauticsConfig.SU_PER_BODY_BLOCK.get()).doubleValue()
                * (float)((Double)LoconauticsConfig.TAKEOFF_MAX_RPM.get()).doubleValue();
    }

    public int getWaterCapacity() {
        return Math.max(0, this.dispWaterCapacity);
    }

    public boolean isDisplayValid() {
        return this.dispValid;
    }

    public float getDisplayPressure() {
        return this.dispPressure;
    }

    public float getDisplayMaxSu() {
        return this.dispMaxSu;
    }

    public float getDisplaySu() {
        return this.dispSu;
    }

    public float getDisplayRpm() {
        return this.dispRpm;
    }

    public float getDisplayEfficiency() {
        return this.dispEfficiency;
    }

    public int getDisplayWater() {
        int capacity = this.getWaterCapacity();
        return Math.max(0, Math.min(capacity, Math.round(this.dispWater + this.dispWaterJitter)));
    }

    public int getDisplayRemaining() {
        return this.dispRemainingSeconds;
    }

    public int getDisplayWasteGas() {
        return this.dispWasteGas;
    }

    public float getLastStressRatio() {
        return this.lastStressRatio;
    }

    public double getLastTrainMass() {
        return this.lastTrainMass;
    }

    public boolean onATrain() {
        return this.lastTrainMass >= 0.0;
    }

    public float getSubLevelSpeed() {
        return this.lastSubSpeed;
    }

    public boolean inSubLevel() {
        return this.inSubLevel;
    }

    public float suOutput() {
        return this.structure == null
            ? 0.0F
            : quantize4(this.structure.boilerCapacity() * (float)((Double)LoconauticsConfig.SU_PER_BODY_BLOCK.get()).doubleValue() * this.shaftRpm());
    }

    private void tickWasteGas(boolean burning) {
        if (burning && !this.hasStack()) {
            this.wasteGas = Math.min(this.wasteGas + 1, (Integer)LoconauticsConfig.DRAFT_CHOKE_TICKS.get());
        } else {
            this.ventWasteGas();
        }
    }

    private void ventWasteGas() {
        this.wasteGas = Math.max(0, this.wasteGas - 2);
    }

    private void tickDisplay() {
        if (--this.displayCooldown <= 0) {
            this.displayCooldown = 20;
            BoilerWaterSource injector = this.water();
            this.dispValid = this.structure != null;
            this.dispSu = this.suOutput();
            this.dispRpm = this.shaftRpm();
            this.dispEfficiency = this.structure == null ? 0.0F : Math.min(1.0F, this.structure.heatRatio()) * this.warmthEfficiency();
            this.dispOverfired = this.structure != null && this.structure.isOverfired();
            this.dispWater = injector == null ? 0 : injector.getWaterAmount();
            this.dispWasteGas = this.wasteGas;
            this.dispStep = this.computeNextStep();
            this.dispOptionalStep = this.computeOptionalStep();
            this.dispRemainingSeconds = this.remainingSeconds();
            this.dispTemperatureJitter = this.rollTemperatureJitter();
            float burstPressure = (float)((Double)LoconauticsConfig.BURST_PRESSURE.get()).doubleValue();
            this.dispPressure = burstPressure <= 0.0F ? 0.0F : this.steamPressure / burstPressure;
            this.dispMaxSu = this.maxPossibleSu();
            this.dispWaterCapacity = this.structure == null ? 0 : this.structure.waterCapacity();
            this.pushTakeoff();
            this.pushSmokestack();
            this.setChanged();
            this.sendData();
        }
    }

    private boolean hasStack() {
        return this.structure != null && this.structure.hasStack();
    }

    private void pushSmokestack() {
        BlockPos base = this.structure == null ? null : this.structure.stackPos();
        if (base != null) {
            BlockPos top = base;

            while (
                top.getY() - base.getY() + 1 < 6
                    && top.getY() < this.level.getMaxBuildHeight() - 1
                    && this.level.getBlockState(top.above()).is(BoilerTags.SMOKESTACK)
            ) {
                top = top.above();
            }

            int power = this.lastBurning ? Math.max(1, Math.round(this.temperatureFraction() * 15.0F)) : 0;

            for (BlockPos at = base; at.getY() <= top.getY(); at = at.above()) {
                if (this.level.getBlockEntity(at) instanceof SmokestackBlockEntity stack) {
                    stack.setBoilerDriven(true);
                    stack.setPower(at.equals(top) ? power : 0);
                    stack.setColor(this.smokeColor());
                }
            }
        }
    }

    private int smokeColor() {
        return switch (this.boilerState) {
            case COLD -> 2104344;
            case WARMING, HOT -> blendSmoke(this.warmthEfficiency());
            case STARVED -> 3814448;
        };
    }

    private static int blendSmoke(float t) {
        int dark = 2104344;
        int light = 15790320;
        int out = 0;

        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = dark >> shift & 0xFF;
            int b = light >> shift & 0xFF;
            out |= Math.round(a + (b - a) * t) << shift;
        }

        return out;
    }

    private void pushTakeoff() {
        if (this.structure != null && this.structure.takeoffPos() != null) {
            if (this.level.getBlockEntity(this.structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff) {
                takeoff.supply(this.shaftRpm(), this.structure.boilerCapacity());
            }
        }
    }

    private boolean tickFailure(boolean watered) {
        boolean dryAndHot = !watered && this.warmupTicks > 0;
        this.dryTicks = dryAndHot ? this.dryTicks + 1 : Math.max(0, this.dryTicks - 2);
        if (this.dryTicks >= (Integer)LoconauticsConfig.CROWN_SHEET_TICKS.get()) {
            this.fail(this.worldPosition, FireboxBlockEntity.FailureCause.CROWN_SHEET);
            return true;
        } else {
            return false;
        }
    }

    private void announceFailure(BlockPos at, FireboxBlockEntity.FailureCause cause) {
        if (this.level instanceof ServerLevel server) {
            MutableComponent var7 = Component.translatable(
                    "loconautics.boiler.failed", new Object[]{Component.translatable(cause.chatKey()).withStyle(ChatFormatting.RED)}
                )
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(Component.translatable(cause.fixKey()).withStyle(ChatFormatting.GRAY));

            for (ServerPlayer player : server.players()) {
                if (player.blockPosition().distSqr(at) < 4096.0) {
                    player.sendSystemMessage(var7);
                }
            }
        }
    }

    private void fail(BlockPos at, FireboxBlockEntity.FailureCause cause) {
        this.announceFailure(at, cause);
        this.lastFailure = cause;
        LoconauticsConstants.LOGGER
            .debug(
                "[boilerfix] fail pos={} cause={} structureNull={} pressure={} realistic={}",
                new Object[]{this.worldPosition, cause, this.structure == null, this.steamPressure, LoconauticsConfig.realistic()}
            );
        float burstPressure = (float)((Double)LoconauticsConfig.BURST_PRESSURE.get()).doubleValue();
        float pressureFraction = cause != FireboxBlockEntity.FailureCause.OVERPRESSURE && !(burstPressure <= 0.0F)
            ? Math.min(1.0F, this.steamPressure / burstPressure)
            : 1.0F;
        this.steamPressure = 0.0F;
        this.dryTicks = 0;
        this.warmupTicks = 0;
        this.boilerState = BoilerState.COLD;
        if (!LoconauticsConfig.realistic()) {
            this.lockedOut = true;
            this.setChanged();
            this.sendData();
        } else {
            float base = (float)((Double)LoconauticsConfig.FAILURE_EXPLOSION_BASE.get()).doubleValue();
            float max = (float)((Double)LoconauticsConfig.FAILURE_EXPLOSION_MAX.get()).doubleValue();
            List<BlockPos> bodies = List.copyOf(this.structure.boilerPositions());
            float scale = Math.min(1.0F, this.structure.boilerCapacity() / 24.0F);
            float power = base + (max - base) * scale;
            Vec3 middle = this.bodyCenter(bodies);
            if (cause == FireboxBlockEntity.FailureCause.OVERPRESSURE) {
                Vec3 back = Vec3.atCenterOf(at);
                Vec3 front = Vec3.atCenterOf(this.frontEnd(bodies, back));
                this.explodeOutward(back, middle, power * 0.75F);
                this.explodeOutward(front, middle, power * 0.75F);
                this.shatterBodyNear(bodies, back, 3.0);
                this.shatterBodyNear(bodies, front, 3.0);
                this.level.destroyBlock(at, false);
                this.level.destroyBlock(BlockPos.containing(front), false);
                if (this.level instanceof ServerLevel server) {
                    TrainAdvancements.boilerBurst(server, at);
                }
            } else {
                float scaled = base * 0.5F + (power - base * 0.5F) * pressureFraction;
                this.level.explode(null, middle.x, middle.y, middle.z, scaled, ExplosionInteraction.BLOCK);
                if (this.level instanceof ServerLevel server) {
                    BoilerBlastEffects.burst(server, middle, scaled, pressureFraction);
                }

                this.shatterBodyNear(bodies, middle, Double.POSITIVE_INFINITY);
                this.level.destroyBlock(at, false);
            }

            this.setChanged();
            this.sendData();
        }
    }

    private void shatterBodyNear(List<BlockPos> bodies, Vec3 center, double reach) {
        double reachSqr = reach * reach;

        for (BlockPos body : bodies) {
            if (Vec3.atCenterOf(body).distanceToSqr(center) <= reachSqr) {
                this.level.destroyBlock(body, false);
            }
        }
    }

    private Vec3 bodyCenter(List<BlockPos> bodies) {
        Vec3 sum = Vec3.ZERO;

        for (BlockPos body : bodies) {
            sum = sum.add(Vec3.atCenterOf(body));
        }

        return sum.scale(1.0 / bodies.size());
    }

    private BlockPos frontEnd(List<BlockPos> bodies, Vec3 back) {
        BlockPos front = bodies.getFirst();

        for (BlockPos body : bodies) {
            if (Vec3.atCenterOf(body).distanceToSqr(back) > Vec3.atCenterOf(front).distanceToSqr(back)) {
                front = body;
            }
        }

        return front;
    }

    private void explodeOutward(Vec3 end, Vec3 middle, float power) {
        Vec3 away = end.subtract(middle);
        Vec3 at = away.lengthSqr() < 1.0E-6 ? end : end.add(away.normalize().scale(0.5));
        this.level.explode(null, at.x, at.y, at.z, power, ExplosionInteraction.BLOCK);
        if (this.level instanceof ServerLevel server) {
            BoilerBlastEffects.jet(server, at, away, power);
        }
    }

    public boolean isLockedOut() {
        return this.lockedOut;
    }

    public void clearLockout() {
        if (this.lockedOut) {
            this.lockedOut = false;
            this.dryTicks = 0;
            this.setChanged();
            this.sendData();
        }
    }

    private void readSubLevelSpeed() {
        SubLevel sub = Sable.HELPER.getContaining(this);
        this.inSubLevel = sub != null;
        if (sub == null) {
            this.lastSubSpeed = 0.0F;
        } else {
            Vector3d center = new Vector3d(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5);
            Vector3d now = sub.logicalPose().transformPosition(center, new Vector3d());
            Vector3d before = sub.lastPose().transformPosition(center, new Vector3d());
            this.lastSubSpeed = (float)(now.sub(before).length() * 20.0);
        }
    }

    private void readAxle() {
        this.lastStressRatio = this.readNetworkStress();
        this.lastTrainMass = this.readTrainMass();
    }

    private float readNetworkStress() {
        if (this.structure != null && this.structure.takeoffPos() != null) {
            return this.level.getBlockEntity(this.structure.takeoffPos()) instanceof SteamTakeoffBlockEntity takeoff ? takeoff.networkStressRatio() : 0.0F;
        } else {
            return 0.0F;
        }
    }

    private double readTrainMass() {
        if (!(this.level instanceof ServerLevel)) {
            return -1.0;
        } else if (Sable.HELPER.getContaining(this) instanceof ServerSubLevel serverSub) {
            BearingAxleBlockEntity axle = BearingAxleValidity.resolve(serverSub);
            return axle == null ? -1.0 : axle.getTrainMass();
        } else {
            return -1.0;
        }
    }

    @Nullable
    public BlockPos getControllerPos() {
        return this.controllerPos;
    }

    public void setControllerPos(@Nullable BlockPos pos) {
        BlockPos next = pos == null ? null : pos.immutable();
        if (!Objects.equals(this.controllerPos, next)) {
            this.controllerPos = next;
            this.setChanged();
            this.sendData();
        }
    }

    public boolean insertFuel(Player player, ItemStack held) {
        if (!isFuel(held)) {
            return false;
        }

        ItemStack leftover = ItemHandlerHelper.insertItem(this.inventory, held.copy(), false);
        if (leftover.getCount() == held.getCount()) {
            return false;
        }

        if (!player.isCreative()) {
            held.setCount(leftover.getCount());
        }

        return true;
    }

    private void resizeFuel(int slots) {
        if (this.inventory.getSlots() != slots) {
            List<ItemStack> kept = new ArrayList<>();

            for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                kept.add(this.inventory.getStackInSlot(slot));
            }

            this.inventory.setSize(slots);

            for (int slot = 0; slot < kept.size(); slot++) {
                ItemStack stack = kept.get(slot);
                if (!stack.isEmpty()) {
                    if (slot < slots) {
                        this.inventory.setStackInSlot(slot, stack);
                    } else {
                        this.spill(stack);
                    }
                }
            }

            this.setChanged();
        }
    }

    private void spill(ItemStack stack) {
        if (this.level != null && !stack.isEmpty()) {
            Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), stack);
        }
    }

    private int firstFuelSlot() {
        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            if (isFuel(this.inventory.getStackInSlot(slot))) {
                return slot;
            }
        }

        return -1;
    }

    public ItemStack firstFuel() {
        int slot = this.firstFuelSlot();
        return slot < 0 ? ItemStack.EMPTY : this.inventory.getStackInSlot(slot);
    }

    public int fuelCapacity() {
        ItemStack fuel = this.firstFuel();
        int perSlot = Math.min(this.inventory.getSlotLimit(0), fuel.isEmpty() ? 64 : fuel.getMaxStackSize());
        return this.inventory.getSlots() * perSlot;
    }

    public int fuelHeld() {
        int held = 0;

        for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
            held += this.inventory.getStackInSlot(slot).getCount();
        }

        return held;
    }

    public void refreshGroup() {
        if (this.level != null) {
            FireboxGroups.rerender(this.level, this.worldPosition);
        }
    }

    public void dropFuel() {
        if (this.level != null) {
            for (int slot = 0; slot < this.inventory.getSlots(); slot++) {
                ItemStack held = this.inventory.getStackInSlot(slot).copy();
                this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
                this.spill(held);
            }
        }
    }

    public void parkWaterInBarrels() {
        if (this.level != null) {
            int totalCapacity = this.trackedCapacity();
            int water = this.waterTank.getFluidAmount();

            for (Entry<BlockPos, Integer> barrel : this.trackedBarrels.entrySet()) {
                if (this.level.getBlockEntity(barrel.getKey()) instanceof BoilerBarrelBlockEntity be) {
                    be.storeWater(shareOf(water, barrel.getValue(), totalCapacity));
                }
            }

            this.trackedBarrels.clear();
            this.waterTank.setFluid(FluidStack.EMPTY);
        }
    }

    private void reconcileBarrels() {
        Set<BlockPos> current = this.structure == null ? Set.of() : new HashSet<>(this.structure.boilerPositions());
        int totalCapacity = this.trackedCapacity();
        int water = this.waterTank.getFluidAmount();
        int leaving = 0;
        Iterator<Entry<BlockPos, Integer>> it = this.trackedBarrels.entrySet().iterator();

        while (it.hasNext()) {
            Entry<BlockPos, Integer> barrel = it.next();
            BlockPos pos = barrel.getKey();
            if (!current.contains(pos) && this.level.isLoaded(pos)) {
                int share = shareOf(water, barrel.getValue(), totalCapacity);
                if (!this.level.getBlockState(pos).is(BoilerTags.BOILER_BODY)) {
                    leaving += share;
                } else if (this.level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity be) {
                    be.storeWater(share);
                    leaving += share;
                }

                it.remove();
            }
        }

        if (leaving > 0) {
            this.waterTank.drain(leaving, FluidAction.EXECUTE);
        }

        for (BlockPos pos : current) {
            this.trackedBarrels.put(pos, BoilerContributions.waterCapacityOf(this.level.getBlockState(pos)));
        }

        this.setChanged();
    }

    private int trackedCapacity() {
        int total = 0;

        for (int capacity : this.trackedBarrels.values()) {
            total += capacity;
        }

        return total;
    }

    private static int shareOf(int water, int capacity, int totalCapacity) {
        return totalCapacity <= 0 ? 0 : (int)((long)water * capacity / totalCapacity);
    }

    private void reclaimParkedWater() {
        for (BlockPos pos : this.structure.boilerPositions()) {
            int room = this.waterTank.getSpace();
            if (room <= 0) {
                return;
            }

            if (this.level.getBlockEntity(pos) instanceof BoilerBarrelBlockEntity barrel) {
                int taken = barrel.takeWater(room);
                if (taken > 0) {
                    this.waterTank.fill(new FluidStack(Fluids.WATER, taken), FluidAction.EXECUTE);
                }
            }
        }
    }

    public static void transferControllerData(FireboxBlockEntity oldController, Level level, BlockPos oldPos, BlockPos newPos, Direction face) {
        if (level.getBlockEntity(newPos) instanceof FireboxBlockEntity newController) {
            newController.copyControllerDataFrom(oldController);
            oldController.resetControllerData();
            newController.rescanCooldown = 0;
            newController.setChanged();
            newController.sendData();
            oldController.setChanged();
            oldController.sendData();
        }
    }

    private void copyControllerDataFrom(FireboxBlockEntity other) {
        this.inventory.setSize(other.inventory.getSlots());

        for (int slot = 0; slot < other.inventory.getSlots(); slot++) {
            this.inventory.setStackInSlot(slot, other.inventory.getStackInSlot(slot).copy());
        }

        this.waterTank.setCapacity(other.waterTank.getCapacity());
        this.waterTank.setFluid(other.waterTank.getFluid().copy());
        this.trackedBarrels.clear();
        this.trackedBarrels.putAll(other.trackedBarrels);
        this.boilerState = other.boilerState;
        this.warmupTicks = other.warmupTicks;
        this.burnTimeRemaining = other.burnTimeRemaining;
        this.dryTicks = other.dryTicks;
        this.lockedOut = other.lockedOut;
        this.lastFailure = other.lastFailure;
        this.steamPressure = other.steamPressure;
        this.lastBurning = other.lastBurning;
        this.lastBurnRate = other.lastBurnRate;
        this.manualDoorsOpen = other.manualDoorsOpen;
    }

    private void resetControllerData() {
        this.inventory.setSize(1);
        this.inventory.setStackInSlot(0, ItemStack.EMPTY);
        this.waterTank.setFluid(FluidStack.EMPTY);
        this.waterTank.setCapacity(0);
        this.trackedBarrels.clear();
        this.boilerState = BoilerState.COLD;
        this.warmupTicks = 0;
        this.burnTimeRemaining = 0.0F;
        this.dryTicks = 0;
        this.lockedOut = false;
        this.lastFailure = null;
        this.steamPressure = 0.0F;
        this.lastBurning = false;
        this.lastBurnRate = 0.0F;
        this.manualDoorsOpen = false;
        this.structure = null;
    }

    public void shiftReferences(BlockPos shift) {
        if (this.controllerPos != null) {
            this.controllerPos = this.controllerPos.offset(shift);
        }

        Map<BlockPos, Integer> moved = new HashMap<>();
        this.trackedBarrels.forEach((pos, capacity) -> moved.put(pos.offset(shift), capacity));
        this.trackedBarrels.clear();
        this.trackedBarrels.putAll(moved);
        this.rescanCooldown = 0;
        this.setChanged();
    }

    public void onBecameController() {
        this.rescanCooldown = 0;
        this.setChanged();
        this.sendData();
    }

    private void updateLit(boolean burning) {
        BlockState state = this.getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT)) {
            if ((Boolean)state.getValue(BlockStateProperties.LIT) != burning) {
                this.level.setBlock(this.worldPosition, (BlockState)state.setValue(BlockStateProperties.LIT, burning), 3);
            }
        }
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (this.level != null) {
            BlockPos controller = FireboxGroups.controllerOf(this.level, this.worldPosition);
            if (controller != null && !controller.equals(this.worldPosition) && this.level.getBlockEntity(controller) instanceof FireboxBlockEntity owner) {
                return owner.addToGoggleTooltip(tooltip, isPlayerSneaking);
            }
        }

        LoconauticsLang.translate("gui.goggles.boiler_stats").forGoggles(tooltip);
        this.addFuelSlot(tooltip);
        this.addFailureWarning(tooltip);
        if (this.lockedOut) {
            LoconauticsLang.translate("gui.goggles.boiler_locked").style(ChatFormatting.RED).forGoggles(tooltip);
            return true;
        } else if (!this.dispValid) {
            LoconauticsLang.translate("gui.goggles.boiler_incomplete").style(ChatFormatting.RED).forGoggles(tooltip);
            this.addNextStep(tooltip);
            return true;
        } else {
            LoconauticsLang.translate("gui.goggles.boiler_state").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate("gui.goggles.boiler_state_" + this.boilerState.name().toLowerCase(Locale.ROOT))
                .style(this.stateColor())
                .forGoggles(tooltip, 1);
            LoconauticsLang.translate("gui.goggles.gauge_temperature").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate("gui.goggles.boiler_feel_" + this.temperatureFeelKey()).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
            this.addNextStep(tooltip);
            this.addOptionalStep(tooltip);
            return true;
        }
    }

    private void addFuelSlot(List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.boiler_fuel").style(ChatFormatting.GRAY).forGoggles(tooltip);
        if (this.firstFuel().isEmpty()) {
            LoconauticsLang.translate("gui.goggles.boiler_fuel_empty").style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        } else {
            LoconauticsLang.builder()
                .add(this.firstFuel().getHoverName().copy())
                .text(" " + this.fuelHeld() + "/" + this.fuelCapacity())
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip, 1);
        }
    }

    private static FireboxBlockEntity.FailureCause readFailure(String name) {
        if (name != null && !name.isEmpty()) {
            try {
                return FireboxBlockEntity.FailureCause.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    private void addFailureWarning(List<Component> tooltip) {
        if (this.lastFailure != null) {
            LoconauticsLang.translate("gui.goggles.boiler_last_failure").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate(this.lastFailure.langKey()).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }
    }

    private void addNextStep(List<Component> tooltip) {
        String step = this.nextStepKey();
        if (step != null) {
            LoconauticsLang.translate("gui.goggles.boiler_next_step").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate("gui.goggles.boiler_step_" + step).style(ChatFormatting.YELLOW).forGoggles(tooltip, 1);
        }
    }

    private void addOptionalStep(List<Component> tooltip) {
        String step = this.optionalStepKey();
        if (step != null) {
            LoconauticsLang.translate("gui.goggles.boiler_optional_step").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate("gui.goggles.boiler_step_" + step).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        }
    }

    private ChatFormatting stateColor() {
        return switch (this.boilerState) {
            case COLD -> ChatFormatting.DARK_GRAY;
            case WARMING -> ChatFormatting.GOLD;
            case HOT -> ChatFormatting.GREEN;
            case STARVED -> ChatFormatting.RED;
        };
    }

    public BoilerState getBoilerState() {
        return this.boilerState;
    }

    public int getWarmupTicks() {
        return this.warmupTicks;
    }

    public float getBurnTimeRemaining() {
        return this.burnTimeRemaining;
    }

    public float getLastBurnRate() {
        return this.lastBurnRate;
    }

    public int getLastWaterDrawn() {
        return this.lastWaterDrawn;
    }

    public boolean hasValidStructure() {
        return this.structure != null;
    }

    @Nullable
    public BoilerStructure getStructure() {
        return this.structure;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    protected void saveAdditional(CompoundTag tag, Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.put("WaterTank", this.waterTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("TankCapacity", this.waterTank.getCapacity());
        tag.putString("BoilerState", this.boilerState.name());
        tag.putInt("WarmupTicks", this.warmupTicks);
        tag.putFloat("BurnTimeRemaining", this.burnTimeRemaining);
        tag.putInt("DryTicks", this.dryTicks);
        tag.putInt("WasteGas", this.wasteGas);
        tag.putBoolean("LockedOut", this.lockedOut);
        tag.putString("LastFailure", this.lastFailure == null ? "" : this.lastFailure.name());
        tag.putBoolean("DispValid", this.dispValid);
        tag.putFloat("DispSu", this.dispSu);
        tag.putFloat("DispRpm", this.dispRpm);
        tag.putFloat("DispEfficiency", this.dispEfficiency);
        tag.putBoolean("DispOverfired", this.dispOverfired);
        tag.putInt("DispWater", this.dispWater);
        tag.putInt("DispWasteGas", this.dispWasteGas);
        tag.putInt("DispRemaining", this.dispRemainingSeconds);
        tag.putFloat("DispTempJitter", this.dispTemperatureJitter);
        tag.putInt("DispWaterDrawn", this.lastWaterDrawn);
        tag.putFloat("DispPressure", this.dispPressure);
        tag.putFloat("SteamPressure", this.steamPressure);
        tag.putFloat("DispMaxSu", this.dispMaxSu);
        tag.putInt("DispWaterCapacity", this.dispWaterCapacity);
        tag.putFloat("DispBurnRate", this.lastBurnRate);
        tag.putFloat("DispStress", this.lastStressRatio);
        tag.putDouble("DispMass", this.lastTrainMass);
        tag.putFloat("DispSubSpeed", this.lastSubSpeed);
        tag.putBoolean("DispInSubLevel", this.inSubLevel);
        tag.putString("DispStep", this.dispStep == null ? "" : this.dispStep);
        tag.putString("DispOptionalStep", this.dispOptionalStep == null ? "" : this.dispOptionalStep);
        tag.putBoolean("DoorsOpen", this.manualDoorsOpen);
        if (this.controllerPos != null) {
            tag.putLong("Controller", this.controllerPos.asLong());
        }

        ListTag barrels = new ListTag();

        for (Entry<BlockPos, Integer> barrel : this.trackedBarrels.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", barrel.getKey().asLong());
            entry.putInt("Capacity", barrel.getValue());
            barrels.add(entry);
        }

        tag.put("TrackedBarrels", barrels);
    }

    protected void loadAdditional(CompoundTag tag, Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack fuelBefore = this.inventory.getStackInSlot(0).copy();
        BlockPos ownerBefore = this.controllerPos;
        this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        this.controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        this.waterTank.setCapacity(tag.getInt("TankCapacity"));
        this.waterTank.readFromNBT(registries, tag.getCompound("WaterTank"));

        try {
            this.boilerState = BoilerState.valueOf(tag.getString("BoilerState"));
        } catch (IllegalArgumentException e) {
            this.boilerState = BoilerState.COLD;
        }

        this.warmupTicks = tag.getInt("WarmupTicks");
        this.burnTimeRemaining = tag.getFloat("BurnTimeRemaining");
        this.dryTicks = tag.getInt("DryTicks");
        this.wasteGas = tag.getInt("WasteGas");
        this.lockedOut = tag.getBoolean("LockedOut");
        this.lastFailure = readFailure(tag.getString("LastFailure"));
        this.dispValid = tag.getBoolean("DispValid");
        this.dispSu = tag.getFloat("DispSu");
        this.dispRpm = tag.getFloat("DispRpm");
        this.dispEfficiency = tag.getFloat("DispEfficiency");
        this.dispOverfired = tag.getBoolean("DispOverfired");
        this.dispWater = tag.getInt("DispWater");
        this.dispWasteGas = tag.getInt("DispWasteGas");
        this.dispRemainingSeconds = tag.getInt("DispRemaining");
        this.dispTemperatureJitter = tag.getFloat("DispTempJitter");
        this.lastWaterDrawn = tag.getInt("DispWaterDrawn");
        this.dispPressure = tag.getFloat("DispPressure");
        this.steamPressure = tag.getFloat("SteamPressure");
        this.dispMaxSu = tag.getFloat("DispMaxSu");
        this.dispWaterCapacity = tag.getInt("DispWaterCapacity");
        this.lastBurnRate = tag.getFloat("DispBurnRate");
        this.lastStressRatio = tag.getFloat("DispStress");
        this.lastTrainMass = tag.contains("DispMass") ? tag.getDouble("DispMass") : -1.0;
        this.lastSubSpeed = tag.getFloat("DispSubSpeed");
        this.inSubLevel = tag.getBoolean("DispInSubLevel");
        this.dispStep = tag.getString("DispStep");
        this.dispOptionalStep = tag.getString("DispOptionalStep");
        this.manualDoorsOpen = tag.getBoolean("DoorsOpen");
        this.trackedBarrels.clear();
        ListTag barrels = tag.getList("TrackedBarrels", 10);

        for (int i = 0; i < barrels.size(); i++) {
            CompoundTag entry = barrels.getCompound(i);
            this.trackedBarrels.put(BlockPos.of(entry.getLong("Pos")), entry.getInt("Capacity"));
        }

        if (StrandTemplates.isPlacing()) {
            this.trackedBarrels.clear();
            this.steamPressure = 0.0F;
            this.dispPressure = 0.0F;
        }

        if (this.level != null
            && this.level.isClientSide
            && (!ItemStack.matches(fuelBefore, this.inventory.getStackInSlot(0)) || !Objects.equals(ownerBefore, this.controllerPos))) {
            this.refreshGroup();
        }
    }

    public CompoundTag getUpdateTag(Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sendData() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public enum FailureCause {
        CROWN_SHEET,
        OVERPRESSURE;

        public String langKey() {
            return "gui.goggles.boiler_failure_" + this.name().toLowerCase(Locale.ROOT);
        }

        public String chatKey() {
            return "loconautics.boiler.cause." + this.name().toLowerCase(Locale.ROOT);
        }

        public String fixKey() {
            return "loconautics.boiler.fix." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    private final class TankSource implements BoilerWaterSource {
        @Override
        public int getWaterAmount() {
            return FireboxBlockEntity.this.waterTank.getFluidAmount();
        }

        @Override
        public int getWaterCapacity() {
            return FireboxBlockEntity.this.waterTank.getCapacity();
        }

        @Override
        public int drawWater(int millibuckets) {
            return millibuckets <= 0 ? 0 : FireboxBlockEntity.this.waterTank.drain(millibuckets, FluidAction.EXECUTE).getAmount();
        }

        @Override
        public void returnWater(int millibuckets) {
            if (millibuckets > 0) {
                FireboxBlockEntity.this.waterTank.fill(new FluidStack(Fluids.WATER, millibuckets), FluidAction.EXECUTE);
            }
        }

        @Override
        public void setWaterCapacity(int millibuckets) {
            int capacity = Math.max(0, millibuckets);
            if (FireboxBlockEntity.this.waterTank.getCapacity() != capacity) {
                FireboxBlockEntity.this.waterTank.setCapacity(capacity);
                int overflow = FireboxBlockEntity.this.waterTank.getFluidAmount() - capacity;
                if (overflow > 0) {
                    FireboxBlockEntity.this.waterTank.drain(overflow, FluidAction.EXECUTE);
                }

                FireboxBlockEntity.this.setChanged();
            }
        }
    }
}
