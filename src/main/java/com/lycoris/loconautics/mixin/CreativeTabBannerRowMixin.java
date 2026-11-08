package com.lycoris.loconautics.mixin;

import com.lycoris.loconautics.common.creative.CreativeSections;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CreativeModeTab.class, remap = false)
public class CreativeTabBannerRowMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void loconautics$reserveBannerRow(ItemDisplayParameters parameters, CallbackInfo ci) {
        if (LoconauticsRegistries.LOCONAUTICS_TAB.isBound() && this == LoconauticsRegistries.LOCONAUTICS_TAB.get()) {
            this.displayItems = CreativeSections.layout(new ArrayList<>(this.displayItems));
        }
    }
}
