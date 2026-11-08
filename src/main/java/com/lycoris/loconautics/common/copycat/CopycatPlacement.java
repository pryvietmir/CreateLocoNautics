package com.lycoris.loconautics.common.copycat;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public enum CopycatPlacement implements StringRepresentable {
    TOP("top", 0),
    CENTER("center", -2),
    BOTTOM("bottom", -4);

    public static final EnumProperty<CopycatPlacement> PLACEMENT = EnumProperty.create("placement", CopycatPlacement.class);
    private final String name;
    private final int pixels;

    CopycatPlacement(String name, int pixels) {
        this.name = name;
        this.pixels = pixels;
    }

    public int pixels() {
        return this.pixels;
    }

    public float blocks() {
        return this.pixels / 16.0F;
    }

    public static CopycatPlacement aimedAt(BlockPlaceContext context) {
        double height = context.getClickLocation().y - context.getClickedPos().getY();
        if (height > 0.6666666666666666) {
            return TOP;
        } else {
            return height > 0.3333333333333333 ? CENTER : BOTTOM;
        }
    }

    public CopycatPlacement next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public String getSerializedName() {
        return this.name;
    }
}
