package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
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

public class RedstoneLinkCondition extends ScheduleWaitCondition {
    public Couple<Frequency> freq = Couple.create(() -> Frequency.EMPTY);

    @Override
    public int slotsTargeted() {
        return 2;
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(
            AllBlocks.REDSTONE_LINK.asStack(),
            this.lowActivation()
                ? CreateLang.translateDirect("schedule.condition.redstone_link_off", new Object[0])
                : CreateLang.translateDirect("schedule.condition.redstone_link_on", new Object[0])
        );
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return ImmutableList.of(
            CreateLang.translateDirect(slot == 0 ? "logistics.firstFrequency" : "logistics.secondFrequency", new Object[0]).withStyle(ChatFormatting.RED)
        );
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(
            CreateLang.translateDirect("schedule.condition.redstone_link.frequency_" + (this.lowActivation() ? "unpowered" : "powered"), new Object[0]),
            Component.literal(" #1 ")
                .withStyle(ChatFormatting.GRAY)
                .append(((Frequency)this.freq.getFirst()).getStack().getHoverName().copy().withStyle(ChatFormatting.DARK_AQUA)),
            Component.literal(" #2 ")
                .withStyle(ChatFormatting.GRAY)
                .append(((Frequency)this.freq.getSecond()).getStack().getHoverName().copy().withStyle(ChatFormatting.DARK_AQUA))
        );
    }

    @Override
    public boolean tickCompletion(Level level, SableTrain train, CompoundTag context) {
        int lastChecked = context.contains("LastChecked") ? context.getInt("LastChecked") : -1;
        int status = Create.REDSTONE_LINK_NETWORK_HANDLER.globalPowerVersion.get();
        if (status == lastChecked) {
            return false;
        }

        context.putInt("LastChecked", status);
        return Create.REDSTONE_LINK_NETWORK_HANDLER.hasAnyLoadedPower(this.freq) != this.lowActivation();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.freq.set(slot == 0, Frequency.of(stack));
        super.setItem(slot, stack);
    }

    @Override
    public ItemStack getItem(int slot) {
        return ((Frequency)this.freq.get(slot == 0)).getStack();
    }

    @Override
    public ResourceLocation getId() {
        return Schedule.portedId("redstone_link");
    }

    @Override
    protected void writeAdditional(Provider registries, CompoundTag tag) {
        tag.put("Frequency", this.freq.serializeEach(f -> (CompoundTag)f.getStack().saveOptional(registries)));
    }

    public boolean lowActivation() {
        return this.intData("Inverted") == 1;
    }

    @Override
    protected void readAdditional(Provider registries, CompoundTag tag) {
        if (tag.contains("Frequency")) {
            this.freq = Couple.deserializeEach(tag.getList("Frequency", 10), c -> Frequency.of(ItemStack.parseOptional(registries, c)));
        }
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addSelectionScrollInput(
            20,
            101,
            (i, l) -> i.forOptions(CreateLang.translatedOptions("schedule.condition.redstone_link", new String[]{"powered", "unpowered"}))
                .titled(CreateLang.translateDirect("schedule.condition.redstone_link.frequency_state", new Object[0])),
            "Inverted"
        );
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag) {
        return CreateLang.translateDirect("schedule.condition.redstone_link.status", new Object[0]);
    }
}
