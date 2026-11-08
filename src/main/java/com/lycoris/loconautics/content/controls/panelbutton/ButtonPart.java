package com.lycoris.loconautics.content.controls.panelbutton;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartNbt;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.RedstoneLinkedPart;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record ButtonPart(long pressedUntil, @Nullable BlockPos link, int body, int capColor, int style, int options) implements RedstoneLinkedPart {
    public static final int PRESS_TICKS = 20;
    public static final int SIGNAL = 15;
    public static final int DEFAULT_BODY = 3;
    public static final int DEFAULT_CAP_COLOR = 0;
    public static final int DEFAULT_STYLE = 0;
    public static final int DEFAULT_OPTIONS = PanelStyles.BUTTON.defaultOptions();

    public static ButtonPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.BUTTON, 0);
        return new ButtonPart(
            tag.getLong("PressedUntil"),
            PanelPartNbt.readLink(tag),
            PanelPartNbt.readBody(tag, 3),
            PanelPartNbt.readColor(tag, "CapColor", 0),
            style,
            PanelPartNbt.readOptions(tag, PanelStyles.BUTTON, DEFAULT_OPTIONS)
        );
    }

    public boolean pressed(@Nullable Level level) {
        return level != null && level.getGameTime() < this.pressedUntil;
    }

    public int shownOptions() {
        return PanelStyles.BUTTON.effectiveOptions(this.options, this.style);
    }

    @Override
    public PanelGlass glass() {
        return PanelStyles.BUTTON.isOn(this.shownOptions(), PanelStyles.BUTTON_GLASS) ? PanelGlass.BODY : null;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new ButtonPart(this.pressedUntil, PanelPart.followMoved(level, this.link, shift), this.body, this.capColor, this.style, this.options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.BUTTON;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putLong("PressedUntil", this.pressedUntil);
        PanelPartNbt.writeAppearance(tag, this.link, this.body, "CapColor", this.capColor, this.style, this.options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack((ItemLike)BoilerBlocks.GAUGE_BUTTON_ITEM.get());
        if (this.link != null) {
            stack.set((DataComponentType)LoconauticsRegistries.BUTTON_TARGET.get(), this.link);
        }

        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (level != null) {
            if (level.getGameTime() == this.pressedUntil) {
                level.playSound(null, panel.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.35F, 1.4F);
            }

            if (this.link != null) {
                this.driveLink(
                    panel,
                    slot,
                    level,
                    this.link,
                    new ButtonPart(this.pressedUntil, null, this.body, this.capColor, this.style, this.options),
                    this.signal(level)
                );
            }
        }
    }

    @Override
    public int signal(Level level) {
        return this.pressed(level) ? 15 : 0;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        Level level = panel.getLevel();
        if (level == null) {
            return InteractionResult.PASS;
        }

        panel.replacePart(slot, new ButtonPart(level.getGameTime() + 20L, this.link, this.body, this.capColor, this.style, this.options));
        level.playSound(null, panel.getBlockPos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.35F, 1.4F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return this.pressed(panel.getLevel()) ? 1.0F : 0.0F;
    }

    @Override
    public double chaseSpeed() {
        return 0.45;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.button_stats").forGoggles(tooltip);
        if (!this.connected(panel)) {
            PanelPart.addDisconnected(tooltip);
        } else {
            boolean pressed = this.pressed(panel.getLevel());
            LoconauticsLang.translate(pressed ? "gui.goggles.button_pressed" : "gui.goggles.button_released")
                .style(pressed ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        }
    }
}
