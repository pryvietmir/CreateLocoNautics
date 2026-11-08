package com.lycoris.loconautics.content.schedule.map.client;

import com.lycoris.loconautics.network.packets.schedule.MapColorRequestPacket;
import com.lycoris.loconautics.network.packets.schedule.MapColorResponsePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class FullscreenMapScreen extends Screen {
    private static final int GRID_CHUNKS = 96;
    private static final int REQUEST_CAP = 400;
    private static final int POLL_INTERVAL = 20;
    private static final int EDGE_INTERVAL = 20;
    private static final double EDGE_PICK_PX = 6.0;
    private static FullscreenMapScreen active;
    private static final Map<Long, byte[]> COLORS = new ConcurrentHashMap<>();
    private final Screen parent;
    private final BiConsumer<MapTrackOverlay.Edge, Double> onPick;
    private final MapCamera camera;
    private final MapRasterTexture raster;
    private final int viewOriginX;
    private final int viewOriginZ;
    private volatile boolean colorsDirty;
    private int pollTimer;
    private List<MapTrackOverlay.Edge> edges = new ArrayList<>();
    private final Set<Long> selected = new HashSet<>();
    private final Map<Long, Double> pickPositions = new HashMap<>();
    private int edgeTimer;
    private int mapX;
    private int mapY;
    private int mapSize;
    private boolean panning;
    private boolean panMoved;
    private double panStartMx;
    private double panStartMy;
    private double panCamX;
    private double panCamZ;
    private boolean boxing;
    private double boxStartMx;
    private double boxStartMy;
    private double boxCurMx;
    private double boxCurMy;

    public FullscreenMapScreen(Screen parent, BiConsumer<MapTrackOverlay.Edge, Double> onPick) {
        super(Component.translatable("loconautics.schedule.map_title"));
        this.parent = parent;
        this.onPick = onPick;
        Player p = Minecraft.getInstance().player;
        int cx = p != null ? p.chunkPosition().x : 0;
        int cz = p != null ? p.chunkPosition().z : 0;
        this.viewOriginX = cx - 48;
        this.viewOriginZ = cz - 48;
        this.camera = new MapCamera(this.viewOriginX, this.viewOriginZ, 96, cx, cz);
        this.raster = new MapRasterTexture(96, this.viewOriginX, this.viewOriginZ);
    }

    protected void init() {
        active = this;
        int margin = 20;
        this.mapSize = Math.min(this.width, this.height) - margin * 2;
        this.mapX = (this.width - this.mapSize) / 2;
        this.mapY = (this.height - this.mapSize) / 2;
        this.colorsDirty = true;
        this.rebuildEdges();
    }

    private void rebuildEdges() {
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            this.edges = MapTrackOverlay.collect(p.level().dimension());
        }
    }

    public void tick() {
        if (this.pollTimer-- <= 0) {
            this.pollTimer = 20;
            this.requestVisibleChunks();
        }

        if (this.edgeTimer-- <= 0) {
            this.edgeTimer = 20;
            this.rebuildEdges();
        }

        this.raster.tick();
        if (this.colorsDirty) {
            this.colorsDirty = false;
            this.raster.rebuildAsync(COLORS);
        }
    }

    private void forVisibleChunks(LongConsumer action) {
        double v = 96.0 / this.camera.getZoom();
        int minX = Math.max(this.viewOriginX, (int)Math.floor(this.camera.getCamX() - v / 2.0));
        int maxX = Math.min(this.viewOriginX + 96 - 1, (int)Math.ceil(this.camera.getCamX() + v / 2.0));
        int minZ = Math.max(this.viewOriginZ, (int)Math.floor(this.camera.getCamZ() - v / 2.0));
        int maxZ = Math.min(this.viewOriginZ + 96 - 1, (int)Math.ceil(this.camera.getCamZ() + v / 2.0));

        for (int cz = minZ; cz <= maxZ; cz++) {
            for (int cx = minX; cx <= maxX; cx++) {
                action.accept(key(cx, cz));
            }
        }
    }

    private void requestVisibleChunks() {
        List<long[]> req = new ArrayList<>();
        this.forVisibleChunks(k -> {
            if (req.size() < 400 && !COLORS.containsKey(k)) {
                req.add(new long[]{(int)(k >> 32), (int)k});
            }
        });
        if (!req.isEmpty()) {
            CatnipServices.NETWORK.sendToServer(new MapColorRequestPacket(req));
        }
    }

    public static void acceptColors(List<MapColorResponsePacket.Entry> entries) {
        for (MapColorResponsePacket.Entry e : entries) {
            COLORS.put(key(e.chunkX(), e.chunkZ()), e.colors());
        }

        if (active != null) {
            active.colorsDirty = true;
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.camera.tick();
        graphics.fill(0, 0, this.width, this.height, -1072688618);
        graphics.fill(this.mapX, this.mapY, this.mapX + this.mapSize, this.mapY + this.mapSize, -15395044);
        double zoom = this.camera.getZoom();
        double v = 96.0 / zoom;
        double sx = this.camera.getCamX() - v / 2.0;
        double sz = this.camera.getCamZ() - v / 2.0;
        if (this.raster.isTextureValid()) {
            int ppc = this.raster.pxPerChunk();
            float u0 = (float)((sx - this.viewOriginX) * ppc);
            float v0 = (float)((sz - this.viewOriginZ) * ppc);
            int srcW = Math.max(1, (int)Math.round(v * ppc));
            graphics.enableScissor(this.mapX, this.mapY, this.mapX + this.mapSize, this.mapY + this.mapSize);
            graphics.blit(MapRasterTexture.TEXTURE_LOC, this.mapX, this.mapY, this.mapSize, this.mapSize, u0, v0, srcW, srcW, 96 * ppc, 96 * ppc);
            graphics.disableScissor();
        }

        this.renderTracks(graphics, sx, sz, v);
        this.renderSelectedEdges(graphics, sx, sz, v);
        this.renderPlayerMarker(graphics, sx, sz, v);
        if (this.boxing) {
            int x0 = (int)Math.min(this.boxStartMx, this.boxCurMx);
            int x1 = (int)Math.max(this.boxStartMx, this.boxCurMx);
            int y0 = (int)Math.min(this.boxStartMy, this.boxCurMy);
            int y1 = (int)Math.max(this.boxStartMy, this.boxCurMy);
            graphics.fill(x0, y0, x1, y1, 861252351);
            graphics.renderOutline(x0, y0, x1 - x0, y1 - y0, -11162881);
        }

        graphics.renderOutline(this.mapX, this.mapY, this.mapSize, this.mapSize, -12959926);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.mapY - 12, -1);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    protected void renderBlurredBackground(float partialTick) {
    }

    private void renderTracks(GuiGraphics graphics, double sx, double sz, double v) {
        if (Minecraft.getInstance().level != null) {
            TrainMapManager.tick(Minecraft.getInstance().level.dimension());
            double scale = this.mapSize / (16.0 * v);
            double offX = this.mapX - sx / v * this.mapSize;
            double offY = this.mapY - sz / v * this.mapSize;
            Rect2i bounds = new Rect2i((int)Math.floor(sx * 16.0), (int)Math.floor(sz * 16.0), (int)Math.ceil(v * 16.0), (int)Math.ceil(v * 16.0));
            graphics.enableScissor(this.mapX, this.mapY, this.mapX + this.mapSize, this.mapY + this.mapSize);
            RenderSystem.enableBlend();
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate((float)offX, (float)offY, 0.0F);
            pose.scale((float)scale, (float)scale, 1.0F);
            TrainMapRenderer.INSTANCE.render(graphics, false, bounds);
            graphics.bufferSource().endBatch();
            pose.popPose();
            graphics.disableScissor();
        }
    }

    private void renderSelectedEdges(GuiGraphics graphics, double sx, double sz, double v) {
        if (!this.selected.isEmpty()) {
            graphics.enableScissor(this.mapX, this.mapY, this.mapX + this.mapSize, this.mapY + this.mapSize);

            for (MapTrackOverlay.Edge e : this.edges) {
                if (this.selected.contains(e.key())) {
                    double[] xs = e.xs();
                    double[] zs = e.zs();
                    int prevX = 0;
                    int prevY = 0;

                    for (int i = 0; i < xs.length; i++) {
                        int px = this.mapX + (int)((xs[i] / 16.0 - sx) / v * this.mapSize);
                        int py = this.mapY + (int)((zs[i] / 16.0 - sz) / v * this.mapSize);
                        if (i > 0) {
                            drawLine(graphics, prevX, prevY, px, py, -8909);
                        }

                        prevX = px;
                        prevY = py;
                    }
                }
            }

            graphics.disableScissor();
        }
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
        } else {
            for (int i = 0; i <= steps; i++) {
                int x = x0 + (x1 - x0) * i / steps;
                int y = y0 + (y1 - y0) * i / steps;
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private void renderPlayerMarker(GuiGraphics graphics, double sx, double sz, double v) {
        Player p = Minecraft.getInstance().player;
        if (p != null) {
            double pcx = p.getX() / 16.0;
            double pcz = p.getZ() / 16.0;
            int px = this.mapX + (int)((pcx - sx) / v * this.mapSize);
            int py = this.mapY + (int)((pcz - sz) / v * this.mapSize);
            if (px >= this.mapX && px < this.mapX + this.mapSize && py >= this.mapY && py < this.mapY + this.mapSize) {
                graphics.fill(px - 2, py - 2, px + 2, py + 2, -1);
                graphics.fill(px - 1, py - 1, px + 1, py + 1, -2084816);
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.inMap(mx, my)) {
            return super.mouseClicked(mx, my, button);
        } else if (button == 1) {
            this.boxing = true;
            this.boxStartMx = this.boxCurMx = mx;
            this.boxStartMy = this.boxCurMy = my;
            return true;
        } else if (button == 0) {
            this.panning = true;
            this.panMoved = false;
            this.panStartMx = mx;
            this.panStartMy = my;
            this.panCamX = this.camera.getCamX();
            this.panCamZ = this.camera.getCamZ();
            return true;
        } else {
            return super.mouseClicked(mx, my, button);
        }
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.panning && button == 0) {
            if (Math.abs(mx - this.panStartMx) > 2.0 || Math.abs(my - this.panStartMy) > 2.0) {
                this.panMoved = true;
            }

            double v = 96.0 / this.camera.getZoom();
            double ppc = this.mapSize / v;
            this.camera.setImmediate(this.panCamX - (mx - this.panStartMx) / ppc, this.panCamZ - (my - this.panStartMy) / ppc);
            return true;
        } else if (this.boxing && button == 1) {
            this.boxCurMx = mx;
            this.boxCurMy = my;
            return true;
        } else {
            return super.mouseDragged(mx, my, button, dx, dy);
        }
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (this.panning && button == 0) {
            this.panning = false;
            if (!this.panMoved) {
                this.onEdgePick(mx, my);
            }

            return true;
        } else if (this.boxing && button == 1) {
            this.boxing = false;
            this.onBoxSelect(this.boxStartMx, this.boxStartMy, this.boxCurMx, this.boxCurMy);
            return true;
        } else {
            return super.mouseReleased(mx, my, button);
        }
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.inMap(mx, my)) {
            this.camera.zoomAt(mx, my, sy, this.mapX, this.mapY, this.mapSize);
        }

        return true;
    }

    private void onEdgePick(double mx, double my) {
        double v = 96.0 / this.camera.getZoom();
        double sx = this.camera.getCamX() - v / 2.0;
        double sz = this.camera.getCamZ() - v / 2.0;
        Long best = null;
        double bestDist = 36.0;
        double bestAlong = 0.0;

        for (MapTrackOverlay.Edge e : this.edges) {
            double[] xs = e.xs();
            double[] zs = e.zs();
            double px0 = 0.0;
            double py0 = 0.0;
            double along = 0.0;

            for (int i = 0; i < xs.length; i++) {
                double px = this.mapX + (xs[i] / 16.0 - sx) / v * this.mapSize;
                double py = this.mapY + (zs[i] / 16.0 - sz) / v * this.mapSize;
                if (i > 0) {
                    double d = distToSegSq(mx, my, px0, py0, px, py);
                    double segWorld = Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
                    if (d < bestDist) {
                        bestDist = d;
                        best = e.key();
                        bestAlong = along + segWorld * segT(mx, my, px0, py0, px, py);
                    }

                    along += segWorld;
                }

                px0 = px;
                py0 = py;
            }
        }

        if (best != null) {
            this.selected.clear();
            this.selected.add(best);
            this.pickPositions.put(best, bestAlong);
            this.openCanvas();
        }
    }

    private static double segT(double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lenSq = dx * dx + dy * dy;
        return lenSq < 1.0E-9 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - x0) * dx + (py - y0) * dy) / lenSq));
    }

    private void onBoxSelect(double x0, double y0, double x1, double y1) {
        double minX = Math.min(x0, x1);
        double maxX = Math.max(x0, x1);
        double minY = Math.min(y0, y1);
        double maxY = Math.max(y0, y1);
        double v = 96.0 / this.camera.getZoom();
        double sx = this.camera.getCamX() - v / 2.0;
        double sz = this.camera.getCamZ() - v / 2.0;
        this.selected.clear();
        this.pickPositions.clear();

        for (MapTrackOverlay.Edge e : this.edges) {
            double[] xs = e.xs();
            double[] zs = e.zs();

            for (int i = 0; i < xs.length; i++) {
                double px = this.mapX + (xs[i] / 16.0 - sx) / v * this.mapSize;
                double py = this.mapY + (zs[i] / 16.0 - sz) / v * this.mapSize;
                if (px >= minX && px <= maxX && py >= minY && py <= maxY) {
                    this.selected.add(e.key());
                    break;
                }
            }
        }

        if (!this.selected.isEmpty()) {
            this.openCanvas();
        }
    }

    private void openCanvas() {
        for (MapTrackOverlay.Edge e : this.edges) {
            if (this.selected.contains(e.key())) {
                this.onPick.accept(e, this.pickPositions.getOrDefault(e.key(), e.arcLength() / 2.0));
                this.minecraft.setScreen(this.parent);
                return;
            }
        }
    }

    private static double distToSegSq(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0.0 ? 0.0 : ((px - ax) * dx + (py - ay) * dy) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        double ex = px - cx;
        double ey = py - cy;
        return ex * ex + ey * ey;
    }

    private boolean inMap(double mx, double my) {
        return mx >= this.mapX && mx < this.mapX + this.mapSize && my >= this.mapY && my < this.mapY + this.mapSize;
    }

    private static long key(int cx, int cz) {
        return (long)cx << 32 | cz & 4294967295L;
    }

    public void removed() {
        this.camera.saveState();
        this.raster.free();
        if (active == this) {
            active = null;
        }
    }

    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
