package com.lycoris.loconautics.content.turntablebearing;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class TurntablePlateRenderer extends KineticBlockEntityRenderer<TurntablePlateBlockEntity> {
    public TurntablePlateRenderer(Context context) {
        super(context);
    }

    protected SuperByteBuffer getRotatedModel(TurntablePlateBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(LoconauticsPartialModels.TURNTABLE_SHAFT, state, (Direction)state.getValue(TurntablePlateBlock.FACING));
    }
}
