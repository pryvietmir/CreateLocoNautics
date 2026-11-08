package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlock;
import com.lycoris.loconautics.content.analogcontroller.AnalogControllerBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleBlockEntity;
import com.lycoris.loconautics.content.bearingaxle.BearingAxleValidity;
import com.lycoris.loconautics.content.transmission.TransmissionBlockEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class ScheduleReadiness {
    private ScheduleReadiness() {
    }

    public static ScheduleReadiness.Result check(ServerSubLevel sub, AnalogControllerBlockEntity controller, UUID trainId) {
        if (sub == null || sub.isRemoved()) {
            return ScheduleReadiness.Result.fail("no_sub_level");
        }

        if (sub.getPlot().getLoadedChunks().isEmpty()) {
            return ScheduleReadiness.Result.fail("not_loaded");
        }

        if (controller == null) {
            return ScheduleReadiness.Result.fail("no_controller");
        }

        BlockPos seat = driverSeat(sub, trainId);
        if (seat != null && atControls(controller, seat)) {
            BearingAxleBlockEntity axle = BearingAxleValidity.resolve(sub);
            if (axle == null) {
                return ScheduleReadiness.Result.fail("invalid_bearing_axle");
            }

            List<TransmissionBlockEntity> transmissions = SubLevelBlocks.findAll(sub, TransmissionBlockEntity.class);
            if (transmissions.isEmpty()) {
                return ScheduleReadiness.Result.fail("no_transmission");
            }

            TransmissionBlockEntity transmission = null;

            for (TransmissionBlockEntity candidate : transmissions) {
                if (LocomotiveHardware.link(candidate, axle) == LocomotiveHardware.Link.OK) {
                    transmission = candidate;
                    break;
                }
            }

            if (transmission == null) {
                return ScheduleReadiness.Result.fail(LocomotiveHardware.linkKey(LocomotiveHardware.link(transmissions.get(0), axle)));
            } else {
                LocomotiveHardware.Result wiring = LocomotiveHardware.validate(controller, transmission, axle);
                if (!wiring.ok()) {
                    return ScheduleReadiness.Result.fail(wiring.messageKey());
                } else {
                    return Math.abs(transmission.getSpeed()) < 0.001F ? ScheduleReadiness.Result.fail("no_rotation") : ScheduleReadiness.Result.PASS;
                }
            }
        } else {
            return ScheduleReadiness.Result.fail("no_driver");
        }
    }

    @Nullable
    public static BlockPos driverSeat(ServerSubLevel sub, UUID trainId) {
        for (BlazeBurnerBlockEntity burner : SubLevelBlocks.findAll(sub, BlazeBurnerBlockEntity.class)) {
            if (burner.hat && burner.getHeatLevelFromBlock() != HeatLevel.NONE) {
                return burner.getBlockPos();
            }
        }

        BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
        AABB box = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0);

        for (LivingEntity mob : sub.getLevel().getEntitiesOfClass(LivingEntity.class, box)) {
            if (mob.getVehicle() != null && ScheduleConductor.drives(mob, trainId)) {
                return BlockPos.containing(mob.getVehicle().position());
            }
        }

        return null;
    }

    public static boolean atControls(AnalogControllerBlockEntity controller, BlockPos driver) {
        return atControls(controller.getBlockState(), controller.getBlockPos(), driver);
    }

    private static boolean atControls(BlockState controller, BlockPos controllerPos, BlockPos driver) {
        BlockPos front = controllerPos.relative((Direction)controller.getValue(HorizontalDirectionalBlock.FACING));
        return driver.getX() == front.getX() && driver.getZ() == front.getZ() && (driver.getY() == front.getY() || driver.getY() == front.getY() - 1);
    }

    public static boolean seatedAtControls(LivingEntity rider) {
        if (!(rider.getVehicle() instanceof SeatEntity seat)) {
            return false;
        } else {
            BlockPos var8 = BlockPos.containing(seat.position());

            for (Direction side : Plane.HORIZONTAL) {
                for (int up = 0; up <= 1; up++) {
                    BlockPos at = var8.relative(side).above(up);
                    BlockState state = rider.level().getBlockState(at);
                    if (state.getBlock() instanceof AnalogControllerBlock && atControls(state, at, var8)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public static ScheduleReadiness.Reach reach(@Nullable ServerSubLevel sub, UUID trainId, @Nullable Direction axleFront) {
        AnalogControllerBlockEntity driven = drivenController(sub, trainId);
        if (driven != null && axleFront != null) {
            boolean forward = looksAlong(driven, axleFront);
            boolean backward = looksAlong(driven, axleFront.getOpposite());

            for (AnalogControllerBlockEntity other : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
                if (other != driven && LocomotiveHardware.validate(sub, other).ok()) {
                    forward |= looksAlong(other, axleFront);
                    backward |= looksAlong(other, axleFront.getOpposite());
                }
            }

            return !forward && !backward ? ScheduleReadiness.Reach.FORWARD : new ScheduleReadiness.Reach(forward, backward);
        } else {
            return ScheduleReadiness.Reach.FORWARD;
        }
    }

    public static boolean looksAlong(AnalogControllerBlockEntity controller, Direction direction) {
        return ((Direction)controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING)).getOpposite() == direction;
    }

    @Nullable
    public static AnalogControllerBlockEntity controllerAt(ServerSubLevel sub, BlockPos driver) {
        for (AnalogControllerBlockEntity controller : SubLevelBlocks.findAll(sub, AnalogControllerBlockEntity.class)) {
            if (atControls(controller, driver)) {
                return controller;
            }
        }

        return null;
    }

    @Nullable
    public static AnalogControllerBlockEntity drivenController(@Nullable ServerSubLevel sub, UUID trainId) {
        if (sub != null && !sub.isRemoved()) {
            BlockPos seat = driverSeat(sub, trainId);
            return seat == null ? null : controllerAt(sub, seat);
        } else {
            return null;
        }
    }

    public record Reach(boolean forward, boolean backward) {
        static final ScheduleReadiness.Reach FORWARD = new ScheduleReadiness.Reach(true, false);
    }

    public record Result(boolean ok, String reasonKey) {
        static final ScheduleReadiness.Result PASS = new ScheduleReadiness.Result(true, null);

        static ScheduleReadiness.Result fail(String key) {
            return new ScheduleReadiness.Result(false, key);
        }
    }
}
