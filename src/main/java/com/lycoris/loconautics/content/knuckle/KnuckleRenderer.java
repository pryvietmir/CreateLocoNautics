package com.lycoris.loconautics.content.knuckle;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.copycat.CopycatPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class KnuckleRenderer extends SmartBlockEntityRenderer<KnuckleBlockEntity> {
    private static final float PX = 0.0625F;
    private static final float ARM_HINGE_X = 0.5F;
    private static final float ARM_HINGE_Z = 0.8125F;
    private static final float STRETCH_ANCHOR_Z = 0.9375F;
    private static final float STRETCH_LENGTH = 0.1875F;
    private static final float HAND_PIVOT_Y = 0.8125F;
    private static final float COUPLING_PIVOT_X = 0.84375F;
    private static final float COUPLING_PIVOT_Y = 0.5625F;
    private static final float COUPLING_PIVOT_Z = 0.03125F;
    private static final float PIN_PIVOT_X = 0.5F;
    private static final float PIN_PIVOT_Y = 0.875F;
    private static final float PIN_PIVOT_Z = 0.3125F;
    private static final float[][] HAND_YAW = new float[][]{{0.0F, 0.0F}, {0.5F, -7.5F}, {1.0F, -10.0F}, {1.5833F, -13.75F}, {2.0F, 0.0F}};
    private static final float[][] COUPLING_YAW = new float[][]{{1.25F, -22.5F}, {1.8333F, -6.11F}, {2.0F, 0.0F}};
    private static final float[][] PIN_YAW = new float[][]{{1.3333F, -22.5F}, {1.5F, 0.0F}};
    private static final float[][] PIN_LIFT = new float[][]{{1.8333F, 1.0F}, {2.0F, 0.0F}};
    private static final float[][] DRAW_IN = new float[][]{{0.0F, 16.0F}, {0.5F, 11.0F}, {1.0F, 8.0F}, {1.25F, 5.0F}, {1.5F, 4.0F}, {2.0F, 0.0F}};
    private static final float ARM_REACH = 0.8125F;
    private final WallClipVertexConsumer ownWall = new WallClipVertexConsumer();
    private final WallClipVertexConsumer partnerWall = new WallClipVertexConsumer();

    public KnuckleRenderer(Context context) {
        super(context);
    }

    protected void renderSafe(KnuckleBlockEntity be, float partialTicks, PoseStack ps, MultiBufferSource bufferSource, int light, int overlay) {
        Direction myFacing = (Direction)be.getBlockState().getValue(KnuckleBlock.FACING);
        BlockPos myPos = be.getBlockPos();
        BlockState state = be.getBlockState();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.cutoutMipped());
        Matrix4f base = new Matrix4f(ps.last().pose());
        ps.pushPose();
        ps.translate(0.0F, CopycatPlacement.CENTER.blocks(), 0.0F);
        Vector3d a = KnuckleBlockEntity.hingeLocal(myPos, myFacing);
        double restX = a.x - myPos.getX();
        double restZ = a.z - myPos.getZ();
        float restYaw = facingYaw(myFacing);
        this.ownWall.begin(buffer, base, (float)restX, 0.5F, (float)restZ, -myFacing.getStepX(), 0.0F, -myFacing.getStepZ());
        KnuckleRenderer.Mate mate = !be.isConnected() && !be.isReleasing() ? null : partnerMate(be);
        Vector3d b = mate == null ? null : mate.hinge();
        boolean live = b != null && be.isConnected();
        double dx = b == null ? 0.0 : b.x - a.x;
        double dz = b == null ? 0.0 : b.z - a.z;
        double dist = Math.hypot(dx, dz);
        float clip = be.animationClip(dist, partialTicks);
        float handClip = b == null ? 0.0F : clip;
        if (b == null || clip <= 0.0F) {
            renderArm(ps, this.ownWall, state, light, clip, 0.0F, restX, restZ, restYaw);
            ps.popPose();
        } else if (dist < 1.0E-6) {
            ps.popPose();
        } else {
            float blend = clip / 2.0F;
            float aim = (float)Math.toDegrees(Math.atan2(dz, dx));
            float ownYaw = restYaw + Mth.degreesDifference(restYaw, aim) * blend;
            float drawHalf = sample(DRAW_IN, clip) * 0.0625F * 0.5F;
            float shank = Math.max(0.0F, (float)(dist * 0.5) - 0.8125F - drawHalf) * blend;
            ps.pushPose();
            ps.translate(restX, 0.0, restZ);
            ps.mulPose(Axis.YP.rotationDegrees(modelYaw(ownYaw)));
            ps.translate(-0.5F, 0.0F, -0.8125F);
            ps.translate(0.0F, 0.0F, 0.9375F);
            ps.scale(1.0F, 1.0F, shank / 0.1875F);
            ps.translate(0.0F, 0.0F, -0.9375F);
            CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_STRETCH_ARM, state).light(light).renderInto(ps, this.ownWall);
            ps.popPose();
            if (live && !mate.controller()) {
                ps.popPose();
            } else {
                double ux = dx / dist;
                double uz = dz / dist;
                double cx = (a.x + b.x) * 0.5 - myPos.getX();
                double cz = (a.z + b.z) * 0.5 - myPos.getZ();
                double reach = 0.8125F + drawHalf;
                renderArm(
                    ps, this.ownWall, state, light, clip, handClip, Mth.lerp(blend, restX, cx - ux * reach), Mth.lerp(blend, restZ, cz - uz * reach), ownYaw
                );
                if (live) {
                    this.partnerWall
                        .begin(
                            buffer,
                            base,
                            (float)(b.x - myPos.getX()),
                            0.5F,
                            (float)(b.z - myPos.getZ()),
                            (float)mate.wallOutward().x,
                            0.0F,
                            (float)mate.wallOutward().z
                        );
                    renderArm(
                        ps,
                        this.partnerWall,
                        state,
                        light,
                        clip,
                        handClip,
                        Mth.lerp(blend, b.x - myPos.getX(), cx + ux * reach),
                        Mth.lerp(blend, b.z - myPos.getZ(), cz + uz * reach),
                        ownYaw + 180.0F
                    );
                }

                ps.popPose();
            }
        }
    }

    private static void renderArm(PoseStack ps, VertexConsumer buffer, BlockState state, int light, float clip, float handClip, double x, double z, float yaw) {
        ps.pushPose();
        ps.translate(x, 0.0, z);
        ps.mulPose(Axis.YP.rotationDegrees(modelYaw(yaw)));
        ps.translate(-0.5F, 0.0F, -0.8125F);
        handBone(ps, handClip);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_HAND, state).light(light).renderInto(ps, buffer);
        ps.pushPose();
        ps.translate(0.84375F, 0.5625F, 0.03125F);
        ps.mulPose(Axis.YP.rotationDegrees(sample(COUPLING_YAW, clip)));
        ps.translate(-0.84375F, -0.5625F, -0.03125F);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_COUPLING, state).light(light).renderInto(ps, buffer);
        ps.popPose();
        ps.translate(0.5F, 0.875F + sample(PIN_LIFT, clip) * 0.0625F, 0.3125F);
        ps.mulPose(Axis.YP.rotationDegrees(sample(PIN_YAW, clip)));
        ps.translate(-0.5F, -0.875F, -0.3125F);
        CachedBuffers.partial(LoconauticsPartialModels.KNUCKLE_PIN, state).light(light).renderInto(ps, buffer);
        ps.popPose();
    }

    private static void handBone(PoseStack ps, float clip) {
        ps.translate(0.5F, 0.8125F, 0.8125F);
        ps.mulPose(Axis.YP.rotationDegrees(sample(HAND_YAW, clip)));
        ps.translate(-0.5F, -0.8125F, -0.8125F);
    }

    private static float sample(float[][] keys, float seconds) {
        if (seconds <= keys[0][0]) {
            return keys[0][1];
        }

        for (int i = 1; i < keys.length; i++) {
            if (!(seconds > keys[i][0])) {
                float span = keys[i][0] - keys[i - 1][0];
                float t = span <= 0.0F ? 1.0F : (seconds - keys[i - 1][0]) / span;
                return Mth.lerp(t, keys[i - 1][1], keys[i][1]);
            }
        }

        return keys[keys.length - 1][1];
    }

    private static float modelYaw(float aimDegrees) {
        return -(aimDegrees + 90.0F);
    }

    private static float facingYaw(Direction facing) {
        return (float)Math.toDegrees(Math.atan2(facing.getStepZ(), facing.getStepX()));
    }

    private static KnuckleRenderer.Mate partnerMate(KnuckleBlockEntity be) {
        BlockPos partnerPos = be.getPartnerLocalPos();
        ClientLevel clientLevel = Minecraft.getInstance().level;
        if (clientLevel != null && partnerPos != null) {
            ClientSubLevelContainer container = SubLevelContainer.getContainer(clientLevel);
            if (container != null && container.getSubLevel(be.getPartnerSubLevelId()) instanceof ClientSubLevel partnerSub) {
                ClientSubLevel ownSub = Sable.HELPER.getContainingClient(be);
                Pose3dc ownPose = ownSub != null ? ownSub.renderPose() : null;
                if (ownPose != null && partnerSub.getLevel().getBlockEntity(partnerPos) instanceof KnuckleBlockEntity partnerBe) {
                    Pose3dc var13 = partnerSub.renderPose();
                    Direction partnerFacing = (Direction)partnerBe.getBlockState().getValue(KnuckleBlock.FACING);
                    Vector3d hinge = KnuckleBlockEntity.hingeLocal(partnerPos, partnerFacing);
                    var13.transformPosition(hinge);
                    ownPose.transformPositionInverse(hinge);
                    Vector3d outward = new Vector3d(-partnerFacing.getStepX(), 0.0, -partnerFacing.getStepZ());
                    var13.transformNormal(outward);
                    ownPose.transformNormalInverse(outward);
                    return new KnuckleRenderer.Mate(hinge, outward, be.isController(ownSub.getUniqueId()));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private record Mate(Vector3d hinge, Vector3d wallOutward, boolean controller) {
    }
}
