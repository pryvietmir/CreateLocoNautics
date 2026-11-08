package com.lycoris.loconautics.common;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BogeyGroundDrop {
    private static final String KEY = "LoconauticsGrounded";
    public static final float DROP = 0.3125F;
    public static final VoxelShape RAISED_SHAPE = Shapes.or(Block.box(0.0, 7.0, 0.0, 16.0, 16.0, 16.0), Block.box(2.0, 5.0, 2.0, 14.0, 7.0, 14.0));
    public static final VoxelShape SHAPE = Shapes.or(Block.box(0.0, 2.0, 0.0, 16.0, 11.0, 16.0), Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0));

    private BogeyGroundDrop() {
    }

    public static float of(BlockEntity blockEntity) {
        return blockEntity instanceof AbstractBogeyBlockEntity bogey && bogey.getBogeyData().getBoolean("LoconauticsGrounded") ? 0.3125F : 0.0F;
    }

    public static void set(AbstractBogeyBlockEntity bogey, boolean grounded) {
        if (grounded) {
            bogey.getBogeyData().putBoolean("LoconauticsGrounded", true);
        } else {
            if (!bogey.getBogeyData().contains("LoconauticsGrounded")) {
                return;
            }

            bogey.getBogeyData().remove("LoconauticsGrounded");
        }

        bogey.setBogeyStyle(bogey.getStyle());
    }
}
