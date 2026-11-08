package com.lycoris.loconautics.client.scrollmenu;

import java.util.List;
import java.util.function.IntConsumer;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ScrollMenu {
    public final List<ScrollMenu.Entry> entries;
    public final int original;
    public final IntConsumer onConfirm;
    public final boolean alwaysConfirm;
    public final int[] holdKeys;
    public int selected;
    public int idleTicks;
    public boolean open = true;
    public double closeSpeed = 0.4;
    public final LerpedFloat alpha = LerpedFloat.linear().startWithValue(0.0);
    public final LerpedFloat scale = LerpedFloat.linear().startWithValue(0.9);
    public final LerpedFloat nameAlpha = LerpedFloat.linear().startWithValue(0.0);

    public ScrollMenu(List<ScrollMenu.Entry> entries, int selected, IntConsumer onConfirm, boolean alwaysConfirm, int... holdKeys) {
        this.entries = entries;
        this.selected = selected;
        this.original = selected;
        this.onConfirm = onConfirm;
        this.alwaysConfirm = alwaysConfirm;
        this.holdKeys = holdKeys;
    }

    public int wrapped(int offset) {
        int n = this.entries.size();
        return ((this.selected + offset) % n + n) % n;
    }

    public record Entry(ItemStack icon, Component name) {
    }
}
