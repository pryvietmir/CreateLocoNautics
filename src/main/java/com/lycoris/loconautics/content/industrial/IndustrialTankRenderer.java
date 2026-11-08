package com.lycoris.loconautics.content.industrial;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public class IndustrialTankRenderer extends SafeBlockEntityRenderer<IndustrialTankBlockEntity> {
    private static final float HULL = 0.0703125F;
    private static final float CAP = 0.25195312F;
    private static final float MIN_PUDDLE = 0.0625F;
    private static final float EDGE = 1.0E-4F;

    public IndustrialTankRenderer(Context context) {
    }

    protected void renderSafe(IndustrialTankBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Level level = be.getLevel();
        if (level != null && state.is((Block)IndustrialBlocks.TANK.get()) && (Boolean)state.getValue(IndustrialTankBlock.WINDOW)) {
            IndustrialTankBlockEntity head = be.getControllerBE();
            if (head != null) {
                FluidStack fluid = head.getFluid();
                if (!fluid.isEmpty()) {
                    BlockPos pos = be.getBlockPos();
                    float fill = head.cellFill(pos.getY(), partialTicks);
                    if (!(fill <= 0.0F)) {
                        boolean[] joined = new boolean[6];

                        for (Direction direction : Direction.values()) {
                            joined[direction.get3DDataValue()] = TankMultiblock.isTank(level, pos.relative(direction));
                        }

                        Axis axis = (Axis)state.getValue(IndustrialTankBlock.AXIS);
                        float xMin = inset(joined, Direction.WEST, axis);
                        float xMax = 1.0F - inset(joined, Direction.EAST, axis);
                        float zMin = inset(joined, Direction.NORTH, axis);
                        float zMax = 1.0F - inset(joined, Direction.SOUTH, axis);
                        float floor = inset(joined, Direction.DOWN, axis);
                        float ceiling = 1.0F - inset(joined, Direction.UP, axis);
                        float range = ceiling - floor;
                        float height = Math.min(range, Math.max(fill * range, 0.0625F));
                        boolean reversed = fluid.getFluid().getFluidType().isLighterThanAir();
                        float yMin = reversed ? ceiling - height : floor;
                        float yMax = reversed ? ceiling : floor + height;
                        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
                        TextureAtlasSprite sprite = (TextureAtlasSprite)Minecraft.getInstance()
                            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                            .apply(extensions.getStillTexture(fluid));
                        int color = extensions.getTintColor(fluid);
                        int luminosity = Math.max(light >> 4 & 15, fluid.getFluid().getFluidType().getLightLevel(fluid));
                        int packedLight = light & 15728640 | luminosity << 4;
                        VertexConsumer builder = FluidRenderHelper.getFluidBuilder(buffer);
                        if (!joined[Direction.WEST.get3DDataValue()]) {
                            FluidRenderHelper.renderStillTiledFace(Direction.WEST, zMin, yMin, zMax, yMax, xMin, builder, ms, packedLight, color, sprite);
                        }

                        if (!joined[Direction.EAST.get3DDataValue()]) {
                            FluidRenderHelper.renderStillTiledFace(Direction.EAST, zMin, yMin, zMax, yMax, xMax, builder, ms, packedLight, color, sprite);
                        }

                        if (!joined[Direction.NORTH.get3DDataValue()]) {
                            FluidRenderHelper.renderStillTiledFace(Direction.NORTH, xMin, yMin, xMax, yMax, zMin, builder, ms, packedLight, color, sprite);
                        }

                        if (!joined[Direction.SOUTH.get3DDataValue()]) {
                            FluidRenderHelper.renderStillTiledFace(Direction.SOUTH, xMin, yMin, xMax, yMax, zMax, builder, ms, packedLight, color, sprite);
                        }

                        boolean bottomShared = joined[Direction.DOWN.get3DDataValue()]
                            && yMin <= floor + 1.0E-4F
                            && head.cellFill(pos.getY() - 1, partialTicks) > 0.0F;
                        if (!bottomShared) {
                            FluidRenderHelper.renderStillTiledFace(Direction.DOWN, xMin, zMin, xMax, zMax, yMin, builder, ms, packedLight, color, sprite);
                        }

                        boolean topShared = joined[Direction.UP.get3DDataValue()]
                            && yMax >= ceiling - 1.0E-4F
                            && head.cellFill(pos.getY() + 1, partialTicks) > 0.0F;
                        if (!topShared) {
                            FluidRenderHelper.renderStillTiledFace(Direction.UP, xMin, zMin, xMax, zMax, yMax, builder, ms, packedLight, color, sprite);
                        }
                    }
                }
            }
        }
    }

    private static float inset(boolean[] joined, Direction side, Axis axis) {
        if (joined[side.get3DDataValue()]) {
            return 0.0F;
        } else {
            return side.getAxis() == axis ? 0.25195312F : 0.0703125F;
        }
    }
}
