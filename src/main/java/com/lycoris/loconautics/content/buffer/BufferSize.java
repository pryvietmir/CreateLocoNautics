package com.lycoris.loconautics.content.buffer;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum BufferSize implements StringRepresentable {
    HALF("half", 11),
    FULL("full", 16),
    ONE_AND_HALF("one_and_half", 24);

    public static final EnumProperty<BufferSize> SIZE = EnumProperty.create("size", BufferSize.class);
    public static final int RESTING_REACH_PIXELS = 8;
    private final String name;
    private final int reachPixels;

    BufferSize(String name, int reachPixels) {
        this.name = name;
        this.reachPixels = reachPixels;
    }

    public int extensionPixels() {
        return this.reachPixels - 8;
    }

    public int sleeveOutPixels() {
        return this == HALF ? 0 : 3;
    }

    public double extensionBlocks() {
        return this.extensionPixels() / 16.0;
    }

    public BufferSize next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public String getSerializedName() {
        return this.name;
    }
}
