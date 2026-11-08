package com.lycoris.loconautics.client.connected;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public record ConnectionBounds(int x, int y, int z) {
    public static final ConnectionBounds UNBOUNDED = new ConnectionBounds(64, 64, 64);
    public static final ConnectionBounds FLAT_3X3 = new ConnectionBounds(3, 1, 3);

    public int limit(Axis axis) {
        return switch (axis) {
            case X -> this.x;
            case Y -> this.y;
            case Z -> this.z;
            default -> throw new MatchException(null, null);
        };
    }

    public boolean fits(Collection<BlockPos> group, BlockPos candidate) {
        int minX = candidate.getX();
        int maxX = minX;
        int minY = candidate.getY();
        int maxY = minY;
        int minZ = candidate.getZ();
        int maxZ = minZ;

        for (BlockPos member : group) {
            minX = Math.min(minX, member.getX());
            maxX = Math.max(maxX, member.getX());
            minY = Math.min(minY, member.getY());
            maxY = Math.max(maxY, member.getY());
            minZ = Math.min(minZ, member.getZ());
            maxZ = Math.max(maxZ, member.getZ());
        }

        return maxX - minX < this.x && maxY - minY < this.y && maxZ - minZ < this.z;
    }

    public Set<BlockPos> group(ConnectedBlock block, BlockGetter level, BlockState state, BlockPos anchor) {
        BlockPos start = anchor.immutable();
        Set<BlockPos> found = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        found.add(start);
        pending.add(start);

        while (!pending.isEmpty()) {
            BlockPos current = pending.poll();

            for (Direction dir : Direction.values()) {
                if (this.limit(dir.getAxis()) >= 2) {
                    BlockPos next = current.relative(dir);
                    if (!found.contains(next) && this.fits(found, next) && block.connectsTo(level, state, start, next)) {
                        found.add(next);
                        pending.add(next);
                    }
                }
            }
        }

        return found;
    }
}
