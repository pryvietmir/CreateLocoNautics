package com.lycoris.loconautics.content.schedule.destination;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.content.schedule.SchedulePath;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class CoupleInstruction extends ScheduleInstruction {
    private static final Map<UUID, Vector3d> CREEP_START = new HashMap<>();

    public CoupleInstruction() {
        this.data.putInt("Value", 32);
    }

    @Override
    public ResourceLocation getId() {
        return LoconauticsConstants.id("couple");
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    public int limit() {
        return Math.max(4, this.intData("Value"));
    }

    @Nullable
    @Override
    public SchedulePath start(SableScheduleRuntime runtime, Level level) {
        SableTrain train = runtime.train();
        Set<UUID> former = runtime.former();
        if (level instanceof ServerLevel serverLevel && train != null && !former.isEmpty() && !coupled(serverLevel, train, former)) {
            if (!runtime.maneuver()) {
                runtime.startManeuver(former, 10);
            }

            Vector3d at = TrainStatusChat.bodyPosition(train);
            Vector3d from = at == null ? null : CREEP_START.computeIfAbsent(train.id(), id -> at);
            if (from != null && from.distance(at) > this.limit()) {
                CREEP_START.remove(train.id());
                LoconauticsConstants.LOGGER.debug("[maneuver] {} couple gave up after {} blocks", LogFormat.shortId(train.id()), this.limit());
                runtime.announcer().line(Component.literal("Could not couple within " + this.limit() + " blocks"));
                runtime.endManeuver();
                return this.finishInstant(runtime);
            } else {
                runtime.waitHere();
                return null;
            }
        } else {
            finish(runtime, train, former);
            return this.finishInstant(runtime);
        }
    }

    private static void finish(SableScheduleRuntime runtime, @Nullable SableTrain train, Set<UUID> former) {
        if (train != null) {
            CREEP_START.remove(train.id());
        }

        for (UUID id : former) {
            SableTrain wagon = SableTrainRegistry.get(id);
            if (wagon != null) {
                wagon.setScheduleHold(false);
            }
        }

        runtime.clearFormer();
        runtime.endManeuver();
        LoconauticsConstants.LOGGER.debug("[maneuver] couple done, former cleared");
    }

    private static boolean coupled(ServerLevel level, SableTrain train, Set<UUID> former) {
        Set<UUID> consist = TrainConsist.connectedSubLevels(level, train.car().subLevelId());

        for (UUID id : former) {
            SableTrain wagon = SableTrainRegistry.get(id);
            if (wagon != null && consist.contains(wagon.car().subLevelId())) {
                return true;
            }
        }

        return false;
    }
}
