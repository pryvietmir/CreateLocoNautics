package com.lycoris.loconautics.content.hose;

public final class HoseSwell {
    public static final float MAX_SWELL_PX = 0.8F;
    public static final double PHASE_STEP = 0.25;
    public static final float WAVE_PERIOD_TICKS = 27.0F;

    private HoseSwell() {
    }

    public static float wavePx(double phase) {
        return (float)(0.5 * (1.0 - Math.cos(phase)));
    }

    public static float frontPx(double fill, double cumDist) {
        float local = (float)Math.max(0.0, Math.min(1.0, fill - cumDist));
        return local * local * (3.0F - 2.0F * local);
    }

    public static float scale(float px) {
        return (3.0F + 0.8F * px) / 3.0F;
    }

    public static float travelling(double ticks, double distFromEntry) {
        double phase = ticks * Math.PI * 2.0 / 27.0 - distFromEntry * 0.25 * Math.PI * 2.0;
        return scale(wavePx(phase));
    }
}
