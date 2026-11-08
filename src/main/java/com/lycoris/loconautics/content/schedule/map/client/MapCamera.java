package com.lycoris.loconautics.content.schedule.map.client;

public class MapCamera {
    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 8.0;
    public static final double ZOOM_STEP = 1.25;
    private static final float CAM_LERP_SPEED = 0.25F;
    private static MapCamera.CameraState savedCameraState;
    private double camX;
    private double camZ;
    private double zoom;
    private double targetCamX;
    private double targetCamZ;
    private double targetZoom;
    private final int viewOriginX;
    private final int viewOriginZ;
    private final int gridChunks;

    public MapCamera(int viewOriginX, int viewOriginZ, int gridChunks, int centerChunkX, int centerChunkZ) {
        this.viewOriginX = viewOriginX;
        this.viewOriginZ = viewOriginZ;
        this.gridChunks = gridChunks;
        MapCamera.CameraState saved = savedCameraState;
        if (saved != null) {
            this.camX = saved.camX;
            this.camZ = saved.camZ;
            this.zoom = saved.zoom;
        } else {
            this.camX = centerChunkX;
            this.camZ = centerChunkZ;
            this.zoom = 1.0;
        }

        this.targetCamX = this.camX;
        this.targetCamZ = this.camZ;
        this.targetZoom = this.zoom;
    }

    public void tick() {
        double dx = this.targetCamX - this.camX;
        double dz = this.targetCamZ - this.camZ;
        double dZm = this.targetZoom - this.zoom;
        if (dx * dx + dz * dz > 1.0E-6) {
            this.camX += dx * 0.25;
            this.camZ += dz * 0.25;
        } else if (dx != 0.0 || dz != 0.0) {
            this.camX = this.targetCamX;
            this.camZ = this.targetCamZ;
        }

        if (Math.abs(dZm) > 1.0E-7) {
            this.zoom += dZm * 0.25;
        } else if (dZm != 0.0) {
            this.zoom = this.targetZoom;
        }

        this.clampCamera();
    }

    public void saveState() {
        savedCameraState = new MapCamera.CameraState(this.camX, this.camZ, this.zoom);
    }

    public void zoomAt(double mx, double my, double sy, int mapAreaX, int mapAreaY, int mapAreaSize) {
        double vb = this.gridChunks / this.zoom;
        double ccx = this.camX - vb / 2.0 + (mx - mapAreaX) / mapAreaSize * vb;
        double ccz = this.camZ - vb / 2.0 + (my - mapAreaY) / mapAreaSize * vb;
        double old = this.targetZoom;
        if (sy > 0.0) {
            this.targetZoom = Math.min(8.0, this.targetZoom * 1.25);
        } else if (sy < 0.0) {
            this.targetZoom = Math.max(1.0, this.targetZoom / 1.25);
        }

        if (this.targetZoom != old) {
            double va = this.gridChunks / this.targetZoom;
            this.targetCamX = ccx - ((mx - mapAreaX) / mapAreaSize - 0.5) * va;
            this.targetCamZ = ccz - ((my - mapAreaY) / mapAreaSize - 0.5) * va;
        }
    }

    public void setImmediate(double x, double z) {
        this.camX = x;
        this.camZ = z;
        this.targetCamX = x;
        this.targetCamZ = z;
        this.clampCamera();
    }

    private void clampCamera() {
        double hv = this.gridChunks / this.zoom / 2.0;
        double mnX = this.viewOriginX + hv;
        double mxX = this.viewOriginX + this.gridChunks - hv;
        this.camX = mnX > mxX ? this.viewOriginX + this.gridChunks / 2.0 : Math.max(mnX, Math.min(mxX, this.camX));
        double mnZ = this.viewOriginZ + hv;
        double mxZ = this.viewOriginZ + this.gridChunks - hv;
        this.camZ = mnZ > mxZ ? this.viewOriginZ + this.gridChunks / 2.0 : Math.max(mnZ, Math.min(mxZ, this.camZ));
        double th = this.gridChunks / this.targetZoom / 2.0;
        double tnX = this.viewOriginX + th;
        double txX = this.viewOriginX + this.gridChunks - th;
        this.targetCamX = tnX > txX ? this.viewOriginX + this.gridChunks / 2.0 : Math.max(tnX, Math.min(txX, this.targetCamX));
        double tnZ = this.viewOriginZ + th;
        double txZ = this.viewOriginZ + this.gridChunks - th;
        this.targetCamZ = tnZ > txZ ? this.viewOriginZ + this.gridChunks / 2.0 : Math.max(tnZ, Math.min(txZ, this.targetCamZ));
    }

    public double getCamX() {
        return this.camX;
    }

    public double getCamZ() {
        return this.camZ;
    }

    public double getZoom() {
        return this.zoom;
    }

    public record CameraState(double camX, double camZ, double zoom) {
    }
}
