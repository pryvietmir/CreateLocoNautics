package com.lycoris.loconautics.foundation.math;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VoxelShapes {
    private VoxelShapes() {
    }

    public static Map<Direction, VoxelShape> byHorizontalFacing(VoxelShape facingNorth) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, facingNorth.optimize());
        shapes.put(Direction.EAST, rotateY(facingNorth, 270));
        shapes.put(Direction.SOUTH, rotateY(facingNorth, 180));
        shapes.put(Direction.WEST, rotateY(facingNorth, 90));
        return Map.copyOf(shapes);
    }

    public static VoxelShape rotateY(VoxelShape shape, int degrees) {
        VoxelShape result = Shapes.empty();

        for (AABB box : shape.toAabbs()) {
            double minX = box.minX;
            double minZ = box.minZ;
            double maxX = box.maxX;
            double maxZ = box.maxZ;
            double newMinX;
            double newMinZ;
            double newMaxX;
            double newMaxZ;
            switch ((degrees % 360 + 360) % 360) {
                case 90:
                    newMinX = minZ;
                    newMaxX = maxZ;
                    newMinZ = 1.0 - maxX;
                    newMaxZ = 1.0 - minX;
                    break;
                case 180:
                    newMinX = 1.0 - maxX;
                    newMaxX = 1.0 - minX;
                    newMinZ = 1.0 - maxZ;
                    newMaxZ = 1.0 - minZ;
                    break;
                case 270:
                    newMinX = 1.0 - maxZ;
                    newMaxX = 1.0 - minZ;
                    newMinZ = minX;
                    newMaxZ = maxX;
                    break;
                default:
                    newMinX = minX;
                    newMaxX = maxX;
                    newMinZ = minZ;
                    newMaxZ = maxZ;
            }

            result = Shapes.or(result, Shapes.box(newMinX, box.minY, newMinZ, newMaxX, box.maxY, newMaxZ));
        }

        return result.optimize();
    }
}
