package com.lycoris.loconautics.content.controls.panelgauge;

import net.minecraft.util.StringRepresentable;

public enum GaugeNeedle implements StringRepresentable {
    DEFAULT("default", 4.0F, 2.0F, -160.0F, 0.0F, 1, 36, 4, 2, 4),
    CLASSIC("classic", 2.13137F, 2.13137F, -80.0F, 0.0F, 1, 46, 4, 2, 5),
    VERTICAL("vertical", 6.0F, 4.0F, -150.0F, 0.0F, 1, 36, 4, 2, 4),
    LINEAR("linear", 0.0F, 0.0F, 0.0F, 3.5F, 9, 41, 1, 0, 2);

    public static final int COLORS = 8;
    private static final int AUTHORED_COLOR = 4;
    private final String name;
    private final float pivotX;
    private final float pivotZ;
    private final float sweepDegrees;
    private final float slidePixels;
    private final int sheetX;
    private final int sheetY;
    private final int colorsPerBand;
    private final int colorStrideU;
    private final int bandStrideV;

    GaugeNeedle(
        String name,
        float pivotX,
        float pivotZ,
        float sweepDegrees,
        float slidePixels,
        int sheetX,
        int sheetY,
        int colorsPerBand,
        int colorStrideU,
        int bandStrideV
    ) {
        this.name = name;
        this.pivotX = pivotX;
        this.pivotZ = pivotZ;
        this.sweepDegrees = sweepDegrees;
        this.slidePixels = slidePixels;
        this.sheetX = sheetX;
        this.sheetY = sheetY;
        this.colorsPerBand = colorsPerBand;
        this.colorStrideU = colorStrideU;
        this.bandStrideV = bandStrideV;
    }

    public static GaugeNeedle forBackground(int background) {
        return switch (Math.floorMod(background, 6)) {
            case 3 -> DEFAULT;
            case 4 -> CLASSIC;
            case 5 -> VERTICAL;
            default -> LINEAR;
        };
    }

    public float pivotX() {
        return this.pivotX / 16.0F;
    }

    public float pivotZ() {
        return this.pivotZ / 16.0F;
    }

    public float sweepDegrees() {
        return this.sweepDegrees;
    }

    public float slidePixels() {
        return this.slidePixels;
    }

    public boolean slides() {
        return this.slidePixels != 0.0F;
    }

    public int colorOffsetU(int color) {
        return (color % this.colorsPerBand - 4 % this.colorsPerBand) * this.colorStrideU;
    }

    public int colorOffsetV(int color) {
        return (color / this.colorsPerBand - 4 / this.colorsPerBand) * this.bandStrideV;
    }

    public int sheetX(int color) {
        return this.sheetX + this.colorOffsetU(color);
    }

    public int sheetY(int color) {
        return this.sheetY + this.colorOffsetV(color);
    }

    public int sheetWidth() {
        return this == LINEAR ? 6 : 1;
    }

    public int sheetHeight() {
        return this == LINEAR ? 1 : (this == CLASSIC ? 4 : 3);
    }

    public static int clampColor(int color) {
        return Math.floorMod(color, 8);
    }

    public String getSerializedName() {
        return this.name;
    }
}
