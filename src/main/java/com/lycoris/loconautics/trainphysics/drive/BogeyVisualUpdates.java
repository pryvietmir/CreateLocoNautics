package com.lycoris.loconautics.trainphysics.drive;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.rail.RailCarriage;
import com.lycoris.loconautics.trainphysics.spawn.SubLevelSplitWatcher;
import com.lycoris.loconautics.trainphysics.visual.BogeyYawVisual;
import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class BogeyVisualUpdates {
    private BogeyVisualUpdates() {
    }

    static void updateYaw(SableTrain train) {
        GhostWheelBodies.syncSwing(train);
        if (train.isDerailed()) {
            clearBogeyRailPose(train);
        } else {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            if (container != null) {
                SableTrain.Car car = train.car();
                if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                    if (!car.bogeys().isEmpty()) {
                        ServerLevel subLevel = sub.getLevel();

                        for (SableTrain.Bogey bogey : car.bogeys()) {
                            BlockPos worldPos = sub.getPlot().getCenterBlock().offset(bogey.localPos());
                            if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                                double vv = train.speed() * train.speed() * 400.0;
                                double kappa = bogey.rail().signedLateralCurvature();
                                Vector3f offsetLocal = bogeyPivotOffset(vv, kappa);
                                Vector3d counter = PoseTweak.counter(
                                    sub,
                                    new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
                                    car.carriage().referenceForward(),
                                    train.poseTweak()
                                );
                                BogeyYawVisual.setRailPose(
                                    be, railLinkOf(bogey.rail()), offsetLocal, new Vector3f((float)counter.x, (float)counter.y, (float)counter.z)
                                );
                            }
                        }

                        for (BlockPos local : SubLevelSplitWatcher.idleBogeysCached(train)) {
                            BlockPos worldPos = sub.getPlot().getCenterBlock().offset(local);
                            RailCarriage idleRail = SubLevelSplitWatcher.idleBogeyRail(train, sub, local);
                            if (idleRail != null && subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                                Vector3d counter = PoseTweak.counter(
                                    sub,
                                    new Vector3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5),
                                    car.carriage().referenceForward(),
                                    train.poseTweak()
                                );
                                BogeyYawVisual.setRailPose(
                                    be,
                                    railLinkOf(idleRail),
                                    bogeyPivotOffset(train.speed() * train.speed() * 400.0, idleRail.signedLateralCurvature()),
                                    new Vector3f((float)counter.x, (float)counter.y, (float)counter.z)
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    static BogeyYawVisual.Link railLinkOf(RailCarriage rail) {
        if (rail != null && rail.graph() != null) {
            TravellingPoint lead = rail.leading();
            TravellingPoint trail = rail.trailing();
            return lead != null && trail != null && lead.node1 != null && lead.node2 != null && trail.node1 != null && trail.node2 != null
                ? new BogeyYawVisual.Link(
                    rail.graph().id,
                    lead.node1.getNetId(),
                    lead.node2.getNetId(),
                    (float)lead.position,
                    trail.node1.getNetId(),
                    trail.node2.getNetId(),
                    (float)trail.position,
                    rail.lastTravelSign() < 0.0
                )
                : null;
        } else {
            return null;
        }
    }

    static void clearBogeyRailPose(SableTrain train) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
        if (container != null) {
            SableTrain.Car car = train.car();
            if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                ServerLevel subLevel = sub.getLevel();

                for (SableTrain.Bogey bogey : car.bogeys()) {
                    BlockPos worldPos = sub.getPlot().getCenterBlock().offset(bogey.localPos());
                    if (subLevel.getBlockEntity(worldPos) instanceof AbstractBogeyBlockEntity be) {
                        BogeyYawVisual.clearRailBodyPose(be);
                    }
                }
            }
        }
    }

    static Vector3f bogeyPivotOffset(double vv, double kappa) {
        double swayMax = (Double)LoconauticsConfig.BOGEY_SWAY_MAX.get();
        double sway = clamp((Double)LoconauticsConfig.BOGEY_SWAY_GAIN.get() * vv * kappa, -swayMax, swayMax);
        return new Vector3f((float)sway, 0.0F, 0.0F);
    }

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static void updateWheels(SableTrain train) {
        SableTrain.Car car = train.car();
        if (!SableTrainDriver.useForceGlue(car) && !train.isTrackless()) {
            double ds = train.speed();
            if (ds != 0.0) {
                ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
                if (container != null) {
                    if (car.subLevelId() != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved()) {
                        TrainRailAdvance.animateBogeyWheels(car, sub, ds);
                    }
                }
            }
        }
    }
}
