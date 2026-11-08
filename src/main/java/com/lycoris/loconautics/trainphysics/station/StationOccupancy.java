package com.lycoris.loconautics.trainphysics.station;

import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class StationOccupancy {
    private StationOccupancy() {
    }

    @Nullable
    public static SableTrain parkedAt(GlobalStation station, SableTrain asking) {
        if (station == null) {
            return null;
        }

        Set<UUID> ownConsist = consistOf(asking);

        for (SableTrain other : SableTrainRegistry.all()) {
            if (other != asking
                && other.car() != null
                && !ownConsist.contains(other.car().subLevelId())
                && TrainStations.sameStation(station, other.atStation())) {
                return other;
            }
        }

        return null;
    }

    private static Set<UUID> consistOf(SableTrain train) {
        return train != null && train.car() != null && train.car().subLevelId() != null && train.level() != null
            ? TrainConsist.connectedSubLevels(train.level(), train.car().subLevelId())
            : Set.of();
    }
}
