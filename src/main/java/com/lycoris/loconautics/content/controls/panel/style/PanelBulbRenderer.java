package com.lycoris.loconautics.content.controls.panel.style;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.render.RenderTypes;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

public final class PanelBulbRenderer {
    private static final float LIT_THRESHOLD = 0.125F;

    private PanelBulbRenderer() {
    }

    public static float glowFor(float outputFraction) {
        return outputFraction <= 0.01F ? 0.0F : 0.2F + 0.55F * outputFraction;
    }

    public static boolean isLit(float glow) {
        return glow > 0.125F;
    }

    public static int additiveLevel(float glow) {
        float curve = Mth.clamp(1.0F - 2.0F * (float)Math.pow(glow - 0.75F, 2.0), -1.0F, 1.0F);
        return (int)(200.0F * curve);
    }

    public static void render(PoseStack ms, MultiBufferSource buffers, int light, BlockState state, float glow, PartialModel bulb) {
        boolean lit = isLit(glow);
        CachedBuffers.partial(bulb, state).light(lit ? 15728880 : light).renderInto(ms, buffers.getBuffer(RenderType.translucent()));
        if (lit) {
            int color = additiveLevel(glow);
            CachedBuffers.partial(bulb, state).light(15728880).color(color, color, color, 255).renderInto(ms, buffers.getBuffer(RenderTypes.additive()));
        }
    }
}
