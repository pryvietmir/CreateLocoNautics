package com.lycoris.loconautics.mixin.client.trainmap;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.foundation.LogFormat;
import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapSync.TrainMapSyncEntry;
import com.simibubi.create.content.trains.entity.Train;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TrainMapManager.class, remap = false)
public abstract class TrainMapSkipStaleEntryMixin {
    private static final Set<UUID> LOGGED = new HashSet<>();

    @WrapOperation(method = "drawTrains", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object loconautics$skipStaleEntry(Map<?, ?> map, Object key, Operation<Object> original) {
        Object entry = original.call(new Object[]{map, key});
        if (entry instanceof TrainMapSyncEntry sync && key instanceof UUID id) {
            Train train = (Train)CreateClient.RAILWAYS.trains.get(id);
            if (train != null && (sync.dimensions == null || sync.dimensions.size() < train.carriages.size())) {
                if (LOGGED.add(id)) {
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[trainmap] skipped train {} on the map, client has {} carriages but the sync entry has {}",
                            new Object[]{LogFormat.shortId(id), train.carriages.size(), sync.dimensions == null ? 0 : sync.dimensions.size()}
                        );
                }

                return null;
            } else {
                LOGGED.remove(id);
                return entry;
            }
        } else {
            return entry;
        }
    }
}
