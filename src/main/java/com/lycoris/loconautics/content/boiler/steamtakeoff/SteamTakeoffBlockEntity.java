package com.lycoris.loconautics.content.boiler.steamtakeoff;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SteamTakeoffBlockEntity extends GeneratingKineticBlockEntity {
    private float rpm;
    private float boilerCapacity;
    private float regulator = 1.0F;
    public static final int SUPPLY_TIMEOUT = 60;
    private int sinceSupplied = 60;

    public SteamTakeoffBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void supply(float shaftRpm, float suppliedCapacity) {
        this.sinceSupplied = 0;
        float newRpm = Math.max(0.0F, shaftRpm);
        if (newRpm != this.rpm || suppliedCapacity != this.boilerCapacity) {
            this.rpm = newRpm;
            this.boilerCapacity = suppliedCapacity;
            this.updateGeneratedRotation();
        }
    }

    public void cut() {
        boolean wasZero = this.rpm == 0.0F && this.boilerCapacity == 0.0F;
        this.rpm = 0.0F;
        this.boilerCapacity = 0.0F;
        this.sinceSupplied = 60;
        if (!wasZero) {
            this.updateGeneratedRotation();
        }
    }

    public void tick() {
        super.tick();
        if (this.level != null && !this.level.isClientSide && !(this.rpm <= 0.0F)) {
            if (++this.sinceSupplied >= 60) {
                this.cut();
            }
        }
    }

    public float regulator() {
        return this.regulator;
    }

    public void setRegulator(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        if (!(Math.abs(clamped - this.regulator) < 0.001F)) {
            this.regulator = clamped;
            this.setChanged();
            this.sendData();
        }
    }

    public float getRpm() {
        return this.rpm;
    }

    public float networkStressRatio() {
        return !(super.capacity <= 0.0F) && !(super.stress <= 0.0F) ? Math.min(1.0F, super.stress / super.capacity) : 0.0F;
    }

    public boolean networkOverstressed() {
        return super.capacity > 0.0F && super.stress > super.capacity;
    }

    public float getGeneratedSpeed() {
        if (this.rpm <= 0.0F) {
            return 0.0F;
        }

        Direction along = Direction.get(AxisDirection.POSITIVE, (Axis)this.getBlockState().getValue(SteamTakeoffBlock.AXIS));
        return convertToDirection(this.rpm, along);
    }

    public float calculateAddedStressCapacity() {
        float perBody = (float)((Double)LoconauticsConfig.SU_PER_BODY_BLOCK.get()).doubleValue();
        float added = this.boilerCapacity * perBody;
        this.lastCapacityProvided = added;
        return added;
    }

    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.takeoff_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.takeoff_pressure").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.number(this.rpm).style(this.rpm > 0.0F ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.takeoff_regulator").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
            .text(Math.round(this.regulator * 100.0F) + "%")
            .style(this.regulator > 0.0F ? ChatFormatting.GOLD : ChatFormatting.RED)
            .forGoggles(tooltip, 1);
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Rpm", this.rpm);
        tag.putFloat("Capacity", this.boilerCapacity);
        tag.putFloat("Regulator", this.regulator);
    }

    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.rpm = tag.getFloat("Rpm");
        this.boilerCapacity = tag.getFloat("Capacity");
        this.regulator = tag.contains("Regulator") ? tag.getFloat("Regulator") : 1.0F;
    }
}
