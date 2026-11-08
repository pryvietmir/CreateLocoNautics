package com.lycoris.loconautics.mixin.client.station;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.AbstractStationScreen;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import java.lang.ref.WeakReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractStationScreen.class, remap = false)
public interface AbstractStationScreenAccessor {
    @Accessor("blockEntity")
    StationBlockEntity loconautics$getBlockEntity();

    @Accessor("station")
    GlobalStation loconautics$getStation();

    @Accessor("displayedTrain")
    WeakReference<Train> loconautics$getDisplayedTrain();
}
