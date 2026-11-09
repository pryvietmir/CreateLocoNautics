package com.lycoris.loconautics.mixin.client.schedule;

import com.lycoris.loconautics.content.schedule.ScheduleEditorArt;
import com.lycoris.loconautics.content.trainmail.PostRowWidgets;
import com.lycoris.loconautics.content.trainmail.PostalEntry;
import com.lycoris.loconautics.content.trainmail.PostalOrder;
import com.simibubi.create.content.trains.schedule.DestinationSuggestions;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import java.util.List;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScheduleScreen.class)
public abstract class SchedulePostalScreenMixin extends AbstractSimiContainerScreen<ScheduleMenu> {
    @Shadow
    private ScheduleInstruction editingDestination;
    @Shadow
    private ScheduleWaitCondition editingCondition;
    @Shadow
    private DestinationSuggestions destinationSuggestions;

    @Shadow
    protected abstract List<IntAttached<String>> getViableStations(IScheduleInput var1);

    @Shadow
    protected abstract void onDestinationEdited(String var1);

    @Shadow
    protected abstract void renderActionTooltip(GuiGraphics var1, List<Component> var2, int var3, int var4);

    private SchedulePostalScreenMixin(ScheduleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Unique
    private boolean loconautics$postal() {
        return this.editingCondition == null && this.editingDestination instanceof PostalEntry;
    }

    @Inject(method = "startEditing", at = @At("HEAD"))
    private void loconautics$postalLast(CallbackInfo ci) {
        PostalOrder.moveLast(Schedule.INSTRUCTION_TYPES);
    }

    @Inject(method = "updateEditorSubwidgets", at = @At("RETURN"))
    private void loconautics$postalRows(IScheduleInput field, CallbackInfo ci) {
        boolean postal = field instanceof PostalEntry;
        ScheduleEditorArt.setTall((ScheduleMenu)(Object)this.menu, postal);
        EditBox stationBox = PostRowWidgets.takeStationBox();
        if (postal && stationBox != null) {
            this.destinationSuggestions = new DestinationSuggestions(
                this.minecraft, this, stationBox, this.font, this.getViableStations(field), false, this.topPos + 33
            );
            this.destinationSuggestions.setAllowSuggestions(true);
            this.destinationSuggestions.updateCommandInfo();
            stationBox.setResponder(this::onDestinationEdited);
        }
    }

    @Inject(method = "stopEditing", at = @At("HEAD"))
    private void loconautics$flatAgain(CallbackInfo ci) {
        ScheduleEditorArt.setTall((ScheduleMenu)(Object)this.menu, false);
    }

    @Redirect(
        method = "renderBg",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            ordinal = 1
        )
    )
    private void loconautics$editorArt(AllGuiTextures texture, GuiGraphics graphics, int x, int y) {
        if (this.loconautics$postal()) {
            ScheduleEditorArt.renderEditor(graphics, x, y);
        } else {
            texture.render(graphics, x, y);
        }
    }

    @Redirect(
        method = "renderBg",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            ordinal = 2
        )
    )
    private void loconautics$inventoryArt(AllGuiTextures texture, GuiGraphics graphics, int x, int y) {
        texture.render(graphics, x, this.loconautics$postal() ? y + 20 : y);
    }

    @ModifyArg(
        method = "renderBg",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
        ),
        index = 3
    )
    private int loconautics$inventoryTitle(int y) {
        return this.loconautics$postal() ? y + 20 : y;
    }

    @Inject(method = "renderForeground", at = @At("RETURN"))
    private void loconautics$addressTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.loconautics$postal()) {
            List<Component> tooltip = PostRowWidgets.secondRowTooltip(
                ((PostalEntry)(Object)this.editingDestination).loconautics$delivers(), this.leftPos, this.topPos, mouseX, mouseY
            );
            if (tooltip != null) {
                this.renderActionTooltip(graphics, tooltip, mouseX, mouseY);
            }
        }
    }
}
