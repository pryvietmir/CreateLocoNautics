package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.Arrays;
import java.util.List;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public abstract class CargoThresholdCondition extends LazyTickedScheduleCondition {
    public CargoThresholdCondition() {
        super(20);
        this.data.putString("Threshold", "10");
    }

    @Override
    public boolean needsBody() {
        return true;
    }

    @Override
    public boolean lazyTickCompletion(Level level, SableTrain train, CompoundTag context) {
        return this.test(level, train, context);
    }

    protected void requestStatusToUpdate(int amount, CompoundTag context) {
        context.putInt("CurrentDisplay", amount);
        super.requestStatusToUpdate(context);
    }

    protected int getLastDisplaySnapshot(CompoundTag context) {
        return !context.contains("CurrentDisplay") ? -1 : context.getInt("CurrentDisplay");
    }

    public int currentAmount(CompoundTag context) {
        return this.getLastDisplaySnapshot(context);
    }

    protected abstract boolean test(Level var1, SableTrain var2, CompoundTag var3);

    protected abstract Component getUnit();

    protected abstract ItemStack getIcon();

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(this.getIcon(), Component.literal(this.getOperator().formatted + " " + this.getThreshold()).append(this.getUnit()));
    }

    @Override
    public int slotsTargeted() {
        return 1;
    }

    public CargoThresholdCondition.Ops getOperator() {
        return this.enumData("Operator", CargoThresholdCondition.Ops.class);
    }

    public int getThreshold() {
        try {
            return Integer.valueOf(this.textData("Threshold"));
        } catch (NumberFormatException e) {
            this.data.putString("Threshold", "0");
            return 0;
        }
    }

    public int getMeasure() {
        return this.intData("Measure");
    }

    protected static int percentOf(long amount, long capacity) {
        return capacity <= 0L ? 0 : (int)(amount * 100L / capacity);
    }

    protected static MutableComponent percentUnit() {
        return Component.translatable("loconautics.schedule.condition.threshold.percent");
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return ImmutableList.of(
            CreateLang.translateDirect("schedule.condition.threshold.place_item", new Object[0]),
            CreateLang.translateDirect("schedule.condition.threshold.place_item_2", new Object[0]).withStyle(ChatFormatting.GRAY),
            CreateLang.translateDirect("schedule.condition.threshold.place_item_3", new Object[0]).withStyle(ChatFormatting.GRAY)
        );
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addSelectionScrollInput(
            0,
            24,
            (i, l) -> i.forOptions(CargoThresholdCondition.Ops.translatedOptions())
                .titled(CreateLang.translateDirect("schedule.condition.threshold.train_holds", new Object[]{""}))
                .format(state -> Component.literal(" " + CargoThresholdCondition.Ops.values()[state].formatted)),
            "Operator"
        );
        builder.addIntegerTextInput(29, 41, (e, t) -> {}, "Threshold");
    }

    public enum Ops {
        GREATER(">"),
        LESS("<"),
        EQUAL("=");

        public String formatted;

        Ops(String formatted) {
            this.formatted = formatted;
        }

        public boolean test(int current, int target) {
            return switch (this) {
                case GREATER -> current > target;
                case LESS -> current < target;
                case EQUAL -> current == target;
                default -> throw new IllegalArgumentException("Unexpected value: " + this);
            };
        }

        public static List<? extends Component> translatedOptions() {
            return Arrays.stream(values())
                .map(op -> CreateLang.translateDirect("schedule.condition.threshold." + Lang.asId(op.name()), new Object[0]))
                .toList();
        }
    }
}
