package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.mixin.client.gui.SlotYAccessor;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class ScheduleEditorArt {
    public static final int EXTRA = 20;
    private static final int EDITOR_HEIGHT = 89;
    private static final int SPLIT = 66;
    private static final int STRIPE = 2;
    private static final Map<ScheduleMenu, Boolean> TALL = new WeakHashMap<>();

    private ScheduleEditorArt() {
    }

    public static void renderEditor(GuiGraphics graphics, int x, int y) {
        ResourceLocation texture = AllGuiTextures.SCHEDULE_EDITOR.getLocation();
        graphics.blit(texture, x, y, 0, 0, 256, 66);

        for (int i = 0; i < 10; i++) {
            graphics.blit(texture, x, y + 66 + i * 2, 0, 66, 256, 2);
        }

        graphics.blit(texture, x, y + 66 + 20, 0, 66, 256, 23);
    }

    public static void setTall(ScheduleMenu menu, boolean tall) {
        if (TALL.getOrDefault(menu, false) != tall) {
            TALL.put(menu, tall);
            int shift = tall ? 20 : -20;

            for (Slot slot : menu.slots) {
                if (slot.container instanceof Inventory) {
                    ((SlotYAccessor)slot).loconautics$setY(slot.y + shift);
                }
            }
        }
    }
}
