package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.impact.TrainKnockback;
import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.train.TrainStatusChat;
import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.glue.CarriageRoll;
import com.lycoris.loconautics.trainphysics.spawn.BogeyEditTrace;
import com.lycoris.loconautics.trainphysics.spawn.RerailApproach;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.station.TrainStations;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class TrainDerailment {
    static final int RAILS_GONE_GRACE_TICKS = 40;
    static final Map<UUID, Integer> RAILS_GONE = new HashMap<>();
    static final int TILT_DERAIL_TICKS = 5;
    static final Map<UUID, Integer> TILT_OVER = new HashMap<>();
    static final double DERAIL_LAUNCH_LIFT = 0.35;
    static final double MAX_COUPLING_STRESS = 1.5;

    private TrainDerailment() {
    }

    static void retain(Set<UUID> live) {
        RAILS_GONE.keySet().retainAll(live);
        TILT_OVER.keySet().retainAll(live);
    }

    static void check(SableTrain train) {
        if (!train.isPhysics()) {
            if (train.isPoseSettling()) {
                if (train.isDerailed()) {
                    BogeyEditTrace.phase(train, "posesettle", "");
                }
            } else if (train.isDerailed()) {
                if (ExternalForceDerail.holdsRerail(train)) {
                    BogeyEditTrace.phase(train, "regrip", "a push in the last 40 ticks blocks every rerail");
                } else {
                    if (RerailApproach.tryAutoRerail(train)) {
                        TrainStatusChat.clearPartialDerail(train);
                    }
                }
            } else {
                if (train.hasOffTrackBogey()
                    && !ExternalForceDerail.holdsRerail(train)
                    && SableTrainSpawner.tryRerailOffTrackBogeys(train)
                    && !train.hasOffTrackBogey()) {
                    TrainStatusChat.clearPartialDerail(train);
                }

                ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
                if (container != null) {
                    SableTrain.Car car = train.car();
                    if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                        if (!SubLevelBlocks.hasBlock(sub, AbstractBogeyBlock.class)) {
                            train.setDerailed(true);
                            LoconauticsConstants.LOGGER
                                .debug("[sabletrain] car {} lost all its bogeys, released from the rail (now a free sub level)", train.id());
                        } else if (checkRailsGone(train, sub)) {
                            if (!checkAttitudeDerail(train)) {
                                if (!ExternalForceDerail.check(train, sub)) {
                                    double approach = Math.max(Math.abs(train.speed()), Math.abs(train.lastRollingSpeed()));
                                    train.setLastRollingSpeed(train.speed());
                                    if ((Boolean)LoconauticsConfig.DERAIL_AT_TRACK_END.get() && SableTrainDriver.DEAD_END.contains(sub.getUniqueId())) {
                                        boolean full = false;

                                        for (SableTrain.Bogey bogey : car.bogeys()) {
                                            if (!train.isBogeyOffTrack(bogey.localPos()) && bogey.rail().reachedEnd()) {
                                                full |= derailBogey(train, car, bogey, "end_of_track");
                                            }
                                        }

                                        if (full) {
                                            double impact = SableTrainDriver.trainMass(sub) * approach * 20.0;
                                            if (impact > (Double)LoconauticsConfig.DERAIL_MAX_END_IMPACT.get()) {
                                                TrainKnockback.launchOnDerail(train, 0.35);
                                            }

                                            LoconauticsConstants.LOGGER
                                                .debug(
                                                    "[sabletrain] car {} ran every bogey off the track end, impact {} vs {}",
                                                    new Object[]{
                                                        train.id(),
                                                        SableTrainDriver.f(impact),
                                                        SableTrainDriver.f((Double)LoconauticsConfig.DERAIL_MAX_END_IMPACT.get())
                                                    }
                                                );
                                            return;
                                        }

                                        if (train.hasOffTrackBogey()) {
                                            return;
                                        }
                                    }

                                    if (TrainPropulsion.isMonorail(train) || !checkCouplingStress(train, sub)) {
                                        ;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static boolean checkRailsGone(SableTrain train, ServerSubLevel sub) {
        if (TrainRailAdvance.railsValid(train)) {
            RAILS_GONE.remove(train.id());
            return true;
        }

        if (SableTrainSpawner.reseatOnRail(train, sub)) {
            RAILS_GONE.remove(train.id());
            return true;
        }

        if (RAILS_GONE.merge(train.id(), 1, Integer::sum) < 40) {
            return false;
        }

        RAILS_GONE.remove(train.id());
        train.setSpeed(0.0);
        train.setTargetSpeed(0.0);
        train.setDerailed(true, "track_missing");
        LoconauticsConstants.LOGGER.debug("[sabletrain] car {} has had no rail under it for {} ticks, released from the graph", train.id(), 40);
        return false;
    }

    static boolean checkAttitudeDerail(SableTrain train) {
        if (CarriageRoll.isRolling(train.car().subLevelId())) {
            TILT_OVER.remove(train.id());
            return false;
        }

        double limit = (Double)LoconauticsConfig.DERAIL_MAX_TILT_DEGREES.get();
        double tilt = Math.toDegrees(train.car().carriage().hostTilt());
        if (!(limit >= 180.0) && !(tilt <= limit)) {
            if (TILT_OVER.merge(train.id(), 1, Integer::sum) < 5) {
                return false;
            }

            TILT_OVER.remove(train.id());
            train.setSpeed(0.0);
            train.setTargetSpeed(0.0);
            train.setDerailed(true, "overturned");
            LoconauticsConstants.LOGGER
                .debug(
                    "[sabletrain] car {} fell off a rail leaning {} deg, past {}",
                    new Object[]{train.id(), SableTrainDriver.f(tilt), SableTrainDriver.f(limit)}
                );
            return true;
        } else {
            TILT_OVER.remove(train.id());
            return false;
        }
    }

    static boolean derailBogey(SableTrain train, SableTrain.Car car, SableTrain.Bogey bogey, String statusKey) {
        train.setDerailReason(statusKey);
        if (car.bogeys().size() >= 2 && bogey != null) {
            if (train.markBogeyOffTrack(bogey.localPos())) {
                TrainStatusChat.partialDerail(train);
                LoconauticsConstants.LOGGER
                    .debug(
                        "[sabletrain] car {} bogey {} derailed ({}), car pivots on the rest",
                        new Object[]{train.id(), bogey.localPos().toShortString(), statusKey}
                    );
            }

            if (train.offTrackBogeyCount() < car.bogeys().size()) {
                return false;
            }

            train.setDerailed(true, statusKey);
            return true;
        } else {
            train.setDerailed(true, statusKey);
            return true;
        }
    }

    static SableTrain.Bogey nextRailedBogey(SableTrain train) {
        List<SableTrain.Bogey> onRail = new ArrayList<>();

        for (SableTrain.Bogey bogey : train.car().bogeys()) {
            if (!train.isBogeyOffTrack(bogey.localPos())) {
                onRail.add(bogey);
            }
        }

        return onRail.isEmpty() ? null : TrainStations.frontBogey(train, onRail);
    }

    static boolean checkCouplingStress(SableTrain train, ServerSubLevel sub) {
        TrainRailAdvance.Stress stress = TrainRailAdvance.takeMaxStress(sub.getUniqueId());
        if (stress != null && !(stress.amount() <= 1.5)) {
            SableTrain.Bogey culprit = null;

            for (SableTrain.Bogey bogey : train.car().bogeys()) {
                if (bogey.localPos().equals(stress.bogey()) && !train.isBogeyOffTrack(bogey.localPos())) {
                    culprit = bogey;
                }
            }

            if (culprit == null) {
                return false;
            }

            derailBogey(train, train.car(), culprit, "coupling_stress");
            tellPlayersAboard(sub, CreateLang.translateDirect("train.status.coupling_stress", new Object[0]));
            LoconauticsConstants.LOGGER
                .debug(
                    "[sabletrain] car {} bogey {} derailed, coupling stress {} > {}",
                    new Object[]{train.id(), culprit.localPos().toShortString(), SableTrainDriver.f(stress.amount()), SableTrainDriver.f(1.5)}
                );
            return true;
        } else {
            return false;
        }
    }

    static void tellPlayersAboard(ServerSubLevel sub, Component message) {
        BoundingBox3ic bounds = sub.getPlot().getBoundingBox();

        for (ServerPlayer player : sub.getLevel().getServer().getPlayerList().getPlayers()) {
            Vec3 at = player.getVehicle() != null ? player.getVehicle().position() : player.position();
            if (bounds.contains((int)Math.floor(at.x), (int)Math.floor(at.y), (int)Math.floor(at.z))) {
                player.displayClientMessage(
                    CreateLang.translateDirect("train.status", new Object[]{LogFormat.shortId(sub.getUniqueId())}).withStyle(ChatFormatting.GOLD), false
                );
                player.displayClientMessage(
                    Component.literal(" - ").withStyle(ChatFormatting.GRAY).append(message.copy().withStyle(st -> st.withColor(16765876))), false
                );
            }
        }
    }
}
