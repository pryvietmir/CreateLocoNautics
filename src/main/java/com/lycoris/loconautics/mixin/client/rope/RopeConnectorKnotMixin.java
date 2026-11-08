package com.lycoris.loconautics.mixin.client.rope;

import com.lycoris.loconautics.client.LoconauticsPartialModels;
import com.lycoris.loconautics.common.strand.StrandKind;
import com.lycoris.loconautics.content.steelcable.SteelCableHolderBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllBlocks;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlock;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorRenderer;
import java.util.UUID;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RopeConnectorRenderer.class, remap = false)
public abstract class RopeConnectorKnotMixin {
    @Inject(method = "renderSafe", at = @At("RETURN"))
    private void loconautics$renderSteelCableKnot(
        RopeConnectorBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci
    ) {
        SteelCableHolderBehaviour holder = (SteelCableHolderBehaviour)be.getBehaviour(SteelCableHolderBehaviour.TYPE);
        if (holder != null && !holder.attachedStrandIds().isEmpty()) {
            boolean hose = false;
            boolean cable = false;

            for (UUID id : holder.attachedStrandIds()) {
                if (StrandKind.HOSE_LINK.clientStrands().get(id) != null) {
                    hose = true;
                } else {
                    cable = true;
                }
            }

            if (cable) {
                drawKnot(LoconauticsPartialModels.STEEL_CABLE_CONNECTOR_KNOT, be, ms, buffer, light);
            }

            if (hose) {
                drawKnot(LoconauticsPartialModels.HOSE_CONNECTOR_KNOT, be, ms, buffer, light);
            }
        }
    }

    @Unique
    private static void drawKnot(PartialModel model, RopeConnectorBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        SuperByteBuffer knot = CachedBuffers.partialFacing(model, AllBlocks.ROPE.getDefaultState(), Direction.NORTH);
        BlockPos pos = be.getBlockPos();
        BlockState state = be.getBlockState();
        Direction facing = (Direction)state.getValue(RopeConnectorBlock.FACING);
        boolean alongFirst = (Boolean)state.getValue(RopeConnectorBlock.AXIS_ALONG_FIRST_COORDINATE);
        float zRotLast = alongFirst ^ facing.getAxis() == Axis.Z ? 90.0F : 0.0F;
        float yRot = AngleHelper.horizontalAngle(facing) + (!alongFirst && facing.getAxis() == Axis.Y ? 90.0F : 0.0F);
        float zRot = facing == Direction.UP ? 270.0F : (facing == Direction.DOWN ? 90.0F : 0.0F);
        ((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)((SuperByteBuffer)knot.light(light)
                                .translate(be.getVisualAttachmentPoint(pos, state).subtract(pos.getCenter())))
                            .rotateCentered((float)(zRot / 180.0F * Math.PI), Direction.SOUTH))
                        .rotateCentered((float)(yRot / 180.0F * Math.PI), Direction.UP))
                    .rotateCentered((float)(zRotLast / 180.0F * Math.PI), Direction.SOUTH))
                .rotateCentered((float) (Math.PI / 2), Direction.UP))
            .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}
