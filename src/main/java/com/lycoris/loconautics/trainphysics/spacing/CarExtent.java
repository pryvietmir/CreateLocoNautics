package com.lycoris.loconautics.trainphysics.spacing;

import net.minecraft.nbt.CompoundTag;

public record CarExtent(double forward, double backward, double left, double right, double height) {
    private static final String KEY = "Extent";

    public double width() {
        return this.left + this.right;
    }

    public double halfLength() {
        return Math.max(this.forward, this.backward);
    }

    public double halfWidth() {
        return Math.max(0.0, Math.max(this.left, this.right));
    }

    public void write(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putDouble("F", this.forward);
        t.putDouble("B", this.backward);
        t.putDouble("L", this.left);
        t.putDouble("R", this.right);
        t.putDouble("H", this.height);
        tag.put("Extent", t);
    }

    public static CarExtent read(CompoundTag tag) {
        if (!tag.contains("Extent")) {
            return null;
        }

        CompoundTag t = tag.getCompound("Extent");
        CarExtent read = new CarExtent(t.getDouble("F"), t.getDouble("B"), t.getDouble("L"), t.getDouble("R"), t.getDouble("H"));
        return read.valid() ? read : null;
    }

    private boolean valid() {
        return Double.isFinite(this.forward)
            && Double.isFinite(this.backward)
            && Double.isFinite(this.left)
            && Double.isFinite(this.right)
            && Double.isFinite(this.height)
            && this.forward >= 0.0
            && this.backward >= 0.0
            && this.left + this.right > 0.0
            && this.height > 0.0;
    }
}
