package com.lycoris.loconautics.compat.railways;

import com.railwayteam.railways.mixin_interfaces.ISwitchDisabledEdge;
import com.railwayteam.railways.util.MixinVariables;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import net.neoforged.fml.ModList;

public final class RailwaysSwitches {
    private static Boolean loaded;

    private RailwaysSwitches() {
    }

    public static void beginTravel(TravellingPoint front, TravellingPoint rear) {
        if (railwaysLoaded()) {
            RailwaysSwitches.Flag.set(true, RailwaysSwitches.Flag.onClosedLeg(front) || RailwaysSwitches.Flag.onClosedLeg(rear));
        }
    }

    public static void endTravel() {
        if (railwaysLoaded()) {
            RailwaysSwitches.Flag.set(false, false);
        }
    }

    private static boolean railwaysLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("railways");
        }

        return loaded;
    }

    private static final class Flag {
        static void set(boolean travelling, boolean skipSwitches) {
            MixinVariables.trackEdgeCarriageTravelling = travelling;
            MixinVariables.temporarilySkipSwitches = skipSwitches;
        }

        static boolean onClosedLeg(TravellingPoint point) {
            return point.edge != null && ISwitchDisabledEdge.isDisabled(point.edge);
        }
    }
}
