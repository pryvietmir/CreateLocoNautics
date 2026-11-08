package com.lycoris.loconautics.content.bearingaxle;

import com.lycoris.loconautics.common.LinkHolder;
import com.lycoris.loconautics.common.RedstoneLinkPair;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.drive.AxleGearing;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BearingAxleBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation, MenuProvider, LinkHolder {
    private double totalMassKg;
    private double slopeAngleDeg;
    private boolean opposed;
    private int driveGroup = 1;
    private int groupSize;
    private double ownStrength;
    private double netStrength;
    private double slipFactor = 1.0;
    private long lastClassified = Long.MIN_VALUE;
    private int gear;
    private double gearRatio = 1.0;
    private double maxGearRatio = 1.0;
    private final RedstoneLinkPair links = new RedstoneLinkPair(this, s -> this.setLeftLink(s > 0), s -> this.setRightLink(s > 0));
    private boolean leftActive;
    private boolean rightActive;
    private boolean leftLink;
    private boolean leftRedstone;
    private boolean rightLink;
    private boolean rightRedstone;
    private boolean drivingBogey;
    private long placedTime;
    private BearingAxleValidity.Status validity = BearingAxleValidity.Status.VALID;
    @Nullable
    private BlockPos driverBogey;

    public void setPlacedTime(long time) {
        this.placedTime = time;
        this.setChanged();
    }

    public long getPlacedTime() {
        return this.placedTime;
    }

    public void setValidity(BearingAxleValidity.Status status, @Nullable BlockPos bogey) {
        this.driverBogey = bogey;
        if (this.level != null) {
            this.lastClassified = this.level.getGameTime();
        }

        if (this.validity != status) {
            this.validity = status;
            this.setChanged();
            this.sendData();
        }
    }

    public BearingAxleValidity.Status getValidity() {
        return this.validity;
    }

    @Nullable
    public BlockPos driverBogeyPos() {
        return this.driverBogey;
    }

    public void tick() {
        super.tick();
        if (this.level != null && !this.level.isClientSide && this.level.getGameTime() % 20L == 0L) {
            if (this.level.getGameTime() - this.lastClassified >= 40L) {
                BlockPos bogey = BearingAxleValidity.driverBogey(this.level, this.worldPosition);
                if (bogey == null) {
                    this.setValidity(BearingAxleValidity.Status.NO_BOGEY, null);
                } else {
                    this.setValidity(
                        BogeyFacing.onAxis(this.level.getBlockState(bogey), BearingAxleValidity.axleFrontLocal(this))
                            ? BearingAxleValidity.Status.VALID
                            : BearingAxleValidity.Status.WRONG_AXIS,
                        bogey
                    );
                }
            }
        }
    }

    public void setConsistLoad(
        double massShare, boolean opposed, int group, double ownStrength, double netStrength, double slipFactor, int groupSize, AxleGearing.Gearing gearing
    ) {
        boolean stressChanged = massShare != this.totalMassKg || opposed != this.opposed || Math.abs(gearing.ratio() - this.gearRatio) > this.gearRatio * 0.005;
        boolean displayChanged = group != this.driveGroup
            || groupSize != this.groupSize
            || Math.abs(ownStrength - this.ownStrength) > 0.5
            || Math.abs(netStrength - this.netStrength) > 0.5
            || Math.abs(slipFactor - this.slipFactor) > 0.01
            || Math.abs(gearing.maxRatio() - this.maxGearRatio) > this.maxGearRatio * 0.01;
        this.totalMassKg = massShare;
        this.opposed = opposed;
        if (stressChanged) {
            this.gearRatio = gearing.ratio();
        }

        this.maxGearRatio = gearing.maxRatio();
        this.driveGroup = group;
        this.ownStrength = ownStrength;
        this.netStrength = netStrength;
        this.slipFactor = slipFactor;
        this.groupSize = groupSize;
        if (stressChanged) {
            this.pushStress();
        }

        if (stressChanged || displayChanged) {
            this.setChanged();
            this.sendData();
        }
    }

    private void pushStress() {
        float stress = this.calculateStressApplied();
        if (this.hasNetwork()) {
            this.getOrCreateNetwork().updateStressFor(this, stress);
        }

        this.networkDirty = false;
    }

    public void setGearSignal(int signal) {
        int clamped = Math.max(0, Math.min(15, signal));
        if (clamped != this.gear) {
            if (this.level != null && !this.level.isClientSide) {
            }

            this.gear = clamped;
            if (this.gear == 0) {
                this.resetGearing();
            }

            this.setChanged();
            this.sendData();
        }
    }

    public void resetGearing() {
        this.maxGearRatio = 1.0;
        if (this.gearRatio != 1.0) {
            this.gearRatio = 1.0;
            this.pushStress();
            this.setChanged();
            this.sendData();
        }
    }

    public int gear() {
        return this.gear;
    }

    public double gearRatio() {
        return this.gearRatio;
    }

    public double maxGearRatio() {
        return this.maxGearRatio;
    }

    public double directImpact(double massShare, boolean opposed) {
        if (this.validity != BearingAxleValidity.Status.VALID) {
            return 0.0;
        }

        double slope = LoconauticsConfig.SLOPE_EFFECTS_ENABLED.get() ? (Double)LoconauticsConfig.SLOPE_STRESS_FACTOR.get() * this.slopeAngleDeg : 0.0;
        return AxleGearing.stressImpact(
            (Double)LoconauticsConfig.BASE_IMPACT.get(),
            massShare,
            (Double)LoconauticsConfig.MASS_DIVISOR.get(),
            slope,
            opposed ? (Double)LoconauticsConfig.OPPOSED_STRESS_FACTOR.get() : 1.0
        );
    }

    public double networkCapacity() {
        return this.capacity;
    }

    public double networkStress() {
        return this.stress;
    }

    public double ownNetworkStress() {
        return this.lastStressApplied * Math.abs(this.getTheoreticalSpeed());
    }

    public double getTrainMass() {
        return this.totalMassKg;
    }

    public float getNetworkStressAbsolute() {
        return this.stress;
    }

    public float getNetworkStressRatio() {
        return this.capacity <= 0.0F ? 0.0F : this.stress / this.capacity;
    }

    public void setSlopeAngle(double degrees) {
        double a = Math.max(0.0, degrees);
        if (!(Math.abs(a - this.slopeAngleDeg) < 0.25)) {
            this.slopeAngleDeg = a;
            this.pushStress();
            this.setChanged();
            this.sendData();
        }
    }

    public double getSlopeAngle() {
        return this.slopeAngleDeg;
    }

    public float calculateStressApplied() {
        if (this.validity != BearingAxleValidity.Status.VALID) {
            this.lastStressApplied = 0.0F;
            return 0.0F;
        } else {
            this.lastStressApplied = (float)(this.directImpact(this.totalMassKg, this.opposed) * this.gearRatio);
            return this.lastStressApplied;
        }
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        LoconauticsLang.translate("gui.goggles.bearing_axle_stats").forGoggles(tooltip);
        if (this.validity != BearingAxleValidity.Status.VALID) {
            LoconauticsLang.translate(switch (this.validity) {
                case BOGEY_TAKEN -> "gui.goggles.bearing_axle_duplicate";
                case WRONG_AXIS -> "gui.goggles.bearing_axle_wrong_axis";
                default -> "gui.goggles.bearing_axle_no_bogey";
            }).style(ChatFormatting.RED).forGoggles(tooltip, 1);
        }

        LoconauticsLang.translate("gui.goggles.load_share").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(this.totalMassKg)
            .translate("gui.goggles.unit_kg", new Object[0])
            .style(this.totalMassKg > 0.0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY)
            .forGoggles(tooltip, 1);
        float impact = this.calculateStressApplied();
        LoconauticsLang.translate("gui.goggles.stress_impact").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(impact).translate("gui.goggles.unit_su", new Object[0]).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        float stressAbs = this.getNetworkStressAbsolute();
        LoconauticsLang.translate("gui.goggles.network_stress").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(stressAbs)
            .translate("gui.goggles.unit_su", new Object[0])
            .style(stressAbs > 0.0F ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)
            .forGoggles(tooltip, 1);
        float stressRatio = this.getNetworkStressRatio();
        LoconauticsLang.translate("gui.goggles.network_stress_ratio").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(stressRatio * 100.0F)
            .translate("gui.goggles.unit_percent", new Object[0])
            .style(stressRatio >= 1.0F ? ChatFormatting.RED : (stressRatio >= 0.75F ? ChatFormatting.GOLD : ChatFormatting.GREEN))
            .forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.rpm").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(Math.abs(this.getSpeed())).translate("gui.goggles.unit_rpm", new Object[0]).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        this.addGearTooltip(tooltip);
        this.addConsistTooltip(tooltip);
        return true;
    }

    private void addGearTooltip(List<Component> tooltip) {
        if (this.validity == BearingAxleValidity.Status.VALID) {
            LoconauticsLang.translate("gui.goggles.axle_gear").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate("gui.goggles.axle_gear_value", this.gear, 15)
                .style(this.gear == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
            LoconauticsLang.translate("gui.goggles.network_capacity").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.number(this.capacity).translate("gui.goggles.unit_su", new Object[0]).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            double baseline = TrainPropulsion.bearingAxleCapBpt() * 20.0;
            double ceiling = TrainPropulsion.speedCeilingMps();
            LoconauticsLang.translate("gui.goggles.geared_top_speed").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.number(Math.min(ceiling, baseline * this.gearRatio))
                .translate("gui.goggles.unit_bps", new Object[0])
                .style(this.gearRatio > 1.0 ? ChatFormatting.GOLD : ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
            LoconauticsLang.translate("gui.goggles.axle_target_speed").style(ChatFormatting.GRAY).forGoggles(tooltip);
            double rpm = this.isOverStressed() ? 0.0 : Math.abs(this.getSpeed());
            LoconauticsLang.number(Math.min(ceiling, AxleGearing.gearedSpeed(baseline, this.gearRatio, rpm)))
                .translate("gui.goggles.unit_bps", new Object[0])
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
        }
    }

    private void addConsistTooltip(List<Component> tooltip) {
        if (this.validity == BearingAxleValidity.Status.VALID) {
            LoconauticsLang.translate("gui.goggles.drive_group").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.translate(this.driveGroup < 0 ? "gui.goggles.drive_group_reverse" : "gui.goggles.drive_group_forward", this.groupSize)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);
            LoconauticsLang.translate("gui.goggles.axle_output").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.number(this.ownStrength).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            LoconauticsLang.translate("gui.goggles.consist_net").style(ChatFormatting.GRAY).forGoggles(tooltip);
            LoconauticsLang.number(this.netStrength)
                .style(Math.abs(this.netStrength) < 0.001 ? ChatFormatting.RED : ChatFormatting.GREEN)
                .forGoggles(tooltip, 1);
            if (this.slipFactor < 1.0) {
                LoconauticsLang.translate("gui.goggles.wheel_slip").style(ChatFormatting.GRAY).forGoggles(tooltip);
                LoconauticsLang.number((1.0 - this.slipFactor) * 100.0)
                    .translate("gui.goggles.unit_percent", new Object[0])
                    .style(this.slipFactor <= 0.5 ? ChatFormatting.RED : ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
            }

            if (this.opposed) {
                LoconauticsLang.translate("gui.goggles.axle_opposed").style(ChatFormatting.RED).forGoggles(tooltip, 1);
            }
        }
    }

    public void initialize() {
        super.initialize();
        if (this.level != null && !this.level.isClientSide) {
            this.links.register(this.level);
        }
    }

    @Override
    public void releaseLinks() {
        if (this.level != null) {
            this.links.unregister(this.level);
        }
    }

    public void invalidate() {
        if (this.level != null) {
            this.links.unregister(this.level);
        }

        super.invalidate();
    }

    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (this.level != null) {
            this.links.unregister(this.level);
        }
    }

    public void setLeftLink(boolean active) {
        if (this.leftLink != active) {
            this.leftLink = active;
            this.refreshIndicators();
        }
    }

    public void setRightLink(boolean active) {
        if (this.rightLink != active) {
            this.rightLink = active;
            this.refreshIndicators();
        }
    }

    public void setLeftRedstone(boolean active) {
        if (this.leftRedstone != active) {
            this.leftRedstone = active;
            this.refreshIndicators();
        }
    }

    public void setRightRedstone(boolean active) {
        if (this.rightRedstone != active) {
            this.rightRedstone = active;
            this.refreshIndicators();
        }
    }

    public boolean isLeftLink() {
        return this.leftLink;
    }

    public boolean isRightLink() {
        return this.rightLink;
    }

    public boolean isLeftRedstone() {
        return this.leftRedstone;
    }

    public boolean isRightRedstone() {
        return this.rightRedstone;
    }

    private void refreshIndicators() {
        boolean l = this.leftLink || this.leftRedstone;
        boolean r = this.rightLink || this.rightRedstone;
        if (l != this.leftActive || r != this.rightActive) {
            this.leftActive = l;
            this.rightActive = r;
            this.setChanged();
            this.sendData();
        }
    }

    public void setLeftFrequency(ItemStack first, ItemStack second) {
        this.links.setFirstFrequency(first, second);
        if (this.level != null && !this.level.isClientSide) {
            this.links.register(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public void setRightFrequency(ItemStack first, ItemStack second) {
        this.links.setSecondFrequency(first, second);
        if (this.level != null && !this.level.isClientSide) {
            this.links.register(this.level);
        }

        this.setChanged();
        this.sendData();
    }

    public int getSteerInput() {
        if (this.leftActive == this.rightActive) {
            return 0;
        } else {
            return this.leftActive ? -1 : 1;
        }
    }

    public ItemStack getLeftFreqFirst() {
        return this.links.getFirstFreqA();
    }

    public ItemStack getLeftFreqSecond() {
        return this.links.getFirstFreqB();
    }

    public ItemStack getRightFreqFirst() {
        return this.links.getSecondFreqA();
    }

    public ItemStack getRightFreqSecond() {
        return this.links.getSecondFreqB();
    }

    public Component getDisplayName() {
        return Component.translatable("block.loconautics.bearing_axle");
    }

    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BearingAxleMenu((MenuType<?>)LoconauticsRegistries.BEARING_AXLE_MENU.get(), id, inventory, this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buffer) {
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.left"));
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.translatable("gui.loconautics.bind_frequency.right"));
        super.sendToMenu(buffer);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("TotalMassKg", this.totalMassKg);
        tag.putDouble("SlopeAngle", this.slopeAngleDeg);
        tag.putBoolean("Opposed", this.opposed);
        tag.putInt("DriveGroup", this.driveGroup);
        tag.putInt("GroupSize", this.groupSize);
        tag.putDouble("OwnStrength", this.ownStrength);
        tag.putDouble("NetStrength", this.netStrength);
        tag.putDouble("SlipFactor", this.slipFactor);
        tag.putInt("Gear", this.gear);
        tag.putDouble("GearRatio", this.gearRatio);
        tag.putDouble("MaxGearRatio", this.maxGearRatio);
        tag.putBoolean("LeftActive", this.leftActive);
        tag.putBoolean("RightActive", this.rightActive);
        CompoundTag freq = new CompoundTag();
        freq.put("LeftFirst", this.links.getFirstFreqA().saveOptional(registries));
        freq.put("LeftSecond", this.links.getFirstFreqB().saveOptional(registries));
        freq.put("RightFirst", this.links.getSecondFreqA().saveOptional(registries));
        freq.put("RightSecond", this.links.getSecondFreqB().saveOptional(registries));
        tag.put("Frequency", freq);
        tag.putLong("PlacedTime", this.placedTime);
        tag.putByte("Validity", (byte)this.validity.ordinal());
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.totalMassKg = tag.getDouble("TotalMassKg");
        this.slopeAngleDeg = tag.getDouble("SlopeAngle");
        this.opposed = tag.getBoolean("Opposed");
        this.driveGroup = tag.contains("DriveGroup") ? tag.getInt("DriveGroup") : 1;
        this.groupSize = tag.getInt("GroupSize");
        this.ownStrength = tag.getDouble("OwnStrength");
        this.netStrength = tag.getDouble("NetStrength");
        this.slipFactor = tag.contains("SlipFactor") ? tag.getDouble("SlipFactor") : 1.0;
        this.gear = Math.max(0, Math.min(15, tag.getInt("Gear")));
        this.gearRatio = tag.contains("GearRatio") ? Math.max(1.0, tag.getDouble("GearRatio")) : 1.0;
        this.maxGearRatio = tag.contains("MaxGearRatio") ? Math.max(1.0, tag.getDouble("MaxGearRatio")) : 1.0;
        this.leftActive = tag.getBoolean("LeftActive");
        this.rightActive = tag.getBoolean("RightActive");
        if (tag.contains("Frequency")) {
            CompoundTag freq = tag.getCompound("Frequency");
            this.links
                .setFirstFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("LeftFirst")), ItemStack.parseOptional(registries, freq.getCompound("LeftSecond"))
                );
            this.links
                .setSecondFrequency(
                    ItemStack.parseOptional(registries, freq.getCompound("RightFirst")), ItemStack.parseOptional(registries, freq.getCompound("RightSecond"))
                );
        }

        this.placedTime = tag.getLong("PlacedTime");
        int ordinal = tag.getByte("Validity");
        BearingAxleValidity.Status[] all = BearingAxleValidity.Status.values();
        this.validity = ordinal >= 0 && ordinal < all.length ? all[ordinal] : BearingAxleValidity.Status.VALID;
        if (clientPacket) {
            this.refreshBogeyModel();
        }
    }

    private void refreshBogeyModel() {
        boolean driving = this.validity == BearingAxleValidity.Status.VALID;
        if (this.level != null && driving != this.drivingBogey) {
            this.drivingBogey = driving;
            BlockPos bogey = BearingAxleValidity.driverBogey(this.level, this.worldPosition);
            if (bogey != null) {
                BlockState state = this.level.getBlockState(bogey);
                this.level.setBlocksDirty(bogey, state, state);
            }
        }
    }

    public BearingAxleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
