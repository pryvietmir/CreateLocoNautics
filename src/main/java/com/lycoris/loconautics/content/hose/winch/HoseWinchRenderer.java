package com.lycoris.loconautics.content.hose.winch;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.RopeRendering;
import com.lycoris.loconautics.common.strand.StrandAnchors;
import com.lycoris.loconautics.content.hose.HoseItem;
import com.lycoris.loconautics.content.hose.HoseSwell;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public class HoseWinchRenderer implements BlockEntityRenderer<HoseWinchBlockEntity> {
    public HoseWinchRenderer(Context context) {
    }

    public void render(HoseWinchBlockEntity be, float partialTicks, PoseStack ps, MultiBufferSource buffer, int light, int overlay) {
        Level level = be.getLevel();
        if (level != null) {
            this.renderKinetics(be, ps, buffer, light, partialTicks);
            if (be.clientHasStrand()) {
                this.renderStrand(be, level, ps, buffer, partialTicks);
            }
        }
    }

    private void renderKinetics(HoseWinchBlockEntity be, PoseStack ps, MultiBufferSource buffer, int light, float partialTicks) {
        BlockState state = be.getBlockState();
        if (state.getBlock() instanceof IRotate rotate) {
            Axis var11 = rotate.getRotationAxis(state);
            float angle = be.coilAngleRadians(partialTicks);
            KineticBlockEntityRenderer.renderRotatingKineticBlock(be, KineticBlockEntityRenderer.shaft(var11), ps, buffer.getBuffer(RenderType.solid()), light);
            if (be.clientHasStrand()) {
                SuperByteBuffer coil = CachedBuffers.partial(LoconauticsPartialModels.HOSE_WINCH_COIL, state);
                spin(coil, state, var11, angle).light(light).renderInto(ps, buffer.getBuffer(RenderType.solid()));
            }
        }
    }

    private static SuperByteBuffer spin(SuperByteBuffer buffer, BlockState state, Axis axis, float angle) {
        buffer.rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, axis));
        return placeAsBlock(buffer, state);
    }

    private static SuperByteBuffer placeAsBlock(SuperByteBuffer buffer, BlockState state) {
        Direction facing = (Direction)state.getValue(DirectionalKineticBlock.FACING);
        boolean along = (Boolean)state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
        int xRot = facing.getAxis().isHorizontal() ? 270 : (facing == Direction.UP ? 180 : 0);

        int yRot = switch (facing) {
            case NORTH -> 0;
            case SOUTH -> 180;
            case EAST -> 90;
            case WEST -> 270;
            default -> along ? 0 : 90;
        };
        buffer.rotateCentered(-yRot * (float) (Math.PI / 180.0), Direction.UP);
        buffer.rotateCentered(-xRot * (float) (Math.PI / 180.0), Direction.EAST);
        return buffer;
    }

    private void renderStrand(HoseWinchBlockEntity be, Level level, PoseStack ps, MultiBufferSource buffer, float partialTicks) {
        ClientSubLevel ownerSub = Sable.HELPER.getContainingClient(be);
        Pose3dc ownerPose = ownerSub == null ? null : ownerSub.renderPose();
        List<Vec3> points = be.clientRopePoints(partialTicks);
        if (points == null) {
            Vec3 nozzlePoint = resolveNozzlePoint(be, level);
            if (nozzlePoint == null) {
                return;
            }

            Vec3 winchPoint = be.getAttachmentPoint();
            if (ownerPose != null) {
                winchPoint = transform(ownerPose, winchPoint, false);
            }

            points = buildPolyline(winchPoint, nozzlePoint, be.clientLength());
            if (ownerPose != null) {
                points = toLocal(ownerPose, points);
            }
        }

        if (points.size() >= 2) {
            Vec3 liveNozzle = resolveNozzlePoint(be, level);
            if (liveNozzle != null) {
                points = new ArrayList<>(points);
                points.set(points.size() - 1, ownerPose == null ? liveNozzle : transform(ownerPose, liveNozzle, true));
            }

            List<Vec3> worldPoints = points;
            if (ownerPose != null) {
                worldPoints = new ArrayList<>(points.size());

                for (Vec3 p : points) {
                    worldPoints.add(transform(ownerPose, p, false));
                }
            }

            BlockPos ownerPos = be.getBlockPos();
            VertexConsumer vb = buffer.getBuffer(RenderType.solid());
            SuperByteBuffer knot = CachedBuffers.partialFacing(LoconauticsPartialModels.HOSE_KNOT, be.getBlockState(), Direction.NORTH);
            Quaternionf orient = new Quaternionf();
            double cumDist = 0.0;

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3 p0 = points.get(i);
                Vec3 p1 = points.get(i + 1);
                double length = RopeRendering.orientSegment(orient, p0, p1);
                if (!(length < 1.0E-5)) {
                    if (i > 0) {
                        renderKnot(ps, vb, ownerPos, p0, orient, worldLight(level, worldPoints.get(i)), knot);
                    }

                    SuperByteBuffer middle = CachedBuffers.partialFacing(LoconauticsPartialModels.HOSE_STRAND, be.getBlockState(), Direction.NORTH);
                    float swell = hoseSwell(be, cumDist, partialTicks);
                    ps.pushPose();
                    ps.translate(p0.x - ownerPos.getX(), p0.y - ownerPos.getY(), p0.z - ownerPos.getZ());
                    ps.mulPose(orient);
                    ps.scale(swell, (float)length, swell);
                    ps.translate(-0.5, 0.0, -0.5);
                    middle.light(worldLight(level, worldPoints.get(i))).renderInto(ps, buffer.getBuffer(RenderType.solid()));
                    ps.popPose();
                    cumDist += length;
                }
            }

            renderKnot(ps, vb, ownerPos, points.getLast(), orient, worldLight(level, worldPoints.getLast()), knot);
            if (holdingHoseItem()) {
                RopeRendering.renderHitbox(ps, buffer, points, ownerPos, 0.125F);
            }
        }
    }

    private static Vec3 transform(Pose3dc pose, Vec3 p, boolean toLocal) {
        Vector3d in = new Vector3d(p.x, p.y, p.z);
        Vector3d out = toLocal ? pose.transformPositionInverse(in, new Vector3d()) : pose.transformPosition(in, new Vector3d());
        return new Vec3(out.x, out.y, out.z);
    }

    private static List<Vec3> toLocal(Pose3dc pose, List<Vec3> world) {
        List<Vec3> local = new ArrayList<>(world.size());

        for (Vec3 p : world) {
            local.add(transform(pose, p, true));
        }

        return local;
    }

    private static boolean holdingHoseItem() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? false : player.getMainHandItem().getItem() instanceof HoseItem || player.getOffhandItem().getItem() instanceof HoseItem;
    }

    private static List<Vec3> buildPolyline(Vec3 winch, Vec3 nozzle, float paidOut) {
        double straight = winch.distanceTo(nozzle);
        if (straight < 1.0E-4) {
            return List.of(winch, nozzle);
        }

        Vec3 dir = nozzle.subtract(winch).scale(1.0 / straight);
        int segments = Math.max(1, (int)Math.ceil(straight - 1.0E-6));
        double firstLen = straight - (segments - 1);
        List<Vec3> points = new ArrayList<>(segments + 1);
        points.add(winch);
        double cum = firstLen;
        points.add(winch.add(dir.scale(cum)));

        for (int i = 1; i < segments; i++) {
            points.add(winch.add(dir.scale(++cum)));
        }

        return points;
    }

    private static void renderKnot(PoseStack ps, VertexConsumer vb, BlockPos ownerPos, Vec3 point, Quaternionf orient, int light, SuperByteBuffer knot) {
        ps.pushPose();
        ps.translate(point.x - ownerPos.getX(), point.y - ownerPos.getY(), point.z - ownerPos.getZ());
        ps.mulPose(orient);
        ps.translate(-0.5, -0.5, -0.5);
        knot.light(light).renderInto(ps, vb);
        ps.popPose();
    }

    private static Vec3 resolveNozzlePoint(HoseWinchBlockEntity be, Level level) {
        UUID sub = be.clientNozzleSub();
        BlockPos local = be.clientNozzleLocal();
        if (sub != null && local != null) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container != null && container.getSubLevel(sub) instanceof ClientSubLevel clientSub) {
                Pose3dc var9 = clientSub.renderPose();
                Vec3 anchor = StrandAnchors.hoseNozzle(local);
                Vector3d world = var9.transformPosition(new Vector3d(anchor.x, anchor.y, anchor.z), new Vector3d());
                return new Vec3(world.x, world.y, world.z);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static float hoseSwell(HoseWinchBlockEntity be, double cumDist, float partialTicks) {
        if (!be.clientHasFluid()) {
            return 1.0F;
        } else {
            boolean reachesNozzle = be.clientReach() >= be.clientLength() - 0.001F;
            if (reachesNozzle) {
                double phase = be.wavePhase(partialTicks) - cumDist * 0.25 * Math.PI * 2.0;
                return HoseSwell.scale(HoseSwell.wavePx(phase));
            } else {
                return HoseSwell.scale(HoseSwell.frontPx(be.clientFill(partialTicks), cumDist));
            }
        }
    }

    private static int worldLight(Level level, Vec3 point) {
        return LevelRenderer.getLightColor(level, BlockPos.containing(point.x, point.y, point.z));
    }

    public boolean shouldRenderOffScreen(HoseWinchBlockEntity be) {
        return true;
    }

    public AABB getRenderBoundingBox(HoseWinchBlockEntity be) {
        AABB box = be.getRenderBoundingBox();
        Level level = be.getLevel();
        if (level == null) {
            return box;
        }

        Vec3 nozzle = resolveNozzlePoint(be, level);
        return nozzle == null ? box : box.minmax(new AABB(nozzle, nozzle).inflate(1.0));
    }

    public boolean shouldRender(HoseWinchBlockEntity be, Vec3 cameraPos) {
        return true;
    }

    public int getViewDistance() {
        return 128;
    }
}
