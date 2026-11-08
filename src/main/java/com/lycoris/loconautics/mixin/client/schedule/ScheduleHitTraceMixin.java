package com.lycoris.loconautics.mixin.client.schedule;

import com.lycoris.loconautics.LoconauticsConstants;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScheduleScreen.class)
public abstract class ScheduleHitTraceMixin extends AbstractSimiContainerScreen<ScheduleMenu> {
    @Shadow
    private LerpedFloat scroll;
    @Shadow
    private Schedule schedule;
    @Unique
    private String loconautics$lastHit = "";

    private ScheduleHitTraceMixin(ScheduleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "action", at = @At("RETURN"))
    private void loconautics$traceHit(@Nullable GuiGraphics graphics, double mouseX, double mouseY, int click, CallbackInfoReturnable<Boolean> cir) {
        int x = (int)mouseX - this.leftPos - 25;
        int rawY = (int)mouseY - this.topPos - 25;
        if (x >= 170 && x <= 200 && rawY >= 0 && rawY < 173) {
            int y = (int)(rawY + this.scroll.getValue(0.0F));
            StringBuilder cards = new StringBuilder();
            int top = 0;
            List<ScheduleEntry> entries = this.schedule.entries;

            for (int i = 0; i < entries.size(); i++) {
                ScheduleEntry entry = entries.get(i);
                int maxRows = 0;

                for (List<ScheduleWaitCondition> list : entry.conditions) {
                    maxRows = Math.max(maxRows, list.size());
                }

                boolean conditions = entry.instruction.supportsConditions();
                int height = 22 + (conditions ? 24 + maxRows * 18 : 4);
                cards.append(String.format(" [%d %s top %d h %d]", i, entry.instruction.getId().getPath(), top, height));
                top += height + 10;
            }

            String line = String.format(
                "mouse %d,%d left %d top %d x %d rawY %d scroll %.2f y %d click %d handled %s cards%s",
                (int)mouseX,
                (int)mouseY,
                this.leftPos,
                this.topPos,
                x,
                rawY,
                this.scroll.getValue(0.0F),
                y,
                click,
                cir.getReturnValue(),
                cards
            );
            if (click >= 0 || !line.equals(this.loconautics$lastHit)) {
                this.loconautics$lastHit = line;
                LoconauticsConstants.LOGGER.info("[schedhit] {}{}", click >= 0 ? "CLICK " : "hover ", line);
            }
        }
    }
}
