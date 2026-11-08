package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.createmod.catnip.lang.Lang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;

public class ItemThresholdCondition extends CargoThresholdCondition {
    private FilterItemStack stack = FilterItemStack.empty();
    private static final int MEASURE_STACKS = 1;
    private static final int MEASURE_PERCENT = 2;

    @Override
    protected Component getUnit() {
        return Component.literal(switch (this.getMeasure()) {
            case 1 -> "▤";
            case 2 -> "%";
            default -> "";
        });
    }

    @Override
    protected ItemStack getIcon() {
        return this.stack.item();
    }

    @Override
    protected boolean test(Level level, SableTrain train, CompoundTag context) {
        CargoThresholdCondition.Ops operator = this.getOperator();
        int target = this.getThreshold();
        int measure = this.getMeasure();
        long foundItems = 0L;
        long capacity = 0L;

        for (IItemHandler items : ConsistAccess.itemsIn(train.level(), train.car().subLevelId(), this.carriageIndex())) {
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stackInSlot = items.getStackInSlot(i);
                capacity += Math.min(items.getSlotLimit(i), stackInSlot.isEmpty() ? 64 : stackInSlot.getMaxStackSize());
                if (this.stack.test(level, stackInSlot)) {
                    if (measure == 1) {
                        foundItems += stackInSlot.getCount() == stackInSlot.getMaxStackSize() ? 1L : 0L;
                    } else {
                        foundItems += stackInSlot.getCount();
                    }
                }
            }
        }

        int amount = measure == 2 ? percentOf(foundItems, capacity) : (int)foundItems;
        this.requestStatusToUpdate(amount, context);
        return operator.test(amount, target);
    }

    @Override
    protected void writeAdditional(Provider registries, CompoundTag tag) {
        super.writeAdditional(registries, tag);
        tag.put("Item", this.stack.serializeNBT(registries));
    }

    @Override
    protected void readAdditional(Provider registries, CompoundTag tag) {
        super.readAdditional(registries, tag);
        if (tag.contains("Item")) {
            this.stack = FilterItemStack.of(registries, tag.getCompound("Item"));
        }
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.stack = FilterItemStack.of(stack);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.stack.item();
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(
            CreateLang.translateDirect(
                "schedule.condition.threshold.train_holds",
                new Object[]{CreateLang.translateDirect("schedule.condition.threshold." + Lang.asId(this.getOperator().name()), new Object[0])}
            ),
            CreateLang.translateDirect(
                    "schedule.condition.threshold.x_units_of_item",
                    new Object[]{
                        this.getThreshold(),
                        this.measureName(),
                        this.stack.isEmpty()
                            ? CreateLang.translateDirect("schedule.condition.threshold.anything", new Object[0])
                            : (
                                this.stack.isFilterItem()
                                    ? CreateLang.translateDirect("schedule.condition.threshold.matching_content", new Object[0])
                                    : this.stack.item().getHoverName()
                            )
                    }
                )
                .withStyle(ChatFormatting.DARK_AQUA)
        );
    }

    private Component measureName() {
        return switch (this.getMeasure()) {
            case 1 -> CreateLang.translateDirect("schedule.condition.threshold.stacks", new Object[0]);
            case 2 -> percentUnit();
            default -> CreateLang.translateDirect("schedule.condition.threshold.items", new Object[0]);
        };
    }

    @Override
    public ResourceLocation getId() {
        return Schedule.portedId("item_threshold");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        super.initConfigurationWidgets(builder);
        builder.addSelectionScrollInput(
            71,
            50,
            (i, l) -> i.forOptions(
                    ImmutableList.of(
                        CreateLang.translateDirect("schedule.condition.threshold.items", new Object[0]),
                        CreateLang.translateDirect("schedule.condition.threshold.stacks", new Object[0]),
                        percentUnit()
                    )
                )
                .titled(CreateLang.translateDirect("schedule.condition.threshold.item_measure", new Object[0])),
            "Measure"
        );
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag) {
        int lastDisplaySnapshot = this.getLastDisplaySnapshot(tag);
        if (lastDisplaySnapshot == -1) {
            return Component.empty();
        }

        int offset = this.getOperator() == CargoThresholdCondition.Ops.LESS ? -1 : (this.getOperator() == CargoThresholdCondition.Ops.GREATER ? 1 : 0);
        return CreateLang.translateDirect(
            "schedule.condition.threshold.status", new Object[]{lastDisplaySnapshot, Math.max(0, this.getThreshold() + offset), this.measureName()}
        );
    }
}
