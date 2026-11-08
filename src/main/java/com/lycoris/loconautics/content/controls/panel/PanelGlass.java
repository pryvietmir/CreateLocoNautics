package com.lycoris.loconautics.content.controls.panel;

public enum PanelGlass {
    BODY(4.0F, 2.0F, 6.5F),
    SLIM(4.0F, 2.0F, 6.5F);

    private final float pivotX;
    private final float pivotY;
    private final float pivotZ;

    PanelGlass(float pivotX, float pivotY, float pivotZ) {
        this.pivotX = pivotX / 16.0F;
        this.pivotY = pivotY / 16.0F;
        this.pivotZ = pivotZ / 16.0F;
    }

    public float pivotX() {
        return this.pivotX;
    }

    public float pivotY() {
        return this.pivotY;
    }

    public float pivotZ() {
        return this.pivotZ;
    }
}
