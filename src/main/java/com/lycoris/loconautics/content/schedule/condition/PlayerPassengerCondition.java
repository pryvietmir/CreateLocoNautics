package com.lycoris.loconautics.content.schedule.condition;

import com.google.common.collect.ImmutableList;
import com.lycoris.loconautics.content.schedule.Schedule;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.storage.ConsistAccess;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import java.util.List;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class PlayerPassengerCondition extends ScheduleWaitCondition {
    @Override
    public Pair<ItemStack, Component> getSummary() {
        int target = this.getTarget();
        return Pair.of(
            AllBlocks.SEATS.get(DyeColor.YELLOW).asStack(),
            CreateLang.translateDirect("schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"), new Object[]{target})
        );
    }

    @Override
    public ResourceLocation getId() {
        return Schedule.portedId("player_count");
    }

    public int getTarget() {
        return this.intData("Count");
    }

    public boolean canOvershoot() {
        return this.intData("Exact") != 0;
    }

    @Override
    public List<Component> getTitleAs(String type) {
        int target = this.getTarget();
        return ImmutableList.of(
            CreateLang.translateDirect(
                "schedule.condition.player_count.seated",
                new Object[]{
                    CreateLang.translateDirect(
                        "schedule.condition.player_count." + (target == 1 ? "summary" : "summary_plural"),
                        new Object[]{Component.literal(target + "").withStyle(ChatFormatting.DARK_AQUA)}
                    )
                }
            )
        );
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        builder.addScrollInput(
            0,
            31,
            (i, l) -> i.titled(CreateLang.translateDirect("schedule.condition.player_count.players", new Object[0])).withShiftStep(5).withRange(0, 21),
            "Count"
        );
        builder.addSelectionScrollInput(
            36,
            85,
            (i, l) -> i.forOptions(CreateLang.translatedOptions("schedule.condition.player_count", new String[]{"exactly", "or_above"}))
                .titled(CreateLang.translateDirect("schedule.condition.player_count.condition", new Object[0])),
            "Exact"
        );
    }

    @Override
    public boolean needsBody() {
        return true;
    }

    private int countPassengers(SableTrain train) {
        return ConsistAccess.countPlayersAboard(train.level(), train.car().subLevelId(), this.carriageIndex());
    }

    @Override
    public boolean tickCompletion(Level level, SableTrain train, CompoundTag context) {
        int prev = context.getInt("PrevPlayerCount");
        int present = this.countPassengers(train);
        int target = this.getTarget();
        context.putInt("PrevPlayerCount", present);
        if (prev != present) {
            this.requestStatusToUpdate(context);
        }

        return this.canOvershoot() ? present >= target : present == target;
    }

    @Override
    public MutableComponent getWaitingStatus(Level level, SableTrain train, CompoundTag tag) {
        return CreateLang.translateDirect("schedule.condition.player_count.status", new Object[]{this.countPassengers(train), this.getTarget()});
    }
}
