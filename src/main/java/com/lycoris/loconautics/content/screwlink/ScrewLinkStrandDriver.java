package com.lycoris.loconautics.content.screwlink;

import com.lycoris.loconautics.common.strand.StrandEndpoints;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRope;
import com.lycoris.loconautics.content.screwlink.rope.ScrewLinkRopeManager;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class ScrewLinkStrandDriver {
    private static final double COUPLE_RADIUS = 0.75;

    private ScrewLinkStrandDriver() {
    }

    @SubscribeEvent
    public static void onServerTick(Post event) {
        MinecraftServer server = event.getServer();
        ScrewLinkStrandManager strands = ScrewLinkStrandManager.get(server);

        for (ServerLevel level : server.getAllLevels()) {
            ScrewLinkRopeManager ropes = ScrewLinkRopeManager.get(level);
            SubLevelPhysicsSystem system = SubLevelPhysicsSystem.get(level);
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (ropes != null && system != null && container != null) {
                for (ScrewLinkStrand strand : List.copyOf(strands.all())) {
                    if (StrandEndpoints.drivenBy(level, strand.subA, strand.subB)) {
                        driveStrand(level, container, system, strands, ropes, strand);
                    }
                }
            }
        }
    }

    private static void driveStrand(
        ServerLevel level,
        @Nullable SubLevelContainer container,
        SubLevelPhysicsSystem system,
        ScrewLinkStrandManager strands,
        ScrewLinkRopeManager ropes,
        ScrewLinkStrand strand
    ) {
        if (StrandEndpoints.isLoaded(level, container, strand.subA, strand.localA)) {
            if (StrandEndpoints.blockEntity(level, container, strand.subA, strand.localA) instanceof ScrewLinkBlockEntity head) {
                ScrewLinkRope rope = ropes.get(strand.id);
                if (rope == null) {
                    rope = create(level, container, strand);
                    if (rope == null) {
                        return;
                    }

                    ropes.add(rope);
                }

                if (!rope.isActive()) {
                    dropHook(system, rope);
                    if (rope.areAttachmentsLoaded(level) && system.getTicketManager().wouldBeLoaded(level, rope)) {
                        system.addObject(rope);
                    }
                } else {
                    syncEnd(system, strand, rope);
                    rope.updatePose();
                    strand.points = new ArrayList<>(rope.getPoints());
                    strands.touch();
                    if (rope.getHook() != null && !rope.getHook().isGrabbed()) {
                        tryCouple(level, container, strands, strand, rope);
                    }
                }
            } else {
                remove(level, strands, ropes, strand);
            }
        }
    }

    private static void syncEnd(SubLevelPhysicsSystem system, ScrewLinkStrand strand, ScrewLinkRope rope) {
        if (strand.isCoupled()) {
            dropHook(system, rope);
            rope.coupleTo(strand.subB, strand.localB);
        } else if (rope.getHook() == null) {
            rope.decouple();
            List<Vector3d> points = rope.getPoints();
            ScrewLinkHook hook = new ScrewLinkHook((Vector3dc)points.get(points.size() - 1));
            rope.setHook(hook);
            system.addObject(hook);
        }
    }

    private static void dropHook(SubLevelPhysicsSystem system, ScrewLinkRope rope) {
        ScrewLinkHook hook = rope.getHook();
        if (hook != null) {
            hook.clearGrabs();
            rope.releaseHook();
            if (hook.isActive()) {
                system.removeObject(hook);
            }
        }
    }

    private static void tryCouple(
        ServerLevel level, @Nullable SubLevelContainer container, ScrewLinkStrandManager strands, ScrewLinkStrand strand, ScrewLinkRope rope
    ) {
        ScrewLinkHook hook = rope.getHook();
        if (hook != null) {
            Vector3d hookAt = hook.getGrabCenter();

            for (ScrewLinkStrand other : strands.all()) {
                if (other != strand) {
                    Vector3d anchor = StrandEndpoints.worldPoint(level, container, other.subA, other.localA);
                    if (anchor != null && !(anchor.distanceSquared(hookAt) > 0.5625)) {
                        strand.subB = other.subA;
                        strand.localB = other.localA;
                        strands.touch();
                        return;
                    }
                }
            }
        }
    }

    @Nullable
    private static ScrewLinkRope create(ServerLevel level, @Nullable SubLevelContainer container, ScrewLinkStrand strand) {
        if (strand.points != null && strand.points.size() >= 2) {
            return ScrewLinkRope.restore(strand.id, 2.0, strand.subA, strand.localA, strand.points);
        }

        Vector3d anchor = StrandEndpoints.worldPoint(level, container, strand.subA, strand.localA);
        if (anchor == null) {
            return null;
        }

        Vector3d hangsAt = new Vector3d(anchor).sub(0.0, 2.0, 0.0);
        return ScrewLinkRope.build(strand.id, 2.0, strand.subA, strand.localA, anchor, hangsAt);
    }

    private static void remove(ServerLevel level, ScrewLinkStrandManager strands, ScrewLinkRopeManager ropes, ScrewLinkStrand strand) {
        ropes.remove(SubLevelPhysicsSystem.get(level), strand.id);
        strands.remove(strand.id);
        PacketDistributor.sendToPlayersInDimension(level, StrandPointsPacket.removal(StrandKind.SCREW_LINK, strand.id), new CustomPacketPayload[0]);
    }
}
