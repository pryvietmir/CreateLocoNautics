package com.lycoris.loconautics.content.grab;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GrabHandRenderer {
    private static final float SHIFT_X = 0.05F;
    private static final float SHIFT_Y = 0.15F;
    private static final float MAX_TURN = (float)Math.toRadians(40.0);
    private static final float FADE_PER_TICK = 0.3F;
    private static final Vector3f TOP = new Vector3f(0.48525F, -0.83147F, -0.5254F);
    private static final Vector3f AXIS = new Vector3f(0.3336F, 0.46985F, -0.81729F);
    private static final float EQUIP_DROP = 0.6F;
    private static float blend;
    private static float prevBlend;
    @Nullable
    private static Vec3 lastAnchor;
    @Nullable
    private static Vec3 smoothed;
    private static final double FOLLOW_RATE = 0.6;

    private GrabHandRenderer() {
    }

    public static void tick() {
        prevBlend = blend;
        boolean holding = GrabClientHandler.grabAnchorWorld(1.0F) != null;
        blend = Mth.clamp(blend + (holding ? 0.3F : -0.3F), 0.0F, 1.0F);
        if (blend == 0.0F && prevBlend == 0.0F) {
            lastAnchor = null;
            smoothed = null;
        }
    }

    public static void apply(PoseStack ms, HumanoidArm side, float equippedProgress) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && side == player.getMainArm()) {
            float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
            float t = Mth.lerp(pt, prevBlend, blend);
            float k = t * t * (3.0F - 2.0F * t);
            if (!(k <= 0.0F)) {
                Vec3 anchor = GrabClientHandler.grabAnchorWorld(pt);
                if (anchor == null) {
                    anchor = lastAnchor;
                } else {
                    lastAnchor = anchor;
                }

                if (anchor != null) {
                    if (smoothed == null) {
                        smoothed = anchor;
                    } else {
                        double follow = 1.0 - Math.exp(-mc.getTimer().getRealtimeDeltaTicks() * 0.6);
                        smoothed = smoothed.lerp(anchor, follow);
                    }

                    anchor = smoothed;
                    Camera camera = mc.gameRenderer.getMainCamera();
                    Vec3 offset = anchor.subtract(camera.getPosition());
                    Vector3f target = new Vector3f((float)offset.x, (float)offset.y, (float)offset.z);
                    camera.rotation().transformInverse(target);
                    float s = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                    Vector3f top = new Vector3f(TOP.x * s, TOP.y - 0.6F * equippedProgress, TOP.z);
                    Vector3f axis = new Vector3f(AXIS.x * s, AXIS.y, AXIS.z);
                    Vector3f shoulder = new Vector3f(top).add(-s * 0.05F, 0.15F, 0.0F);
                    Vector3f aimed = target.sub(shoulder, new Vector3f());
                    if (!(aimed.lengthSquared() < 1.0E-6F)) {
                        aimed.normalize();
                        Quaternionf full = new Quaternionf().rotationTo(axis, aimed);
                        float angle = 2.0F * (float)Math.acos(Mth.clamp(full.w, -1.0F, 1.0F));
                        if (angle > Math.PI) {
                            angle = (float) (Math.PI * 2) - angle;
                        }

                        Quaternionf limited = angle > MAX_TURN ? new Quaternionf().slerp(full, MAX_TURN / angle) : full;
                        Quaternionf turn = new Quaternionf().slerp(limited, k);
                        Vector3f pos = top.lerp(shoulder, k, new Vector3f());
                        ms.translate(pos.x, pos.y, pos.z);
                        ms.mulPose(turn);
                        ms.translate(-top.x, -top.y, -top.z);
                    }
                }
            }
        }
    }
}
