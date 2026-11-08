package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.content.schedule.SableScheduleRuntime;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.drive.TrainRailAdvance;
import com.lycoris.loconautics.trainphysics.signal.SignalSnap;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.lycoris.loconautics.trainphysics.state.StationChunkTickets;
import com.lycoris.loconautics.trainphysics.state.TrainResidency;
import com.lycoris.loconautics.trainphysics.station.StationLanding;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RailDebug {
    private static final Map<UUID, UUID> SELECTED = new HashMap<>();

    private RailDebug() {
    }

    public static void clearSelections() {
        SELECTED.clear();
    }

    private static UUID selectedOf(ServerPlayer player) {
        return SELECTED.get(player.getUUID());
    }

    public static void addBranches(LiteralArgumentBuilder<CommandSourceStack> root) {
        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)root.then(
                                Commands.literal("derail")
                                    .executes(ctx -> SableTrainSpawner.derailLookedAt(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))
                            ))
                            .then(
                                Commands.literal("hop")
                                    .then(Commands.literal("where").executes(ctx -> hopWhere(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                            ))
                        .then(
                            ((LiteralArgumentBuilder)Commands.literal("signal")
                                    .then(
                                        ((LiteralArgumentBuilder)Commands.literal("offset")
                                                .executes(ctx -> showSignalOffset(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                                            .then(
                                                Commands.argument("blocks", FloatArgumentType.floatArg(-16.0F, 16.0F))
                                                    .executes(
                                                        ctx -> setSignalOffset(
                                                            ((CommandSourceStack)ctx.getSource()).getPlayerOrException(),
                                                            FloatArgumentType.getFloat(ctx, "blocks")
                                                        )
                                                    )
                                            )
                                    ))
                                .then(Commands.literal("reset").executes(ctx -> resetSignal(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                        ))
                    .then(
                        ((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("station").then(stopOffsetBranch("offset")))
                                .then(stopOffsetBranch("end")))
                            .then(Commands.literal("reset").executes(ctx -> resetStation(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                    ))
                .then(
                    ((LiteralArgumentBuilder)Commands.literal("ghost")
                            .then(Commands.literal("status").executes(ctx -> ghostStatus(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                        .then(Commands.literal("force").executes(ctx -> forceGhost(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
                ))
            .then(
                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("schedule")
                                    .then(Commands.literal("select").executes(ctx -> select(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                                .then(Commands.literal("resume").executes(ctx -> resume(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                            .then(Commands.literal("stop").executes(ctx -> stop(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                        .then(Commands.literal("clear").executes(ctx -> clear(((CommandSourceStack)ctx.getSource()).getPlayerOrException()))))
                    .then(Commands.literal("status").executes(ctx -> stationStatus(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
            );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stopOffsetBranch(String name) {
        return (LiteralArgumentBuilder<CommandSourceStack>)((LiteralArgumentBuilder)Commands.literal(name)
                .executes(ctx -> showStopOffset(((CommandSourceStack)ctx.getSource()).getPlayerOrException())))
            .then(
                Commands.argument("blocks", FloatArgumentType.floatArg(-16.0F, 16.0F))
                    .executes(ctx -> setStopOffset(((CommandSourceStack)ctx.getSource()).getPlayerOrException(), FloatArgumentType.getFloat(ctx, "blocks")))
            );
    }

    private static int hopWhere(ServerPlayer player) {
        UUID selected = selectedOf(player);
        SableTrain train = selected == null ? SableTrainSpawner.pickLookedAtTrain(player) : SableTrainRegistry.get(selected);
        if (train == null) {
            return fail(player, "look at a train or select one first");
        }

        ok(player, "hop probe for " + LogFormat.shortId(train.car().subLevelId()) + ", green marks a rail it can take, red marks a follower that is stuck");

        for (String line : TrainRailAdvance.hopReport(train)) {
            ok(player, line);
        }

        return 1;
    }

    private static int forceGhost(ServerPlayer player) {
        UUID selected = selectedOf(player);
        SableTrain train = selected == null ? SableTrainSpawner.pickLookedAtTrain(player) : SableTrainRegistry.get(selected);
        return train == null ? fail(player, "look at a train or select one first") : ok(player, TrainResidency.forceGhost(train));
    }

    private static int ghostStatus(ServerPlayer player) {
        int ghosts = 0;

        for (SableTrain train : SableTrainRegistry.all()) {
            if (train.isGhost()) {
                ghosts++;
            }

            ok(player, TrainResidency.describe(train) + " " + StationChunkTickets.describe(train));
        }

        ok(player, StationChunkTickets.summary());
        return ok(player, ghosts + " ghost(s) of " + SableTrainRegistry.all().size() + " train(s)");
    }

    private static int showStopOffset(ServerPlayer player) {
        return ok(player, "station park offset is " + StationLanding.parkOffset + " (positive parks short of the marker, the green column sits on it)");
    }

    private static int setStopOffset(ServerPlayer player, float blocks) {
        StationLanding.parkOffset = blocks;
        return ok(player, "station park offset set to " + blocks + ", the green column moves with it");
    }

    private static int resetStation(ServerPlayer player) {
        StationLanding.parkOffset = -0.586;
        return ok(player, "station park offset back to the baked value, " + StationLanding.parkOffset);
    }

    private static int resetSignal(ServerPlayer player) {
        SignalSnap.stopOffset = 0.65;
        return ok(player, "signal stop offset back to the baked value, " + SignalSnap.stopOffset);
    }

    private static int showSignalOffset(ServerPlayer player) {
        return ok(player, "signal stop offset is " + SignalSnap.stopOffset + " (how far short of the boundary the nose parks)");
    }

    private static int setSignalOffset(ServerPlayer player, float blocks) {
        SignalSnap.stopOffset = blocks;
        return ok(player, "signal stop offset set to " + blocks + ", takes effect on the next approach");
    }

    private static int select(ServerPlayer player) {
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            return fail(player, "look at a train to select it");
        }

        SELECTED.put(player.getUUID(), train.id());
        return scheduleOk(player, "selected train " + LogFormat.shortId(train.id()));
    }

    private static int stationStatus(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }

        SableScheduleRuntime runtime = SableScheduleRuntime.get(SableScheduleRuntime.consistKey(selected));
        if (runtime == null) {
            return scheduleFail(player, "no station schedule on " + LogFormat.shortId(selected));
        }

        String held = runtime.heldReason() == null ? "running" : "held, " + runtime.heldReason();
        return scheduleOk(
            player,
            "entry "
                + (runtime.currentEntry + 1)
                + "/"
                + runtime.schedule.entries.size()
                + ", "
                + runtime.state
                + ", "
                + held
                + (runtime.schedule.cyclic ? ", cyclic" : "")
        );
    }

    private static int resume(ServerPlayer player) {
        UUID selected = selectedOf(player);
        return selected == null
            ? scheduleFail(player, "select a train first")
            : scheduleOk(player, SableScheduleRuntime.resume(selected) ? "schedule resumed" : "nothing paused to resume");
    }

    private static int stop(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }

        UUID key = SableScheduleRuntime.consistKey(selected);
        SableScheduleRuntime runtime = SableScheduleRuntime.get(key);
        if (runtime == null) {
            return scheduleFail(player, "no schedule on " + LogFormat.shortId(selected));
        }

        runtime.paused = true;
        releaseHold(key);
        return scheduleOk(player, "schedule paused");
    }

    private static int clear(ServerPlayer player) {
        UUID selected = selectedOf(player);
        if (selected == null) {
            return scheduleFail(player, "select a train first");
        }

        UUID key = SableScheduleRuntime.consistKey(selected);
        SableScheduleRuntime.clear(key);
        releaseHold(key);
        return scheduleOk(player, "schedule cleared");
    }

    private static void releaseHold(UUID selected) {
        SableTrain train = SableTrainRegistry.get(selected);
        if (train != null) {
            train.setScheduleHold(false);
        }
    }

    private static int ok(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }

    private static int fail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 0;
    }

    private static int scheduleOk(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 1;
    }

    private static int scheduleFail(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[loco] " + message));
        return 0;
    }
}
