package com.lycoris.loconautics.content.boiler.safetyvalve;

import com.lycoris.loconautics.foundation.LoconauticsLang;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartSafetyValveBlockEntity extends SafetyValveBlockEntity {
    public static final float AUTO_THRESHOLD = 0.75F;
    public static final float DANGER_THRESHOLD = 0.9F;
    public static final float MIN_THRESHOLD = 0.05F;
    public static final float MAX_THRESHOLD = 1.0F;
    private static final float RAMP_BAND = 0.08F;
    private static final int VALVE_GRACE_TICKS = 5;
    private float threshold = 0.75F;
    private boolean valveDriven;
    private long lastValveTick = Long.MIN_VALUE;

    public SmartSafetyValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected Vec3 mouthDirection() {
        return Vec3.atLowerCornerOf(((Direction)this.getBlockState().getValue(SmartSafetyValveBlock.FACING)).getNormal());
    }

    @Override
    protected double mouthDistance() {
        double lift = this.getBlockState().getValue(SmartSafetyValveBlock.RAISED) ? 0.5 : 0.0;
        return 0.875 + lift - 0.5;
    }

    public float threshold() {
        return this.threshold;
    }

    public boolean isAuto() {
        return !this.valveDriven;
    }

    public boolean isValveDriven() {
        return this.valveDriven;
    }

    public boolean isDangerous() {
        return this.threshold >= 0.9F;
    }

    public static float clampThreshold(float value) {
        return Mth.clamp(value, 0.05F, 1.0F);
    }

    public void driveFromValve(float fraction) {
        if (this.level != null) {
            this.lastValveTick = this.level.getGameTime();
        }

        if (!this.valveDriven) {
            this.valveDriven = true;
            this.setChanged();
            this.sendData();
        }

        this.applyThreshold(0.05F + 0.95F * fraction);
    }

    private void applyThreshold(float value) {
        float clamped = clampThreshold(value);
        if (!(Math.abs(clamped - this.threshold) < 0.001F)) {
            this.threshold = clamped;
            this.setChanged();
            this.sendData();
        }
    }

    public void updateFromBoiler(float pressureFraction) {
        this.expireValve();
        float over = pressureFraction - this.threshold;
        this.setOpening(over <= 0.0F ? 0.0F : Math.min(1.0F, over / 0.08F));
    }

    private void expireValve() {
        if (this.valveDriven && this.level != null) {
            if (this.level.getGameTime() - this.lastValveTick > 5L) {
                this.valveDriven = false;
                this.threshold = 0.75F;
                this.setChanged();
                this.sendData();
            }
        }
    }

    private String modeKey() {
        return this.valveDriven ? "gui.goggles.smart_valve_mode_valve" : "gui.goggles.smart_valve_mode_auto";
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LoconauticsLang.translate("gui.goggles.smart_valve_stats").forGoggles(tooltip);
        LoconauticsLang.translate("gui.goggles.smart_valve_mode").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.translate(this.modeKey()).style(this.isAuto() ? ChatFormatting.AQUA : ChatFormatting.GOLD).forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.smart_valve_threshold").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
            .text(Mth.floor(this.threshold * 100.0F) + "%")
            .style(this.isDangerous() ? ChatFormatting.RED : ChatFormatting.GOLD)
            .forGoggles(tooltip, 1);
        LoconauticsLang.translate("gui.goggles.safety_valve_opening").style(ChatFormatting.GRAY).forGoggles(tooltip);
        LoconauticsLang.builder()
            .text(Mth.floor(this.opening() * 100.0F) + "%")
            .style(this.opening() <= 0.0F ? ChatFormatting.DARK_GRAY : ChatFormatting.GOLD)
            .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("Threshold", this.threshold);
        tag.putBoolean("ValveDriven", this.valveDriven);
    }

    public void writeSafe(CompoundTag tag, Provider registries) {
        super.writeSafe(tag, registries);
        tag.putFloat("Threshold", this.threshold);
        tag.putBoolean("ValveDriven", this.valveDriven);
    }

    @Override
    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.threshold = tag.contains("Threshold") ? clampThreshold(tag.getFloat("Threshold")) : 0.75F;
        this.valveDriven = tag.getBoolean("ValveDriven");
    }
}
