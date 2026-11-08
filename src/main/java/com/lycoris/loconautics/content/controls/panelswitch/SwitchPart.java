package com.lycoris.loconautics.content.controls.panelswitch;

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
import com.lycoris.loconautics.registry.LoconauticsSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record SwitchPart(int power, @Nullable BlockPos link, int body, int knobColor, int style, int options) implements RedstoneLinkedPart {
    public static final int MAX_POWER = 15;
    public static final int DEFAULT_BODY = 3;
    public static final int DEFAULT_KNOB_COLOR = 0;
    public static final int DEFAULT_STYLE = 0;
    public static final int DEFAULT_OPTIONS = PanelStyles.SWITCH.defaultOptions();

    public static SwitchPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.SWITCH, 0);
        return new SwitchPart(
            tag.getInt("Power") > 0 ? 15 : 0,
            PanelPartNbt.readLink(tag),
            PanelPartNbt.readBody(tag, 3),
            PanelPartNbt.readColor(tag, "KnobColor", 0),
            style,
            PanelPartNbt.readOptions(tag, PanelStyles.SWITCH, DEFAULT_OPTIONS)
        );
    }

    public boolean on() {
        return this.power > 0;
    }

    public float fraction() {
        return this.on() ? 1.0F : 0.0F;
    }

    public int shownOptions() {
        return PanelStyles.SWITCH.effectiveOptions(this.options, this.style);
    }

    @Override
    public PanelGlass glass() {
        return PanelStyles.SWITCH.isOn(this.shownOptions(), PanelStyles.SWITCH_GLASS) ? PanelGlass.SLIM : null;
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new SwitchPart(this.power, PanelPart.followMoved(level, this.link, shift), this.body, this.knobColor, this.style, this.options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.SWITCH;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Power", this.power);
        PanelPartNbt.writeAppearance(tag, this.link, this.body, "KnobColor", this.knobColor, this.style, this.options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack((ItemLike)BoilerBlocks.GAUGE_SWITCH_ITEM.get());
        if (this.link != null) {
            stack.set((DataComponentType)LoconauticsRegistries.SWITCH_TARGET.get(), this.link);
        }

        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (this.link != null && level != null) {
            this.driveLink(panel, slot, level, this.link, new SwitchPart(this.power, null, this.body, this.knobColor, this.style, this.options), this.power);
        }
    }

    @Override
    public int engineSignal(int signal) {
        return 15 - signal;
    }

    @Override
    public int signal(Level level) {
        return this.power;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        Level level = panel.getLevel();
        if (level == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        boolean next = !this.on();
        panel.replacePart(slot, new SwitchPart(next ? 15 : 0, this.link, this.body, this.knobColor, this.style, this.options));
        level.playSound(null, panel.getBlockPos(), (SoundEvent)LoconauticsSounds.SWITCH_TOGGLE.get(), SoundSource.BLOCKS, 0.6F, next ? 1.0F : 0.85F);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
        PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
        return InteractionResult.CONSUME;
    }

    @Override
    public float animationTarget(PanelBlockEntity panel) {
        return this.fraction();
    }

    @Override
    public double chaseSpeed() {
        return 0.4;
    }

    @Override
    public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
        LoconauticsLang.translate("gui.goggles.switch_stats").forGoggles(tooltip);
        if (!this.connected(panel)) {
            PanelPart.addDisconnected(tooltip);
        } else {
            LoconauticsLang.translate(this.on() ? "gui.goggles.switch_on" : "gui.goggles.switch_off")
                .style(this.on() ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        }
    }
}
