package com.lycoris.loconautics.client.connected;

import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConnectedVariantModel implements IDynamicBakedModel {
    public static final ModelProperty<ConnectedVariantModel.Variant> VARIANT = new ModelProperty();
    private final BakedModel single;
    private final BakedModel start;
    private final BakedModel middle;
    private final BakedModel end;

    public ConnectedVariantModel(BakedModel single, BakedModel start, BakedModel middle, BakedModel end) {
        this.single = single;
        this.start = start;
        this.middle = middle;
        this.end = end;
    }

    private BakedModel pick(@NotNull ModelData data) {
        ConnectedVariantModel.Variant variant = (ConnectedVariantModel.Variant)data.get(VARIANT);
        if (variant == null) {
            return this.single;
        }

        return switch (variant) {
            case SINGLE -> this.single;
            case START -> this.start;
            case MIDDLE -> this.middle;
            case END -> this.end;
        };
    }

    @NotNull
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        if (state.getBlock() instanceof ConnectedBlock block) {
            if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                return modelData;
            }

            Set<BlockPos> group = block.connectionBounds().group(block, level, state, block.groupAnchor(level, state, pos));
            Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            boolean toStart = group.contains(pos.relative(facing.getCounterClockWise()));
            boolean toEnd = group.contains(pos.relative(facing.getClockWise()));
            ConnectedVariantModel.Variant variant;
            if (toStart && toEnd) {
                variant = ConnectedVariantModel.Variant.MIDDLE;
            } else if (toEnd) {
                variant = ConnectedVariantModel.Variant.START;
            } else if (toStart) {
                variant = ConnectedVariantModel.Variant.END;
            } else {
                variant = ConnectedVariantModel.Variant.SINGLE;
            }

            return modelData.derive().with(VARIANT, variant).build();
        } else {
            return modelData;
        }
    }

    @NotNull
    public List<BakedQuad> getQuads(
        @Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand, @NotNull ModelData modelData, @Nullable RenderType renderType
    ) {
        return this.pick(modelData).getQuads(state, face, rand, ModelData.EMPTY, renderType);
    }

    @NotNull
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return this.pick(data).getRenderTypes(state, rand, ModelData.EMPTY);
    }

    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand) {
        return this.getQuads(state, face, rand, ModelData.EMPTY, null);
    }

    public boolean useAmbientOcclusion() {
        return this.single.useAmbientOcclusion();
    }

    public boolean isGui3d() {
        return this.single.isGui3d();
    }

    public boolean usesBlockLight() {
        return this.single.usesBlockLight();
    }

    public boolean isCustomRenderer() {
        return false;
    }

    @NotNull
    public ItemTransforms getTransforms() {
        return this.single.getTransforms();
    }

    @NotNull
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @NotNull
    public TextureAtlasSprite getParticleIcon() {
        return this.single.getParticleIcon();
    }

    public enum Variant {
        SINGLE,
        START,
        MIDDLE,
        END;
    }
}
