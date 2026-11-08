package com.lycoris.loconautics.content.relocator;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import java.util.Map;
import java.util.WeakHashMap;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class CreativeTrainRelocatorItemRenderer extends CustomRenderedItemModelRenderer {
    private static final float OPEN_LEN = 0.5F;
    private static final float CLOSE_LEN = 0.5F;
    private static final float LOOP_LEN = 1.0F;
    private static final float SPIN_LEN = 1.0F;
    private static final float TAU = (float) (Math.PI * 2);
    private static final int SHUT = 0;
    private static final int OPENING = 1;
    private static final int IDLE = 2;
    private static final int CLOSING = 3;
    private static final float JAW_PIVOT_X = 0.0F;
    private static final float JAW_PIVOT_Y = 7.0F;
    private static final float JAW_PIVOT_Z = -0.2F;
    private static final float COOB_PIVOT_X = 0.0F;
    private static final float COOB_PIVOT_Y = 1.0F;
    private static final float COOB_PIVOT_Z = -0.9F;
    private static final Map<ItemStack, CreativeTrainRelocatorItemRenderer.AnimState> STATES = new WeakHashMap<>();

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
        CreativeTrainRelocatorItemRenderer.AnimState state = STATES.computeIfAbsent(stack, s -> new CreativeTrainRelocatorItemRenderer.AnimState());
        float now = AnimationTickHolder.getRenderTime() / 20.0F;
        boolean heldTrain = SableTrainRelocator.isRelocating() || RelocatorItemData.read(stack) != null;
        if (heldTrain != state.lastHeldTrain) {
            state.lastHeldTrain = heldTrain;
            state.transitionAt = now;
        }

        this.resolvePhase(state, heldTrain, now - state.transitionAt);
        state.spin = (now % 1.0F + 1.0F) % 1.0F / 1.0F;
        renderer.render(model.getOriginalModel(), light);
        this.renderJawGroup(ms, renderer, light, state, LoconauticsPartialModels.RELOCATOR_DRAWHANDLE.get());
        this.renderJawGroup(ms, renderer, light, state, LoconauticsPartialModels.RELOCATOR_LOWER_JAW.get());
        this.renderGreaterJaw(ms, renderer, light, state);
        this.renderOmnicoob(ms, renderer, light, state);
    }

    private void resolvePhase(CreativeTrainRelocatorItemRenderer.AnimState state, boolean heldTrain, float elapsed) {
        if (heldTrain) {
            if (elapsed < 0.5F) {
                state.phase = 1;
                state.phaseTime = elapsed;
            } else {
                state.phase = 2;
                state.phaseTime = (elapsed - 0.5F) % 1.0F;
            }
        } else {
            if (elapsed < 0.5F) {
                state.phase = 3;
                state.phaseTime = elapsed;
            } else {
                state.phase = 0;
                state.phaseTime = 0.0F;
            }
        }
    }

    private float jawRotX(CreativeTrainRelocatorItemRenderer.AnimState state) {
        return switch (state.phase) {
            case 1 -> state.phaseTime < 0.25F ? ease(state.phaseTime, 0.0F, -25.5F, 0.0F, 0.25F) : ease(state.phaseTime, -25.5F, -22.5F, 0.25F, 0.5F);
            case 2 -> -21.25F - 1.25F * Mth.cos((float) (Math.PI * 2) * state.phaseTime / 1.0F);
            case 3 -> state.phaseTime < 0.25F ? -22.5F : easeIn(state.phaseTime, -22.5F, 0.0F, 0.25F, 0.5F);
            default -> 0.0F;
        };
    }

    private float greaterJawY(CreativeTrainRelocatorItemRenderer.AnimState state) {
        return switch (state.phase) {
            case 1 -> state.phaseTime < 0.25F ? ease(state.phaseTime, 0.0F, 3.4F, 0.0F, 0.25F) : ease(state.phaseTime, 3.4F, 3.0F, 0.25F, 0.5F);
            case 2 -> 3.25F - 0.25F * Mth.cos((float) (Math.PI * 2) * state.phaseTime / 1.0F);
            case 3 -> state.phaseTime < 0.25F ? 3.0F : easeIn(state.phaseTime, 3.0F, 0.0F, 0.25F, 0.5F);
            default -> 0.0F;
        };
    }

    private float omnicoobY(CreativeTrainRelocatorItemRenderer.AnimState state) {
        return switch (state.phase) {
            case 1 -> state.phaseTime < 0.25F ? ease(state.phaseTime, 7.0F, 8.75F, 0.0F, 0.25F) : ease(state.phaseTime, 8.75F, 8.5F, 0.25F, 0.4F);
            case 2 -> 8.6F - 0.1F * Mth.cos((float) (Math.PI * 2) * state.phaseTime / 1.0F);
            case 3 -> state.phaseTime < 0.2083F ? 8.5F : easeIn(state.phaseTime, 8.5F, 7.0F, 0.2083F, 0.5F);
            default -> 7.0F;
        };
    }

    private float omnicoobScale(CreativeTrainRelocatorItemRenderer.AnimState state) {
        return switch (state.phase) {
            case 1 -> state.phaseTime < 0.2F ? ease(state.phaseTime, 0.0F, 1.15F, 0.0F, 0.2F) : ease(state.phaseTime, 1.15F, 1.0F, 0.2F, 0.3833F);
            case 2 -> 1.0F;
            case 3 -> state.phaseTime < 0.0833F ? 1.0F : easeIn(state.phaseTime, 1.0F, 0.0F, 0.0833F, 0.5F);
            default -> 0.0F;
        };
    }

    private void renderJawGroup(PoseStack ms, PartialItemModelRenderer renderer, int light, CreativeTrainRelocatorItemRenderer.AnimState state, BakedModel part) {
        ms.pushPose();
        float px = 0.0F;
        float py = 0.4375F;
        float pz = -0.0125F;
        ms.translate(px, py, pz);
        ms.mulPose(Axis.XP.rotationDegrees(this.jawRotX(state)));
        ms.translate(-px, -py, -pz);
        renderer.render(part, light);
        ms.popPose();
    }

    private void renderGreaterJaw(PoseStack ms, PartialItemModelRenderer renderer, int light, CreativeTrainRelocatorItemRenderer.AnimState state) {
        ms.pushPose();
        ms.translate(0.0F, this.greaterJawY(state) / 16.0F, 0.0F);
        renderer.render(LoconauticsPartialModels.RELOCATOR_GREATER_JAW.get(), light);
        ms.popPose();
    }

    private void renderOmnicoob(PoseStack ms, PartialItemModelRenderer renderer, int light, CreativeTrainRelocatorItemRenderer.AnimState state) {
        float scale = this.omnicoobScale(state);
        if (!(scale <= 0.0F)) {
            ms.pushPose();
            ms.translate(0.0F, this.omnicoobY(state) / 16.0F, -0.078125F);
            float px = 0.0F;
            float py = 0.0625F;
            float pz = -0.05625F;
            ms.translate(px, py, pz);
            ms.mulPose(Axis.ZP.rotationDegrees(-270.0F - state.spin * 360.0F));
            ms.mulPose(Axis.YP.rotationDegrees(-45.0F));
            ms.mulPose(Axis.XP.rotationDegrees(180.0F - state.spin * 360.0F));
            ms.scale(scale, scale, scale);
            ms.translate(-px, -py, -pz);
            renderer.render(LoconauticsPartialModels.RELOCATOR_OMNICOOB.get(), light);
            ms.popPose();
        }
    }

    private static float ease(float t, float v0, float v1, float t0, float t1) {
        float n = Math.clamp((t - t0) / (t1 - t0), 0.0F, 1.0F);
        return v0 + n * n * (3.0F - 2.0F * n) * (v1 - v0);
    }

    private static float easeIn(float t, float v0, float v1, float t0, float t1) {
        float n = Math.clamp((t - t0) / (t1 - t0), 0.0F, 1.0F);
        return v0 + n * n * n * (v1 - v0);
    }

    private static final class AnimState {
        boolean lastHeldTrain;
        float transitionAt = Float.NEGATIVE_INFINITY;
        int phase = 0;
        float phaseTime;
        float spin;
    }
}
