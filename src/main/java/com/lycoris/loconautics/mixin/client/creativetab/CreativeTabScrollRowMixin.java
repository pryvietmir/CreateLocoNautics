package com.lycoris.loconautics.mixin.client.creativetab;

import com.lycoris.loconautics.client.gui.CreativeTabBanner;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemPickerMenu.class, remap = false)
public abstract class CreativeTabScrollRowMixin {
    @Shadow
    protected abstract int getRowIndexForScroll(float var1);

    @Inject(method = "scrollTo", at = @At("HEAD"))
    private void loconautics$trackRow(float pos, CallbackInfo ci) {
        CreativeTabBanner.currentRow = this.getRowIndexForScroll(pos);
    }
}
