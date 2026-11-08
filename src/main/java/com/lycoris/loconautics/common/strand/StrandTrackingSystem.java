package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.network.packets.attachment.StrandPointsPacket;
import dev.ryanhcode.sable.api.physics.object.rope.RopeHandle.AttachmentPoint;
import dev.ryanhcode.sable.api.sublevel.SubLevelTrackingPlugin;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public final class StrandTrackingSystem<R extends AttachedRopePhysicsObject> implements SubLevelTrackingPlugin {
    private final ServerLevel level;
    private final Function<R, StrandKind> kindOf;
    private final Function<Level, RopeManager<R>> ropes;

    public StrandTrackingSystem(ServerLevel level, Function<R, StrandKind> kindOf, Function<Level, RopeManager<R>> ropes) {
        this.level = level;
        this.kindOf = kindOf;
        this.ropes = ropes;
    }

    @Override
    public Iterable<UUID> neededPlayers() {
        RopeManager<R> manager = this.ropes.apply(this.level);
        boolean moving = false;
        if (manager != null) {
            for (R rope : manager.all()) {
                StrandKnotGrab grab = rope.knotGrab();
                if (grab.traceTicks > 0 && --grab.traceTicks % 10 == 0) {
                    LoconauticsConstants.LOGGER
                        .debug(
                            "[knotgrab] after release id={} active={} midY={} engaged={}",
                            new Object[]{rope.getUUID(), rope.isActive(), ((Vector3d)rope.getPoints().get(rope.getPoints().size() / 2)).y, grab.engaged()}
                        );
                }

                if (rope.isActive()) {
                    rope.updatePose();
                    RopePullTrace.ends(rope, this.level);
                    moving |= grab.engaged() || rope.needsSync();
                }
            }
        }

        return !moving ? List.of() : this.level.players().stream().<UUID>map(Entity::getUUID).toList();
    }

    @Override
    public void sendTrackingData(int interpolationTick) {
        RopeManager<R> manager = this.ropes.apply(this.level);
        if (manager != null) {
            for (R rope : manager.all()) {
                if (rope.isActive()) {
                    this.send(rope, interpolationTick);
                }
            }
        }
    }

    private void send(@Nullable R rope, int interpolationTick) {
        if (rope.needsSync()) {
            rope.setNetworkingStopped(false);
            PacketDistributor.sendToPlayersInDimension(
                this.level,
                new StrandPointsPacket(
                    this.kindOf.apply(rope),
                    rope.getUUID(),
                    interpolationTick,
                    false,
                    List.copyOf(rope.getPoints()),
                    rope.endOf(AttachmentPoint.START),
                    rope.endOf(AttachmentPoint.END)
                ),
                new CustomPacketPayload[0]
            );
            rope.justSynced();
        } else if (!rope.isNetworkingStopped()) {
            rope.setNetworkingStopped(true);
            PacketDistributor.sendToPlayersInDimension(
                this.level,
                new StrandPointsPacket(
                    this.kindOf.apply(rope),
                    rope.getUUID(),
                    interpolationTick,
                    true,
                    List.copyOf(rope.getPoints()),
                    rope.endOf(AttachmentPoint.START),
                    rope.endOf(AttachmentPoint.END)
                ),
                new CustomPacketPayload[0]
            );
        }
    }
}
