package com.lycoris.loconautics.client.gui;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.client.sprite.PausableSpriteTicker;
import com.lycoris.loconautics.client.sprite.SpriteTickerHolder;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CreativeTabBanner {
    public static final ResourceLocation SPRITE = LoconauticsConstants.id("banner");
    private static final int WIDTH = 162;
    private static final int HEIGHT = 18;
    private static final int TITLE_BACKGROUND = -1072427252;
    private static final int TITLE_LIGHT = -858936;
    private static final int TITLE_DARK = -7587286;
    public static int currentRow;

    private CreativeTabBanner() {
    }

    public static void render(CreativeModeInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY, Component title) {
        if (currentRow != 0) {
            setPlaying(false);
        } else {
            int left = screen.getGuiLeft() + 8;
            int top = screen.getGuiTop() + 17;
            setPlaying(mouseX >= left && mouseX < left + 162 && mouseY >= top && mouseY < top + 18);
            PoseStack ps = graphics.pose();
            ps.pushPose();
            ps.translate(left, top, 0.0F);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.blitSprite(SPRITE, 0, 0, 162, 18);
            Font font = Minecraft.getInstance().font;
            graphics.fill(2, 2, font.width(title) + 8, 16, -1072427252);
            drawAuraText(graphics, font, title, 5, 5);
            RenderSystem.disableDepthTest();
            ps.popPose();
        }
    }

    private static void drawAuraText(GuiGraphics graphics, Font font, Component text, int x, int y) {
        graphics.drawString(font, text, x, y, -7587286, true);
        PoseStack ps = graphics.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 1.0F);
        Matrix4f pose = ps.last().pose();
        Vector3f origin = pose.transformPosition(new Vector3f(x, y, 0.0F));
        Vector3f corner = pose.transformPosition(new Vector3f(x + font.width(text), y + 5.0F, 0.0F));
        Window window = Minecraft.getInstance().getWindow();
        float scale = (float)window.getGuiScale();
        origin.mul(scale);
        corner.mul(scale);
        int width = (int)(corner.x - origin.x);
        int height = (int)(corner.y - origin.y);
        if (width > 0 && height > 0) {
            RenderSystem.enableScissor((int)origin.x, window.getHeight() - (int)origin.y - height, width, height);
            graphics.drawString(font, text, x, y, -858936, false);
            RenderSystem.disableScissor();
        }

        ps.popPose();
    }

    public static void setPlaying(boolean playing) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getGuiSprites().getSprite(SPRITE);
        if (((SpriteTickerHolder)sprite.contents()).loconautics$getTicker() instanceof PausableSpriteTicker pausable) {
            pausable.loconautics$setPlaying(playing);
        }
    }
}
