package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.state.SableTrainRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class DockApproach {
    private static final double RANGE = 4.0;
    private static final double LATERAL_LIMIT = 0.9;
    private static final double ARRIVED = 0.1;
    private static final double MIN_BPS = 0.5;
    private static final double MAX_BPS = 3.0;
    private static final double GAIN_BPS_PER_BLOCK = 2.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final int RESCAN_TICKS = 40;
    private static final int CHECK_TICKS = 10;
    private static final double MIN_PROGRESS = 0.02;
    private static final Map<UUID, DockApproach.Approach> ACTIVE = new HashMap<>();
    private static final Map<UUID, List<DockingConnectorBlockEntity>> CONNECTORS = new HashMap<>();
    private static final Map<UUID, Long> SCANNED = new HashMap<>();

    private DockApproach() {
    }

    public static void clear() {
        ACTIVE.clear();
        CONNECTORS.clear();
        SCANNED.clear();
    }

    private static List<DockingConnectorBlockEntity> connectorsOf(ServerSubLevel sub, long now) {
        UUID id = sub.getUniqueId();
        Long scanned = SCANNED.get(id);
        if (scanned == null || now - scanned >= 40L) {
            SCANNED.put(id, now);
            CONNECTORS.put(id, SubLevelBlocks.findAll(sub, DockingConnectorBlockEntity.class));
        }

        return CONNECTORS.get(id);
    }

    private static boolean open(DockingConnectorBlockEntity connector) {
        return !connector.isRemoved() && connector.isExtended() && !connector.hasOtherConnector();
    }

    private static Vector3d tipWorld(ServerSubLevel sub, DockingConnectorBlockEntity connector) {
        Vec3 tip = connector.getTipPosition();
        return sub.logicalPose().transformPosition(new Vector3d(tip.x, tip.y, tip.z), new Vector3d());
    }

    private static Vector3d facingWorld(ServerSubLevel sub, DockingConnectorBlockEntity connector) {
        Direction facing = (Direction)connector.getBlockState().getValue(BlockStateProperties.FACING);
        return sub.logicalPose().orientation().transform(new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ()), new Vector3d());
    }

    public static void drive(ServerSubLevel sub, SableTrain train) {
        if (sub.getLevel() instanceof ServerLevel level && train.car() != null) {
            long now = level.getGameTime();
            List<DockingConnectorBlockEntity> mine = connectorsOf(sub, now);
            if (mine != null && !mine.isEmpty()) {
                UUID id = sub.getUniqueId();

                for (DockingConnectorBlockEntity connector : mine) {
                    if (open(connector)) {
                        Vector3d facing = facingWorld(sub, connector);
                        Vector3d tip = tipWorld(sub, connector);

                        for (SableTrain other : SableTrainRegistry.all()) {
                            if (other.car() != null && !id.equals(other.car().subLevelId()) && other.level() == train.level()) {
                                ServerSubLevel otherSub = SubLevelData.resolve(level, other.car().subLevelId());
                                if (otherSub != null && !otherSub.isRemoved() && movesToward(sub, otherSub)) {
                                    for (DockingConnectorBlockEntity partner : connectorsOf(otherSub, now)) {
                                        if (open(partner) && pull(level, sub, train, connector, facing, tip, otherSub, partner, now)) {
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                stop(train, id);
            }
        }
    }

    private static boolean movesToward(ServerSubLevel sub, ServerSubLevel other) {
        double mine = sub.getMassTracker().getMass();
        double theirs = other.getMassTracker().getMass();
        return mine < theirs || mine == theirs && sub.getUniqueId().compareTo(other.getUniqueId()) < 0;
    }

    private static boolean pull(
        ServerLevel level,
        ServerSubLevel sub,
        SableTrain train,
        DockingConnectorBlockEntity connector,
        Vector3d facing,
        Vector3d tip,
        ServerSubLevel otherSub,
        DockingConnectorBlockEntity partner,
        long now
    ) {
        Vector3d partnerFacing = facingWorld(otherSub, partner);
        if (facing.dot(partnerFacing) > -0.9) {
            return false;
        }

        Vector3d gap = tipWorld(otherSub, partner).sub(tip);
        double along = gap.dot(facing);
        double lateral = new Vector3d(gap).fma(-along, facing).length();
        if (!(along > 4.0) && !(along < -0.3) && !(lateral > 0.9)) {
            Vector3d axis = SubLevelData.directionOf(sub, train.car().carriage().referenceForward()).normalize();
            double aligned = facing.dot(axis);
            if (Math.abs(aligned) < 0.5) {
                return false;
            }

            UUID id = sub.getUniqueId();
            DockApproach.Approach approach = ACTIVE.computeIfAbsent(id, key -> new DockApproach.Approach());
            if (now - approach.checkedAt >= 10L) {
                if (approach.checkedAlong - along < 0.02 && approach.checkedAt != 0L && along > 0.1) {
                    approach.flip = !approach.flip;
                }

                approach.checkedAt = now;
                approach.checkedAlong = along;
            }

            double bps = along <= 0.1 ? 0.0 : Math.min(3.0, Math.max(0.5, 2.0 * along));
            double speed = Math.signum(aligned) * (approach.flip ? -1.0 : 1.0) * bps / 20.0;
            train.setTargetSpeed(speed);
            train.setSpeed(speed);
            return true;
        } else {
            return false;
        }
    }

    private static void stop(SableTrain train, UUID id) {
        if (ACTIVE.remove(id) != null) {
            train.setTargetSpeed(0.0);
        }
    }

    private static final class Approach {
        private boolean flip;
        private long checkedAt;
        private double checkedAlong = Double.MAX_VALUE;
    }
}
