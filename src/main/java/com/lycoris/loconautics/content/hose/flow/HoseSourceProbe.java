package com.lycoris.loconautics.content.hose.flow;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities.FluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class HoseSourceProbe {
    private static final int REFRESH_TICKS = 10;
    private static final int MAX_PIPES = 128;
    private final Set<IFluidHandler> sources = Collections.newSetFromMap(new IdentityHashMap<>());
    private long readTick = -4611686018427387904L;

    public boolean feeds(Level level, BlockPos endpoint, Direction inputSide, @Nullable IFluidHandler sink) {
        IFluidHandler tank = HoseFlowSink.tankOf(sink);
        if (tank == null) {
            return false;
        }

        long now = level.getGameTime();
        if (now - this.readTick >= 10L) {
            this.refresh(level, endpoint, inputSide);
            this.readTick = now;
        }

        return this.sources.contains(tank);
    }

    private void refresh(Level level, BlockPos endpoint, Direction inputSide) {
        this.sources.clear();
        BlockPos first = endpoint.relative(inputSide);
        if (FluidPropagator.getPipe(level, first) == null) {
            this.addHandler(level, first, inputSide.getOpposite());
        } else {
            Queue<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(first);
            seen.add(first);

            while (!queue.isEmpty() && seen.size() <= 128) {
                BlockPos pos = queue.poll();
                FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, pos);
                if (pipe != null) {
                    for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(pos), pipe)) {
                        BlockPos next = pos.relative(d);
                        if (!next.equals(endpoint) && level.isLoaded(next) && seen.add(next)) {
                            if (FluidPropagator.getPipe(level, next) != null) {
                                queue.add(next);
                            } else {
                                this.addHandler(level, next, d.getOpposite());
                            }
                        }
                    }
                }
            }
        }
    }

    private void addHandler(Level level, BlockPos pos, Direction side) {
        IFluidHandler handler = (IFluidHandler)level.getCapability(FluidHandler.BLOCK, pos, side);
        if (handler != null) {
            this.sources.add(handler);
        }
    }
}
