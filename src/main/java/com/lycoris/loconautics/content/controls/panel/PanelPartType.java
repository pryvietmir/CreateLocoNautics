package com.lycoris.loconautics.content.controls.panel;

import com.lycoris.loconautics.content.controls.panelbutton.ButtonPart;
import com.lycoris.loconautics.content.controls.panelgauge.GaugeBlockEntity;
import com.lycoris.loconautics.content.controls.panellever.LeverPart;
import com.lycoris.loconautics.content.controls.panelswitch.SwitchPart;
import com.lycoris.loconautics.content.controls.panelvalve.ValvePart;
import java.util.function.Function;
import net.createmod.catnip.lang.Lang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public enum PanelPartType implements StringRepresentable {
    GAUGE(GaugeBlockEntity.Gauge::read, PanelBlock.FLAT_SLOT_BOX),
    VALVE(ValvePart::read, new double[]{1.0, 0.0, 1.0, 7.0, 2.75, 7.0}),
    BUTTON(ButtonPart::read, PanelBlock.FLAT_SLOT_BOX),
    LEVER(LeverPart::read, new double[]{2.0, 0.0, 1.0, 6.0, 2.0, 7.0}),
    SWITCH(SwitchPart::read, new double[]{2.0, 0.0, 1.0, 6.0, 2.0, 7.0});

    public static final int PALETTE_COLORS = 35;
    public static final int BODY_VARIANTS = 4;
    private final Function<CompoundTag, PanelPart> reader;
    private final double[] slotBox;
    private VoxelShape[][][] shapes;
    private VoxelShape[][][] centeredShapes;

    public static int clampColor(int color) {
        return Mth.clamp(color, 0, 34);
    }

    public static int clampBody(int body) {
        return Mth.clamp(body, 0, 3);
    }

    PanelPartType(Function<CompoundTag, PanelPart> reader, double[] slotBox) {
        this.reader = reader;
        this.slotBox = slotBox;
    }

    public PanelPart read(CompoundTag tag) {
        return this.reader.apply(tag);
    }

    public AABB localBox(double offsetX, double offsetZ) {
        return new AABB(
            (this.slotBox[0] + offsetX) / 16.0,
            this.slotBox[1] / 16.0,
            (this.slotBox[2] + offsetZ) / 16.0,
            (this.slotBox[3] + offsetX) / 16.0,
            this.slotBox[4] / 16.0,
            (this.slotBox[5] + offsetZ) / 16.0
        );
    }

    public VoxelShape[][][] shapes() {
        if (this.shapes == null) {
            this.shapes = PanelBlock.buildShapes(this.slotBox);
        }

        return this.shapes;
    }

    public VoxelShape[][][] centeredShapes() {
        if (this.centeredShapes == null) {
            this.centeredShapes = PanelBlock.buildShapes(this.slotBox, true);
        }

        return this.centeredShapes;
    }

    public static PanelPartType byName(@Nullable String name) {
        if (name != null && !name.isEmpty()) {
            for (PanelPartType type : values()) {
                if (type.getSerializedName().equals(name)) {
                    return type;
                }
            }

            return GAUGE;
        } else {
            return GAUGE;
        }
    }

    public String getSerializedName() {
        return Lang.asId(this.name());
    }
}
