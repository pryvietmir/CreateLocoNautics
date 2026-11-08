package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.foundation.LogFormat;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.lycoris.loconautics.trainphysics.TrainLength;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.TrackGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class GhostGapTrace {
    private static final long PERIOD = 20L;
    private static final double LOOKAHEAD = 150.0;
    private static final double STEP = 1.0;
    private static boolean enabled;

    private GhostGapTrace() {
    }

    public static void tick() {
        if (enabled) {
            for (SableTrain train : SableTrainRegistry.all()) {
                ServerLevel level = train.level();
                if (level != null && train.isGhost() && train.car() != null && train.car().subLevelId() != null && level.getGameTime() % 20L == 0L) {
                    probe(level, train);
                }
            }
        }
    }

    private static void probe(ServerLevel level, SableTrain train) {
        UUID id = train.car().subLevelId();
        List<SableTrain.Bogey> chain = train.car().chain();
        if (!chain.isEmpty()) {
            RailCarriage nose = chain.get(chain.size() - 1).rail();
            TravellingPoint front = nose.leading();
            TrackGraph graph = nose.graph();
            String own = String.format(
                "ownCar=%.2f ownConsist=%.2f bodyless=%s",
                TrainLength.carLength(level, train),
                TrainLength.consistLength(level, id),
                SubLevelData.resolve(level, id) == null
            );
            if (graph != null && front != null && front.edge != null) {
                List<GhostGapTrace.Occupant> occupants = occupants(level, id);
                TravellingPoint scout = new TravellingPoint(front.node1, front.node2, front.edge, front.position, front.upsideDown);
                double walked = 0.0;

                while (walked <= 150.0) {
                    GhostGapTrace.Occupant hit = hitAt(scout, occupants);
                    if (hit != null) {
                        LoconauticsConstants.LOGGER
                            .info(
                                "[ghostgap] ghost {} nose is {} blocks from {} ({}, half={}), {}",
                                new Object[]{
                                    LogFormat.shortId(id),
                                    String.format("%.1f", walked),
                                    LogFormat.shortId(hit.train()),
                                    hit.ghost() ? "ghost" : "loaded",
                                    String.format("%.2f", hit.half()),
                                    own
                                }
                            );
                        return;
                    }

                    double moved = scout.travel(
                        graph, 1.0, scout.steer(SteerDirection.NONE, nose.upNormal()), scout.ignoreEdgePoints(), scout.ignoreTurns(), scout.ignorePortals()
                    );
                    if (moved < 0.5 || scout.edge == null) {
                        LoconauticsConstants.LOGGER
                            .info(
                                "[ghostgap] ghost {} track ends {} blocks ahead, no train, {}",
                                new Object[]{LogFormat.shortId(id), String.format("%.1f", walked), own}
                            );
                        return;
                    }

                    walked += moved;
                }

                LoconauticsConstants.LOGGER
                    .info(
                        "[ghostgap] ghost {} clear for {} blocks, {} other points checked, {}", new Object[]{LogFormat.shortId(id), 150, occupants.size(), own}
                    );
            } else {
                LoconauticsConstants.LOGGER.info("[ghostgap] ghost {} has no graph position, {}", LogFormat.shortId(id), own);
            }
        }
    }

    private static List<GhostGapTrace.Occupant> occupants(ServerLevel level, UUID ownId) {
        Set<UUID> mine = new HashSet<>(TrainConsist.orderedChain(level, ownId));
        mine.add(ownId);
        List<GhostGapTrace.Occupant> out = new ArrayList<>();

        for (SableTrain other : SableTrainRegistry.all()) {
            if (other.car() != null && other.car().subLevelId() != null && other.level() == level && !mine.contains(other.car().subLevelId())) {
                double half = TrainLength.carLength(level, other) / 2.0;

                for (SableTrain.Bogey bogey : other.car().chain()) {
                    add(out, other, half, bogey.rail().leading());
                    add(out, other, half, bogey.rail().trailing());
                }
            }
        }

        return out;
    }

    private static void add(List<GhostGapTrace.Occupant> out, SableTrain train, double half, TravellingPoint point) {
        if (point != null && point.edge != null) {
            int first = point.node1.getNetId();
            int second = point.node2.getNetId();
            boolean forward = first <= second;
            out.add(
                new GhostGapTrace.Occupant(
                    train.car().subLevelId(),
                    train.isGhost(),
                    half,
                    Math.min(first, second),
                    Math.max(first, second),
                    forward ? point.position : point.edge.getLength() - point.position
                )
            );
        }
    }

    private static GhostGapTrace.Occupant hitAt(TravellingPoint scout, List<GhostGapTrace.Occupant> occupants) {
        int first = scout.node1.getNetId();
        int second = scout.node2.getNetId();
        int a = Math.min(first, second);
        int b = Math.max(first, second);
        double pos = first <= second ? scout.position : scout.edge.getLength() - scout.position;

        for (GhostGapTrace.Occupant occupant : occupants) {
            if (occupant.a() == a && occupant.b() == b && Math.abs(occupant.pos() - pos) <= occupant.half()) {
                return occupant;
            }
        }

        return null;
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
            Commands.literal("ghostgap")
                .executes(
                    ctx -> {
                        enabled = !enabled;
                        ((CommandSourceStack)ctx.getSource())
                            .sendSuccess(() -> Component.literal("ghost gap trace " + (enabled ? "on" : "off") + ", grep [ghostgap] in latest.log"), false);
                        return 1;
                    }
                )
        );
    }

    private record Occupant(UUID train, boolean ghost, double half, int a, int b, double pos) {
    }
}
