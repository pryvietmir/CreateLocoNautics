package com.lycoris.loconautics.content.combustion;

import net.minecraft.util.StringRepresentable;

public enum EnginePart implements StringRepresentable {
    SINGLE("single"),
    FRONT("front"),
    MIDDLE("middle"),
    BACK("back");

    private final String key;

    EnginePart(String key) {
        this.key = key;
    }

    public String getSerializedName() {
        return this.key;
    }
}
