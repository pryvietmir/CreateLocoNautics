package com.lycoris.loconautics.mixin.client.rope;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.client.LoconauticsSpriteShifts;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchRenderer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RopeWinchRenderer.class, remap = false)
public abstract class RopeWinchCoilMixin {
    @Inject(method = "renderComponents", at = @At("RETURN"))
    private void loconautics$renderSteelCableCoil(
        RopeWinchBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci
    ) {
        SteelCableHolderBehaviour holder = (SteelCableHolderBehaviour)be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder != null && !holder.attachedStrandIds().isEmpty() && !be.getRopeHolder().isAttached()) {
            BlockState state = be.getBlockState();
            SuperByteBuffer coil = CachedBuffers.partial(LoconauticsPartialModels.STEEL_CABLE_WINCH_COIL, state);
            coil.light(light);
            Direction facing = (Direction)state.getValue(DirectionalKineticBlock.FACING);
            boolean alongFirst = (Boolean)state.getValue(DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE);
            float speed = facing == Direction.DOWN
                ? (facing.getAxisDirection() == AxisDirection.NEGATIVE ? 1.0F : -1.0F)
                : (facing.getAxisDirection() == AxisDirection.NEGATIVE == alongFirst ? 1.0F : -1.0F);
            AbstractPulleyRenderer.scrollCoil(
                coil,
                LoconauticsSpriteShifts.STEEL_CABLE_WINCH_COIL,
                ((RopeWinchBlockEntityAccessor)be).loconautics$getClientAngle().getValue(partialTicks),
                speed
            );
            float zRotLast = alongFirst ^ facing.getAxis() == Axis.Z ? 90.0F : 0.0F;
            float yRot = AngleHelper.horizontalAngle(facing) + (!alongFirst && facing.getAxis() == Axis.Y ? 90.0F : 0.0F);
            float zRot = facing == Direction.UP ? 270.0F : (facing == Direction.DOWN ? 90.0F : 0.0F);
            ms.pushPose();
            ((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)coil.rotateCentered((float)(zRot / 180.0F * Math.PI), Direction.SOUTH))
                        .rotateCentered((float)(yRot / 180.0F * Math.PI), Direction.UP))
                    .rotateCentered((float)(zRotLast / 180.0F * Math.PI), Direction.SOUTH))
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
            ms.popPose();
        }
    }
}
