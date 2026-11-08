package com.lycoris.loconautics.client.copycat;

import com.lycoris.loconautics.client.connected.ConnectedBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CopycatBedModel implements IDynamicBakedModel {
    public static final ModelProperty<CopycatBedModel.Bed> BED = new ModelProperty();
    private static final ChunkRenderTypeSet RENDER_TYPES = ChunkRenderTypeSet.of(new RenderType[]{RenderType.solid(), RenderType.cutout()});
    private final float low;
    private final float high;
    private final float inset;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;

    public CopycatBedModel(float low, float high, float inset, TextureAtlasSprite particle, ItemTransforms transforms) {
        this.low = low;
        this.high = high;
        this.inset = inset;
        this.particle = particle;
        this.transforms = transforms;
    }

    @NotNull
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        if (state.getBlock() instanceof CopycatMaterialSource source) {
            BlockState material = source.copycatMaterial(level, pos, state);
            if (material == null) {
                return modelData;
            }

            boolean[] neighbors = new boolean[4];
            if (state.getBlock() instanceof ConnectedBlock block) {
                Set<BlockPos> group = block.connectionBounds().group(block, level, state, block.groupAnchor(level, state, pos));
                if (group.contains(pos)) {
                    for (Direction dir : Plane.HORIZONTAL) {
                        neighbors[dir.get2DDataValue()] = group.contains(pos.relative(dir));
                    }
                }
            }

            return modelData.derive().with(BED, new CopycatBedModel.Bed(material, neighbors)).build();
        } else {
            return modelData;
        }
    }

    @NotNull
    public List<BakedQuad> getQuads(
        @Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand, @NotNull ModelData modelData, @Nullable RenderType renderType
    ) {
        CopycatBedModel.Bed bed = (CopycatBedModel.Bed)modelData.get(BED);
        if (face == null && bed != null) {
            List<BakedQuad> quads = new ArrayList<>();
            CopycatQuads.crop(quads, bed.material(), rand, renderType, this.box(this.inset, 1.0F - this.inset, this.inset, 1.0F - this.inset));

            for (Direction dir : Plane.HORIZONTAL) {
                if (bed.neighbor(dir)) {
                    CopycatQuads.crop(quads, bed.material(), rand, renderType, this.arm(dir));
                    Direction next = dir.getClockWise();
                    if (bed.neighbor(next)) {
                        CopycatQuads.crop(quads, bed.material(), rand, renderType, this.corner(dir, next));
                    }
                }
            }

            return quads;
        } else {
            return Collections.emptyList();
        }
    }

    private AABB arm(Direction dir) {
        return switch (dir) {
            case NORTH -> this.box(this.inset, 1.0F - this.inset, 0.0F, this.inset);
            case SOUTH -> this.box(this.inset, 1.0F - this.inset, 1.0F - this.inset, 1.0F);
            case WEST -> this.box(0.0F, this.inset, this.inset, 1.0F - this.inset);
            default -> this.box(1.0F - this.inset, 1.0F, this.inset, 1.0F - this.inset);
        };
    }

    private AABB corner(Direction a, Direction b) {
        boolean west = a == Direction.WEST || b == Direction.WEST;
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        float minX = west ? 0.0F : 1.0F - this.inset;
        float minZ = north ? 0.0F : 1.0F - this.inset;
        return this.box(minX, minX + this.inset, minZ, minZ + this.inset);
    }

    private AABB box(float minX, float maxX, float minZ, float maxZ) {
        return new AABB(minX, this.low, minZ, maxX, this.high, maxZ);
    }

    @NotNull
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        CopycatBedModel.Bed bed = (CopycatBedModel.Bed)data.get(BED);
        return bed == null ? RENDER_TYPES : CopycatQuads.donorModel(bed.material()).getRenderTypes(bed.material(), rand, ModelData.EMPTY);
    }

    @NotNull
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction face, @NotNull RandomSource rand) {
        return Collections.emptyList();
    }

    public boolean useAmbientOcclusion() {
        return true;
    }

    public boolean isGui3d() {
        return true;
    }

    public boolean usesBlockLight() {
        return true;
    }

    public boolean isCustomRenderer() {
        return false;
    }

    @NotNull
    public ItemTransforms getTransforms() {
        return this.transforms;
    }

    @NotNull
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @NotNull
    public TextureAtlasSprite getParticleIcon() {
        return this.particle;
    }

    public record Bed(BlockState material, boolean[] neighbors) {
        public boolean neighbor(Direction dir) {
            return this.neighbors[dir.get2DDataValue()];
        }
    }
}
