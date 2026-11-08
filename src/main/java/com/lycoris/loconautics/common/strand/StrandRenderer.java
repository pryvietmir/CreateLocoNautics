package com.lycoris.loconautics.common.strand;

import com.lycoris.loconautics.trainphysics.portal.PortalCutsClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class StrandRenderer {
    private static final Vector3d UP = new Vector3d(0.0, 1.0, 0.0);
    private static final Vector3d DOWN = new Vector3d(0.0, -1.0, 0.0);
    public static final float OUTLINE_RADIUS = 0.1875F;

    private StrandRenderer() {
    }

    public static void renderAll(
        PoseStack ps,
        MultiBufferSource buffer,
        Vec3 camera,
        float partialTick,
        ClientStrands strands,
        PartialModel middleModel,
        PartialModel knotModel,
        double groundOffset,
        @Nullable UUID outlined
    ) {
        renderAll(ps, buffer, camera, partialTick, strands, middleModel, knotModel, groundOffset, outlined, null, null);
    }

    public static void renderAll(
        PoseStack ps,
        MultiBufferSource buffer,
        Vec3 camera,
        float partialTick,
        ClientStrands strands,
        PartialModel middleModel,
        PartialModel knotModel,
        double groundOffset,
        @Nullable UUID outlined,
        @Nullable Predicate<Vector3d> endKnotAt,
        @Nullable StrandRenderer.SegmentSwell swell
    ) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level != null) {
            SuperByteBuffer middle = CachedBuffers.partialFacing(middleModel, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
            SuperByteBuffer knot = CachedBuffers.partialFacing(knotModel, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);

            for (Entry<UUID, ClientStrands.Strand> entry : strands.entries()) {
                if (!endGone(level, entry.getValue().start()) && !endGone(level, entry.getValue().end())) {
                    List<StrandRenderer.RenderPoint> render = buildRenderPoints(level, partialTick, entry.getValue(), groundOffset);
                    if (render.size() >= 2) {
                        VertexConsumer vb = buffer.getBuffer(RenderType.solid());
                        double total = 0.0;
                        if (swell != null) {
                            for (int i = 1; i < render.size(); i++) {
                                total += render.get(i).position().distance(render.get(i - 1).position());
                            }
                        }

                        double cum = 0.0;

                        for (int i = 1; i < render.size(); i++) {
                            StrandRenderer.RenderPoint from = render.get(i - 1);
                            double length = render.get(i).position().distance(from.position());
                            float girth = swell == null ? 1.0F : swell.at(entry.getKey(), cum, total, partialTick);
                            Vector3d to = render.get(i).position();
                            Vector3d mid = new Vector3d((from.position().x + to.x) * 0.5, (from.position().y + to.y) * 0.5, (from.position().z + to.z) * 0.5);
                            if (!cutAt(entry.getValue(), mid)) {
                                renderSegment(level, ps, vb, middle, knot, from, length, camera, i > 1, girth);
                            }

                            cum += length;
                        }

                        if (endKnotAt != null) {
                            StrandRenderer.RenderPoint first = render.get(0);
                            StrandRenderer.RenderPoint last = render.get(render.size() - 1);
                            if (endKnotAt.test(first.position()) && !cutAt(entry.getValue(), first.position())) {
                                renderKnot(level, ps, vb, knot, first, camera);
                            }

                            if (endKnotAt.test(last.position()) && !cutAt(entry.getValue(), last.position())) {
                                renderKnot(level, ps, vb, knot, last, camera);
                            }
                        }

                        if (entry.getKey().equals(outlined)) {
                            renderOutline(ps, buffer, render, camera);
                        }
                    }
                }
            }
        }
    }

    private static boolean endGone(Level level, @Nullable StrandEnd end) {
        if (end != null && end.subLevelId() != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            return container == null || !(container.getSubLevel(end.subLevelId()) instanceof ClientSubLevel);
        } else {
            return false;
        }
    }

    private static boolean cutAt(ClientStrands.Strand strand, Vector3d p) {
        StrandEnd start = strand.start();
        StrandEnd end = strand.end();
        return start != null && PortalCutsClient.hiddenForBody(start.subLevelId(), p.x, p.y, p.z)
            || end != null && PortalCutsClient.hiddenForBody(end.subLevelId(), p.x, p.y, p.z);
    }

    private static void renderKnot(Level level, PoseStack ps, VertexConsumer vb, SuperByteBuffer knot, StrandRenderer.RenderPoint at, Vec3 camera) {
        Vector3d p = at.position();
        ps.pushPose();
        ps.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
        ps.mulPose(at.orientation());
        ps.translate(-0.5, -0.5, -0.5);
        knot.light(LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y, p.z))).renderInto(ps, vb);
        ps.popPose();
    }

    private static void renderSegment(
        Level level,
        PoseStack ps,
        VertexConsumer vb,
        SuperByteBuffer middle,
        SuperByteBuffer knot,
        StrandRenderer.RenderPoint from,
        double length,
        Vec3 camera,
        boolean drawKnot,
        float girth
    ) {
        if (!(length < 1.0E-6)) {
            Vector3d p = from.position();
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(p.x, p.y, p.z));
            ps.pushPose();
            ps.translate(p.x - camera.x, p.y - camera.y, p.z - camera.z);
            ps.mulPose(from.orientation());
            ps.translate(-0.5, -0.5, -0.5);
            if (drawKnot) {
                knot.light(light).renderInto(ps, vb);
            }

            ps.translate(0.5, 0.5, 0.5);
            ps.scale(girth, (float)length, girth);
            ps.translate(-0.5, 0.0, -0.5);
            middle.light(light).renderInto(ps, vb);
            ps.popPose();
        }
    }

    private static List<StrandRenderer.RenderPoint> buildRenderPoints(Level level, float partialTick, ClientStrands.Strand strand, double groundOffset) {
        List<ClientStrands.Point> input = strand.points();
        List<StrandRenderer.RenderPoint> out = new ArrayList<>();
        if (input.size() < 2) {
            return out;
        }

        Vector3d first = liveEnd(level, strand.start());
        if (first == null) {
            first = input.get(0).renderPos(partialTick, new Vector3d());
        }

        Vector3d second = input.get(1).renderPos(partialTick, new Vector3d());
        Vector3d normal = second.sub(first, new Vector3d());
        double firstLength = normal.length();
        if (firstLength < 1.0E-6) {
            return out;
        }

        normal.normalize();
        double trim = Math.min(startTrim(level, strand.start()), firstLength * 0.9);
        if (trim > 0.0) {
            first.fma(trim, normal);
        }

        Quaternionf running = orient(normal);
        out.add(new StrandRenderer.RenderPoint(new Quaternionf(running), lowered(first, groundOffset)));
        Vector3d a = new Vector3d();
        Vector3d b = new Vector3d();

        for (int i = 2; i < input.size(); i++) {
            input.get(i - 1).renderPos(partialTick, a);
            input.get(i).renderPos(partialTick, b);
            Vector3d step = b.sub(a, new Vector3d());
            if (step.length() > 1.0E-6) {
                running = orient(step.normalize());
            }

            out.add(new StrandRenderer.RenderPoint(new Quaternionf(running), new Vector3d(a)));
        }

        Vector3d last = liveEnd(level, strand.end());
        if (last == null) {
            last = input.get(input.size() - 1).renderPos(partialTick, new Vector3d());
        }

        out.add(new StrandRenderer.RenderPoint(new Quaternionf(running), lowered(last, groundOffset)));
        return out;
    }

    private static double startTrim(Level level, @Nullable StrandEnd end) {
        if (end == null) {
            return 0.0;
        }

        Level holderLevel = level;
        if (end.subLevelId() != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null || !(container.getSubLevel(end.subLevelId()) instanceof ClientSubLevel sub)) {
                return 0.0;
            }

            holderLevel = sub.getLevel();
        }

        return holderLevel.getBlockEntity(end.local()) instanceof StrandHolder holder ? holder.strandStartTrim() : 0.0;
    }

    @Nullable
    private static Vector3d liveEnd(Level level, @Nullable StrandEnd end) {
        if (end != null && end.subLevelId() != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(end.subLevelId()) instanceof ClientSubLevel sub) {
                Vec3 anchor = sub.getLevel().getBlockEntity(end.local()) instanceof StrandHolder holder
                    ? holder.getAttachmentPoint()
                    : Vec3.atCenterOf(end.local());
                return sub.renderPose().transformPosition(new Vector3d(anchor.x, anchor.y, anchor.z), new Vector3d());
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Vector3d lowered(Vector3d v, double groundOffset) {
        return new Vector3d(v.x, v.y - groundOffset, v.z);
    }

    public static Quaternionf rotationTo(Vector3dc start, Vector3dc end) {
        Vector3d cross = new Vector3d();
        start.cross(end, cross);
        Quaternionf q = new Quaternionf((float)cross.x(), (float)cross.y(), (float)cross.z(), 1.0F + (float)start.dot(end));
        return q.normalize();
    }

    private static Quaternionf orient(Vector3d dir) {
        return dir.dot(UP) < -0.15 ? rotationTo(DOWN, dir).rotateZ((float) Math.PI) : rotationTo(UP, dir);
    }

    private static void renderOutline(PoseStack ps, MultiBufferSource buffer, List<StrandRenderer.RenderPoint> render, Vec3 camera) {
        float rad = 0.1875F;
        Vector3d[] corners = new Vector3d[]{
            new Vector3d(-rad, 0.0, -rad), new Vector3d(-rad, 0.0, rad), new Vector3d(rad, 0.0, rad), new Vector3d(rad, 0.0, -rad)
        };
        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        Matrix4f pose = ps.last().pose();
        Vector3d previous = new Vector3d();
        Vector3d current = new Vector3d();
        Vector3d diff = new Vector3d();

        for (int i = 0; i < render.size() + 1; i++) {
            StrandRenderer.RenderPoint p0 = render.get(Math.max(0, i - 1));
            StrandRenderer.RenderPoint p1 = render.get(Math.min(render.size() - 1, i));
            boolean start = i == 0;
            boolean end = i == render.size();

            for (Vector3d corner : corners) {
                p0.orientation()
                    .transform(start ? corner.rotateY(Math.PI / 2, previous) : corner, previous)
                    .add(p0.position())
                    .sub(camera.x, camera.y, camera.z);
                p1.orientation().transform(end ? corner.rotateY(Math.PI / 2, corner) : corner, current).add(p1.position()).sub(camera.x, camera.y, camera.z);
                current.sub(previous, diff).normalize();
                lines.addVertex(pose, (float)previous.x, (float)previous.y, (float)previous.z)
                    .setColor(0.0F, 0.0F, 0.0F, 0.4F)
                    .setNormal(ps.last(), (float)diff.x, (float)diff.y, (float)diff.z);
                lines.addVertex(pose, (float)current.x, (float)current.y, (float)current.z)
                    .setColor(0.0F, 0.0F, 0.0F, 0.4F)
                    .setNormal(ps.last(), (float)diff.x, (float)diff.y, (float)diff.z);
            }
        }
    }

    private record RenderPoint(Quaternionf orientation, Vector3d position) {
    }

    @FunctionalInterface
    public interface SegmentSwell {
        float at(UUID var1, double var2, double var4, float var6);
    }
}
