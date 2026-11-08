package com.lycoris.loconautics.trainphysics.spawn;

import com.lycoris.loconautics.content.ghostwheel.GhostWheelBlock;
import com.lycoris.loconautics.registry.LoconauticsRegistries;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WheelColliders {
    private static final Vec3i[] WHEEL_OFFSETS = plateOffsets(-1);
    private static final Vec3i[] HANGING_WHEEL_OFFSETS = plateOffsets(1);

    private WheelColliders() {
    }

    private static Vec3i[] plateOffsets(int dy) {
        Vec3i[] offsets = new Vec3i[9];
        int i = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                offsets[i++] = new Vec3i(dx, dy, dz);
            }
        }

        return offsets;
    }

    private static Vec3i[] offsetsFor(boolean upsideDown) {
        return upsideDown ? HANGING_WHEEL_OFFSETS : WHEEL_OFFSETS;
    }

    public static boolean isMonorailBogey(BlockState bogey) {
        return bogey.getBlock() instanceof AbstractBogeyBlock && BuiltInRegistries.BLOCK.getKey(bogey.getBlock()).getPath().contains("mono_bogey");
    }

    public static boolean isUpsideDown(BlockState bogey) {
        return bogey.getBlock() instanceof AbstractBogeyBlock<?> block && block.isUpsideDown(bogey);
    }

    private static BlockState colliderBlock(Vec3i offset, boolean upsideDown, boolean monorail, Axis axis) {
        BlockState state = (BlockState)((BlockState)((BlockState)((GhostWheelBlock)LoconauticsRegistries.GHOST_WHEEL.get())
                    .defaultBlockState()
                    .setValue(GhostWheelBlock.UPSIDE_DOWN, upsideDown))
                .setValue(GhostWheelBlock.ON_MONORAIL, monorail))
            .setValue(GhostWheelBlock.AXIS, axis);
        int across = axis == Axis.X ? offset.getZ() : offset.getX();
        int along = axis == Axis.X ? offset.getX() : offset.getZ();
        if (along != 0) {
            state = (BlockState)state.setValue(GhostWheelBlock.ALONG, along > 0 ? 1 : 2);
        }

        return across == 0 ? (BlockState)state.setValue(GhostWheelBlock.MIDDLE, true) : (BlockState)state.setValue(GhostWheelBlock.CORNER, across > 0 ? 1 : 2);
    }

    private static BlockPos bogeyPos(ServerSubLevel sub, SableTrain.Bogey bogey) {
        return sub.getPlot().getCenterBlock().offset(bogey.localPos()).immutable();
    }

    private static boolean bogeyAlive(ServerLevel level, BlockPos bogeyPos) {
        return level.getBlockState(bogeyPos).getBlock() instanceof AbstractBogeyBlock;
    }

    private static ServerSubLevel resolveSubLevel(SableTrain train) {
        SableTrain.Car car = train.car();
        if (car != null && car.subLevelId() != null) {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(train.level());
            return container != null && container.getSubLevel(car.subLevelId()) instanceof ServerSubLevel sub && !sub.isRemoved() ? sub : null;
        } else {
            return null;
        }
    }

    public static List<BlockPos> placePads(ServerLevel level, BlockPos bogeyPos) {
        BlockState bogeyState = level.getBlockState(bogeyPos);
        boolean upsideDown = isUpsideDown(bogeyState);
        boolean monorail = isMonorailBogey(bogeyState);
        Axis axis = bogeyState.hasProperty(AbstractBogeyBlock.AXIS) ? (Axis)bogeyState.getValue(AbstractBogeyBlock.AXIS) : Axis.Z;
        List<BlockPos> placed = new ArrayList<>(WHEEL_OFFSETS.length);

        for (Vec3i offset : offsetsFor(upsideDown)) {
            BlockPos pos = bogeyPos.offset(offset).immutable();
            if (level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, colliderBlock(offset, upsideDown, monorail, axis));
            }

            if (level.getBlockState(pos).is((Block)LoconauticsRegistries.GHOST_WHEEL.get())) {
                placed.add(pos);
            }
        }

        return placed;
    }

    public static void reconcile(SableTrain train) {
        if (!train.isPoseSettling()) {
            ServerSubLevel sub = resolveSubLevel(train);
            SableTrain.Car car = train.car();
            if (sub != null && car != null) {
                ServerLevel level = sub.getLevel();
                List<SableTrain.Bogey> alive = new ArrayList<>(car.bogeys().size());

                for (SableTrain.Bogey bogey : car.bogeys()) {
                    BlockPos bogeyPos = bogeyPos(sub, bogey);
                    if (bogeyAlive(level, bogeyPos)) {
                        alive.add(bogey);
                    } else {
                        clearPadsAround(level, bogeyPos);
                    }
                }

                if (alive.size() != car.bogeys().size()) {
                    BogeyEditTrace.refuse(train, "reconcile dropped " + (car.bogeys().size() - alive.size()) + " bogey(s)");
                    train.setBogeys(alive);
                    SubLevelSplitWatcher.refitAfterBogeyDrop(train, sub, alive);
                }
            }
        }
    }

    public static boolean isOrphanPad(LevelAccessor level, BlockPos pad) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockState(pad.offset(dx, 1, dz)).getBlock() instanceof AbstractBogeyBlock
                    || level.getBlockState(pad.offset(dx, -1, dz)).getBlock() instanceof AbstractBogeyBlock) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean isPad(BlockState state) {
        return state.is((Block)LoconauticsRegistries.GHOST_WHEEL.get());
    }

    public static void clearPadsAround(LevelAccessor level, BlockPos bogeyPos) {
        for (Vec3i[] layer : new Vec3i[][]{WHEEL_OFFSETS, HANGING_WHEEL_OFFSETS}) {
            for (Vec3i offset : layer) {
                BlockPos pos = bogeyPos.offset(offset);
                if (level.getBlockState(pos).is((Block)LoconauticsRegistries.GHOST_WHEEL.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
