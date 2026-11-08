package com.lycoris.loconautics.content.hose.flow;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.lycoris.loconautics.content.hose.link.HoseConnectorBlockEntity;
import com.lycoris.loconautics.content.hose.link.HoseLinks;
import com.lycoris.loconautics.content.industrial.IndustrialBlocks;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.PipeConnection;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class HosePressure {
    public static final float MIN_SPRAY_STRENGTH = 0.15F;
    private static final float REACH_TOLERANCE = 0.5F;
    private static final int MAX_LINK_DEPTH = 4;

    private HosePressure() {
    }

    public static float rpmFraction(float rpm) {
        int maxRpm = (Integer)AllConfigs.server().kinetics.maxRotationSpeed.get();
        return Mth.clamp(Math.abs(rpm) / maxRpm, 0.0F, 1.0F);
    }

    public static float reach(float rpm) {
        return rpmFraction(rpm) * ((Integer)LoconauticsConfig.HOSE_MAX_LENGTH.get()).intValue();
    }

    public static boolean reaches(float rpm, double hoseLength) {
        return reach(rpm) + 0.5F >= Math.min(hoseLength, ((Integer)LoconauticsConfig.HOSE_MAX_LENGTH.get()).intValue());
    }

    public static float strength(float rpm, double hoseLength) {
        float lengthFactor = 1.0F - (float)hoseLength / ((Integer)LoconauticsConfig.HOSE_MAX_LENGTH.get()).intValue();
        return Mth.clamp(rpmFraction(rpm) * lengthFactor, 0.15F, 1.0F);
    }

    public static HosePressure.Pressure read(Level level, BlockPos endpoint, Direction inputSide) {
        return read(level, endpoint, inputSide, 0);
    }

    public static HosePressure.Pressure read(Level level, BlockPos endpoint, Direction inputSide, int linkDepth) {
        BlockPos neighbor = endpoint.relative(inputSide);
        Direction facing = inputSide.getOpposite();
        BlockEntity be = level.getBlockEntity(neighbor);
        if (!(be instanceof HoseConnectorBlockEntity connector)) {
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, neighbor);
            PipeConnection face = pipe == null ? null : pipe.getConnection(facing);
            if (face != null && face.hasPressure()) {
                HosePressure.Pressure raw = new HosePressure.Pressure((Float)face.getPressure().getSecond(), (Float)face.getPressure().getFirst());
                return be instanceof PumpBlockEntity ? raw : raw.scaled(distanceFactor(level, neighbor));
            } else {
                return HosePressure.Pressure.NONE;
            }
        } else {
            return connector.plateSide() == facing && linkDepth < 4 ? HoseLinks.pressureBehind(connector, linkDepth + 1) : HosePressure.Pressure.NONE;
        }
    }

    private static float distanceFactor(Level level, BlockPos firstPipe) {
        Queue<HosePressure.Step> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        int limit = (int)(FluidPropagator.getPumpRange() * 1.5F);
        queue.add(new HosePressure.Step(firstPipe, 1));
        visited.add(firstPipe);

        while (!queue.isEmpty()) {
            HosePressure.Step step = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, step.pos());
            if (pipe != null) {
                for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(step.pos()), pipe)) {
                    BlockPos next = step.pos().relative(d);
                    if (level.isLoaded(next)) {
                        if (level.getBlockEntity(next) instanceof PumpBlockEntity pump) {
                            if (drives(pump, d.getOpposite())) {
                                int range = IndustrialBlocks.pumpRange(pump.getBlockState(), FluidPropagator.getPumpRange());
                                return Mth.clamp(1.0F - (float)step.pipes() / range, 0.0F, 1.0F);
                            }
                        } else if (step.pipes() < limit && visited.add(next)) {
                            queue.add(new HosePressure.Step(next, step.pipes() + 1));
                        }
                    }
                }
            }
        }

        return 1.0F;
    }

    private static boolean drives(PumpBlockEntity pump, Direction side) {
        FluidTransportBehaviour transport = (FluidTransportBehaviour)pump.getBehaviour(FluidTransportBehaviour.TYPE);
        PipeConnection connection = transport == null ? null : transport.getConnection(side);
        return connection != null && connection.hasPressure();
    }

    public record Pressure(float push, float pull) {
        public static final HosePressure.Pressure NONE = new HosePressure.Pressure(0.0F, 0.0F);

        HosePressure.Pressure scaled(float factor) {
            return new HosePressure.Pressure(this.push * factor, this.pull * factor);
        }
    }

    private record Step(BlockPos pos, int pipes) {
    }
}
