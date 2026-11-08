package com.lycoris.loconautics.foundation.debug;

import com.lycoris.loconautics.network.packets.debug.DebugLinePacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DebugLines {
    public static final float DEFAULT_WIDTH = 0.125F;
    public static final int DEFAULT_TTL = 20;
    public static final double COLUMN_HEIGHT = 3.0;
    private static final double SEND_RANGE = 128.0;

    private DebugLines() {
    }

    public static int rgb(float r, float g, float b) {
        return (int)(r * 255.0F) << 16 | (int)(g * 255.0F) << 8 | (int)(b * 255.0F);
    }

    public static void line(ServerLevel level, String slot, Vec3 start, Vec3 end, int color) {
        line(level, slot, start, end, color, 0.125F, 20);
    }

    public static void line(ServerLevel level, String slot, Vec3 start, Vec3 end, int color, float width, int ttl) {
        if (level != null && slot != null && start != null && end != null) {
            PacketDistributor.sendToPlayersNear(
                level, null, start.x, start.y, start.z, 128.0, new DebugLinePacket(slot, start, end, color, width, ttl), new CustomPacketPayload[0]
            );
        }
    }

    public static void column(ServerLevel level, String slot, Vec3 at, int color) {
        column(level, slot, at, color, 20);
    }

    public static void column(ServerLevel level, String slot, Vec3 at, int color, int ttl) {
        if (at != null) {
            line(level, slot, at, at.add(0.0, 3.0, 0.0), color, 0.125F, ttl);
        }
    }

    public static void arrow(ServerLevel level, String slot, Vec3 from, Vec3 to, int color, float width, int ttl) {
        if (level != null && slot != null && from != null && to != null) {
            Vec3 along = to.subtract(from);
            if (!(along.lengthSqr() < 1.0E-9)) {
                Vec3 dir = along.normalize();
                Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
                if (side.lengthSqr() < 1.0E-6) {
                    side = dir.cross(new Vec3(1.0, 0.0, 0.0));
                }

                side = side.normalize().scale(along.length() * 0.18);
                Vec3 back = to.subtract(dir.scale(along.length() * 0.28));
                line(level, slot, from, to, color, width, ttl);
                line(level, slot + ":barbA", to, back.add(side), color, width, ttl);
                line(level, slot + ":barbB", to, back.subtract(side), color, width, ttl);
            }
        }
    }

    public static void clearArrow(ServerLevel level, String slot) {
        clear(level, slot);
        clear(level, slot + ":barbA");
        clear(level, slot + ":barbB");
    }

    public static void clear(ServerLevel level, String slot) {
        if (level != null && slot != null) {
            PacketDistributor.sendToPlayersInDimension(level, new DebugLinePacket(slot, Vec3.ZERO, Vec3.ZERO, 0, 0.0F, 0), new CustomPacketPayload[0]);
        }
    }
}
