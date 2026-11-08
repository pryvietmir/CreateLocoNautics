package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.content.boiler.BoilerBlocks;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public record LeverPart(int power, @Nullable BlockPos link, int body, int knobColor, int style, int options) implements RedstoneLinkedPart {
    public static final int MAX_POWER = 15;
    public static final int DEFAULT_BODY = 3;
    public static final int DEFAULT_KNOB_COLOR = 0;
    public static final int DEFAULT_STYLE = 0;
    public static final int DEFAULT_OPTIONS = PanelStyles.LEVER.defaultOptions();

    public static int clamp(int power) {
        return Mth.clamp(power, 0, 15);
    }

    public static LeverPart read(CompoundTag tag) {
        int style = PanelPartNbt.readStyle(tag, PanelStyles.LEVER, 0);
        return new LeverPart(
            clamp(tag.getInt("Power")),
            PanelPartNbt.readLink(tag),
            PanelPartNbt.readBody(tag, 3),
            PanelPartNbt.readColor(tag, "KnobColor", 0),
            style,
            PanelPartNbt.readOptions(tag, PanelStyles.LEVER, DEFAULT_OPTIONS)
        );
    }

    public float fraction() {
        return this.power / 15.0F;
    }

    public int shownOptions() {
        return PanelStyles.LEVER.effectiveOptions(this.options, this.style);
    }

    @Override
    public PanelPart relocated(Level level, BlockPos shift) {
        return new LeverPart(this.power, PanelPart.followMoved(level, this.link, shift), this.body, this.knobColor, this.style, this.options);
    }

    @Override
    public PanelPartType type() {
        return PanelPartType.LEVER;
    }

    @Override
    public void write(CompoundTag tag) {
        tag.putInt("Power", this.power);
        PanelPartNbt.writeAppearance(tag, this.link, this.body, "KnobColor", this.knobColor, this.style, this.options);
    }

    @Override
    public ItemStack asStack() {
        ItemStack stack = new ItemStack((ItemLike)BoilerBlocks.GAUGE_LEVER_ITEM.get());
        if (this.link != null) {
            stack.set((DataComponentType)LoconauticsRegistries.LEVER_TARGET.get(), this.link);
        }

        return stack;
    }

    @Override
    public void serverTick(PanelBlockEntity panel, PanelSlot slot) {
        Level level = panel.getLevel();
        if (this.link != null && level != null) {
            this.driveLink(panel, slot, level, this.link, new LeverPart(this.power, null, this.body, this.knobColor, this.style, this.options), this.power);
        }
    }

    @Override
    public int signal(Level level) {
        return this.power;
    }

    @Override
    public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
        return InteractionResult.CONSUME;
    }

    @Override
    public void clientUse(PanelBlockEntity panel, PanelSlot slot, Player player) {
        if (panel.getLevel() != null && player.isLocalPlayer()) {
            LeverHoldInteraction.grab(panel.getLevel(), player, panel.getBlockPos(), slot, this.power, panel.isInverted(slot));
        }
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
        LoconauticsLang.translate("gui.goggles.lever_stats").forGoggles(tooltip);
        if (!this.connected(panel)) {
            PanelPart.addDisconnected(tooltip);
        } else {
            LoconauticsLang.builder().text(this.power + " / 15").style(this.power > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
    }
}
