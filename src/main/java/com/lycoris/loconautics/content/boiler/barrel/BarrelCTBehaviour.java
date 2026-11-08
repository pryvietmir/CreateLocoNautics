package com.lycoris.loconautics.content.boiler.barrel;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.Base;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.ContextRequirement;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BarrelCTBehaviour extends Base {
    private final CTSpriteShiftEntry shift;

    public BarrelCTBehaviour(CTSpriteShiftEntry shift) {
        this.shift = shift;
    }

    @Nullable
    public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
        if (!(state.getBlock() instanceof BoilerBarrelBlock)) {
            return null;
        }

        Axis axis = ((Direction)state.getValue(BoilerBarrelBlock.FACING)).getAxis();
        return direction.getAxis() == axis ? null : this.shift;
    }

    public CTContext buildContext(BlockAndTintGetter reader, BlockPos pos, BlockState state, Direction face, ContextRequirement requirement) {
        CTContext context = new CTContext();
        if (!(state.getBlock() instanceof BoilerBarrelBlock)) {
            return context;
        }

        Direction facing = (Direction)state.getValue(BoilerBarrelBlock.FACING);
        Axis axis = facing.getAxis();
        Direction pos_ = BoilerBarrelBlock.positiveOf(axis);
        boolean cPos = (Boolean)state.getValue(BoilerBarrelBlock.connectedFor(axis, pos_));
        boolean cNeg = (Boolean)state.getValue(BoilerBarrelBlock.connectedFor(axis, pos_.getOpposite()));
        boolean flip = facing.getAxisDirection() == AxisDirection.NEGATIVE;
        context.right = flip ? cNeg : cPos;
        context.left = flip ? cPos : cNeg;
        return context;
    }
}
