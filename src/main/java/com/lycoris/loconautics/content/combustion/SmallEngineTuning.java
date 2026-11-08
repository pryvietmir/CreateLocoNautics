package com.lycoris.loconautics.content.combustion;

public final class SmallEngineTuning {
    public static final float TOP_RPM = 256.0F;
    public static final float SU_PER_ENGINE = 8192.0F;
    public static final float SU_PER_RPM = 32.0F;
    public static final int MAX_TANDEM = 3;
    public static final int TANK_CAPACITY = 1000;
    public static final int OFF_SIGNAL = 15;
    public static final int SLOWEST_SIGNAL = 14;
    private static final int SPEED_STEPS = 16;

    private SmallEngineTuning() {
    }

    public static boolean isOff(int signal) {
        return signal >= 15;
    }

    public static float speedFraction(int signal) {
        return isOff(signal) ? 0.0F : (16 - Math.max(0, signal)) / 16.0F;
    }
}
