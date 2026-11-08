package com.lycoris.loconautics.mixin.create.schedule;

import com.lycoris.loconautics.content.trainmail.CreateTrainMail;
import com.lycoris.loconautics.content.trainmail.PostRowWidgets;
import com.lycoris.loconautics.content.trainmail.PostRows;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeliverPackagesInstruction.class)
public abstract class DeliverPackagesInstructionMixin extends ScheduleInstruction implements PostalEntry {
    @Inject(method = "getTitleAs", at = @At("HEAD"), cancellable = true)
    private void loconautics$title(String type, CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(PostRows.deliverTitle(this.textData("PostStation"), this.textData("Text")));
    }

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void loconautics$start(ScheduleRuntime runtime, Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
        cir.setReturnValue(CreateTrainMail.deliver(runtime, this.textData("Text"), this.textData("PostStation")));
    }

    @Override
    public String loconautics$address() {
        return this.textData("Text");
    }

    @Override
    public String loconautics$post() {
        return this.textData("PostStation");
    }

    @Override
    public boolean loconautics$delivers() {
        return true;
    }

    public List<Component> getSecondLineTooltip(int slot) {
        return PostRows.stationTooltip(true);
    }

    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        PostRowWidgets.addRows(builder, true);
    }

    @OnlyIn(Dist.CLIENT)
    public boolean renderSpecialIcon(GuiGraphics graphics, int x, int y) {
        return PostRowWidgets.renderEditorIcons(graphics, x, y);
    }
}
