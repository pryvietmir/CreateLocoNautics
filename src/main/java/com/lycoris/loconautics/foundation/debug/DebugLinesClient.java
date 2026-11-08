package com.lycoris.loconautics.foundation.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class DebugLinesClient {
    private static final Map<String, DebugLinesClient.Entry> LINES = new HashMap<>();

    private DebugLinesClient() {
    }

    public static void put(String slot, Vec3 start, Vec3 end, int color, float width, int ttl) {
        if (ttl <= 0) {
            LINES.remove(slot);
        } else {
            DebugLinesClient.Entry entry = LINES.computeIfAbsent(slot, s -> new DebugLinesClient.Entry());
            entry.start = start;
            entry.end = end;
            entry.color = color;
            entry.width = width;
            entry.ticksLeft = ttl;
        }
    }

    public static void tick() {
        Iterator<DebugLinesClient.Entry> it = LINES.values().iterator();

        while (it.hasNext()) {
            if (--it.next().ticksLeft <= 0) {
                it.remove();
            }
        }
    }

    public static void clear() {
        LINES.clear();
    }

    public static void renderAll(PoseStack ps, MultiBufferSource buffers, Vec3 camera) {
        if (!LINES.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(DebugRenderTypes.XRAY_LINES);

            for (DebugLinesClient.Entry entry : LINES.values()) {
                render(ps, vc, camera, entry);
            }
        }
    }

    private static void render(PoseStack ps, VertexConsumer vc, Vec3 camera, DebugLinesClient.Entry entry) {
        Vec3 along = entry.end.subtract(entry.start);
        double length = along.length();
        if (!(length < 1.0E-6)) {
            ps.pushPose();
            ps.translate(entry.start.x - camera.x, entry.start.y - camera.y, entry.start.z - camera.z);
            ps.mulPose(new Quaternionf().rotateTo(0.0F, 1.0F, 0.0F, (float)(along.x / length), (float)(along.y / length), (float)(along.z / length)));
            float h = entry.width * 0.5F;
            float len = (float)length;
            int r = entry.color >> 16 & 0xFF;
            int g = entry.color >> 8 & 0xFF;
            int b = entry.color & 0xFF;
            Matrix4f pose = ps.last().pose();
            quad(vc, pose, r, g, b, -h, 0.0F, h, h, 0.0F, h, h, len, h, -h, len, h);
            quad(vc, pose, r, g, b, h, 0.0F, -h, -h, 0.0F, -h, -h, len, -h, h, len, -h);
            quad(vc, pose, r, g, b, h, 0.0F, h, h, 0.0F, -h, h, len, -h, h, len, h);
            quad(vc, pose, r, g, b, -h, 0.0F, -h, -h, 0.0F, h, -h, len, h, -h, len, -h);
            quad(vc, pose, r, g, b, -h, len, h, h, len, h, h, len, -h, -h, len, -h);
            quad(vc, pose, r, g, b, -h, 0.0F, -h, h, 0.0F, -h, h, 0.0F, h, -h, 0.0F, h);
            ps.popPose();
        }
    }

    private static void quad(
        VertexConsumer vc,
        Matrix4f pose,
        int r,
        int g,
        int b,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        float x4,
        float y4,
        float z4
    ) {
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, 255);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, 255);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, 255);
        vc.addVertex(pose, x4, y4, z4).setColor(r, g, b, 255);
    }

    private static final class Entry {
        Vec3 start;
        Vec3 end;
        int color;
        float width;
        int ticksLeft;
    }
}
