package com.lycoris.loconautics.mixin.client.gui;

import com.simibubi.create.foundation.gui.ModularGuiLine;
import java.util.List;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModularGuiLine.class)
public abstract class ModularGuiLineRowsMixin {
    @Unique
    private static final int LOCONAUTICS_ROW_HEIGHT = 20;
    @Shadow
    List<Pair<AbstractWidget, String>> widgets;
    @Shadow
    List<Couple<Integer>> customBoxes;
    @Shadow
    boolean speechBubble;

    @Shadow
    protected abstract void box(GuiGraphics var1, int var2, int var3, boolean var4);

    @Inject(method = "renderWidgetBG", at = @At("HEAD"), cancellable = true)
    private void loconautics$drawEachRow(int guiLeft, GuiGraphics graphics, CallbackInfo ci) {
        if (this.customBoxes.isEmpty()) {
            int top = Integer.MAX_VALUE;
            int bottom = Integer.MIN_VALUE;

            for (Pair<AbstractWidget, String> pair : this.widgets) {
                if (!((String)pair.getSecond()).equals("Dummy")) {
                    top = Math.min(top, ((AbstractWidget)pair.getFirst()).getY());
                    bottom = Math.max(bottom, ((AbstractWidget)pair.getFirst()).getY());
                }
            }

            if (bottom - top >= 10) {
                boolean first = true;

                for (Pair<AbstractWidget, String> pair : this.widgets) {
                    if (!((String)pair.getSecond()).equals("Dummy")) {
                        AbstractWidget widget = (AbstractWidget)pair.getFirst();
                        int x = widget.getX();
                        int width = widget.getWidth();
                        if (widget instanceof EditBox) {
                            x -= 5;
                            width += 9;
                        }

                        int row = Math.round((widget.getY() - top) / 20.0F);
                        graphics.pose().pushPose();
                        graphics.pose().translate(0.0F, row * 20, 0.0F);
                        this.box(graphics, x, width, first & this.speechBubble);
                        graphics.pose().popPose();
                        first = false;
                    }
                }

                ci.cancel();
            }
        }
    }
}
