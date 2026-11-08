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
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidThresholdCondition extends CargoThresholdCondition {
    private FilterItemStack compareStack = FilterItemStack.empty();
    private static final int MEASURE_PERCENT = 1;

    @Override
    protected Component getUnit() {
        return Component.literal(this.inPercent() ? "%" : "b");
    }

    private boolean inPercent() {
        return this.getMeasure() == 1;
    }

    private Component measureName() {
        return this.inPercent() ? percentUnit() : CreateLang.translateDirect("schedule.condition.threshold.buckets", new Object[0]);
    }

    @Override
    protected ItemStack getIcon() {
        return this.compareStack.item();
    }

    @Override
    protected boolean test(Level level, SableTrain train, CompoundTag context) {
        CargoThresholdCondition.Ops operator = this.getOperator();
        int target = this.getThreshold();
        long foundFluid = 0L;
        long capacity = 0L;

        for (IFluidHandler fluids : ConsistAccess.fluidsIn(train.level(), train.car().subLevelId(), this.carriageIndex())) {
            for (int i = 0; i < fluids.getTanks(); i++) {
                capacity += fluids.getTankCapacity(i);
                FluidStack fluidInTank = fluids.getFluidInTank(i);
                if (this.compareStack.test(level, fluidInTank)) {
                    foundFluid += fluidInTank.getAmount();
                }
            }
        }

        if (this.inPercent()) {
            int percent = percentOf(foundFluid, capacity);
            this.requestStatusToUpdate(percent, context);
            return operator.test(percent, target);
        } else {
            this.requestStatusToUpdate((int)(foundFluid / 1000L), context);
            return operator.test((int)foundFluid, target * 1000);
        }
    }

    @Override
    protected void writeAdditional(Provider registries, CompoundTag tag) {
        super.writeAdditional(registries, tag);
        tag.put("Bucket", this.compareStack.serializeNBT(registries));
    }

    @Override
    protected void readAdditional(Provider registries, CompoundTag tag) {
        super.readAdditional(registries, tag);
        if (tag.contains("Bucket")) {
            this.compareStack = FilterItemStack.of(registries, tag.getCompound("Bucket"));
        }
    }

    @OnlyIn(Dist.CLIENT)
    private FluidStack loadFluid() {
        return this.compareStack.fluid(Minecraft.getInstance().level);
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
                        this.compareStack.isEmpty()
                            ? CreateLang.translateDirect("schedule.condition.threshold.anything", new Object[0])
                            : (
                                this.compareStack.isFilterItem()
                                    ? CreateLang.translateDirect("schedule.condition.threshold.matching_content", new Object[0])
                                    : this.loadFluid().getHoverName()
                            )
                    }
                )
                .withStyle(ChatFormatting.DARK_AQUA)
        );
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.compareStack = FilterItemStack.of(stack);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.compareStack.item();
    }

    @Override
    public ResourceLocation getId() {
        return Schedule.portedId("fluid_threshold");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        super.initConfigurationWidgets(builder);
        builder.addSelectionScrollInput(
            71,
            50,
            (i, l) -> i.forOptions(ImmutableList.of(CreateLang.translateDirect("schedule.condition.threshold.buckets", new Object[0]), percentUnit()))
                .titled(null),
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
