package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.compat.computercraft.CcControl;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class ConsistBrake {
    public static final ConsistBrake.Demand NONE = new ConsistBrake.Demand(0.0, false);

    private ConsistBrake() {
    }

    public static ConsistBrake.Demand demandFor(SableTrain train) {
        ServerLevel level = train.level();
        UUID myId = train.car() == null ? null : train.car().subLevelId();
        if (level != null && myId != null) {
            Set<UUID> consist = TrainConsist.connectedSubLevels(level, myId);
            if (consist.size() <= 1) {
                return NONE;
            }

            double strongest = 0.0;
            boolean hold = false;

            for (UUID id : consist) {
                SableTrain other = SableTrainRegistry.bySubLevel(id);
                if (other != null) {
                    if (pulling(other)) {
                        return NONE;
                    }

                    if (other.stationState() == SableTrain.StationState.STOPPED || other.scheduleHold()) {
                        hold = true;
                        strongest = 60.0;
                    }
                }
            }

            return strongest <= 0.0 && !hold ? NONE : new ConsistBrake.Demand(strongest, hold);
        } else {
            return NONE;
        }
    }

    private static boolean pulling(SableTrain train) {
        if (train.stationState() != SableTrain.StationState.DEPARTING && !CcControl.isDriving(train.id())) {
            AnalogControllerBlockEntity drive = SableTrainDriver.controllerIn(train);
            return drive == null ? false : drive.isForwardHeld() || drive.isReverseHeld() || drive.isScheduleControlled() && drive.getCurrentPower() != 0;
        } else {
            return true;
        }
    }

    public record Demand(double decel, boolean hold) {
    }
}
