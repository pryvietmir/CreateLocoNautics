package com.lycoris.loconautics.trainphysics.signal;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.station.StationLanding;

public final class SignalSnap {
    public static final double DEFAULT_STOP_OFFSET = 0.65;
    public static double stopOffset = 0.65;

    private SignalSnap() {
    }

    public static boolean canSnap(SableTrain train, double gap) {
        return StationLanding.canSnap(train, gap);
    }

    public static boolean wentPast(double gap) {
        return gap < -0.03125;
    }

    public static void snap(SableTrain train, double signedGap) {
        StationLanding.snapConsist(train, signedGap);
    }
}
