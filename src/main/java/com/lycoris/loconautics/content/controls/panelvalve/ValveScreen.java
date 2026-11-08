package com.lycoris.loconautics.content.controls.panelvalve;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.foundation.ui.NoBlurScreen;
import com.lycoris.loconautics.network.packets.controls.ValveTurnPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public class ValveScreen extends NoBlurScreen {
    private static final ResourceLocation OVERLAY = LoconauticsConstants.id("textures/gui/valve/overlay.png");
    private static final ResourceLocation WHEEL = LoconauticsConstants.id("textures/gui/valve/bg.png");
    private static final int PANEL_W = 123;
    private static final int PANEL_H = 121;
    private static final int SHEET = 256;
    private static final int DIAL_CX = 64;
    private static final int DIAL_CY = 64;
    private static final int WHEEL_SIZE = 128;
    private static final int DEAD_ZONE = 14;
    private static final int READOUT_LIFT = 6;
    private static final int DIGIT_U = 0;
    private static final int DIGIT_V = 153;
    private static final int DIGIT_W = 5;
    private static final int DIGIT_H = 7;
    private static final int DIGIT_ADVANCE = 6;
    private final BlockPos panelPos;
    private final PanelSlot slot;
    private float degrees;
    private int sentDegrees;
    private boolean closing;
    private int guiLeft;
    private int guiTop;

    private ValveScreen(BlockPos panelPos, PanelSlot slot, float degrees) {
        super(Component.translatable("gui.loconautics.valve"));
        this.panelPos = panelPos;
        this.slot = slot;
        this.degrees = ValvePart.wrap(degrees);
        this.sentDegrees = Mth.floor(this.degrees);
    }

    public static void open(BlockPos panelPos, PanelSlot slot, float degrees) {
        Minecraft.getInstance().setScreen(new ValveScreen(panelPos, slot, degrees));
    }

    protected void init() {
        this.guiLeft = (this.width - 123) / 2;
        this.guiTop = (this.height - 121) / 2;
    }

    private int centerX() {
        return this.guiLeft + 64;
    }

    private int centerY() {
        return this.guiTop + 64;
    }

    private void turnTo(double mouseX, double mouseY) {
        double dx = mouseX - this.centerX();
        double dy = mouseY - this.centerY();
        if (!(dx * dx + dy * dy < 196.0)) {
            float aimed = ValvePart.wrap((float)Math.toDegrees(Math.atan2(dx, -dy)));
            if (hasShiftDown()) {
                aimed = ValvePart.wrap(Math.round(aimed / 45.0F) * 45.0F);
            }

            this.degrees = Mth.clamp(this.degrees + Mth.degreesDifference(this.degrees, aimed), 0.0F, 360.0F);
            this.sendIfMoved();
        }
    }

    private void sendIfMoved() {
        int whole = Mth.floor(this.degrees);
        if (whole != this.sentDegrees) {
            this.sentDegrees = whole;
            PacketDistributor.sendToServer(new ValveTurnPacket(this.panelPos, this.slot.ordinal(), whole), new CustomPacketPayload[0]);
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(OVERLAY, this.guiLeft, this.guiTop, 0.0F, 0.0F, 123, 121, 256, 256);
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(this.centerX(), this.centerY(), 0.0F);
        ms.mulPose(Axis.ZP.rotationDegrees(this.degrees));
        ms.translate(-64.0F, -64.0F, 0.0F);
        graphics.blit(WHEEL, 0, 0, 0.0F, 0.0F, 128, 128, 128, 128);
        ms.popPose();
        this.drawReadout(graphics, Mth.floor(this.degrees));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawReadout(GuiGraphics graphics, int value) {
        String text = Integer.toString(value);
        int width = text.length() * 6 - 1;
        int x = this.centerX() - width / 2;

        for (int i = 0; i < text.length(); i++) {
            int digit = text.charAt(i) - '0';
            graphics.blit(OVERLAY, x + i * 6, this.guiTop - 6, 0 + digit * 6, 153.0F, 5, 7, 256, 256);
        }
    }

    public void tick() {
        if (!useHeld()) {
            this.onClose();
        }
    }

    private static boolean useHeld() {
        Key key = Minecraft.getInstance().options.keyUse.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : InputConstants.isKeyDown(window, key.getValue());
    }

    public void mouseMoved(double mouseX, double mouseY) {
        this.turnTo(mouseX, mouseY);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.turnTo(mouseX, mouseY);
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (Minecraft.getInstance().options.keyUse.matchesMouse(button)) {
            this.onClose();
        }

        return true;
    }

    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            PacketDistributor.sendToServer(new ValveTurnPacket(this.panelPos, this.slot.ordinal(), Mth.floor(this.degrees)), new CustomPacketPayload[0]);
            super.onClose();
        }
    }

    public boolean isPauseScreen() {
        return false;
    }
}
