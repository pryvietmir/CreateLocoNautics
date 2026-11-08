package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.MutableObject;

public class TimeOfDayCondition extends ScheduleWaitCondition {
    public TimeOfDayCondition() {
        this.data.putInt("Hour", 8);
        this.data.putInt("Rotation", 5);
    }

    @Override
    public boolean tickCompletion(Level level, SableTrain train, CompoundTag context) {
        int maxTickDiff = 40;
        int targetHour = this.intData("Hour");
        int targetMinute = this.intData("Minute");
        int dayTime = (int)(level.getDayTime() % this.getRotation());
        int targetTicks = (int)(((targetHour + 18) % 24 * 1000 + Math.ceil(targetMinute / 60.0F * 1000.0F)) % this.getRotation());
        int diff = dayTime - targetTicks;
        return diff >= 0 && maxTickDiff >= diff;
    }

    public int getRotation() {
        int index = this.intData("Rotation");

        return switch (index) {
            case 1 -> 12000;
            case 2 -> 6000;
            case 3 -> 4000;
            case 4 -> 3000;
            case 5 -> 2000;
            case 6 -> 1000;
            case 7 -> 750;
            case 8 -> 500;
            case 9 -> 250;
            default -> 24000;
        };
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(new ItemStack(Items.STRUCTURE_VOID), this.getDigitalDisplay(this.intData("Hour"), this.intData("Minute"), false));
    }

    public MutableComponent getDigitalDisplay(int hour, int minute, boolean doubleDigitHrs) {
        int hour12raw = hour % 12 == 0 ? 12 : hour % 12;
        String hr12 = doubleDigitHrs ? this.twoDigits(hour12raw) : hour12raw + "";
        String hr24 = doubleDigitHrs ? this.twoDigits(hour) : hour + "";
        return CreateLang.translateDirect(
            "schedule.condition.time_of_day.digital_format",
            new Object[]{
                hr12,
                hr24,
                this.twoDigits(minute),
                hour > 11 ? CreateLang.translateDirect("generic.daytime.pm", new Object[0]) : CreateLang.translateDirect("generic.daytime.am", new Object[0])
            }
        );
    }

    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(
            CreateLang.translateDirect("schedule.condition.time_of_day.scheduled", new Object[0]),
            this.getDigitalDisplay(this.intData("Hour"), this.intData("Minute"), false)
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(
                    ((Component)CreateLang.translatedOptions(
                                "schedule.condition.time_of_day.rotation",
                                new String[]{
                                    "every_24", "every_12", "every_6", "every_4", "every_3", "every_2", "every_1", "every_0_45", "every_0_30", "every_0_15"
                                }
                            )
                            .get(this.intData("Rotation")))
                        .copy()
                        .withStyle(ChatFormatting.GRAY)
                )
        );
    }

    public String twoDigits(int t) {
        return t < 10 ? "0" + t : t + "";
    }

    @Override
    public ResourceLocation getId() {
        return Schedule.portedId("time_of_day");
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean renderSpecialIcon(GuiGraphics graphics, int x, int y) {
        int displayHr = (this.intData("Hour") + 12) % 24;
        float progress = (displayHr * 60.0F + this.intData("Minute")) / 1440.0F;
        ResourceLocation location = ResourceLocation.withDefaultNamespace(
            "textures/item/clock_" + this.twoDigits(Mth.clamp((int)(progress * 64.0F), 0, 63)) + ".png"
        );
        graphics.blit(location, x, y, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        MutableObject<ScrollInput> minuteInput = new MutableObject();
        MutableObject<ScrollInput> hourInput = new MutableObject();
        MutableObject<Label> timeLabel = new MutableObject();
        builder.addScrollInput(0, 16, (i, l) -> {
            i.withRange(0, 24);
            timeLabel.setValue(l);
            hourInput.setValue(i);
        }, "Hour");
        builder.addScrollInput(18, 16, (i, l) -> {
            i.withRange(0, 60);
            minuteInput.setValue(i);
            l.visible = false;
        }, "Minute");
        builder.addSelectionScrollInput(
            52,
            62,
            (i, l) -> i.forOptions(
                    CreateLang.translatedOptions(
                        "schedule.condition.time_of_day.rotation",
                        new String[]{"every_24", "every_12", "every_6", "every_4", "every_3", "every_2", "every_1", "every_0_45", "every_0_30", "every_0_15"}
                    )
                )
                .titled(CreateLang.translateDirect("schedule.condition.time_of_day.rotation", new Object[0])),
            "Rotation"
        );
        ((ScrollInput)hourInput.getValue()).titled(CreateLang.translateDirect("generic.daytime.hour", new Object[0])).calling(t -> {
            this.data.putInt("Hour", t);
            ((Label)timeLabel.getValue()).text = this.getDigitalDisplay(t, ((ScrollInput)minuteInput.getValue()).getState(), true);
        }).writingTo(null).withShiftStep(6);
        ((ScrollInput)minuteInput.getValue()).titled(CreateLang.translateDirect("generic.daytime.minute", new Object[0])).calling(t -> {
            this.data.putInt("Minute", t);
            ((Label)timeLabel.getValue()).text = this.getDigitalDisplay(((ScrollInput)hourInput.getValue()).getState(), t, true);
        }).writingTo(null).withShiftStep(15);
        ((ScrollInput)minuteInput.getValue()).lockedTooltipX = ((ScrollInput)hourInput.getValue()).lockedTooltipX = -15;
        ((ScrollInput)minuteInput.getValue()).lockedTooltipY = ((ScrollInput)hourInput.getValue()).lockedTooltipY = 35;
        ((ScrollInput)hourInput.getValue()).setState(this.intData("Hour"));
        ((ScrollInput)minuteInput.getValue()).setState(this.intData("Minute")).onChanged();
        builder.customArea(0, 52);
        builder.customArea(52, 69);
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag) {
        int targetHour = this.intData("Hour");
        int targetMinute = this.intData("Minute");
        int dayTime = (int)(level.getDayTime() % this.getRotation());
        int targetTicks = (int)(((targetHour + 18) % 24 * 1000 + Math.ceil(targetMinute / 60.0F * 1000.0F)) % this.getRotation());
        int diff = targetTicks - dayTime;
        if (diff < 0) {
            diff += this.getRotation();
        }

        int departureTime = (int)(level.getDayTime() + diff) % 24000;
        int departingHour = (departureTime / 1000 + 6) % 24;
        int departingMinute = departureTime % 1000 * 60 / 1000;
        return CreateLang.translateDirect("schedule.condition.time_of_day.status", new Object[0])
            .append(this.getDigitalDisplay(departingHour, departingMinute, false));
    }
}
