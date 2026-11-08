package com.lycoris.loconautics.content.brakes.pull;

public final class PullBrakeMotion {
    public static final float WHEEL_PIVOT_X = 0.5F;
    public static final float WHEEL_PIVOT_Y = 0.5F;
    public static final float WHEEL_PIVOT_Z = 0.8125F;
    public static final float WHEEL_FULL_STROKE_DEGREES = 360.0F;
    public static final float CHAIN_TRAVEL_PIXELS = 11.0F;
    public static final float CHAIN_STEP_PIXELS = 5.0F;
    private static final float CHAIN_REST_LIFT_PIXELS = 1.0F;
    public static final int FIRST_LINK = -3;
    public static final int LAST_LINK = 0;

    private PullBrakeMotion() {
    }

    public static float wheelDegrees(float pull) {
        return -pull * 360.0F;
    }

    public static float linkOffsetPixels(int link, float pull) {
        return 1.0F - ((link + 1) * 5.0F + pull * 11.0F);
    }

    public static boolean linkSpawned(int link, float pull) {
        return linkOffsetPixels(link, pull) < 1.0F;
    }
}
