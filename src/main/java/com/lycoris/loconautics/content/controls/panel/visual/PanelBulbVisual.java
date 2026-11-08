package com.lycoris.loconautics.content.controls.panel.visual;

import com.lycoris.loconautics.content.controls.panel.style.PanelBulbRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.function.Function;
import net.minecraft.util.Mth;

public final class PanelBulbVisual {
    private final TransformedInstance glass;
    private final TransformedInstance glow;
    private boolean lastLit;
    private int lastLevel = -1;

    public PanelBulbVisual(Function<Model, TransformedInstance> factory, PartialModel bulb) {
        this.glass = factory.apply(ShiftedModels.plain(bulb));
        this.glow = factory.apply(ShiftedModels.withTransparency(bulb, Transparency.ADDITIVE));
    }

    public void update(PoseStack ms, int light, float bulbGlow, boolean full) {
        float amount = PanelBulbRenderer.glowFor(bulbGlow);
        boolean lit = PanelBulbRenderer.isLit(amount);
        int level = lit ? Mth.clamp(PanelBulbRenderer.additiveLevel(amount), 0, 255) : -1;
        if (full || lit != this.lastLit || level != this.lastLevel) {
            this.lastLit = lit;
            this.lastLevel = level;
            PanelPartVisual.place(this.glass, ms, lit ? 15728880 : light);
            if (lit) {
                this.glow.setTransform(ms);
                this.glow.light(15728880);
                this.glow.color(level, level, level, 255);
            } else {
                this.glow.setZeroTransform();
            }

            this.glow.setChanged();
        }
    }
}
