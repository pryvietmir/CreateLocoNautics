package com.lycoris.loconautics.content.controls.panelgauge;

import com.lycoris.loconautics.content.combustion.SmallEngineBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelBlockEntity;
import com.lycoris.loconautics.content.controls.panel.PanelFrequencyMenu;
import com.lycoris.loconautics.content.controls.panel.PanelPart;
import com.lycoris.loconautics.content.controls.panel.PanelPartType;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class GaugeBlockEntity extends PanelBlockEntity {
    public static final int DEFAULT_BACKGROUND = 9;
    public static final int DEFAULT_FRONT = 3;
    public static final int DEFAULT_NEEDLE_COLOR = 4;
    public static final int DEFAULT_OPTIONS = PanelStyles.GAUGE.defaultOptions();

    public GaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public LerpedFloat needle(PanelSlot slot) {
        return this.animation(slot);
    }

    public boolean hasGauge(PanelSlot slot) {
        return this.getGauge(slot) != null;
    }

    @Nullable
    public GaugeBlockEntity.Gauge getGauge(PanelSlot slot) {
        return this.getPart(slot) instanceof GaugeBlockEntity.Gauge gauge ? gauge : null;
    }

    public int gaugeCount() {
        return this.partCount();
    }

    public boolean addGauge(PanelSlot slot, BlockPos firebox, GaugeMetric metric) {
        return this.putPart(slot, new GaugeBlockEntity.Gauge(firebox, metric, metric.defaultBackground(), 3, 4, DEFAULT_OPTIONS));
    }

    public boolean removeGauge(PanelSlot slot) {
        return this.removePart(slot);
    }

    public void configure(PanelSlot slot, GaugeMetric metric, int background, int front, int needleColor, int options) {
        GaugeBlockEntity.Gauge gauge = this.getGauge(slot);
        if (gauge != null) {
            this.replacePart(
                slot,
                new GaugeBlockEntity.Gauge(
                    gauge.firebox(),
                    this.accepts(gauge, metric) ? metric : gauge.metric(),
                    Mth.clamp(background, 0, 35),
                    Mth.clamp(front, 0, 3),
                    GaugeNeedle.clampColor(needleColor),
                    PanelStyles.GAUGE.clampOptions(options)
                )
            );
        }
    }

    private boolean accepts(GaugeBlockEntity.Gauge gauge, GaugeMetric metric) {
        return metric.group() == gauge.metric().group() || metric.group() == this.liveGroup(gauge);
    }

    @Nullable
    public GaugeMetric.Group liveGroup(GaugeBlockEntity.Gauge gauge) {
        return GaugeMetric.Group.of(sourceOf(this, gauge));
    }

    public GaugeMetric pickableMetric(GaugeBlockEntity.Gauge gauge) {
        GaugeMetric.Group live = this.liveGroup(gauge);
        return live != null && live != gauge.metric().group() ? GaugeMetric.defaultFor(live) : gauge.metric();
    }

    public void openConfiguration(Player player, PanelSlot slot) {
        if (this.hasGauge(slot) && this.level != null && !this.level.isClientSide) {
            PanelFrequencyMenu.open(player, this.worldPosition, slot);
        }
    }

    @Nullable
    public BlockEntity sourceOf(GaugeBlockEntity.Gauge gauge) {
        return sourceOf(this, gauge);
    }

    @Nullable
    static BlockEntity sourceOf(PanelBlockEntity panel, @Nullable GaugeBlockEntity.Gauge gauge) {
        if (panel.getLevel() != null && gauge != null) {
            BlockEntity source = panel.getLevel().getBlockEntity(gauge.firebox());
            return (BlockEntity)(source == null && gauge.metric().group() == GaugeMetric.Group.SMALL_ENGINE
                ? SmallEngineBlockEntity.resolve(panel.getLevel(), gauge.firebox())
                : source);
        } else {
            return null;
        }
    }

    public record Gauge(BlockPos firebox, GaugeMetric metric, int background, int front, int needleColor, int options) implements PanelPart {
        public static final BlockPos UNBOUND = new BlockPos(0, -2048, 0);

        public boolean bound() {
            return !UNBOUND.equals(this.firebox);
        }

        public GaugeNeedle needle() {
            return GaugeNeedle.forBackground(this.background);
        }

        public static GaugeBlockEntity.Gauge read(CompoundTag tag) {
            GaugeMetric metric = GaugeMetric.byName(tag.getString("Metric"));
            int background = Mth.clamp(tag.contains("Background") ? tag.getInt("Background") : 9, 0, 35);
            int front = Mth.clamp(tag.contains("Front") ? tag.getInt("Front") : 3, 0, 3);
            int needleColor = GaugeNeedle.clampColor(tag.contains("NeedleColor") ? tag.getInt("NeedleColor") : 4);
            int options = PanelStyles.GAUGE.clampOptions(tag.contains("Options") ? tag.getInt("Options") : GaugeBlockEntity.DEFAULT_OPTIONS);
            return new GaugeBlockEntity.Gauge(BlockPos.of(tag.getLong("Firebox")), metric, background, front, needleColor, options);
        }

        @Override
        public PanelPart relocated(Level level, BlockPos shift) {
            return !this.bound()
                ? this
                : new GaugeBlockEntity.Gauge(
                    PanelPart.followMoved(level, this.firebox, shift), this.metric, this.background, this.front, this.needleColor, this.options
                );
        }

        @Override
        public boolean bindFromHeld(PanelBlockEntity panel, PanelSlot slot, Player player) {
            if (!this.bound() && panel instanceof GaugeBlockEntity gaugePanel) {
                ItemStack held = player.getMainHandItem();
                BlockPos target = held.getItem() instanceof GaugeBlockItem ? GaugeBlockItem.boundFirebox(held) : null;
                if (target == null) {
                    return false;
                }

                if (!player.level().isClientSide) {
                    gaugePanel.replacePart(
                        slot, new GaugeBlockEntity.Gauge(target, GaugeBlockItem.boundMetric(held), this.background, this.front, this.needleColor, this.options)
                    );
                }

                return true;
            } else {
                return false;
            }
        }

        @Override
        public PanelPartType type() {
            return PanelPartType.GAUGE;
        }

        @Override
        public void write(CompoundTag tag) {
            tag.putLong("Firebox", this.firebox.asLong());
            tag.putString("Metric", this.metric.getSerializedName());
            tag.putInt("Background", this.background);
            tag.putInt("Front", this.front);
            tag.putInt("NeedleColor", this.needleColor);
            tag.putInt("Options", this.options);
        }

        @Override
        public ItemStack asStack() {
            return GaugeBlockItem.stackFor(this);
        }

        @Override
        public InteractionResult use(PanelBlockEntity panel, PanelSlot slot, Player player) {
            return InteractionResult.PASS;
        }

        @Override
        public boolean consumesUse() {
            return false;
        }

        @Override
        public InteractionResult configure(PanelBlockEntity panel, PanelSlot slot, Player player) {
            PanelFrequencyMenu.open(player, panel.getBlockPos(), slot);
            return InteractionResult.CONSUME;
        }

        @Override
        public float animationTarget(PanelBlockEntity panel) {
            return this.metric.normalized(GaugeBlockEntity.sourceOf(panel, this));
        }

        @Override
        public float lightLevel(PanelBlockEntity panel, PanelSlot slot) {
            return panel.animation(slot).getValue();
        }

        @Override
        public void addToGoggleTooltip(PanelBlockEntity panel, List<Component> tooltip) {
            LoconauticsLang.translate("gui.goggles.gauge_stats").forGoggles(tooltip);
            BlockEntity source = GaugeBlockEntity.sourceOf(panel, this);
            if (source == null) {
                PanelPart.addDisconnected(tooltip);
            } else {
                LoconauticsLang.translate(this.metric.langKey()).style(ChatFormatting.GRAY).forGoggles(tooltip);
                LoconauticsLang.builder()
                    .text(this.metric.read(source))
                    .style(this.metric.accepts(source) && !this.metric.faulted(source) ? this.metric.colorFor(source) : ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
            }
        }
    }
}
