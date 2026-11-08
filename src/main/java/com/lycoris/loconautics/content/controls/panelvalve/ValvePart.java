package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.boiler.safetyvalve.SafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.safetyvalve.SmartSafetyValveBlockEntity;
import com.lycoris.loconautics.content.boiler.steamtakeoff.SteamTakeoffBlockEntity;
import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelDriveBus;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record ValvePart(float degrees, @Nullable BlockPos safetyValve, int wheelColor) implements PanelPart {
    public static final float FULL_TURN = 360.0F;
    public static final float SNAP = 45.0F;
    public static final int DEFAULT_WHEEL_COLOR = 1;

    public static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    public static float clamp(float degrees) {
        return Mth.clamp(degrees, 0.0F, 360.0F);
    }

    public static ValvePart read(CompoundTag tag) {
        return new ValvePart(
            clamp(tag.getFloat("Degrees")),
            tag.contains("SafetyValve") ? BlockPos.of(tag.getLong("SafetyValve")) : null,
            Mth.clamp(tag.contains("WheelColor") ? tag.getInt("WheelColor") : 1, 0, 34)
        );
    }

    public float fraction() {
        return this.degrees / 360.0F;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new ValvePart(this.degrees, PanelPart.followMoved(level, this.safetyValve, shift), this.wheelColor);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.VALVE;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putFloat("Degrees", this.degrees);
        if (this.safetyValve != null) {
            tag.putLong("SafetyValve", this.safetyValve.asLong());
        }

        tag.putInt("WheelColor", this.wheelColor);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack((ItemLike)BoilerBlocks.VALVE_ITEM.get());
        if (this.safetyValve != null) {
            stack.set((DataComponentType)LoconauticsRegistries.VALVE_TARGET.get(), this.safetyValve);
        }

        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (this.safetyValve != null && level != null) {
            SmallEngineBlockEntity engine = SmallEngineBlockEntity.resolve(level, this.safetyValve);
            if (engine != null) {
                int step = Math.round(this.fraction() * 14.0F);
                float snapped = step * 360.0F / 14.0F;
                if (Math.abs(snapped - this.degrees) > 0.5F) {
                    panel.replacePart(slot, new ValvePart(snapped, this.safetyValve, this.wheelColor));
                    return;
                }

                engine.driveFromPanel(14 - step);
            } else if (level.getBlockEntity(this.safetyValve) instanceof SmartSafetyValveBlockEntity smart) {
                smart.driveFromValve(PanelDriveBus.report(level, this.safetyValve, this.fraction()));
            } else if (level.getBlockEntity(this.safetyValve) instanceof SafetyValveBlockEntity valve) {
                valve.setOpening(PanelDriveBus.report(level, this.safetyValve, this.fraction()));
            } else if (level.getBlockEntity(this.safetyValve) instanceof SteamTakeoffBlockEntity takeoff) {
                takeoff.setRegulator(PanelDriveBus.report(level, this.safetyValve, this.fraction()));
            } else if (level.isLoaded(this.safetyValve)) {
                PanelDriveBus.release(level, this.safetyValve);
                panel.replacePart(slot, new ValvePart(this.degrees, null, this.wheelColor));
            }
        }
    }

    @Override
    public void onRemoved(PanelBlockEntity panel) {
        Level level = panel.getLevel();
        if (this.safetyValve != null && level != null) {
            PanelDriveBus.release(level, this.safetyValve);
            if (level.getBlockEntity(this.safetyValve) instanceof SafetyValveBlockEntity valve) {
                valve.setOpening(0.0F);
            }

            if (level.getBlockEntity(this.safetyValve) instanceof SteamTakeoffBlockEntity takeoff) {
                takeoff.setRegulator(1.0F);
            }
        }
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        ValveMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return this.degrees;
    }

    @Override
    public double chaseSpeed() {
        return 0.35;
    }

    @Override
    public LerpedFloat newAnimation() {
        return LerpedFloat.angular().startWithValue(this.degrees);
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.valve_stats").forGoggles(tooltip);
        if (this.safetyValve == null) {
            PanelPart.addDisconnected(tooltip);
        } else {
            Level level = panel.getLevel();
            if (level != null) {
                LoconauticsLang.translate("gui.goggles.valve_linked_to", level.getBlockState(this.safetyValve).getBlock().getName())
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
            }

            LoconauticsLang.builder().text(Mth.floor(this.degrees) + "°").style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
    }
}
