package com.lycoris.loconautics.compat.create;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LoconauticsLang;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import java.util.List;
import java.util.function.Supplier;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class ManeuverEditorInstruction extends ScheduleInstruction {
    private static final int OPEN_KNUCKLE_MODEL = 1;
    private final ManeuverEditorInstruction.Kind kind;

    public ManeuverEditorInstruction(ManeuverEditorInstruction.Kind kind) {
        this.kind = kind;
        if (kind.hasValue) {
            this.data.putInt("Value", kind.start);
        }
    }

    public static void register() {
        for (ManeuverEditorInstruction.Kind kind : ManeuverEditorInstruction.Kind.values()) {
            Schedule.INSTRUCTION_TYPES
                .add(Pair.of(LoconauticsConstants.id(kind.path), (Supplier<ScheduleInstruction>)() -> new ManeuverEditorInstruction(kind)));
        }
    }

    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(this.icon(), this.line());
    }

    public ResourceLocation getId() {
        return LoconauticsConstants.id(this.kind.path);
    }

    public ItemStack getSecondLineIcon() {
        return this.icon();
    }

    public boolean supportsConditions() {
        return false;
    }

    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(this.line().copy().withStyle(ChatFormatting.GOLD));
    }

    private Component line() {
        if (!this.kind.hasValue) {
            return LoconauticsLang.translate("schedule.instruction." + this.kind.path + ".summary").component();
        }

        int value = this.intData("Value");
        String key = "schedule.instruction." + this.kind.path + (value == 1 ? ".summary.one" : ".summary");
        return LoconauticsLang.translate(key, Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE)).component();
    }

    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        if (this.kind.hasValue) {
            builder.addScrollInput(
                0,
                this.kind.suffix.isEmpty() ? 31 : 50,
                (si, l) -> {
                    si.withRange(this.kind.min, this.kind.max)
                        .withStepFunction(c -> c.shift ? this.kind.shiftStep : this.kind.step)
                        .titled(LoconauticsLang.translate("schedule.instruction." + this.kind.path + "_edit_box").component());
                    if (!this.kind.suffix.isEmpty()) {
                        l.withSuffix(this.kind.suffix);
                    }
                },
                "Value"
            );
        }
    }

    public List<Component> getSecondLineTooltip(int slot) {
        return !this.kind.hasValue
            ? ImmutableList.of()
            : ImmutableList.of(
                LoconauticsLang.translate("schedule.instruction." + this.kind.path + "_edit_box").component(),
                LoconauticsLang.translate("schedule.instruction." + this.kind.path + "_edit_box_1").component().withStyle(ChatFormatting.GRAY)
            );
    }

    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }

    private ItemStack icon() {
        return switch (this.kind) {
            case DECOUPLE -> new ItemStack((ItemLike)LoconauticsRegistries.KNUCKLE_ITEM.get());
            case MANEUVER -> AllBlocks.TRAIN_CONTROLS.asStack();
            case COUPLE -> {
                ItemStack open = new ItemStack((ItemLike)LoconauticsRegistries.KNUCKLE_ITEM.get());
                open.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
                yield open;
            }
        };
    }

    public enum Kind {
        DECOUPLE("decouple", true, 1, 1, 65, 1, 1, ""),
        MANEUVER("maneuver", true, 10, 1, 76, 1, 5, "%"),
        COUPLE("couple", true, 32, 4, 129, 4, 16, "m");

        private final String path;
        private final boolean hasValue;
        private final int start;
        private final int min;
        private final int max;
        private final int step;
        private final int shiftStep;
        private final String suffix;

        Kind(String path, boolean hasValue, int start, int min, int max, int step, int shiftStep, String suffix) {
            this.path = path;
            this.hasValue = hasValue;
            this.start = start;
            this.min = min;
            this.max = max;
            this.step = step;
            this.shiftStep = shiftStep;
            this.suffix = suffix;
        }
    }
}
