package com.lycoris.loconautics.content.controls.panellever;

import com.lycoris.loconautics.content.controls.panel.PanelSlot;
import com.lycoris.loconautics.network.packets.controls.LeverSetPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.UIRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class LeverHoldInteraction extends BlockHoldInteraction {
    private static final LeverHoldInteraction INSTANCE = new LeverHoldInteraction();
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 14;
    private static final int TEXT_COLOR = 4864552;
    private PanelSlot slot = PanelSlot.BOTTOM_LEFT;
    private boolean inverted;
    private int signal;
    private int sentSignal;
    private float value;
    private float animatedValue;
    private float lastAnimatedValue;

    public static LeverHoldInteraction registered() {
        return SimClickInteractions.register(INSTANCE);
    }

    public static boolean gripped() {
        return INSTANCE.isActive();
    }

    public static boolean grab(Level level, Player player, BlockPos panelPos, PanelSlot slot, int power, boolean inverted) {
        if (INSTANCE.isActive()) {
            return false;
        }

        INSTANCE.slot = slot;
        INSTANCE.inverted = inverted;
        INSTANCE.signal = INSTANCE.sentSignal = LeverPart.clamp(power);
        INSTANCE.value = INSTANCE.signal / 15.0F;
        INSTANCE.animatedValue = INSTANCE.lastAnimatedValue = INSTANCE.value;
        INSTANCE.startHold(level, player, panelPos);
        return true;
    }

    @Override
    public boolean activeTick(Level level, LocalPlayer player) {
        if (this.getInteractionPos() == null) {
            return true;
        }

        if (!inInteractionRange(player, this.getInteractionPos().getCenter(), 0.0)) {
            return true;
        }

        this.lastAnimatedValue = this.animatedValue;
        this.animatedValue = this.animatedValue * 0.15F + this.value * 0.85F;
        return false;
    }

    @Override
    public boolean activeOnMouseMove(double yaw, double pitch) {
        this.value = Math.min(1.0F, Math.max(0.0F, this.value - (float)(pitch / 180.0)));
        this.signal = LeverPart.clamp(Math.round(this.value * 15.0F));
        if (this.signal != this.sentSignal) {
            this.sentSignal = this.signal;
            PacketDistributor.sendToServer(new LeverSetPacket(this.getInteractionPos(), this.slot.ordinal(), this.signal), new CustomPacketPayload[0]);
        }

        return true;
    }

    @Override
    public void renderOverlay(GuiGraphics graphics, int width, int height, boolean hideGui) {
        if (!hideGui) {
            int x = width / 2 - 50 + 16;
            int y = height / 2 - 7;
            PoseStack ms = graphics.pose();
            ms.pushPose();
            ms.translate(x + 50.0F, y + 7.0F, 0.0F);
            ms.mulPose(Axis.ZP.rotationDegrees(90.0F));
            ms.translate(-x - 50.0F, -y - 7.0F, 0.0F);
            AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
            AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + 100 - 4, y);
            AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + 14 - 4);
            AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + 100 - 4, y + 14 - 4);
            UIRenderHelper.drawStretched(graphics, x, y + 4, 3, 6, 2, AllGuiTextures.BRASS_FRAME_LEFT);
            UIRenderHelper.drawStretched(graphics, x + 100 - 3, y + 4, 3, 6, 2, AllGuiTextures.BRASS_FRAME_RIGHT);
            UIRenderHelper.drawCropped(graphics, x + 4, y, 92, 3, 2, AllGuiTextures.BRASS_FRAME_TOP);
            UIRenderHelper.drawCropped(graphics, x + 4, y + 14 - 3, 92, 3, 2, AllGuiTextures.BRASS_FRAME_BOTTOM);
            int barStep = AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1;

            for (int filled = 0; filled < 94; filled += barStep) {
                UIRenderHelper.drawCropped(graphics, x + 3 + filled, y + 3, Math.min(barStep, 94 - filled), 8, 2, AllGuiTextures.VALUE_SETTINGS_BAR);
            }

            ms.popPose();
            ms.pushPose();
            ms.translate(0.0F, 0.0F, 4.0F);
            float partialTick = AnimationTickHolder.getPartialTicks();
            float shown = this.lastAnimatedValue * (1.0F - partialTick) + this.animatedValue * partialTick;
            float cursorY = (1.0F - 2.0F * shown) * 3.0F * 14.0F + 2.0F;
            int cursorX = x + 50 - 7;
            ms.pushPose();
            ms.translate(0.0F, y + 7 - 9 + cursorY, 0.0F);
            AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(graphics, cursorX - 3, 0);
            UIRenderHelper.drawCropped(graphics, cursorX, 0, 14, 14, 2, AllGuiTextures.VALUE_SETTINGS_CURSOR);
            AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(graphics, cursorX + 14, 0);
            ms.translate(0.0F, 0.0F, 4.0F);
            graphics.drawString(Minecraft.getInstance().font, String.valueOf(this.inverted ? 15 - this.signal : this.signal), cursorX + 1, 3, 4864552, false);
            ms.popPose();
            ms.popPose();
        }
    }
}
