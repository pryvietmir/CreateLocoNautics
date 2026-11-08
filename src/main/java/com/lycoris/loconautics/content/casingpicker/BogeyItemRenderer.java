package com.lycoris.loconautics.content.casingpicker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import java.util.HashMap;
import java.util.Map;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BogeyItemRenderer extends CustomRenderedItemModelRenderer {
    private static final float MIN_GUI_SCALE = 0.35F;
    private static final float HELD_SCALE = 0.4F;
    private static final Quaternionf GUI_ROTATION = new Quaternionf().rotateX((float)Math.toRadians(30.0)).rotateY((float)Math.toRadians(225.0));
    private static final Map<BogeyChoice, AABB> BOUNDS = new HashMap<>();

    protected void render(
        ItemStack stack,
        CustomRenderedItemModel model,
        PartialItemModelRenderer renderer,
        ItemDisplayContext transformType,
        PoseStack ms,
        MultiBufferSource buffer,
        int light,
        int overlay
    ) {
        BogeyChoice choice = BogeyItem.choiceOf(stack);
        AABB bounds = BOUNDS.computeIfAbsent(choice, BogeyItemRenderer::measure);
        float scale = transformType == ItemDisplayContext.GUI ? Math.max(0.35F, guiFit(bounds)) : 0.4F;
        Vec3 center = bounds.getCenter();
        ms.pushPose();
        ms.scale(scale, scale, scale);
        ms.translate(-center.x, -center.y, -center.z);
        draw(choice, ms, buffer, light, overlay);
        ms.popPose();
    }

    private static void draw(BogeyChoice choice, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = (BlockState)choice.block().defaultBlockState().setValue(AbstractBogeyBlock.AXIS, Axis.Z);
        ms.pushPose();
        ms.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ms, buffer, light, overlay);
        ms.popPose();
        choice.style().render(choice.size(), AnimationTickHolder.getPartialTicks(), ms, buffer, light, overlay, 0.0F, null, false);
    }

    private static float guiFit(AABB bounds) {
        Vec3 center = bounds.getCenter();
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int corner = 0; corner < 8; corner++) {
            Vector3f v = new Vector3f(
                (float)(((corner & 1) == 0 ? bounds.minX : bounds.maxX) - center.x),
                (float)(((corner & 2) == 0 ? bounds.minY : bounds.maxY) - center.y),
                (float)(((corner & 4) == 0 ? bounds.minZ : bounds.maxZ) - center.z)
            );
            GUI_ROTATION.transform(v);
            minX = Math.min(minX, v.x);
            maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y);
            maxY = Math.max(maxY, v.y);
        }

        float extent = Math.max(maxX - minX, maxY - minY);
        return extent > 0.0F ? 1.0F / extent : 1.0F;
    }

    private static AABB measure(BogeyChoice choice) {
        BogeyItemRenderer.BoundsCollector collector = new BogeyItemRenderer.BoundsCollector();
        draw(choice, new PoseStack(), type -> collector, 15728880, OverlayTexture.NO_OVERLAY);
        return collector.bounds();
    }

    private static final class BoundsCollector implements VertexConsumer {
        private double minX = Double.MAX_VALUE;
        private double minY = Double.MAX_VALUE;
        private double minZ = Double.MAX_VALUE;
        private double maxX = -Double.MAX_VALUE;
        private double maxY = -Double.MAX_VALUE;
        private double maxZ = -Double.MAX_VALUE;

        AABB bounds() {
            return this.minX > this.maxX
                ? new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5)
                : new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }

        public VertexConsumer addVertex(float x, float y, float z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            this.maxZ = Math.max(this.maxZ, z);
            return this;
        }

        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
