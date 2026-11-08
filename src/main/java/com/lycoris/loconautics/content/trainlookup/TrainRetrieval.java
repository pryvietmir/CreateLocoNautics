package com.lycoris.loconautics.content.trainlookup;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.TrainStructures;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public final class TrainRetrieval {
    private TrainRetrieval() {
    }

    public static TrainRetrieval.Result validateAndRetrieve(ServerLevel level, UUID trainId, int carriageIndex) {
        TrainLookup.TrainInfo info = TrainLookup.getTrain(level, trainId);
        if (info == null) {
            return TrainRetrieval.Result.failed("that train is not in the registry");
        }

        List<TrainLookup.CarriageInfo> carriages = TrainLookup.getCarriages(level, trainId);
        List<UUID> targets = new ArrayList<>();
        if (carriageIndex < 0) {
            for (TrainLookup.CarriageInfo carriage : carriages) {
                targets.add(carriage.subLevelId());
            }

            if (targets.isEmpty()) {
                targets.add(info.subLevelId());
            }
        } else {
            TrainLookup.CarriageInfo target = carriages.stream().filter(c -> c.index() == carriageIndex).findFirst().orElse(null);
            if (target == null) {
                return TrainRetrieval.Result.failed("that train has no carriage " + carriageIndex);
            }

            targets.add(target.subLevelId());
        }

        for (UUID subLevelId : targets) {
            if (SubLevelData.resolve(level, subLevelId) == null) {
                return TrainRetrieval.Result.failed("carriage " + subLevelId.toString().substring(0, 8) + " is not loaded, stand closer and try again");
            }
        }

        int ejected = 0;
        List<UUID> stopped = new ArrayList<>();
        List<CompoundTag> structures = new ArrayList<>();

        try {
            for (UUID subLevelId : targets) {
                ejected += ejectRiders(level, subLevelId);
                killForces(level, subLevelId);
                ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
                SableTrain train = SableTrainRegistry.bySubLevel(subLevelId);
                if (sub == null || train == null) {
                    return TrainRetrieval.Result.failed(
                        "carriage " + subLevelId.toString().substring(0, 8) + " went missing partway through, nothing was taken"
                    );
                }

                structures.add(TrainStructures.capture(level, sub));
                TrainStructures.removeBody(level, train);
                stopped.add(subLevelId);
            }
        } catch (RuntimeException failure) {
            LoconauticsConstants.LOGGER.error("[trainlookup] retrieval failed for train {}, rolling back", trainId, failure);
            return TrainRetrieval.Result.failed("retrieval failed partway, nothing was taken");
        }

        return new TrainRetrieval.Result(true, null, List.copyOf(stopped), ejected, List.copyOf(structures));
    }

    public static void killForces(ServerLevel level, UUID subLevelId) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (sub != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null) {
                PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
                pipeline.resetVelocity(sub);
            }

            SableTrain train = SableTrainRegistry.bySubLevel(subLevelId);
            if (train != null) {
                train.setSpeed(0.0);
                train.setTargetSpeed(0.0);
                train.setPowered(false);
            }
        }
    }

    public static int ejectRiders(ServerLevel level, UUID subLevelId) {
        ServerSubLevel sub = SubLevelData.resolve(level, subLevelId);
        if (sub == null) {
            return 0;
        }

        BoundingBox3ic box = sub.getPlot().getBoundingBox();
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1.0, box.maxY() + 1.0, box.maxZ() + 1.0);
        int ejected = 0;

        for (Entity entity : sub.getLevel().getEntities((Entity)null, area, e -> e.getVehicle() != null)) {
            entity.stopRiding();
            ejected++;
        }

        return ejected;
    }

    public record Result(boolean success, String failure, List<UUID> retrieved, int ridersEjected, List<CompoundTag> structures) {
        public static TrainRetrieval.Result failed(String reason) {
            return new TrainRetrieval.Result(false, reason, List.of(), 0, List.of());
        }
    }
}
