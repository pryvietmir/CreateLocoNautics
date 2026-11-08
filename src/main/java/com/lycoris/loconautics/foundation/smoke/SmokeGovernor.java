package com.lycoris.loconautics.foundation.smoke;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.particle.LateParticles;
import net.minecraft.client.Minecraft;

public final class SmokeGovernor {
    private static final long STEP_NANOS = 1000000000L;
    private static final float MIN_SCALE = 0.25F;
    private static final int MIN_LIVE = 200;
    private static final float CUT = 0.85F;
    private static final float REGAIN = 1.1F;
    private static final float REGAIN_MARGIN = 1.25F;
    private static float scale = 1.0F;
    private static long lastStep;

    private SmokeGovernor() {
    }

    public static float scale() {
        if (!(Boolean)LoconauticsConfig.SMOKESTACK_ADAPTIVE.get()) {
            scale = 1.0F;
            return scale;
        }

        long now = System.nanoTime();
        if (now - lastStep >= 1000000000L) {
            lastStep = now;
            adjust();
        }

        return scale;
    }

    private static void adjust() {
        int target = (Integer)LoconauticsConfig.SMOKESTACK_TARGET_FPS.get();
        int fps = Minecraft.getInstance().getFps();
        if (fps < target && LateParticles.liveCount() > 200) {
            scale = Math.max(0.25F, scale * 0.85F);
        } else if (fps > target * 1.25F) {
            scale = Math.min(1.0F, scale * 1.1F);
        }
    }
}
