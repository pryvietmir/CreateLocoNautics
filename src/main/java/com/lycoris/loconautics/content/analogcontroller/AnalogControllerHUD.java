package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw.Layer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

public class AnalogControllerHUD {
    public static final Layer OVERLAY = AnalogControllerHUD::render;
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath("create", "textures/gui/widgets.png");
    private static final ResourceLocation LOCK_ICON = LoconauticsConstants.id("textures/gui/widget/lock_icon.png");
    private static final int SPEED_BG_W = 182;
    private static final int SPEED_BG_H = 5;
    private static final int SPEED_W = 182;
    private static final int SPEED_H = 5;
    private static final int THROTTLE_W = 182;
    private static final int THROTTLE_H = 5;
    private static final int PTR_W = 6;
    private static final int PTR_H = 9;
    private static final int FRAME_W = 186;
    private static final int FRAME_H = 7;
    private static final int DIR_ZONE_X = 77;
    private static final int LOCK_W = 8;
    private static final int LOCK_H = 10;
    private static final LerpedFloat displayedSpeed = LerpedFloat.linear();
    private static final LerpedFloat displayedThrottle = LerpedFloat.linear();
    public static Component currentPrompt;
    public static boolean currentPromptShadow;
    public static int promptKeepAlive;
    private static final LerpedFloat displayedPromptSize = LerpedFloat.linear();

    public static void tick() {
        if (promptKeepAlive > 0) {
            promptKeepAlive--;
        } else {
            currentPrompt = null;
        }

        Minecraft mc = Minecraft.getInstance();
        displayedPromptSize.chase(currentPrompt != null ? mc.font.width(currentPrompt) + 17 : 0.0, 0.5, Chaser.EXP);
        displayedPromptSize.tickChaser();
    }

    private static SableTrainClientRegistry.TrainMarker mountedTrain() {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(AnalogControllerClientHandler.getMountedPos());
        return sub == null ? null : SableTrainClientRegistry.get(sub.getUniqueId());
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.hideGui && mc.gameMode != null && mc.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            if (AnalogControllerClientHandler.isControlling()) {
                if (mc.level != null) {
                    AnalogControllerBlockEntity ace = AnalogControllerClientHandler.resolveMountedBlockEntity();
                    if (ace != null) {
                        boolean locked = ace.isLocked();
                        int maxPower = ace.getMaxPower();
                        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
                        SableTrainClientRegistry.TrainMarker marker = mountedTrain();
                        double ratio = marker == null ? 0.0 : AnalogControllerClientHandler.speedFraction(marker.speed(), marker);
                        displayedSpeed.chase((int)(ratio * 18.0) / 18.0, 0.5, Chaser.EXP);
                        displayedSpeed.tickChaser();
                        displayedThrottle.chase(maxPower / 15.0, 0.2, Chaser.EXP);
                        displayedThrottle.tickChaser();
                        float animSpeed = displayedSpeed.getValue(partialTicks);
                        float animThrottle = displayedThrottle.getValue(partialTicks);
                        int originX = graphics.guiWidth() / 2 - 91;
                        int barY = graphics.guiHeight() - 29;
                        graphics.blit(WIDGETS, originX, barY, 0, 0.0F, 190.0F, 182, 5, 256, 256);
                        int fillW = Math.round(animSpeed * 182.0F);
                        if (fillW > 0) {
                            graphics.blit(WIDGETS, originX, barY, 0, 0.0F, 185.0F, fillW, 5, 256, 256);
                        }

                        int throttleW = Math.round((1.0F - animThrottle) * 182.0F);
                        int invW = 182 - throttleW;
                        if (throttleW > 0) {
                            graphics.blit(WIDGETS, originX + invW, barY, 0, invW, 195.0F, throttleW, 5, 256, 256);
                        }

                        graphics.blit(WIDGETS, originX - 2, barY + 5 - 4, 0, 0.0F, 200.0F, 186, 7, 256, 256);
                        int ptrX = originX + Math.max(1, invW) - 3;
                        graphics.blit(WIDGETS, ptrX, barY - 2, 0, 0.0F, 209.0F, 6, 9, 256, 256);
                        int dirZoneAbsX = originX + 77;
                        graphics.blit(WIDGETS, dirZoneAbsX, barY - 20, 0, 77.0F, 165.0F, 28, 20, 256, 256);
                        int steer = ace.getSteerInput();
                        float steerAngle = steer < 0 ? -45.0F : (steer > 0 ? 45.0F : 0.0F);
                        PoseStack arrowPose = graphics.pose();
                        arrowPose.pushPose();
                        arrowPose.translate(originX + 91, barY - 9, 0.0F);
                        arrowPose.scale(0.925F, 0.925F, 1.0F);
                        PlacementClient.textured(arrowPose, 0.0F, 0.0F, 1.0F, steerAngle);
                        arrowPose.popPose();
                        int promptSize = (int)displayedPromptSize.getValue(partialTicks);
                        if (promptSize > 1) {
                            PoseStack poseStack = graphics.pose();
                            poseStack.pushPose();
                            poseStack.translate(originX, barY, 0.0F);
                            poseStack.pushPose();
                            poseStack.translate(promptSize / -2.0F + 91.0F, -27.0F, 0.0F);
                            AllGuiTextures.TRAIN_PROMPT_L.render(graphics, -3, 0);
                            AllGuiTextures.TRAIN_PROMPT_R.render(graphics, promptSize, 0);
                            graphics.blit(
                                AllGuiTextures.TRAIN_PROMPT.location,
                                0,
                                0,
                                0,
                                AllGuiTextures.TRAIN_PROMPT.getStartX() + (128.0F - promptSize / 2.0F),
                                AllGuiTextures.TRAIN_PROMPT.getStartY(),
                                promptSize,
                                AllGuiTextures.TRAIN_PROMPT.getHeight(),
                                256,
                                256
                            );
                            poseStack.popPose();
                            Font font = mc.font;
                            if (currentPrompt != null && font.width(currentPrompt) < promptSize - 10) {
                                poseStack.pushPose();
                                poseStack.translate(font.width(currentPrompt) / -2.0F + 82.0F, -27.0F, 100.0F);
                                if (currentPromptShadow) {
                                    graphics.drawString(font, currentPrompt, 9, 4, 5524805);
                                } else {
                                    graphics.drawString(font, currentPrompt, 9, 4, 5524805, false);
                                }

                                poseStack.popPose();
                            }

                            poseStack.popPose();
                        }

                        if (locked) {
                            int iconX = originX + 87;
                            int iconY = barY + -2;
                            graphics.blit(LOCK_ICON, iconX, iconY, 0.0F, 0.0F, 8, 10, 8, 10);
                        }
                    }
                }
            }
        }
    }
}
