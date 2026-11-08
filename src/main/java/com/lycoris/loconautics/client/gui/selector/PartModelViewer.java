package com.lycoris.loconautics.client.gui.selector;

import com.lycoris.loconautics.foundation.ui.PoseScissor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

public abstract class PartModelViewer {
    protected static final float PIVOT_X = 0.25F;
    protected static final float PIVOT_Y = 0.0625F;
    protected static final float PIVOT_Z = 0.25F;
    protected static final float DEFAULT_YAW = 136.66673F;
    protected static final float DEFAULT_PITCH = 43.666695F;
    protected static final float DEFAULT_SCALE = 3.0759742F;
    protected static final float DEFAULT_PAN_X = 0.0F;
    protected static final float DEFAULT_PAN_Y = 2.0F;
    private static final float ZOOM_STEP = 1.1F;
    private static final int ZOOM_LIMIT = 16;
    private static float modelYaw = 136.66673F;
    private static float modelPitch = 43.666695F;
    private static float modelPanX = 0.0F;
    private static float modelPanY = 2.0F;
    private static int zoomLevel;
    protected BlockState state;
    private int modelDrag;
    private int vx;
    private int vy;
    private int vw;
    private int vh;

    public void setState(BlockState state) {
        this.state = state;
    }

    protected abstract void draw(PoseStack var1, MultiBufferSource var2);

    protected void drawGlass(PoseStack pose, MultiBufferSource buffers) {
    }

    protected float iconDrop() {
        return 0.0F;
    }

    protected float iconScale() {
        return 1.0F;
    }

    public void render(GuiGraphics g, int x, int y, int w, int h) {
        this.vx = x;
        this.vy = y;
        this.vw = w;
        this.vh = h;
        if (this.state != null && w > 0 && h > 0) {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);
            float size = Math.min(w, h) * 0.5F * scale();
            PoseScissor.enable(g, x, y, x + w, y + h);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(x + w / 2.0F + modelPanX, y + h / 2.0F + modelPanY, 250.0F);
            pose.scale(size, -size, size);
            pose.mulPose(Axis.XP.rotationDegrees(modelPitch));
            pose.mulPose(Axis.YP.rotationDegrees(modelYaw));
            pose.translate(-0.25F, -0.0625F, -0.25F);
            BufferSource buf = mc.renderBuffers().bufferSource();
            this.draw(pose, buf);
            this.drawGlass(pose, buf);
            buf.endBatch();
            pose.popPose();
            g.disableScissor();
        }
    }

    public void renderIcon(GuiGraphics g, int cx, int cy, int box) {
        if (this.state != null && box > 0) {
            Minecraft mc = Minecraft.getInstance();
            mc.getTextureManager().getTexture(InventoryMenu.BLOCK_ATLAS).setFilter(false, false);
            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(cx, cy + box * this.iconDrop(), 250.0F);
            float size = box * 0.5F * 3.0759742F * this.iconScale();
            pose.scale(size, -size, size);
            pose.mulPose(Axis.XP.rotationDegrees(43.666695F));
            pose.mulPose(Axis.YP.rotationDegrees(136.66673F));
            pose.translate(-0.25F, -0.0625F, -0.25F);
            BufferSource buf = mc.renderBuffers().bufferSource();
            this.draw(pose, buf);
            buf.endBatch();
            pose.popPose();
        }
    }

    public void resetView() {
        modelYaw = 136.66673F;
        modelPitch = 43.666695F;
        modelPanX = 0.0F;
        modelPanY = 2.0F;
        zoomLevel = 0;
    }

    private static float scale() {
        return 3.0759742F * (float)Math.pow(1.1F, zoomLevel);
    }

    public int zoom() {
        return zoomLevel;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.inside(mx, my)) {
            return false;
        }

        this.modelDrag = button == 0 ? 1 : 2;
        return true;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.modelDrag == 1) {
            modelYaw += (float)dx;
            modelPitch = Mth.clamp((float)(modelPitch + dy), -90.0F, 90.0F);
            return true;
        } else if (this.modelDrag == 2) {
            modelPanX += (float)dx;
            modelPanY += (float)dy;
            return true;
        } else {
            return false;
        }
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean wasDragging = this.modelDrag != 0;
        this.modelDrag = 0;
        return wasDragging;
    }

    public boolean mouseScrolled(double mx, double my, double sy) {
        if (this.inside(mx, my) && sy != 0.0) {
            zoomLevel = Mth.clamp(zoomLevel + (sy > 0.0 ? 1 : -1), -16, 16);
            return true;
        } else {
            return false;
        }
    }

    private boolean inside(double mx, double my) {
        return mx >= this.vx && mx < this.vx + this.vw && my >= this.vy && my < this.vy + this.vh;
    }
}
